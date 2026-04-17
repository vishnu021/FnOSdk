package com.vish.fno.reader.core;

import java.util.Date;

/**
 * Source-of-truth for non-trading days. Injected into {@link HistoricalDataProvider}
 * so that fetches for known holidays (e.g. Ambedkar Jayanti, Holi, Republic Day)
 * short-circuit before touching Kite's rate-limited historical endpoint.
 *
 * <p>FnOSdk ships with {@link NoOpHolidayCalendar} as the default — consumers
 * (OrderManager) provide the real implementation that reads {@code holidays.yml}.
 *
 * <p>Motivation: on 2026-04-15 (day after Ambedkar Jayanti) the per-minute cron
 * walked backward looking for Apr 14 candle data, producing 30,436 fetch attempts
 * and 237 HTTP 429s before the rate limit windowed forward. See
 * {@code docs/daily-analysis/2026-04-15/app-performance.md} §4.3.
 */
@FunctionalInterface
public interface HolidayCalendar {
    boolean isHoliday(Date date);
}
