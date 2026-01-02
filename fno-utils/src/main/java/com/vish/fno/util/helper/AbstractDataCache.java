package com.vish.fno.util.helper;

import com.vish.fno.model.Ticker;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public abstract class AbstractDataCache implements DataCache {
    private static final int MAX_TICKS_PER_SYMBOL = 500;

    protected final Map<String, Ticker> latestTicks;
    protected final Map<String, Deque<Ticker>> ticksCache;

    public AbstractDataCache() {
        this.latestTicks = new ConcurrentHashMap<>();
        this.ticksCache = new ConcurrentHashMap<>();
    }

    public void appendTick(String symbol, Ticker tick) {
        latestTicks.put(symbol, tick);

        // Thread-safe deque: O(1) addLast and O(1) removeFirst
        Deque<Ticker> ticks = ticksCache.computeIfAbsent(symbol, k -> new ConcurrentLinkedDeque<>());
        ticks.addLast(tick);

        // Keep only latest 100 ticks to prevent memory overflow
        if (ticks.size() > MAX_TICKS_PER_SYMBOL) {
            ticks.removeFirst();
        }
    }

    public Ticker getLatestTick(String symbol) {
        return latestTicks.get(symbol);
    }

    public List<Ticker> getTicks(String symbol) {
        Deque<Ticker> deque = ticksCache.get(symbol);
        // Convert Deque to List for backward compatibility
        return deque == null ? List.of() : new ArrayList<>(deque);
    }
}
