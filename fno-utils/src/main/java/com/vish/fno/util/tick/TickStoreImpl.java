package com.vish.fno.util.tick;

import com.vish.fno.model.Ticker;
import com.vish.fno.util.time.TimeSource;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe tick storage using pre-allocated circular buffers.
 *
 * <p>Replaces the previous {@code AbstractDataCache} (which was an abstract superclass
 * of {@code DataCacheImpl}). Now a standalone, concrete class with its own date-boundary
 * lifecycle — tick caches clear when the trading day changes, independent of candle operations.
 *
 * <p>Thread safety: single-writer (WebSocket tick-processing thread) /
 * multiple-reader (strategy threads). Uses {@link ConcurrentHashMap} for the symbol-keyed
 * maps and volatile fields in {@link TickCircularBuffer} for cross-thread visibility.
 *
 * @see TickStore interface this class implements
 * @see TickCircularBuffer zero-allocation circular buffer for per-symbol tick history
 */
@Slf4j
public class TickStoreImpl implements TickStore {

    private static final int MAX_TICKS_PER_SYMBOL = 500;

    private final Map<String, Ticker> latestTicks;
    private final Map<String, TickCircularBuffer> ticksCache;
    private final TimeSource timeSource;

    /**
     * Tracks the last trading day for which ticks are cached.
     * On the first tick of a new day, all tick caches are cleared.
     * Volatile for cross-thread visibility (WebSocket writer vs strategy readers).
     */
    private volatile String lastTickDate;

    public TickStoreImpl(TimeSource timeSource) {
        this.latestTicks = new ConcurrentHashMap<>();
        this.ticksCache = new ConcurrentHashMap<>();
        this.timeSource = timeSource;
    }

    @Override
    public void appendTick(String symbol, Ticker tick) {
        clearOnDateChange();

        latestTicks.put(symbol, tick);

        // Zero-allocation circular buffer: O(1) add, overwrites oldest when full
        TickCircularBuffer buffer = ticksCache.computeIfAbsent(
                symbol, k -> new TickCircularBuffer(MAX_TICKS_PER_SYMBOL));
        buffer.add(tick);
    }

    @Override
    public Ticker getLatestTick(String symbol) {
        return latestTicks.get(symbol);
    }

    @Override
    public List<Ticker> getTicks(String symbol) {
        TickCircularBuffer buffer = ticksCache.get(symbol);
        // Returns lightweight unmodifiable List view — no element copying
        return buffer == null ? List.of() : buffer.asList();
    }

    /**
     * Clears all tick caches when the trading day changes.
     *
     * <p>Called on every {@code appendTick} — the fast path (same day) is a single
     * volatile read + String comparison. The slow path (date change) clears both maps.
     *
     * <p>Single-writer safety: only the WebSocket tick-processing thread calls appendTick,
     * so no lock is needed for the clear operation itself.
     */
    private void clearOnDateChange() {
        String currentDate = timeSource.getTodaysDateString();
        if (!currentDate.equals(lastTickDate)) {
            log.info("Tick date changed from {} to {} — clearing tick caches", lastTickDate, currentDate);
            latestTicks.clear();
            ticksCache.values().forEach(TickCircularBuffer::clear);
            ticksCache.clear();
            lastTickDate = currentDate;
        }
    }
}
