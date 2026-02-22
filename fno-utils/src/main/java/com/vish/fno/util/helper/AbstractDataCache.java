package com.vish.fno.util.helper;

import com.vish.fno.model.Ticker;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

abstract class AbstractDataCache implements DataCache {
    private static final int MAX_TICKS_PER_SYMBOL = 500;

    protected final Map<String, Ticker> latestTicks;
    protected final Map<String, TickCircularBuffer> ticksCache;

    public AbstractDataCache() {
        this.latestTicks = new ConcurrentHashMap<>();
        this.ticksCache = new ConcurrentHashMap<>();
    }

    public void appendTick(String symbol, Ticker tick) {
        latestTicks.put(symbol, tick);

        // Zero-allocation circular buffer: O(1) add, overwrites oldest when full
        TickCircularBuffer buffer = ticksCache.computeIfAbsent(
                symbol, k -> new TickCircularBuffer(MAX_TICKS_PER_SYMBOL));
        buffer.add(tick);
    }

    public Ticker getLatestTick(String symbol) {
        return latestTicks.get(symbol);
    }

    public List<Ticker> getTicks(String symbol) {
        TickCircularBuffer buffer = ticksCache.get(symbol);
        // Returns lightweight unmodifiable List view — no element copying
        return buffer == null ? List.of() : buffer.asList();
    }

    protected void clearTickCache() {
        latestTicks.clear();
        ticksCache.values().forEach(TickCircularBuffer::clear);
        ticksCache.clear();
    }
}
