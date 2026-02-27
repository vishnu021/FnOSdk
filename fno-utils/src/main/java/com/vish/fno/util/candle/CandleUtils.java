package com.vish.fno.util.candle;

import com.vish.fno.model.Candle;
import com.vish.fno.util.time.TimeUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("PMD.AvoidThrowingRawExceptionTypes")
public final class CandleUtils {

    public static final List<String> timeArray = TimeUtils.timeArray;

    public static boolean isBullish(Candle candle) {
        return candle.close() > candle.open();
    }

    public static boolean isBearish(Candle candle) {
        return candle.close() < candle.open();
    }

    public static double getBodyLength(Candle candle) {
        if(isBullish(candle)) {
            return candle.close() - candle.open();
        }
        return candle.open() - candle.close();
    }

    public static double getTotalLength(Candle candle) {
        return candle.high() - candle.low();
    }

    public static double getUpperWick(Candle candle) {
        if(isBullish(candle)) {
            return candle.high() - candle.close();
        }
        return candle.high() - candle.open();
    }

    public static double getLowerWick(Candle candle) {
        if(isBullish(candle)) {
            return candle.open() - candle.low();
        }
        return candle.close() - candle.low();
    }

    public static boolean contains(Candle candle, double value) {
        return candle.high() > value && candle.low() < value;
    }

    /**
     * Find the index of a local minimum (a low point surrounded by higher values)
     * @param candles List of candlesticks
     * @param startIndex Search starting index
     * @param window Number of candles to check on each side
     * @return Index of local minimum or -1 if not found
     */
    public static int findLocalMinimum(List<Candle> candles, int startIndex, int window) {
        if (candles == null || candles.isEmpty() || startIndex < window || startIndex >= candles.size() - window) {
            return -1;
        }

        for (int i = startIndex; i < candles.size() - window; i++) {
            boolean isMin = true;
            double currentLow = candles.get(i).low();

            // Check previous candles
            for (int j = Math.max(0, i - window); j < i; j++) {
                if (candles.get(j).low() < currentLow) {
                    isMin = false;
                    break;
                }
            }

            // If still a potential minimum, check following candles
            if (isMin) {
                for (int j = i + 1; j <= i + window; j++) {
                    if (candles.get(j).low() < currentLow) {
                        isMin = false;
                        break;
                    }
                }
            }

            if (isMin) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Find the index of a local maximum (a high point surrounded by lower values)
     * @param candles List of candlesticks
     * @param startIndex Search starting index
     * @param window Number of candles to check on each side
     * @return Index of local maximum or -1 if not found
     */
    public static int findLocalMaximum(List<Candle> candles, int startIndex, int window) {
        if (candles == null || candles.isEmpty() || startIndex < window || startIndex >= candles.size() - window) {
            return -1;
        }

        for (int i = startIndex; i < candles.size() - window; i++) {
            boolean isMax = true;
            double currentHigh = candles.get(i).high();

            // Check previous candles
            for (int j = Math.max(0, i - window); j < i; j++) {
                if (candles.get(j).high() > currentHigh) {
                    isMax = false;
                    break;
                }
            }

            // If still a potential maximum, check following candles
            if (isMax) {
                for (int j = i + 1; j <= i + window; j++) {
                    if (candles.get(j).high() > currentHigh) {
                        isMax = false;
                        break;
                    }
                }
            }

            if (isMax) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Calculate the body size of a candlestick as a percentage of the total range
     */
    public static double getBodySizePercentage(Candle candle) {
        double range = candle.high() - candle.low();
        if (range == 0) {
            return 0;
        }

        double bodySize = Math.abs(candle.close() - candle.open());
        return bodySize / range;
    }
}
