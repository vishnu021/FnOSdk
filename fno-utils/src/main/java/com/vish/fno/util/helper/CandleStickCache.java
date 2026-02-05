package com.vish.fno.util.helper;

import com.vish.fno.model.Candle;

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
        return candlesCache.get(symbol);
    }

    public Optional<Candle> getLatestCandle(String symbol) {
        List<Candle> candlesForSymbol = get(symbol);
        if(candlesForSymbol == null || candlesForSymbol.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candlesForSymbol.get(candlesForSymbol.size() - 1));
    }

    public void update(String symbol, List<Candle> data) {
        candlesCache.put(symbol, data);
    }

    public void clear(String symbol) {
        candlesCache.remove(symbol);
    }
}
