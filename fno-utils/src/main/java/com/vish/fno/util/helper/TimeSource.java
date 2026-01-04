package com.vish.fno.util.helper;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * TimeSource - Interface for time-providing functionality.
 *
 * This interface enables composition-based time injection, making it easy to:
 * - Use real system time in production (via TimeProvider)
 * - Use controllable time in backtesting (via BacktestTimeProvider)
 * - Use mock time in unit tests
 *
 * Key design principles:
 * - Composition over inheritance (both TimeProvider and BacktestTimeProvider implement this)
 * - Dependency inversion (depend on abstraction, not concrete classes)
 * - Easy testability (can inject any implementation)
 *
 * Usage:
 * <pre>
 * // Production - inject TimeProvider
 * TimeSource timeSource = new TimeProvider();
 *
 * // Backtest - inject BacktestTimeProvider
 * TimeSource timeSource = new BacktestTimeProvider("2025-01-04");
 *
 * // Unit test - inject mock
 * TimeSource timeSource = mock(TimeSource.class);
 * when(timeSource.now()).thenReturn(LocalDateTime.of(2025, 1, 4, 10, 0));
 * </pre>
 */
public interface TimeSource {

    /**
     * Get the current date-time.
     *
     * @return current LocalDateTime (real time or simulated for backtest)
     */
    LocalDateTime now();

    /**
     * Get today's date as a Date object.
     *
     * @return current Date (real time or simulated for backtest)
     */
    Date todayDate();

    /**
     * Get the current trading minute index (minutes since 9:15 AM).
     *
     * Trading index starts at 0 for 9:15 AM and increments each minute.
     * For example: 9:30 AM = 15, 10:00 AM = 45, 3:30 PM = 375.
     *
     * @return trading minute index
     */
    int currentTimeStampIndex();

    /**
     * Get today's date as a formatted string (yyyy-MM-dd).
     *
     * @return date string in format "yyyy-MM-dd"
     */
    String getTodaysDateString();

    /**
     * Get current date-time as a formatted string (yyyy-MM-dd HH:mm:ss).
     *
     * @return date-time string
     */
    String getCurrentStringDateTime();
}
