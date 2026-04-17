package com.vish.fno.reader.core;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Locks in the Apr 15 P0-C fix for {@link HistoricalDataProvider}: four new guards
 * stop the 30,436-fetch retry storm that wedged the scheduler pool.
 *
 * <ol>
 *   <li><b>Rate limiter</b> at 3 permits/sec — matches Kite's documented historical endpoint limit</li>
 *   <li><b>Dedup cache</b> — same (token, from, to, interval, continuous) tuple returns from memory</li>
 *   <li><b>Retry cap</b> — after {@code maxAttempts} consecutive failures the call stops</li>
 *   <li><b>Holiday skip</b> — {@link HolidayCalendar}-flagged dates never touch Kite</li>
 * </ol>
 *
 * Each test uses a {@link HistoricalDataConfig} tuned for fast execution — production
 * values (rate=3.0, backoff=400ms→2s, 3 attempts) are locked in by {@link #backoffWorstCaseStaysWithinProductionBudget()}.
 */
@ExtendWith(MockitoExtension.class)
class HistoricalDataProviderRateLimitingTest {

    private static final String SYMBOL = "NIFTY 50";
    private static final long TOKEN = 256_265L;
    private static final String INTERVAL = "minute";

    @Mock private KiteSession session;
    @Mock private InstrumentCache instrumentCache;
    @Mock private KiteConnect kiteSdk;

    private Date from;
    private Date to;

    @BeforeEach
    void setUp() {
        from = new Date();
        to = new Date();
        lenient().when(instrumentCache.getInstrument(SYMBOL)).thenReturn(Optional.of(TOKEN));
        lenient().when(session.isInitialised()).thenReturn(true);
        lenient().when(session.getKiteSdk()).thenReturn(kiteSdk);
    }

    // -------------------------------------------------------------------------
    // Test 1 — Rate limiter holds throughput at configured permits/sec
    // -------------------------------------------------------------------------

    @Test
    void rateLimiterHoldsThroughputAtConfiguredPermitsPerSec() throws IOException, KiteException {
        // 3 permits/sec → ~333ms between permits. 7 calls → first is immediate, 6 waits of 333ms → ≥2s.
        HistoricalDataConfig config = new HistoricalDataConfig(
                3.0 /*rate*/, 0L /*no dedup*/, 0L /*no backoff*/, 0L /*no max*/, 1 /*single attempt*/);
        HistoricalDataProvider provider = new HistoricalDataProvider(
                session, instrumentCache, NoOpHolidayCalendar.INSTANCE, config);
        mockSuccessfulExecute();

        int calls = 7;
        long t0 = System.nanoTime();
        for (int i = 0; i < calls; i++) {
            provider.getHistoricalData(new Date(i * 1_000_000L), new Date(i * 1_000_000L + 1), SYMBOL, INTERVAL);
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        assertTrue(elapsedMs >= 1_800,
                () -> "7 calls @ 3/sec must take >= 1.8s (actual " + elapsedMs + "ms)");
        assertTrue(elapsedMs < 3_500,
                () -> "7 calls @ 3/sec must finish under 3.5s (actual " + elapsedMs + "ms)");
        verify(session, times(calls)).executeWithLockChecked(
                any(ApiRateLimiter.CheckedSupplier.class), anyString());
    }

    // -------------------------------------------------------------------------
    // Test 2 — Dedup cache returns from memory for repeated tuples
    // -------------------------------------------------------------------------

    @Test
    void dedupCacheServesRepeatedRequestsWithoutKiteCall() throws IOException, KiteException {
        HistoricalDataConfig config = new HistoricalDataConfig(
                100.0, 30_000L /*30s dedup TTL*/, 0L, 0L, 1);
        HistoricalDataProvider provider = new HistoricalDataProvider(
                session, instrumentCache, NoOpHolidayCalendar.INSTANCE, config);
        mockSuccessfulExecute();

        // Same (symbol, from, to, interval, continuous) tuple three times
        provider.getHistoricalData(from, to, SYMBOL, INTERVAL, false);
        provider.getHistoricalData(from, to, SYMBOL, INTERVAL, false);
        provider.getHistoricalData(from, to, SYMBOL, INTERVAL, false);

        verify(session, times(1)).executeWithLockChecked(
                any(ApiRateLimiter.CheckedSupplier.class), anyString());
    }

    // -------------------------------------------------------------------------
    // Test 3 — Retries stop after maxAttempts on 429
    // -------------------------------------------------------------------------

    @Test
    void stopsAfterMaxAttemptsWhenKiteReturns429() throws IOException, KiteException {
        HistoricalDataConfig config = new HistoricalDataConfig(
                100.0, 0L, 10L /*fast backoff*/, 40L, 3 /*maxAttempts*/);
        HistoricalDataProvider provider = new HistoricalDataProvider(
                session, instrumentCache, NoOpHolidayCalendar.INSTANCE, config);
        when(session.executeWithLockChecked(any(ApiRateLimiter.CheckedSupplier.class), anyString()))
                .thenThrow(new KiteException("Too many requests", 429));

        Optional<HistoricalData> result = provider.getHistoricalData(from, to, SYMBOL, INTERVAL);

        assertFalse(result.isPresent());
        verify(session, times(3)).executeWithLockChecked(
                any(ApiRateLimiter.CheckedSupplier.class), anyString());
    }

    // -------------------------------------------------------------------------
    // Test 4 — Production back-off worst case stays within budget (~1.2s sleep)
    // -------------------------------------------------------------------------

    @Test
    void backoffWorstCaseStaysWithinProductionBudget() throws IOException, KiteException {
        // Production tuning: initial 400ms, cap 2000ms, 3 attempts → cumulative 400 + 800 ≈ 1.2s of sleep
        HistoricalDataConfig config = HistoricalDataConfig.prodDefaults();
        HistoricalDataProvider provider = new HistoricalDataProvider(
                session, instrumentCache, NoOpHolidayCalendar.INSTANCE, config);
        when(session.executeWithLockChecked(any(ApiRateLimiter.CheckedSupplier.class), anyString()))
                .thenThrow(new KiteException("Too many requests", 429));

        long t0 = System.nanoTime();
        Optional<HistoricalData> result = provider.getHistoricalData(from, to, SYMBOL, INTERVAL);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        assertFalse(result.isPresent());
        assertTrue(elapsedMs >= 1_000,
                () -> "Prod back-off must include both retry sleeps (actual " + elapsedMs + "ms)");
        assertTrue(elapsedMs < 3_000,
                () -> "Prod back-off worst case must stay under 3s (actual " + elapsedMs + "ms)");
        // Still exactly 3 attempts with prod settings
        verify(session, times(3)).executeWithLockChecked(
                any(ApiRateLimiter.CheckedSupplier.class), anyString());
    }

    // -------------------------------------------------------------------------
    // Test 5 (bonus) — HolidayCalendar seam short-circuits before Kite
    // -------------------------------------------------------------------------

    @Test
    void holidayCalendarSkipsKiteEntirely() {
        HolidayCalendar allHolidays = date -> true;
        HistoricalDataConfig config = HistoricalDataConfig.prodDefaults();
        HistoricalDataProvider provider = new HistoricalDataProvider(
                session, instrumentCache, allHolidays, config);

        Optional<HistoricalData> result = provider.getHistoricalData(from, to, SYMBOL, INTERVAL);

        assertFalse(result.isPresent());
        verifyNoInteractions(session);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void mockSuccessfulExecute() throws IOException, KiteException {
        HistoricalData data = new HistoricalData();
        when(session.executeWithLockChecked(any(ApiRateLimiter.CheckedSupplier.class), anyString()))
                .thenAnswer(inv -> {
                    ApiRateLimiter.CheckedSupplier<?> supplier = inv.getArgument(0);
                    return supplier.get();
                });
        when(kiteSdk.getHistoricalData(
                any(Date.class), any(Date.class), eq(String.valueOf(TOKEN)),
                eq(INTERVAL), anyBoolean(), anyBoolean()))
                .thenReturn(data);
    }
}
