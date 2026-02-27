package com.vish.fno.util.candle.store;

import com.vish.fno.model.Candle;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class for caching intraday candlestick data for symbols.
 */
class CandleStickCache {

    private final Map<String, List<Candle>> candlesCache = new ConcurrentHashMap<>();

    public List<Candle> get(String symbol) {
        List<Candle> candles = candlesCache.get(symbol);
        return candles != null ? Collections.unmodifiableList(candles) : null;
    }

    public Optional<Candle> getLatestCandle(String symbol) {
        List<Candle> candlesForSymbol = get(symbol);
        if(candlesForSymbol == null || candlesForSymbol.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candlesForSymbol.get(candlesForSymbol.size() - 1));
    }

    public void update(String symbol, List<Candle> data) {
        candlesCache.put(symbol, List.copyOf(data));
    }

    public void clear(String symbol) {
        candlesCache.remove(symbol);
    }

    public void clearAll() {
        candlesCache.clear();
    }
}
