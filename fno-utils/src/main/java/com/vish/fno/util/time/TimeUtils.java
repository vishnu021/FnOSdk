package com.vish.fno.util.time;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.vish.fno.util.FnoConstants.DATE_FORMAT;
import static com.vish.fno.util.FnoConstants.DATE_TIME_FORMAT;
import static com.vish.fno.util.FnoConstants.DATE_TIME_MS_FORMAT;
import static com.vish.fno.util.FnoConstants.DATE_TIME_SEC_T_FORMAT;
import static com.vish.fno.util.FnoConstants.MARKET_CLOSE_HOUR;
import static com.vish.fno.util.FnoConstants.MARKET_CLOSE_MINUTE;
import static com.vish.fno.util.FnoConstants.MARKET_OPEN_HOUR;
import static com.vish.fno.util.FnoConstants.MARKET_OPEN_MINUTE;
import static com.vish.fno.util.FnoConstants.MINUTES_IN_HOUR;
import static com.vish.fno.util.FnoConstants.TIME_FORMAT;
import static com.vish.fno.util.FnoConstants.TOTAL_TRADING_MINUTES;
import static com.vish.fno.util.FnoConstants.YEAR_FORMAT;

@Slf4j
@SuppressWarnings({"PMD.AvoidCatchingGenericException", "PMD.TooManyStaticImports"})
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TimeUtils {

    public static final List<String> timeArray;

    // Thread-safe DateTimeFormatter instances (immutable and thread-safe)
    private static final DateTimeFormatter ISO_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME_MINUTE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern(DATE_FORMAT, Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME_MS_FORMATTER =
        DateTimeFormatter.ofPattern(DATE_TIME_MS_FORMAT, Locale.ENGLISH);
    private static final DateTimeFormatter YEAR_FORMATTER =
        DateTimeFormatter.ofPattern(YEAR_FORMAT, Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME_SEC_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

    // Default timezone for IST
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    static {
        List<String> tempList = new ArrayList<>();
        int hour = MARKET_OPEN_HOUR;
        int minute = MARKET_OPEN_MINUTE;
        for (int i = 0; i <= TOTAL_TRADING_MINUTES; i++) {
            tempList.add(toTimeValue(hour) + ":" + toTimeValue(minute));
            minute++;
            if (minute == MINUTES_IN_HOUR) {
                hour++;
                minute = 0;
            }
        }
        timeArray = Collections.unmodifiableList(tempList);
    }

    public static Optional<String> getTimeStringForZonedDateString(String date) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_SEC_T_FORMAT, Locale.ENGLISH);
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(date, formatter);

            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(TIME_FORMAT, Locale.ENGLISH);
            return Optional.of(timeFormatter.format(zonedDateTime));
        } catch (Exception e) {
            log.error("Failed to format date string: {} to pattern: {}", date, DATE_TIME_SEC_T_FORMAT, e);
        }
        return Optional.empty();
    }

    public static Optional<String> getDateTimeStringForZonedDateString(String date) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_SEC_T_FORMAT, Locale.ENGLISH);
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(date, formatter);

            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT, Locale.ENGLISH);
            return Optional.of(timeFormatter.format(zonedDateTime));
        } catch (Exception e) {
            log.error("Failed to format date string: {} to pattern: {}", date, DATE_TIME_SEC_T_FORMAT, e);
        }
        return Optional.empty();
    }

    public static Optional<Date> getDateTimeForZonedDateString(String date) {
        try {
            final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_SEC_T_FORMAT, Locale.ENGLISH);
            final ZonedDateTime zonedDateTime = ZonedDateTime.parse(date, formatter);
            final Instant instant = zonedDateTime.toInstant();
            return Optional.of(Date.from(instant));
        } catch (Exception e) {
            log.error("Failed to format date string: {} to pattern: {}", date, DATE_TIME_SEC_T_FORMAT, e);
        }
        return Optional.empty();
    }

    public static int getIndexOfTimeStamp(Date timeStamp) {
        return getTime(timeStamp).map(TimeUtils::getIndexOfTime).orElse(-1);
    }

    public static Date appendOpeningTimeToDate(Date day) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(day);
        calendar.set(Calendar.HOUR_OF_DAY, MARKET_OPEN_HOUR);
        calendar.set(Calendar.MINUTE, MARKET_OPEN_MINUTE);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    public static Date appendClosingTimeToDate(Date day) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(day);
        calendar.set(Calendar.HOUR_OF_DAY, MARKET_CLOSE_HOUR);
        calendar.set(Calendar.MINUTE, MARKET_CLOSE_MINUTE);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    @Deprecated(forRemoval = true)
    public static Date currentTime() {
        return new Date(System.currentTimeMillis());
    }

    /**
     * Formats a Date to HH:mm time string (thread-safe).
     *
     * @param timeStamp the date to format
     * @return formatted time string or null if input is null
     */
    public static Optional<String> getTime(Date timeStamp) {
        if (timeStamp == null) {
            return Optional.empty();
        }
        return Optional.of(TIME_FORMATTER.format(timeStamp.toInstant().atZone(SYSTEM_ZONE)));
    }

    /**
     * Parses a date-time string in format "yyyy-MM-dd HH:mm" (thread-safe).
     *
     * @param date the date string to parse
     * @return parsed Date object or null on error
     */
    public static Optional<Date> getDateTimeObjectMinute(String date) {
        try {
            LocalDateTime ldt = LocalDateTime.parse(date, DATE_TIME_MINUTE_FORMATTER);
            return Optional.of(Date.from(ldt.atZone(SYSTEM_ZONE).toInstant()));
        } catch (DateTimeParseException e) {
            log.error("Failed to parse date: {}", date, e);
        }
        return Optional.empty();
    }

    /**
     * Parses a date string in DATE_FORMAT (thread-safe).
     *
     * @param date the date string to parse
     * @return parsed Date object or null on error
     */
    public static Optional<Date> getDateObject(String date) {
        try {
            LocalDate ld = LocalDate.parse(date, DATE_FORMATTER);
            return Optional.of(Date.from(ld.atStartOfDay(SYSTEM_ZONE).toInstant()));
        } catch (DateTimeParseException e) {
            log.error("Failed to parse to date of format yyyy-MM-dd: {}", date, e);
        }
        return Optional.empty();
    }

    /**
     * Converts Date to string in DATE_FORMAT (thread-safe).
     *
     * @param date the date to format
     * @return formatted date string or empty string if null
     */
    public static String getStringDate(Date date) {
        if (date == null) {
            return "";
        }
        return DATE_FORMATTER.format(date.toInstant().atZone(SYSTEM_ZONE));
    }

    /**
     * Converts Date to string in DATE_TIME_MS_FORMAT (thread-safe).
     *
     * @param timeStamp the timestamp to format
     * @return formatted date-time string or null if input is null
     */
    public static Optional<String> getStringDateTime(Date timeStamp) {
        if (timeStamp == null) {
            return Optional.empty();
        }
        return Optional.of(DATE_TIME_MS_FORMATTER.format(timeStamp.toInstant().atZone(SYSTEM_ZONE)));
    }

    private static int getIndexOfTime(String time) {
        return timeArray.indexOf(time);
    }

    public static String getTimeByIndex(int index) {
        return timeArray.get(index);
    }

    /**
     * @deprecated Use {@code appendOpeningTimeToDate(date)} instead.
     */
    @Deprecated(forRemoval = true)
    public static Date getOpeningTime() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, MARKET_OPEN_HOUR);
        calendar.set(Calendar.MINUTE, MARKET_OPEN_MINUTE);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /**
     * @deprecated Use {@code appendClosingTimeToDate(date)} instead.
     */
    @Deprecated(forRemoval = true)
    public static Date getClosingTime() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, MARKET_CLOSE_HOUR);
        calendar.set(Calendar.MINUTE, MARKET_CLOSE_MINUTE);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    public static Date getPreviousWorkDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);

        if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
            calendar.add(Calendar.DATE, -3);
        } else {
            calendar.add(Calendar.DATE, -1);
        }
        return calendar.getTime();
    }

    public static List<Date> getDatesBetween(Date startDate, Date endDate) {
        List<Date> dates = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate);

        while (!calendar.getTime().after(endDate)) {
            if (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY
                    && calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
                dates.add(calendar.getTime());
            }
            calendar.add(Calendar.DATE, 1);
        }

        log.debug("startDate={}, endDate={}, returning {}", startDate, endDate, dates);
        return dates;
    }

    /**
     * Formats a Date to year string (thread-safe).
     *
     * @param date the date to format
     * @return formatted year string or empty string if null
     */
    public static String getStringYear(Date date) {
        if (date == null) {
            return "";
        }
        return YEAR_FORMATTER.format(date.toInstant().atZone(SYSTEM_ZONE));
    }

    public static String getTimeElapsed(long milliseconds) {
        long millis = milliseconds % 1000;
        long seconds = (milliseconds / 1000) % 60;
        long minutes = (milliseconds / (60 * 1000));
        return String.format("%s minutes, %s seconds, %s milliseconds", minutes, seconds, millis);
    }

    public static Date getNDaysBefore(Date date, long n) {
        return new Date(date.getTime() - TimeUnit.DAYS.toMillis(n));
    }

    private static String toTimeValue(int timeVal) {
        if (timeVal >= 0 && timeVal <= 9) {
            return ("0" + timeVal);
        }
        return String.valueOf(timeVal);
    }

    /**
     * Parse timestamp from candlestick time string (thread-safe).
     * Handles both millisecond timestamps and ISO-8601 datetime strings.
     *
     * @param timeStr The time string to parse (either milliseconds or ISO-8601 format)
     * @return The timestamp in milliseconds
     */
    public static long parseCandlestickTimestamp(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            log.warn("Empty timestamp provided, using current time");
            return System.currentTimeMillis();
        }

        // First try to parse as a long (milliseconds)
        try {
            return Long.parseLong(timeStr);
        } catch (NumberFormatException e) {
            // If not a number, parse as ISO-8601 datetime string
            try {
                ZonedDateTime zdt = ZonedDateTime.parse(timeStr, ISO_FORMATTER);
                return zdt.toInstant().toEpochMilli();
            } catch (DateTimeParseException e2) {
                log.warn("Failed to parse timestamp: '{}', using current time", timeStr);
                return System.currentTimeMillis();
            }
        }
    }

    /**
     * Check if timestamp is within trading hours (9:15 AM to 3:30 PM IST)
     *
     * @param timestamp The timestamp in milliseconds
     * @return true if within trading hours, false otherwise
     */
    public static boolean isWithinTradingHours(long timestamp) {
        LocalDateTime dateTime = fromEpochMilli(timestamp);
        int hour = dateTime.getHour();
        int minute = dateTime.getMinute();

        // Trading hours: 9:15 AM to 3:30 PM
        int totalMinutes = hour * MINUTES_IN_HOUR + minute;
        int marketOpen = MARKET_OPEN_HOUR * MINUTES_IN_HOUR + MARKET_OPEN_MINUTE;
        int marketClose = MARKET_CLOSE_HOUR * MINUTES_IN_HOUR + MARKET_CLOSE_MINUTE;

        return totalMinutes >= marketOpen && totalMinutes <= marketClose;
    }

    /**
     * Convert epoch milliseconds to LocalDateTime in IST timezone
     *
     * @param epochMilli The timestamp in milliseconds
     * @return LocalDateTime representation in Asia/Kolkata timezone
     */
    public static LocalDateTime fromEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(
            Instant.ofEpochMilli(epochMilli),
            IST_ZONE
        );
    }

    /**
     * Parse a datetime string to epoch milliseconds (thread-safe).
     * Supports multiple date formats.
     *
     * @param dateTimeStr The datetime string to parse
     * @return The timestamp in milliseconds
     */
    public static long parseDateTimeToEpoch(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return System.currentTimeMillis();
        }

        // Try ISO format first
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(dateTimeStr, ISO_FORMATTER);
            return zdt.toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            // Try other formats
            DateTimeFormatter[] formatters = {
                DATE_TIME_SEC_FORMATTER,
                DATE_FORMATTER,
                DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH)
            };

            for (DateTimeFormatter formatter : formatters) {
                try {
                    // Try parsing as LocalDateTime first
                    try {
                        LocalDateTime ldt = LocalDateTime.parse(dateTimeStr, formatter);
                        return ldt.atZone(SYSTEM_ZONE).toInstant().toEpochMilli();
                    } catch (DateTimeParseException ex) {
                        // Try parsing as LocalDate
                        LocalDate ld = LocalDate.parse(dateTimeStr, formatter);
                        return ld.atStartOfDay(SYSTEM_ZONE).toInstant().toEpochMilli();
                    }
                } catch (DateTimeParseException ex) {
                    // Try next format
                }
            }

            log.warn("Failed to parse datetime: '{}', using current time", dateTimeStr);
            return System.currentTimeMillis();
        }
    }

    /**
     * Format a timestamp to human-readable datetime string (thread-safe).
     *
     * @param timestamp The timestamp in milliseconds
     * @return Formatted datetime string in "yyyy-MM-dd HH:mm:ss" format
     */
    public static String formatDateTime(long timestamp) {
        return DATE_TIME_SEC_FORMATTER.format(
            Instant.ofEpochMilli(timestamp).atZone(SYSTEM_ZONE)
        );
    }

    /**
     * Parses a zoned date-time string to a Date truncated to the minute.
     * @throws IllegalStateException if the date-time string cannot be parsed
     */
    public static Date parseOrderDate(String zonedDateTimeString) {
        return getDateTimeStringForZonedDateString(zonedDateTimeString)
                .flatMap(TimeUtils::getDateTimeObjectMinute)
                .orElseThrow(() -> new IllegalStateException("Failed to parse order date from: " + zonedDateTimeString));
    }

    public static LocalDate getLocalDateFromDate(Date date) {
        return date.toInstant().atZone(IST_ZONE).toLocalDate();
    }
}
