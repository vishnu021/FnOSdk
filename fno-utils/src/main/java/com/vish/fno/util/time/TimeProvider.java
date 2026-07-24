package com.vish.fno.util.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.function.LongSupplier;

import static com.vish.fno.util.FnoConstants.DATE_FORMAT;
import static com.vish.fno.util.FnoConstants.DATE_TIME_SEC_FORMAT;

/**
 * TimeProvider - Real-time implementation of TimeSource.
 *
 * This class provides actual system time for production use.
 * Implements TimeSource interface for easy substitution with
 * BacktestTimeProvider or mock implementations in testing.
 *
 * @see TimeSource
 * @see com.vish.fno.backtest.service.BacktestTimeProvider
 */
public class TimeProvider implements TimeSource {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern(DATE_FORMAT, Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(DATE_TIME_SEC_FORMAT, Locale.ENGLISH);

    private final LongSupplier clock;

    /**
     * Cached local date and the instant it expires. Volatile so the WebSocket writer and
     * strategy readers agree; a benign race can only cause two threads to format the same
     * value on the same day boundary.
     */
    private volatile DayCache dayCache = new DayCache(Long.MIN_VALUE, null);

    public TimeProvider() {
        this(System::currentTimeMillis);
    }

    /**
     * Test seam: supplies the epoch-milli reading used for the date-string methods.
     *
     * @param clock source of {@code System.currentTimeMillis()}-equivalent readings
     */
    TimeProvider(LongSupplier clock) {
        this.clock = clock;
    }

    @Override
    public LocalDateTime now() {
        return LocalDateTime.now();
    }

    @Override
    public Date todayDate() {
        return new Date();
    }

    @Override
    public int currentTimeStampIndex() {
        return TimeUtils.getIndexOfTimeStamp(new Date());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Memoised for the remainder of the local day. This method is called once per tick
     * from {@code TickStoreImpl.appendTick} (~12 M calls/day in production), so the fast
     * path is deliberately a single volatile read plus one {@code long} comparison and
     * allocates nothing. The formatting path runs once per day.
     */
    @Override
    public String getTodaysDateString() {
        long nowMillis = clock.getAsLong();
        DayCache cached = dayCache;
        if (nowMillis < cached.validUntilMillis()) {
            return cached.value();
        }
        return refreshDayCache(nowMillis);
    }

    private String refreshDayCache(long nowMillis) {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime zonedNow = Instant.ofEpochMilli(nowMillis).atZone(zone);
        String value = DATE_FORMATTER.format(zonedNow);
        // atStartOfDay resolves DST gaps to the first valid instant of the next local day.
        long validUntilMillis = zonedNow.toLocalDate().plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli();
        dayCache = new DayCache(validUntilMillis, value);
        return value;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Second-precision, so it cannot be memoised like {@link #getTodaysDateString()}.
     * It still avoids the per-call {@code new SimpleDateFormat(...)} — that construction
     * builds a {@code GregorianCalendar} and triggers a full JDK locale-provider scan.
     */
    @Override
    public String getCurrentStringDateTime() {
        return DATE_TIME_FORMATTER.format(
                Instant.ofEpochMilli(clock.getAsLong()).atZone(ZoneId.systemDefault()));
    }

    /**
     * Immutable (date string, expiry instant) pair published through a single volatile
     * field, so readers always observe a consistent pair without locking.
     *
     * @param validUntilMillis epoch milli at which the cached local date stops being valid
     * @param value            the formatted date string
     */
    private record DayCache(long validUntilMillis, String value) { }
}
