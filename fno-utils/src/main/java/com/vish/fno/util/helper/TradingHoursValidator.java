package com.vish.fno.util.helper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Validates whether a given time falls within configured trading hours.
 * Excludes weekends (Saturday, Sunday).
 */
@Getter
@RequiredArgsConstructor
public class TradingHoursValidator {

    private final LocalTime startTradingHour;
    private final LocalTime endTradingHour;

    /**
     * Check if the given time is within trading hours.
     * Returns false for weekends (Saturday, Sunday).
     *
     * @param now the date-time to check
     * @return true if within trading hours on a weekday
     */
    public boolean isWithinTradingHours(LocalDateTime now) {
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        return !now.toLocalTime().isBefore(startTradingHour) && !now.toLocalTime().isAfter(endTradingHour);
    }
}
