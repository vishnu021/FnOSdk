package com.vish.fno.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vish.fno.model.Candle;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import lombok.extern.slf4j.Slf4j;

import static com.vish.fno.model.util.ModelUtils.getStringDate;
import static com.vish.fno.model.util.ModelUtils.getStringDateTime;
import static com.vish.fno.model.util.ModelUtils.roundTo5Paise;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@SuppressWarnings({"PMD.AvoidCatchingGenericException", "PMD.AvoidThrowingRawExceptionTypes"})
public final class FileUtils implements FnoConstants {

    // VT-safe ObjectMapper: uses shared bounded pool instead of ThreadLocal BufferRecycler
    private static final ObjectMapper staticMapper = JsonUtils.createObjectMapper();
    private static final String CANDLESTICK_PATH = "data";
    private static final String ORDER_LOG_FOLDER = "orderLog";
    private static final int ESTIMATED_BUFFER_SIZE = 125;
    private static final int CSV_HEADER_BUFFER_SIZE = 250;
    private static final int TICK_BUFFER_SIZE = 100;
    /**
     * Maximum time (ms) between flushes. Prevents partial buffers from lingering
     * indefinitely when a symbol stops ticking before reaching {@link #TICK_BUFFER_SIZE}.
     * Without this, symbols with fewer than 100 ticks would never flush until shutdown.
     */
    private static final long FLUSH_INTERVAL_MS = 5000;

    private final ObjectMapper indentedMapper;
    private final ObjectMapper mapper;
    private final Map<String, Queue<String>> tickBuffer = new ConcurrentHashMap<>();
    private volatile long lastFlushTimeMs = System.currentTimeMillis();
    String filePath = Paths.get(".").normalize().toAbsolutePath() + File.separator + directory + File.separator;
    String tickPath = Paths.get(".").normalize().toAbsolutePath() + File.separator + tick_directory + File.separator;
    int bufferLength;

    public FileUtils() {
        // VT-safe ObjectMappers: use shared bounded pool instead of ThreadLocal BufferRecycler
        mapper = JsonUtils.createObjectMapper();
        indentedMapper = JsonUtils.createIndentedObjectMapper();
        bufferLength = 0;
        createDirectoryIfNotExist(filePath);
    }

    public void saveCandlestickData(List<Candle> candles, String symbol, String date) {
        String path = String.format("%s/%s_%s.json", CANDLESTICK_PATH, symbol, date);
        try {
            createDirectoryIfNotExist(CANDLESTICK_PATH);
            indentedMapper.writeValue(new File(path), candles);
            log.trace("Saved Candlestick data to {}", path);
        } catch (IOException e) {
            log.error("Failed to save candlestick data", e);
        }
    }

    public void createDirectoryIfNotExist(String path) {
        try {
            Files.createDirectories(Paths.get(path));
        } catch (IOException e) {
            log.error("Failed to create directory to path : {}", path, e);
        }
    }

    public void saveTickData(String symbol, Object tick) {
        String path = filePath + getFormattedDate(new Date()) + symbol + ".txt";
        createDirectoryIfNotExist(path);
        try {
            indentedMapper.writeValue(new File(path), tick);
        } catch (IOException e) {
            log.warn("Failed to persist tick value", e);
        }
    }

    public void appendTickToFile(String symbol, Object tick) {
        try {
            String jsonString = mapper.writeValueAsString(tick);
            appendSerializedTickToFile(symbol, jsonString);
        } catch (IOException e) {
            log.warn("Failed to serialize tick for {}", symbol, e);
        }
    }

    /**
     * Append a pre-serialized JSON string to the tick buffer for the given symbol.
     *
     * <p>This method exists to support caller-thread serialization: the caller serializes
     * the tick to JSON on its own thread (reusing Jackson's {@code BufferRecycler} via
     * ThreadLocal), then passes the result here. This avoids creating a new
     * {@code BufferRecycler} per virtual thread — the root cause of ~500MB/hr old-gen
     * memory pressure observed in production (Feb 2026, JFR analysis). Not a classical
     * leak (SoftReference-wrapped, eventually GC-eligible), but allocation rate outpaced
     * collection, causing 60-118ms GC pauses.
     *
     * <p>The buffer flushes to disk when either:
     * <ul>
     *   <li>The per-symbol queue reaches {@link #TICK_BUFFER_SIZE} (100 ticks), or</li>
     *   <li>{@link #FLUSH_INTERVAL_MS} (5s) has elapsed since the last flush of any symbol,
     *       preventing partial buffers from lingering indefinitely</li>
     * </ul>
     *
     * @param symbol the instrument symbol (e.g., "NIFTY_50")
     * @param jsonString pre-serialized JSON string of the tick
     * @see #appendTickToFile(String, Object) the original method (serializes internally)
     */
    public void appendSerializedTickToFile(String symbol, String jsonString) {
        Queue<String> queue = tickBuffer.computeIfAbsent(symbol, k -> new ConcurrentLinkedQueue<>());
        queue.add(jsonString);
        if (queue.size() >= TICK_BUFFER_SIZE) {
            flushTickBuffer(symbol);
            lastFlushTimeMs = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - lastFlushTimeMs > FLUSH_INTERVAL_MS) {
            // Time-based flush: prevents partial buffers from lingering when a symbol
            // stops ticking before reaching TICK_BUFFER_SIZE
            flushAllTickBuffers();
            lastFlushTimeMs = System.currentTimeMillis();
        }
    }

    public void flushTickBuffer(String symbol) {
        Queue<String> queue = tickBuffer.get(symbol);
        if (queue == null || queue.isEmpty()) {
            return;
        }

        List<String> ticks = new ArrayList<>();
        String item;
        while ((item = queue.poll()) != null) {
            ticks.add(item);
        }
        if (ticks.isEmpty()) {
            return;
        }

        String folderPath = tickPath + getFormattedDate(new Date());
        createDirectoryIfNotExist(folderPath);
        String path = (folderPath + File.separator + symbol + ".txt").replaceAll("\\s", "_");

        try (FileWriter fw = new FileWriter(path, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            for (String tick : ticks) {
                out.println(tick);
            }
        } catch (IOException e) {
            log.warn("Failed to flush {} ticks for {}", ticks.size(), symbol, e);
        }
    }

    public void flushAllTickBuffers() {
        tickBuffer.keySet().forEach(this::flushTickBuffer);
    }

    private String getFormattedDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(date);
    }

    public void logCompletedOrder(ActiveOrder order) {
        try {
            createDirectoryIfNotExist(ORDER_LOG_FOLDER);
            String fileName = String.format("%s/%s-%s-%s-%d.json",
                    ORDER_LOG_FOLDER,
                    order.getTag(),
                    order.getIndex(),
                    TimeUtils.getStringDate(order.getDate()),
                    (System.currentTimeMillis() % 100_000));
            try (FileWriter fileWriter = new FileWriter(fileName, true);
                 BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
                 PrintWriter out = new PrintWriter(bufferedWriter)) {
                out.println(indentedMapper.writeValueAsString(order));
            }
        } catch (Exception e) {
            try {
                log.error("Failed to persist order log : {}", indentedMapper.writeValueAsString(order), e);
            } catch (JsonProcessingException ex) {
                log.error("Failed to convert order log to json.", e);
            }
        }
    }

    public static List<Candle> getCandleData(String filePath) {
        try {
            String candles = readFile(filePath);
            return staticMapper.readValue(candles, new TypeReference<List<Candle>>(){});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Candle> getPrevDayCandleData(String filePath) {
        try {
            String candles = readFile(filePath);
            return staticMapper.readValue(candles, new TypeReference<List<Candle>>(){});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Double> getSmaData(String fileName) {
        return getIndicatorData(fileName);
    }

    public static List<Double> getEmaData(String fileName) {
        return getIndicatorData(fileName);
    }

    public static List<Double> getBBData(String fileName) {
        return getIndicatorData(fileName);
    }

    private static List<Double> getIndicatorData(String filePath) {
        try {
            String smaValues = readFile(filePath);
            return Arrays.stream(smaValues.split("\n"))
                    .filter(s -> !s.isBlank())
                    .map(Double::parseDouble)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String readFile(String filename) throws IOException {
        Path path = Paths.get(filename);
        log.info("reading file from path : {}", path.toAbsolutePath());
        try (Stream<String> lines = Files.lines(path)) {
            return lines.collect(Collectors.joining("\n"));
        }
    }

    // --- ActiveOrder formatting methods (merged from ActiveOrderFormatter) ---

    /**
     * Generates CSV header for ActiveOrder data.
     *
     * @param order ActiveOrder instance to extract extra data keys from
     * @return CSV header string
     */
    public static String csvHeader(ActiveOrder order) {
        final StringBuilder sb = new StringBuilder(CSV_HEADER_BUFFER_SIZE);

        sb.append("symbol").append(",")
                .append("entryTimeStamp").append(",")
                .append("exitTimeStamp").append(",")
                .append("buyThreshold").append(",")
                .append("buyPrice").append(",")
                .append("target").append(",")
                .append("sellPrice").append(",")
                .append("stopLoss").append(",")
                .append("profit").append(",")
                .append("quantity").append(",")
                .append("reward").append(",")
                .append("call");

        if (order.getExtraData() != null) {
            for (String key : order.getExtraData().keySet()) {
                sb.append(",").append(key);
            }
        }
        return sb.toString();
    }

    /**
     * Converts ActiveOrder to CSV format.
     * Uses the ActiveOrder interface — no concrete type dispatch needed.
     *
     * @param order ActiveOrder instance to convert
     * @return CSV formatted string
     */
    public static String toCSV(ActiveOrder order) {
        final StringBuilder sb = new StringBuilder(ESTIMATED_BUFFER_SIZE);
        sb.append(order.getIndex())
                .append(',').append(' ').append(getStringDate(order.getDate()))
                .append(' ').append(order.getEntryTimeStamp())
                .append(',').append(' ').append(getStringDate(order.getDate()))
                .append(' ').append(order.getExitTimeStamp())
                .append(',').append(' ').append(roundTo5Paise(order.getBuyThreshold()))
                .append(',').append(' ').append(roundTo5Paise(order.getBuyPrice()))
                .append(',').append(' ').append(roundTo5Paise(order.getTarget()))
                .append(',').append(' ').append(roundTo5Paise(order.getSellPrice()))
                .append(',').append(' ').append(roundTo5Paise(order.getStopLoss()))
                .append(',').append(' ').append(roundTo5Paise(order.getProfit()))
                .append(',').append(' ').append(roundTo5Paise(order.getBuyQuantity()));

        if (order.isCallOrder()) {
            sb.append(',').append(' ').append(roundTo5Paise(order.getTarget() - order.getBuyThreshold()));
        } else {
            sb.append(',').append(' ').append(roundTo5Paise(order.getBuyThreshold() - order.getTarget()));
        }
        sb.append(',').append(' ').append(order.isCallOrder());
        if (order.getExtraData() != null) {
            for (String key : order.getExtraData().keySet()) {
                sb.append(',').append(' ').append(order.getExtraData().get(key));
            }
        }
        return sb.toString();
    }

    /**
     * Generates order log string for ActiveOrder.
     * Uses the ActiveOrder interface — no concrete type dispatch needed.
     *
     * @param order ActiveOrder instance to log
     * @return Formatted order log string
     */
    public static String orderLog(ActiveOrder order) {
        final StringBuilder sb = new StringBuilder(ESTIMATED_BUFFER_SIZE);
        sb.append("OrderLog{")
                .append("index='").append(order.getIndex()).append("'")
                .append(",\ttag=").append(order.getTag())
                .append(",\tentry=").append(getStringDateTime(order.getDate()))
                .append(",\texit=").append(order.getExitTimeStamp())
                .append(",\tbuy=").append(roundTo5Paise(order.getBuyPrice()))
                .append(",\ttarget=").append(roundTo5Paise(order.getTarget()))
                .append(",\tsell=").append(roundTo5Paise(order.getSellPrice()))
                .append(",\tcall=").append(order.isCallOrder());

        if (order.getProfit() > 0) {
            sb.append(",\tprofit=");
        } else {
            sb.append(",\tloss=");
        }

        sb.append(roundTo5Paise(order.getProfit())).append("}");

        return sb.toString();
    }
}
