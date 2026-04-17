package com.vish.fno.reader.core;

import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static com.vish.fno.util.PriceUtils.getTopNLines;

/**
 * Fetches historical candle data from Kite with four resilience guards added
 * after the 2026-04-15 retry storm (30,436 attempts / 237 × HTTP 429 / scheduler
 * starvation — see {@code docs/daily-analysis/2026-04-15/app-performance.md} §4):
 *
 * <ol>
 *   <li><b>Holiday short-circuit</b> — {@link HolidayCalendar}-flagged dates never touch Kite</li>
 *   <li><b>Dedup cache</b> — identical (token, from, to, interval, continuous) tuples within
 *       {@link HistoricalDataConfig#dedupTtlMs()} reuse the first result; eliminates the
 *       per-minute cron re-requesting yesterday's candles on every tick</li>
 *   <li><b>Rate limiter</b> — token-bucket at {@link HistoricalDataConfig#rateLimitPermitsPerSec()}
 *       (Kite's documented historical limit is 3 req/sec) prevents structurally exceeding
 *       the per-second budget regardless of concurrent caller fan-out</li>
 *   <li><b>Exponential back-off with retry cap</b> — {@link HistoricalDataConfig#initialBackoffMs()}
 *       → {@link HistoricalDataConfig#maxBackoffMs()}, up to {@link HistoricalDataConfig#maxAttempts()}
 *       attempts. Calibrated tight (400ms → 2s, 3 attempts) because Kite's rate-limit window
 *       refreshes per-second — SaaS-style 5s+ back-offs throw away the budget you're entitled to.</li>
 * </ol>
 */
@Slf4j
class HistoricalDataProvider {
    private static final int ERROR_STACK_TRACE_LINES = 3;
    private static final double BACKOFF_JITTER_FRACTION = 0.20;

    private final KiteSession session;
    private final InstrumentCache instrumentCache;
    private final HolidayCalendar holidayCalendar;
    private final HistoricalDataConfig config;

    /** Rate limiter: next permit becomes available at this monotonic nanosecond. */
    private final long rateLimitIntervalNanos;
    private long nextFreeSlotNanos;
    private final Object rateLimitMutex = new Object();

    /** Dedup cache — see {@link CacheKey} for the canonical request shape. */
    private final Map<CacheKey, CachedResult> dedupCache = new ConcurrentHashMap<>();

    /**
     * Production constructor — uses {@link NoOpHolidayCalendar} and
     * {@link HistoricalDataConfig#prodDefaults()}. OAV2 wiring for the real
     * holidays.yml-backed calendar lands in a follow-up commit.
     */
    HistoricalDataProvider(KiteSession session, InstrumentCache instrumentCache) {
        this(session, instrumentCache, NoOpHolidayCalendar.INSTANCE, HistoricalDataConfig.prodDefaults());
    }

    /**
     * Full constructor — lets callers inject a real {@link HolidayCalendar} and
     * override rate-limit / dedup / back-off tunings. Primarily used by tests.
     */
    HistoricalDataProvider(KiteSession session, InstrumentCache instrumentCache,
                           HolidayCalendar holidayCalendar, HistoricalDataConfig config) {
        this.session = session;
        this.instrumentCache = instrumentCache;
        this.holidayCalendar = holidayCalendar;
        this.config = config;
        double permitsPerSec = Math.max(config.rateLimitPermitsPerSec(), 1e-9);
        this.rateLimitIntervalNanos = (long) (TimeUnit.SECONDS.toNanos(1) / permitsPerSec);
        this.nextFreeSlotNanos = System.nanoTime();
    }

    Optional<HistoricalData> getHistoricalData(Date fromDate, Date toDate, String symbol, String interval) {
        return getHistoricalData(fromDate, toDate, symbol, interval, false);
    }

    Optional<HistoricalData> getHistoricalData(Date from, Date to, String symbol, String interval, boolean continuous) {
        // Guard 1: holiday short-circuit
        if (holidayCalendar.isHoliday(from)) {
            log.debug("Skipping historical fetch for {} on holiday date {}", symbol, from);
            return Optional.empty();
        }

        Optional<String> instrument = getInstrumentToken(symbol, continuous);
        if (instrument.isEmpty()) {
            return Optional.empty();
        }

        if (!session.isInitialised()) {
            log.warn("Kite service is not initialised yet");
            return Optional.empty();
        }

        String token = instrument.get();
        CacheKey cacheKey = new CacheKey(token, from.getTime(), to.getTime(), interval, continuous);

        // Guard 2: dedup cache — serve identical recent tuples from memory
        if (config.dedupTtlMs() > 0) {
            CachedResult cached = dedupCache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                return cached.result();
            }
        }

        // Guard 3: rate limiter — structural 3 req/sec ceiling
        acquireRateLimitSlot();

        // Guard 4: retry with bounded exponential back-off
        Optional<HistoricalData> result = fetchWithBackoff(from, to, token, interval, continuous, symbol);

        // Cache on success only. Caching empty results would convert transient failures
        // into persistent "no data" verdicts, which is the opposite of what callers need.
        if (config.dedupTtlMs() > 0 && result.isPresent()) {
            long expiresAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(config.dedupTtlMs());
            dedupCache.put(cacheKey, new CachedResult(result, expiresAt));
        }
        return result;
    }

    private Optional<HistoricalData> fetchWithBackoff(Date from, Date to, String token, String interval,
                                                      boolean continuous, String symbol) {
        long backoffMs = config.initialBackoffMs();
        long maxBackoff = Math.max(config.maxBackoffMs(), config.initialBackoffMs());
        int maxAttempts = Math.max(config.maxAttempts(), 1);

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            Optional<HistoricalData> result = fetchHistoricalData(from, to, token, interval, continuous, symbol);
            if (result.isPresent()) {
                return result;
            }

            boolean hasMoreAttempts = attempt < maxAttempts - 1;
            if (!hasMoreAttempts || backoffMs <= 0) {
                continue;
            }
            try {
                Thread.sleep(jittered(backoffMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
            backoffMs = Math.min(backoffMs * 2, maxBackoff);
        }
        return Optional.empty();
    }

    /**
     * Rate-limit acquire: compute the next available slot inside the monitor
     * (microseconds) and then park outside the monitor (up to {@code interval}) so that
     * concurrent callers don't serialize through the park itself.
     */
    private void acquireRateLimitSlot() {
        long waitNanos;
        synchronized (rateLimitMutex) {
            long now = System.nanoTime();
            if (now >= nextFreeSlotNanos) {
                nextFreeSlotNanos = now + rateLimitIntervalNanos;
                waitNanos = 0;
            } else {
                waitNanos = nextFreeSlotNanos - now;
                nextFreeSlotNanos += rateLimitIntervalNanos;
            }
        }
        if (waitNanos > 0) {
            LockSupport.parkNanos(waitNanos);
        }
    }

    private static long jittered(long baseMs) {
        double lo = baseMs * (1.0 - BACKOFF_JITTER_FRACTION);
        double hi = baseMs * (1.0 + BACKOFF_JITTER_FRACTION);
        return (long) ThreadLocalRandom.current().nextDouble(lo, hi);
    }

    private Optional<HistoricalData> fetchHistoricalData(Date from, Date to, String token,
                                                          String interval, boolean continuous, String symbol) {
        try {
            log.debug("Collecting data for {} from: {}, to: {}, interval: {}, continuous: {}", token, from, to, interval, continuous);
            HistoricalData data = session.executeWithLockChecked(
                    () -> session.getKiteSdk().getHistoricalData(from, to, token, interval, continuous, true),
                    "getHistoricalData");
            return Optional.of(data);
        } catch (JSONException | IOException | KiteException e) {
            log.error("Error while requesting historical data (from: {}, to: {}, symbol: {}, continuous: {}), errorMessage: {}\n{}",
                    from, to, symbol, continuous, e.getMessage(), getTopNLines(e, ERROR_STACK_TRACE_LINES));
        }
        return Optional.empty();
    }

    private Optional<String> getInstrumentToken(String symbol, boolean continuous) {
        Optional<String> directToken = instrumentCache.getInstrument(symbol).map(String::valueOf);
        if (directToken.isPresent()) {
            return directToken;
        }
        if (continuous) {
            Optional<String> resolved = instrumentCache.resolveNearestFutureToken(symbol).map(String::valueOf);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        log.warn("No instrument available for symbol {} (continuous: {})", symbol, continuous);
        return Optional.empty();
    }

    /**
     * Canonical dedup key. Uses epoch-millis (not {@link Date}) so equal-but-different
     * Date instances hash the same. Interval is a string; continuous distinguishes
     * spot vs continuous-future fetches even when the other fields match.
     */
    private record CacheKey(String token, long fromMillis, long toMillis, String interval, boolean continuous) { }

    /** Cached result with expiry tracked in monotonic nanoseconds. */
    private record CachedResult(Optional<HistoricalData> result, long expiresAtNanos) {
        boolean isExpired() {
            return System.nanoTime() >= expiresAtNanos;
        }
    }
}
