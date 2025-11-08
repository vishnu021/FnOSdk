package com.vish.fno.util.helper;

import com.vish.fno.model.Candle;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class HistoricDataCache {
    // date // symbol //  data
    @Getter
    private final Map<String, Map<String, List<Candle>>> dataCache = new HashMap<>();

    public List<Candle> getData(String date, String symbol) {
        return Optional.of(dataCache)
                .map(d -> d.get(date))
                .map(d -> d.get(symbol))
                .orElseGet(List::of);
    }

    public void update(String date, String symbol, List<Candle> candleStickData) {
        if(!dataCache.containsKey(date)) {
            dataCache.put(date, new HashMap<>());
        }
        dataCache.get(date).put(symbol, candleStickData);
    }
}
