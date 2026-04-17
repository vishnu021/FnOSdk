package com.vish.fno.reader.core;

/**
 * Tuning knobs for {@link HistoricalDataProvider} — rate limit, dedup cache TTL,
 * and retry/back-off parameters. Package-private because they are implementation
 * details; tests use this record to inject fast values.
 *
 * <p>Defaults (see {@link #prodDefaults()}) are calibrated to Kite Connect's
 * documented <b>3 req/sec</b> rate limit for the historical endpoint
 * (https://kite.trade/docs/connect/v3/exceptions/).
 *
 * @param rateLimitPermitsPerSec permits/sec; matches Kite's historical endpoint documented limit of 3
 * @param dedupTtlMs             how long identical (token, from, to, interval, continuous) tuples are cached; cron tickers repeat the same fetch within 60s
 * @param initialBackoffMs       first retry sleep on 429; calibrated shorter than Kite's 1-second rate-limit refresh window
 * @param maxBackoffMs           cap on exponential back-off; keeps worst-case call latency well under the 60s cron period
 * @param maxAttempts            total attempts including the first (so retries = maxAttempts - 1)
 */
record HistoricalDataConfig(double rateLimitPermitsPerSec,
                            long dedupTtlMs,
                            long initialBackoffMs,
                            long maxBackoffMs,
                            int maxAttempts) {

    static HistoricalDataConfig prodDefaults() {
        // Kite historical endpoint: 3 req/sec (per-second sliding window).
        // - rate limiter shapes at 3/sec — structural 429 prevention
        // - dedup 30s — same per-minute cron won't re-hit Kite for the same candle tuple
        // - back-off 400ms → 800ms → cap 2s — calibrated to the per-second refresh;
        //   worst-case cumulative sleep 400+800 = 1.2s, bounded well under cron period
        // - maxAttempts 3 — first attempt + 2 retries
        return new HistoricalDataConfig(3.0, 30_000L, 400L, 2_000L, 3);
    }
}
