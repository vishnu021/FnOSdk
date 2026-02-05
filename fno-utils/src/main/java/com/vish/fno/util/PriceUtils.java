package com.vish.fno.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("PMD.AvoidDecimalLiteralsInBigDecimalConstructor")
public final class PriceUtils {

    public static String getStringRoundedPrice(final double price) {
        boolean signed = price < 0;
        double absolutePrice = Math.abs(price);
        String simplifiedPrice;

        if (absolutePrice > (1000000)) {
            simplifiedPrice = getFormattedRoundPrice(absolutePrice / (1000 * 1000)) + "M";
        }
        else if (absolutePrice > 1000) {
            simplifiedPrice = getFormattedRoundPrice(absolutePrice / 1000) + "K";
        }
        else {
            simplifiedPrice = getFormattedRoundPrice(absolutePrice);
        }

        return signed ? "-" + simplifiedPrice : simplifiedPrice;
    }

    public static String format(double price) {
        return String.format("%.2f", price);
    }

    private static String getFormattedRoundPrice(double price) {
        return format(round(price));
    }

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

    public static String getTopNLines(Throwable throwable, int n) {
        StackTraceElement[] stackTraceElements = throwable.getStackTrace();
        int length = stackTraceElements.length;
        StringBuilder topThreeLines = new StringBuilder();

        for (int i = 0; i < Math.min(n, length); i++) {
            topThreeLines.append(stackTraceElements[i].toString()).append("\n");
        }

        return topThreeLines.toString();
    }
}
