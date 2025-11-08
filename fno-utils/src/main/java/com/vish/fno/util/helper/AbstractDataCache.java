package com.vish.fno.util.helper;

import com.vish.fno.model.Ticker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractDataCache implements DataCache {
    protected final Map<String, Ticker> latestTicks;
    protected final Map<String, List<Ticker>> ticksCache;

    public AbstractDataCache() {
        this.latestTicks = new HashMap<>();
        this.ticksCache = new HashMap<>();
    }

    public void appendTick(String symbol, Ticker tick) {
        latestTicks.put(symbol, tick);
        if(!ticksCache.containsKey(symbol)) {
            ticksCache.put(symbol, new ArrayList<>());
        }
        ticksCache.get(symbol).add(tick);
    }

    public Ticker getLatestTick(String symbol) {
        return latestTicks.get(symbol);
    }

    public List<Ticker> getTicks(String symbol) {
        return ticksCache.get(symbol);
    }
}
