package com.vish.fno.util.helper;

import com.vish.fno.model.Candle;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded cache for historical candlestick data, keyed by date then symbol.
 *
 * <p>Uses LRU eviction at the date level: when more than {@link #MAX_DATES} dates
 * are cached, the least recently accessed date is evicted. This prevents unbounded
 * memory growth during multi-date backtests and production lookbacks.
 *
 * <p>{@code getNCandles()} looks back at most 5 trading days, so keeping 10 dates
 * provides ample headroom while bounding memory to approximately:
 * {@code 10 dates × 30 symbols × 375 candles × ~48 bytes ≈ 54 MB max}.
 *
 * <p>Previously unbounded ({@code ConcurrentHashMap} with zero eviction), this cache
 * was the primary suspect for the 334 MB/hr memory leak in production.
 */
@Slf4j
class HistoricDataCache {

    static final int MAX_DATES = 10;

    // date → (symbol → candle data)
    // LinkedHashMap with accessOrder=true provides LRU eviction at the date level
    private final Map<String, Map<String, List<Candle>>> dataCache;

    @SuppressWarnings("PMD.UseConcurrentHashMap")
    HistoricDataCache() {
        this.dataCache = new LinkedHashMap<>(MAX_DATES + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Map<String, List<Candle>>> eldest) {
                boolean shouldRemove = size() > MAX_DATES;
                if (shouldRemove) {
                    log.info("Evicting historic cache for date: {} ({} symbols), "
                            + "cache size exceeded {}", eldest.getKey(),
                            eldest.getValue().size(), MAX_DATES);
                }
                return shouldRemove;
            }
        };
    }

    public List<Candle> getData(String date, String symbol) {
        synchronized (dataCache) {
            return Optional.of(dataCache)
                    .map(d -> d.get(date))
                    .map(d -> d.get(symbol))
                    .orElseGet(List::of);
        }
    }

    public void update(String date, String symbol, List<Candle> candleStickData) {
        synchronized (dataCache) {
            dataCache.computeIfAbsent(date, k -> new ConcurrentHashMap<>())
                    .put(symbol, candleStickData);
        }
    }
}
