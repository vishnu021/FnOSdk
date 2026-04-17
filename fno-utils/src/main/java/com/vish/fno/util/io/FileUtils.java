package com.vish.fno.util.io;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vish.fno.model.Candle;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.util.FnoConstants;
import com.vish.fno.util.JsonUtils;
import com.vish.fno.util.time.TimeUtils;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.regex.Pattern;
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

    /** Pre-compiled pattern for replacing whitespace in file paths (avoids Pattern.compile per call). */
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ZoneId DATE_ZONE = ZoneId.of("Asia/Kolkata");

    private final ObjectMapper indentedMapper;
    private final ObjectMapper mapper;
    private final Map<String, Queue<String>> tickBuffer = new ConcurrentHashMap<>();

    /**
     * Cache of directories already created this session. Avoids repeated {@code Files.createDirectories()}
     * calls that throw {@code FileAlreadyExistsException} internally on Windows (~5M exceptions/day in JFR).
     *
     * <p>Declared as {@code Map<String, Boolean>} (not {@code Set<String>}) so {@link #createDirectoryOnce}
     * can use {@link ConcurrentHashMap#computeIfAbsent computeIfAbsent} — see that method's Javadoc for
     * the race condition the previous {@code Set.add()}-based gate had. The {@code Boolean.TRUE} value is
     * a sentinel; only key presence matters.
     */
    private final Map<String, Boolean> createdDirectories = new ConcurrentHashMap<>();

    /** Cache of sanitized file paths per symbol (avoids re-computing date + regex replace per flush). */
    private final Map<String, String> symbolFilePathCache = new ConcurrentHashMap<>();

    /** Reusable writers keyed by file path. Avoids creating new FileWriter/BufferedWriter/PrintWriter
     *  per flush (~40 writer triplets/sec with 200 symbols). Cleared on date change and shutdown. */
    private final Map<String, PrintWriter> writerCache = new ConcurrentHashMap<>();
    private volatile String cachedDateFolder = "";
    private volatile long cachedDateEpochDay = -1L;
    private volatile long lastFlushTimeMs = System.currentTimeMillis();

    /** IST offset from UTC in milliseconds (+5:30 = 19,800,000 ms). Used for zero-allocation epoch day check. */
    private static final long IST_OFFSET_MS = 19_800_000L;
    final String filePath = Paths.get(".").normalize().toAbsolutePath() + File.separator + directory + File.separator;
    final String tickPath = Paths.get(".").normalize().toAbsolutePath() + File.separator + tick_directory + File.separator;
    final int bufferLength;

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
            Path p = Paths.get(path);
            if (!Files.exists(p)) {
                Files.createDirectories(p);
            }
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

    @SuppressWarnings("PMD.CloseResource") // Writer is managed by writerCache; closed via closeAllWriters()
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

        String path = getOrCreateTickFilePath(symbol);
        PrintWriter out = getOrCreateWriter(path);

        if (out == null) {
            log.warn("Failed to flush {} ticks for {} - no writer available", ticks.size(), symbol);
            return;
        }

        for (String tick : ticks) {
            out.println(tick);
        }
        out.flush();

        if (out.checkError()) {
            log.warn("Writer error for {}, evicting cached writer", symbol);
            out.close();
            writerCache.remove(path);
        }
    }

    private PrintWriter getOrCreateWriter(String path) {
        PrintWriter existing = writerCache.get(path);
        if (existing != null) {
            return existing;
        }
        try {
            PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(path, true)));
            PrintWriter previous = writerCache.putIfAbsent(path, writer);
            if (previous != null) {
                writer.close();
                return previous;
            }
            return writer;
        } catch (IOException e) {
            log.warn("Failed to open writer for {}", path, e);
            return null;
        }
    }

    /**
     * Returns the sanitized file path for a symbol's tick file, creating the directory once.
     *
     * <p>Caches both the directory creation and the full file path per symbol per date.
     * On date change, the cache is cleared to create new date-based directories.
     * This avoids two hot-path performance issues identified via JFR profiling (Mar 17, 2026):
     * <ul>
     *   <li>{@code Files.createDirectories()} throwing ~5M {@code FileAlreadyExistsException}/day on Windows</li>
     *   <li>{@code String.replaceAll()} compiling a new {@code Pattern} on every call (~10M/day)</li>
     * </ul>
     */
    private String getOrCreateTickFilePath(String symbol) {
        // Zero-allocation epoch day check: derive IST day from system clock without DateTime objects.
        // The date string is only recomputed once per trading day when the epoch day changes.
        long nowEpochDay = (System.currentTimeMillis() + IST_OFFSET_MS) / 86_400_000L;
        if (nowEpochDay != cachedDateEpochDay) {
            String dateFolder = getFormattedDate(new Date());
            symbolFilePathCache.clear();
            closeAllWriters();
            cachedDateFolder = dateFolder;
            cachedDateEpochDay = nowEpochDay;
        }

        return symbolFilePathCache.computeIfAbsent(symbol, s -> {
            String folderPath = tickPath + cachedDateFolder;
            createDirectoryOnce(folderPath);
            return WHITESPACE_PATTERN.matcher(folderPath + File.separator + s + ".txt").replaceAll("_");
        });
    }

    /**
     * Creates a directory only if it hasn't been created in this session.
     *
     * <p><b>Why this exists:</b> avoids repeated {@code Files.createDirectories()} calls
     * that internally throw {@code FileAlreadyExistsException} for each existing path
     * component on Windows (~5M exceptions/day in JFR before this cache was added).
     *
     * <p><b>Why {@code computeIfAbsent} (not {@code Set.add}):</b> the previous implementation
     * used {@code createdDirectories.add(path)} as a "winner takes the work" gate — only the
     * thread whose {@code add} returned {@code true} called {@code Files.createDirectories}.
     * That had a TOCTOU race: <em>losing</em> threads got {@code false} immediately and
     * proceeded to {@link #getOrCreateWriter} <b>before</b> the winning thread had actually
     * created the directory, hitting {@code FileNotFoundException} when {@code FileWriter}
     * tried to open the file. This surfaced on the first tick of every trading day, when
     * many symbol-flush virtual threads race on the brand-new {@code tick/YYYY-MM-DD} folder
     * — see prod incident on 2026-04-17 with 1806 subscribed tokens.
     *
     * <p>{@link ConcurrentHashMap#computeIfAbsent} fixes this because the {@code remappingFunction}
     * is invoked atomically <b>under the bin lock</b> (per CHM javadoc: "the entire method
     * invocation is performed atomically"). Threads racing on the same key block until the
     * lambda completes, so by the time {@code computeIfAbsent} returns, the directory is
     * guaranteed to exist for every caller — winner and losers alike.
     *
     * <p><b>Why a {@code Map<String, Boolean>} instead of a {@code Set<String>}:</b>
     * {@code ConcurrentHashMap.newKeySet()} exposes no atomic compute-and-block primitive
     * equivalent to {@code computeIfAbsent}. The {@code Boolean.TRUE} value is a sentinel —
     * we never read it; the key's presence is what matters.
     *
     * @param path absolute folder path that must exist before any caller proceeds
     */
    private void createDirectoryOnce(String path) {
        createdDirectories.computeIfAbsent(path, p -> {
            createDirectoryIfNotExist(p);
            return Boolean.TRUE;
        });
    }

    public void flushAllTickBuffers() {
        tickBuffer.keySet().forEach(this::flushTickBuffer);
    }

    /**
     * Closes all cached writers. Must be called at application shutdown to ensure
     * all buffered data is flushed and file handles are released.
     */
    public void closeAllWriters() {
        writerCache.forEach((path, writer) -> writer.close());
        writerCache.clear();
    }

    private String getFormattedDate(Date date) {
        return date.toInstant().atZone(DATE_ZONE).toLocalDate().format(DATE_FORMATTER);
    }

    public void logCompletedOrder(ActiveOrder order) {
        try {
            createDirectoryIfNotExist(ORDER_LOG_FOLDER);
            String fileName = String.format("%s/%s-%s-%s-%d.json",
                    ORDER_LOG_FOLDER,
                    order.getOrderRequest().getTag(),
                    order.getOrderRequest().getIndex(),
                    TimeUtils.getStringDate(order.getOrderRequest().getDate()),
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

    @SuppressWarnings("PMD.AvoidCatchingGenericException") // NumberFormatException from malformed indicator files
    private static List<Double> getIndicatorData(String filePath) {
        try {
            String smaValues = readFile(filePath);
            return Arrays.stream(smaValues.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .mapMulti((String s, Consumer<Double> consumer) -> {
                        try {
                            consumer.accept(Double.parseDouble(s));
                        } catch (NumberFormatException e) {
                            log.warn("Skipping non-numeric indicator value: '{}'", s);
                        }
                    })
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
        sb.append(order.getOrderRequest().getIndex())
                .append(',').append(' ').append(getStringDate(order.getOrderRequest().getDate()))
                .append(' ').append(order.getEntryTimeStamp())
                .append(',').append(' ').append(getStringDate(order.getOrderRequest().getDate()))
                .append(' ').append(order.getExitTimeStamp())
                .append(',').append(' ').append(roundTo5Paise(order.getOrderRequest().getBuyThreshold()))
                .append(',').append(' ').append(roundTo5Paise(order.getBuyPrice()))
                .append(',').append(' ').append(roundTo5Paise(order.getOrderRequest().getTarget().first()))
                .append(',').append(' ').append(roundTo5Paise(order.getSellPrice()))
                .append(',').append(' ').append(roundTo5Paise(order.getStopLoss()))
                .append(',').append(' ').append(roundTo5Paise(order.getProfit()))
                .append(',').append(' ').append(roundTo5Paise(order.getBuyQuantity()));

        if (order.isCallOrder()) {
            sb.append(',').append(' ').append(roundTo5Paise(order.getOrderRequest().getTarget().first() - order.getOrderRequest().getBuyThreshold()));
        } else {
            sb.append(',').append(' ').append(roundTo5Paise(order.getOrderRequest().getBuyThreshold() - order.getOrderRequest().getTarget().first()));
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
                .append("index='").append(order.getOrderRequest().getIndex()).append("'")
                .append(",\ttag=").append(order.getOrderRequest().getTag())
                .append(",\tentry=").append(getStringDateTime(order.getOrderRequest().getDate()))
                .append(",\texit=").append(order.getExitTimeStamp())
                .append(",\tbuy=").append(roundTo5Paise(order.getBuyPrice()))
                .append(",\ttarget=").append(order.getOrderRequest().getTarget())
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
