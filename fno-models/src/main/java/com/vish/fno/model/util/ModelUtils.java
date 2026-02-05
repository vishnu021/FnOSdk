package com.vish.fno.model.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@SuppressWarnings("PMD.AvoidDecimalLiteralsInBigDecimalConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ModelUtils {
    public static final String INDENTED_TAB = "\n\t\t\t\t\t\t";
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String TIME_FORMAT = "HH:mm:ss.SSS";
    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm";

    public static double round(double price) {
        return Math.round(price / 0.05) * 0.05;
    }

    public static BigDecimal roundTo5Paise(double price) {
        final BigDecimal value = new BigDecimal(price);
        return roundToNearest(value, new BigDecimal("0.05"));
    }

    public static BigDecimal roundToNearest(final BigDecimal value, final BigDecimal increment) {
        final BigDecimal divided = value.divide(increment, 0, RoundingMode.HALF_UP);
        return divided.multiply(increment).setScale(2, RoundingMode.HALF_UP);
    }

    public static String getStringDate(Date date) {
        if(date==null) {
            return "";
        }
        SimpleDateFormat dateFormatter = new SimpleDateFormat(DATE_FORMAT, Locale.ENGLISH);
        return dateFormatter.format(date);
    }

    public static String getStringTime(Date date) {
        if(date==null) {
            return "";
        }
        SimpleDateFormat dateFormatter = new SimpleDateFormat(TIME_FORMAT, Locale.ENGLISH);
        return dateFormatter.format(date);
    }

    public static String getStringDateTime(Date date) {
        if(date==null) {
            return "";
        }
        SimpleDateFormat dateFormatter = new SimpleDateFormat(DATE_TIME_FORMAT, Locale.ENGLISH);
        return dateFormatter.format(date);
    }
}
