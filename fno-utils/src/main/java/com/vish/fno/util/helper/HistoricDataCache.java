package com.vish.fno.util.helper;

import com.vish.fno.model.Candle;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class HistoricDataCache {
    // date // symbol //  data
    @Getter
    private final Map<String, Map<String, List<Candle>>> dataCache = new ConcurrentHashMap<>();

    public List<Candle> getData(String date, String symbol) {
        return Optional.of(dataCache)
                .map(d -> d.get(date))
                .map(d -> d.get(symbol))
                .orElseGet(List::of);
    }

    public void update(String date, String symbol, List<Candle> candleStickData) {
        dataCache.computeIfAbsent(date, k -> new ConcurrentHashMap<>()).put(symbol, candleStickData);
    }
}
