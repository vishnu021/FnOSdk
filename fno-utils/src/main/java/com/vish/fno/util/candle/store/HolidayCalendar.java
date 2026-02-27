package com.vish.fno.util.candle.store;

import java.util.Date;
import java.util.List;

/**
 * Interface for holiday calendar operations.
 *
 * Implementations:
 * - CalendarService (OrderManager) - Production implementation
 * - CalendarService (BacktestRunner) - Backtest implementation
 */
public interface HolidayCalendar {

    /**
     * Get the previous non-holiday (trading) date.
     *
     * @param date The reference date
     * @return The previous trading date (excludes weekends and holidays)
     */
    Date getPreviousNonHolidayDate(Date date);

    /**
     * Get the list of holiday dates.
     *
     * @return List of holiday dates in yyyy-MM-dd format
     */
    List<String> getHolidays();
}
