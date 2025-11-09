package com.vish.fno.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.vish.fno.util.Constants.*;

@Slf4j
@SuppressWarnings("PMD.AvoidCatchingGenericException")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TimeUtils {

    public static final List<String> timeArray = new ArrayList<>();

    // SimpleDateFormat for ISO-8601 format with timezone like "2025-08-20T09:57:00+0530"
    private static final SimpleDateFormat ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH);

    static {
        int hour = 9;
        int minute = 15;
        for (int i = 0; i <= 375; i++) {
            timeArray.add(toTimeValue(hour) + ":" + toTimeValue(minute));
            minute++;
            if (minute == 60) {
                hour++;
                minute = 0;
            }
        }
    }

    public static String getTimeStringForZonedDateString(String date) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_SEC_T_FORMAT, Locale.ENGLISH);
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(date, formatter);

            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(TIME_FORMAT, Locale.ENGLISH);
            return timeFormatter.format(zonedDateTime);
        } catch (Exception e) {
            log.error("Failed to format date string: {} to pattern: {}", date, DATE_TIME_SEC_T_FORMAT, e);
        }
        return null;
    }

    public static String getDateTimeStringForZonedDateString(String date) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_SEC_T_FORMAT, Locale.ENGLISH);
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(date, formatter);

            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT, Locale.ENGLISH);
            return timeFormatter.format(zonedDateTime);
        } catch (Exception e) {
            log.error("Failed to format date string: {} to pattern: {}", date, DATE_TIME_SEC_T_FORMAT, e);
        }
        return null;
    }

    public static Date getDateTimeForZonedDateString(String date) {
        try {
            final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_SEC_T_FORMAT, Locale.ENGLISH);
            final ZonedDateTime zonedDateTime = ZonedDateTime.parse(date, formatter);
            final Instant instant = zonedDateTime.toInstant();
            return Date.from(instant);
        } catch (Exception e) {
            log.error("Failed to format date string: {} to pattern: {}", date, DATE_TIME_SEC_T_FORMAT, e);
        }
        return null;
    }

    public static int getIndexOfTimeStamp(Date timeStamp) {
        if (timeStamp == null) {
            return -1;
        }
        return getIndexOfTime(getTime(timeStamp));
    }

    public static Date appendOpeningTimeToDate(Date day) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(day);
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        calendar.set(Calendar.MINUTE, 15);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    public static Date appendClosingTimeToDate(Date day) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(day);
        calendar.set(Calendar.HOUR_OF_DAY, 15);
        calendar.set(Calendar.MINUTE, 30);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    public static Date currentTime() {
        return new Date(System.currentTimeMillis());
    }

    public static String getTime(Date timeStamp) {
        if (timeStamp == null) {
            return null;
        }
        SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm", Locale.ENGLISH);
        return timeFormatter.format(timeStamp);
    }

    public static Date getDateTimeObjectMinute(String date) {
        SimpleDateFormat formatterMilliSecond = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH);
        try {
            return formatterMilliSecond.parse(date);
        } catch (ParseException e) {
            log.error("Failed to parse date",e);
        }
        return null;
    }

    public static Date getDateObject(String date) {
        SimpleDateFormat formatterMilliSecond = new SimpleDateFormat(DATE_FORMAT, Locale.ENGLISH);
        try {
            return formatterMilliSecond.parse(date);
        } catch (ParseException e) {
            log.error("Failed to parse to date of format yyyy-MM-dd",e);
        }
        return null;
    }

    public static String getTodayDate() {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(DATE_FORMAT, Locale.ENGLISH);
        return dateFormatter.format(currentTime());
    }

    public static String getStringDate(Date date) {
        if(date==null) {
            return "";
        }
        SimpleDateFormat dateFormatter = new SimpleDateFormat(DATE_FORMAT, Locale.ENGLISH);
        return dateFormatter.format(date);
    }

    public static String getStringDateTime(Date timeStamp) {
        if (timeStamp == null) {
            return null;
        }
        SimpleDateFormat formatterMilliSecond = new SimpleDateFormat(DATE_TIME_MS_FORMAT, Locale.ENGLISH);
        return formatterMilliSecond.format(timeStamp);
    }

    private static int getIndexOfTime(String time) {
        return timeArray.indexOf(time);
    }

    public static String getTimeByIndex(int index) {
        return timeArray.get(index);
    }

    public static Date getOpeningTime() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        calendar.set(Calendar.MINUTE, 15);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    public static Date getClosingTime() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 15);
        calendar.set(Calendar.MINUTE, 30);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    public static Date getPreviousWorkDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);

        if(calendar.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
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
            if(calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY
                    && calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY){
                dates.add(calendar.getTime());
            }
            calendar.add(Calendar.DATE, 1);
        }

        log.debug("startDate={}, endDate={}, returning {}", startDate, endDate, dates);
        return dates;
    }

    public static String getStringYear(Date date) {
        if(date==null) {
            return "";
        }
        SimpleDateFormat dateFormatter = new SimpleDateFormat(YEAR_FORMAT, Locale.ENGLISH);
        return dateFormatter.format(date);
    }

    public static String getTimeElapsed(long milliseconds) {
        long millis = milliseconds % 1000;
        long seconds = (milliseconds/1000) % 60;
        long minutes = (milliseconds/(60 * 1000));
        return String.format("%s minutes, %s seconds, %s milliseconds", minutes, seconds, millis);
    }

    public static Date getNDaysBefore(long n) {
        return new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(n));
    }

    public static Date getNDaysBefore(Date date, long n) {
        return new Date(date.getTime() - TimeUnit.DAYS.toMillis(n));
    }

    public static String getTime() {
        SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm", Locale.ENGLISH);
        return timeFormatter.format(currentTime());
    }

    private static String toTimeValue(int timeVal) {
        if (timeVal >= 0 && timeVal <= 9) {
            return ("0" + timeVal);
        }
        return String.valueOf(timeVal);
    }

    /**
     * Parse timestamp from candlestick time string.
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
                synchronized (ISO_FORMAT) {
                    // SimpleDateFormat is not thread-safe, so we synchronize
                    Date date = ISO_FORMAT.parse(timeStr);
                    return date.getTime();
                }
            } catch (Exception e2) {
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
        int totalMinutes = hour * 60 + minute;
        int marketOpen = 9 * 60 + 15;  // 9:15 AM
        int marketClose = 15 * 60 + 30; // 3:30 PM

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
            ZoneId.of("Asia/Kolkata")
        );
    }

    /**
     * Parse a datetime string to epoch milliseconds
     * Supports multiple date formats
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
            synchronized (ISO_FORMAT) {
                Date date = ISO_FORMAT.parse(dateTimeStr);
                return date.getTime();
            }
        } catch (Exception e) {
            // Try other formats
            String[] formats = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd",
                "dd-MM-yyyy HH:mm:ss",
                "dd-MM-yyyy"
            };

            for (String format : formats) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.ENGLISH);
                    Date date = sdf.parse(dateTimeStr);
                    return date.getTime();
                } catch (Exception ex) {
                    // Try next format
                }
            }

            log.warn("Failed to parse datetime: '{}', using current time", dateTimeStr);
            return System.currentTimeMillis();
        }
    }

    /**
     * Format a timestamp to human-readable datetime string
     *
     * @param timestamp The timestamp in milliseconds
     * @return Formatted datetime string in "yyyy-MM-dd HH:mm:ss" format
     */
    public static String formatDateTime(long timestamp) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
        return dateFormat.format(new Date(timestamp));
    }
}
