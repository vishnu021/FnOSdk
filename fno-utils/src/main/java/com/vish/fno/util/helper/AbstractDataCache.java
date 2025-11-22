package com.vish.fno.util.helper;

import com.vish.fno.model.Ticker;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class AbstractDataCache implements DataCache {
    protected final Map<String, Ticker> latestTicks;
    protected final Map<String, List<Ticker>> ticksCache;

    public AbstractDataCache() {
        this.latestTicks = new ConcurrentHashMap<>();
        this.ticksCache = new ConcurrentHashMap<>();
    }

    public void appendTick(String symbol, Ticker tick) {
        latestTicks.put(symbol, tick);
        ticksCache.computeIfAbsent(symbol, k -> new CopyOnWriteArrayList<>()).add(tick);
    }

    public Ticker getLatestTick(String symbol) {
        return latestTicks.get(symbol);
    }

    public List<Ticker> getTicks(String symbol) {
        return ticksCache.get(symbol);
    }
}
