package com.vish.fno.reader.core;

import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.LTPQuote;
import com.zerodhatech.models.Position;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Small REST client for the feed-loss watchdog: batched last-traded prices and broker net positions.
 * Deliberately uses its OWN short-timeout lock and never the shared {@link ApiRateLimiter} lock, so a
 * recovery path never waits behind a slow historical-data call (OAV2 ADR 0078).
 */
@Slf4j
class QuickQuoteClient {

    private static final String NET_POSITIONS_KEY = "net";

    private final KiteSession session;
    private final InstrumentCache instrumentCache;
    private final long lockTimeoutMillis;
    private final ReentrantLock quickLock = new ReentrantLock(true);

    QuickQuoteClient(KiteSession session, InstrumentCache instrumentCache, long lockTimeoutMillis) {
        this.session = session;
        this.instrumentCache = instrumentCache;
        this.lockTimeoutMillis = lockTimeoutMillis;
    }

    /**
     * @return plain symbol to last price; symbols the broker did not return are absent (never 0.0);
     *         total failure yields an empty map.
     */
    Map<String, Double> getLtp(List<String> symbols) {
        Map<String, Double> result = new ConcurrentHashMap<>();
        if (symbols == null || symbols.isEmpty() || !acquire("getLtp")) {
            return result;
        }
        try {
            Map<String, String> keyToSymbol = new ConcurrentHashMap<>();
            for (String symbol : symbols) {
                keyToSymbol.put(instrumentCache.getExchangeForSymbol(symbol) + ":" + symbol, symbol);
            }
            Map<String, LTPQuote> quotes = session.getKiteSdk().getLTP(keyToSymbol.keySet().toArray(new String[0]));
            if (quotes == null) {
                return result;
            }
            for (Map.Entry<String, String> entry : keyToSymbol.entrySet()) {
                LTPQuote quote = quotes.get(entry.getKey());
                if (quote != null && quote.lastPrice > 0) {
                    result.put(entry.getValue(), quote.lastPrice);
                }
            }
        } catch (KiteException | IOException | JSONException e) {
            log.warn("QUICK_REST getLtp failed: {}", e.getMessage());
            result.clear();
        } finally {
            quickLock.unlock();
        }
        return result;
    }

    /**
     * @return plain trading symbol to net quantity; empty Optional means the call FAILED
     *         (distinct from an empty map = no positions).
     */
    Optional<Map<String, Integer>> getNetPositionQuantities() {
        if (!acquire("getNetPositions")) {
            return Optional.empty();
        }
        try {
            Map<String, List<Position>> positions = session.getKiteSdk().getPositions();
            Map<String, Integer> result = new ConcurrentHashMap<>();
            if (positions != null && positions.get(NET_POSITIONS_KEY) != null) {
                for (Position position : positions.get(NET_POSITIONS_KEY)) {
                    if (position.tradingSymbol != null) {
                        result.put(position.tradingSymbol, position.netQuantity);
                    }
                }
            }
            return Optional.of(result);
        } catch (KiteException | IOException | JSONException e) {
            log.warn("QUICK_REST getNetPositions failed: {}", e.getMessage());
            return Optional.empty();
        } finally {
            quickLock.unlock();
        }
    }

    private boolean acquire(String operation) {
        try {
            if (quickLock.tryLock(lockTimeoutMillis, TimeUnit.MILLISECONDS)) {
                return true;
            }
            log.warn("QUICK_REST lock timeout after {} ms for {}", lockTimeoutMillis, operation);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("QUICK_REST interrupted waiting for lock: {}", operation);
            return false;
        }
    }
}
