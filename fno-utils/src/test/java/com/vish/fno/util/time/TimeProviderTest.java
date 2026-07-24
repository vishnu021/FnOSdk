package com.vish.fno.util.time;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the production {@link TimeProvider}, in particular the per-day memoisation of
 * {@link TimeProvider#getTodaysDateString()}.
 *
 * <p>Why the caching matters: {@code getTodaysDateString()} is called once per tick from
 * {@code TickStoreImpl.appendTick} (~12 M calls/day in production). The previous
 * implementation built a {@code new SimpleDateFormat} per call, which constructs a
 * {@code GregorianCalendar} and triggers a full JDK locale-provider scan — measured at
 * ~30% of all JVM allocation (~44.8 GB/day) across three JFR recordings
 * (2026-07-21/22/23). These tests pin both the correctness contract and the no-allocation
 * fast path.
 */
class TimeProviderTest {

    private static long millisAt(int year, int month, int day, int hour, int minute, int second) {
        return LocalDateTime.of(year, month, day, hour, minute, second)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }

    // ---------------------------------------------------------------- contract

    @Test
    void getTodaysDateStringReturnsTodayInIsoFormat() {
        assertEquals(LocalDate.now().toString(), new TimeProvider().getTodaysDateString());
    }

    @Test
    void getCurrentStringDateTimeUsesSecondPrecisionFormat() {
        assertTrue(new TimeProvider().getCurrentStringDateTime()
                        .matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
                "expected yyyy-MM-dd HH:mm:ss");
    }

    // ------------------------------------------------------- no-allocation path

    @Test
    void getTodaysDateStringReturnsTheSameInstanceWithinTheSameDay() {
        TimeProvider provider = new TimeProvider();

        String first = provider.getTodaysDateString();
        String second = provider.getTodaysDateString();
        String third = provider.getTodaysDateString();

        assertSame(first, second, "repeat calls within a day must not re-format");
        assertSame(first, third, "repeat calls within a day must not re-format");
    }

    // ------------------------------------------------------------- day rollover

    @Test
    void getTodaysDateStringRecomputesAtLocalMidnight() {
        AtomicLong clock = new AtomicLong(millisAt(2026, 7, 23, 15, 30, 0));
        TimeProvider provider = new TimeProvider(clock::get);

        assertEquals("2026-07-23", provider.getTodaysDateString());

        clock.set(millisAt(2026, 7, 23, 23, 59, 59));
        assertEquals("2026-07-23", provider.getTodaysDateString(), "must not roll over early");

        clock.set(millisAt(2026, 7, 24, 0, 0, 0));
        assertEquals("2026-07-24", provider.getTodaysDateString(), "must roll over at local midnight");

        clock.set(millisAt(2026, 7, 24, 9, 15, 0));
        assertEquals("2026-07-24", provider.getTodaysDateString());
    }

    /**
     * Guards the naive {@code millis / 86_400_000} day-key bug. In a zone ahead of UTC
     * (IST = UTC+5:30) the local date advances 5.5 h before the UTC date does, so a
     * UTC-based day key would keep serving the previous date until 05:30 local.
     */
    @Test
    void getTodaysDateStringIsCorrectJustAfterLocalMidnightInAZoneAheadOfUtc() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
            AtomicLong clock = new AtomicLong(millisAt(2026, 7, 23, 23, 0, 0));
            TimeProvider provider = new TimeProvider(clock::get);

            assertEquals("2026-07-23", provider.getTodaysDateString());

            clock.set(millisAt(2026, 7, 24, 0, 30, 0)); // 19:00 UTC on 2026-07-23
            assertEquals("2026-07-24", provider.getTodaysDateString(),
                    "local date must advance at local midnight, not at UTC midnight");
        } finally {
            TimeZone.setDefault(original);
        }
    }

    /**
     * Same guard from the other side: a zone behind UTC must NOT advance when the UTC
     * date rolls over.
     */
    @Test
    void getTodaysDateStringDoesNotRollOverAtUtcMidnightInAZoneBehindUtc() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            AtomicLong clock = new AtomicLong(millisAt(2026, 7, 23, 18, 0, 0));
            TimeProvider provider = new TimeProvider(clock::get);

            assertEquals("2026-07-23", provider.getTodaysDateString());

            clock.set(millisAt(2026, 7, 23, 20, 30, 0)); // 00:30 UTC on 2026-07-24
            assertEquals("2026-07-23", provider.getTodaysDateString(),
                    "must stay on the local date until local midnight");
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void getTodaysDateStringHandlesADaylightSavingTransition() {
        TimeZone original = TimeZone.getDefault();
        try {
            // US DST starts 2026-03-08; local clocks jump 02:00 -> 03:00.
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            AtomicLong clock = new AtomicLong(millisAt(2026, 3, 7, 12, 0, 0));
            TimeProvider provider = new TimeProvider(clock::get);

            assertEquals("2026-03-07", provider.getTodaysDateString());

            clock.set(millisAt(2026, 3, 8, 12, 0, 0));
            assertEquals("2026-03-08", provider.getTodaysDateString());

            clock.set(millisAt(2026, 3, 9, 12, 0, 0));
            assertEquals("2026-03-09", provider.getTodaysDateString());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    // ------------------------------------------------------------- thread safety

    @Test
    void concurrentCallersAllObserveTheSameCorrectValue() throws Exception {
        int threads = 8;
        int callsPerThread = 5_000;
        TimeProvider provider = new TimeProvider();
        String expected = LocalDate.now().toString();

        Set<String> observed = ConcurrentHashMap.newKeySet();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < callsPerThread; i++) {
                            observed.add(provider.getTodaysDateString());
                        }
                    } catch (Throwable e) {
                        failure.compareAndSet(null, e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "workers did not finish");
        } finally {
            pool.shutdownNow();
        }

        assertNull(failure.get(), "a worker threw");
        // Allows for a genuine midnight rollover mid-run; otherwise exactly one value.
        assertTrue(observed.contains(expected), "expected " + expected + " but saw " + observed);
        assertTrue(observed.size() <= 2, "unexpected value spread: " + observed);
    }
}
