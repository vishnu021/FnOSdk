package com.vish.fno.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vish.fno.model.Candle;
import com.vish.fno.model.order.activeorder.ActiveIndexOrder;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.activeorder.OptionBasedActiveOrder;
import com.vish.fno.model.order.activeorder.TickBasedActiveOrder;
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
@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.AvoidCatchingGenericException", "PMD.AvoidThrowingRawExceptionTypes"})
public final class FileUtils implements FnoConstants {

    private static final ObjectMapper staticMapper = new ObjectMapper();
    private static final String CANDLESTICK_PATH = "data";
    private static final String ORDER_LOG_FOLDER = "orderLog";
    private static final int ESTIMATED_BUFFER_SIZE = 125;
    private static final int CSV_HEADER_BUFFER_SIZE = 250;
    private static final int TICK_BUFFER_SIZE = 100;

    private final ObjectMapper indentedMapper;
    private final ObjectMapper mapper;
    private final Map<String, Queue<String>> tickBuffer = new ConcurrentHashMap<>();
    String filePath = Paths.get(".").normalize().toAbsolutePath() + File.separator + directory + File.separator;
    String tickPath = Paths.get(".").normalize().toAbsolutePath() + File.separator + tick_directory + File.separator;
    int bufferLength;

    public FileUtils() {
        mapper = new ObjectMapper();
        indentedMapper = new ObjectMapper();
        indentedMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        indentedMapper.enable(SerializationFeature.INDENT_OUTPUT);
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
            Queue<String> queue = tickBuffer.computeIfAbsent(symbol, k -> new ConcurrentLinkedQueue<>());
            queue.add(jsonString);
            if (queue.size() >= TICK_BUFFER_SIZE) {
                flushTickBuffer(symbol);
            }
        } catch (IOException e) {
            log.warn("Failed to serialize tick for {}", symbol, e);
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

    private String candleFileName(String instrument, Date fromDate) {
        createDirectoryIfNotExist(filePath + getFormattedDate(fromDate));
        return filePath + getFormattedDate(fromDate) + File.separator + instrument + ".json";
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
     * Handles type-specific formatting for ActiveIndexOrder, TickBasedActiveOrder, and OptionBasedActiveOrder.
     *
     * @param order ActiveOrder instance to convert
     * @return CSV formatted string
     */
    public static String toCSV(ActiveOrder order) {
        if (order instanceof ActiveIndexOrder) {
            return formatActiveIndexOrderToCSV((ActiveIndexOrder) order);
        } else if (order instanceof TickBasedActiveOrder) {
            return formatTickBasedActiveOrderToCSV((TickBasedActiveOrder) order);
        } else if (order instanceof OptionBasedActiveOrder) {
            return formatOptionBasedActiveOrderToCSV((OptionBasedActiveOrder) order);
        } else {
            throw new IllegalArgumentException("Unsupported ActiveOrder type: " + order.getClass().getName());
        }
    }

    /**
     * Generates order log string for ActiveOrder.
     * Handles type-specific formatting for ActiveIndexOrder, TickBasedActiveOrder, and OptionBasedActiveOrder.
     *
     * @param order ActiveOrder instance to log
     * @return Formatted order log string
     */
    public static String orderLog(ActiveOrder order) {
        if (order instanceof ActiveIndexOrder) {
            return formatActiveIndexOrderLog((ActiveIndexOrder) order);
        } else if (order instanceof TickBasedActiveOrder) {
            return formatTickBasedActiveOrderLog((TickBasedActiveOrder) order);
        } else if (order instanceof OptionBasedActiveOrder) {
            return formatOptionBasedActiveOrderLog((OptionBasedActiveOrder) order);
        } else {
            throw new IllegalArgumentException("Unsupported ActiveOrder type: " + order.getClass().getName());
        }
    }

    // CPD-OFF
    private static String formatActiveIndexOrderToCSV(ActiveIndexOrder order) {
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

    private static String formatTickBasedActiveOrderToCSV(TickBasedActiveOrder order) {
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

    private static String formatOptionBasedActiveOrderToCSV(OptionBasedActiveOrder order) {
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
                .append(',').append(' ').append(roundTo5Paise(order.getBuyQuantity()))
                .append(',').append(' ').append(roundTo5Paise(order.getTarget() - order.getBuyThreshold()));

        if (order.getExtraData() != null) {
            for (String key : order.getExtraData().keySet()) {
                sb.append(',').append(' ').append(order.getExtraData().get(key));
            }
        }
        return sb.toString();
    }

    private static String formatActiveIndexOrderLog(ActiveIndexOrder order) {
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

    private static String formatTickBasedActiveOrderLog(TickBasedActiveOrder order) {
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

    private static String formatOptionBasedActiveOrderLog(OptionBasedActiveOrder order) {
        final StringBuilder sb = new StringBuilder(ESTIMATED_BUFFER_SIZE);
        sb.append("OrderLog{")
                .append("index='").append(order.getIndex()).append("'")
                .append(",\ttag=").append(order.getTag())
                .append(",\tentry=").append(getStringDateTime(order.getDate()))
                .append(",\texit=").append(order.getExitTimeStamp())
                .append(",\tbuy=").append(roundTo5Paise(order.getBuyPrice()))
                .append(",\ttarget=").append(roundTo5Paise(order.getTarget()))
                .append(",\tsell=").append(roundTo5Paise(order.getSellPrice()));

        if (order.getProfit() > 0) {
            sb.append(",\tprofit=");
        } else {
            sb.append(",\tloss=");
        }

        sb.append(roundTo5Paise(order.getProfit())).append("}");

        return sb.toString();
    }
    // CPD-ON
}
