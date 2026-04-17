package com.vish.fno.reader.core;

import java.util.Date;

/**
 * No-op default: every date is a trading day. Used when no explicit
 * {@link HolidayCalendar} is provided (e.g. backtest, tests) so that
 * the absence of a holiday source does not silently skip legitimate
 * fetches.
 */
final class NoOpHolidayCalendar implements HolidayCalendar {
    static final NoOpHolidayCalendar INSTANCE = new NoOpHolidayCalendar();

    private NoOpHolidayCalendar() { }

    @Override
    public boolean isHoliday(Date date) {
        return false;
    }
}
