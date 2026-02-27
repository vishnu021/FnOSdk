package com.vish.fno.util.candle;

import com.vish.fno.model.Candle;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Utility class for identifying candlestick patterns used in Wyckoff phase trading.
 * Provides methods to detect reversal and continuation patterns.
 *
 * @author Vishnu Shankar
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CandlePatternUtils {

    private static final double BODY_TO_RANGE_RATIO = 0.6; // Minimum body size for pattern validity
    private static final double DOJI_BODY_RATIO = 0.1; // Maximum body size for doji
    private static final double ENGULFING_SIZE_FACTOR = 1.2; // Minimum size factor for engulfing

    /**
     * Check if a candle is a Hammer pattern (bullish reversal)
     */
    public static boolean isHammer(Candle candle) {
        double body = Math.abs(candle.close() - candle.open());
        double range = candle.high() - candle.low();
        double lowerShadow = Math.min(candle.open(), candle.close()) - candle.low();
        double upperShadow = candle.high() - Math.max(candle.open(), candle.close());

        if (range == 0) {
            return false;
        }

        return lowerShadow > body * 2 && // Long lower shadow
               upperShadow < body * 0.3 && // Small upper shadow
               body / range < 0.4; // Small body relative to range
    }

    /**
     * Check if a candle is an Inverted Hammer pattern
     */
    public static boolean isInvertedHammer(Candle candle) {
        double body = Math.abs(candle.close() - candle.open());
        double range = candle.high() - candle.low();
        double lowerShadow = Math.min(candle.open(), candle.close()) - candle.low();
        double upperShadow = candle.high() - Math.max(candle.open(), candle.close());

        if (range == 0) {
            return false;
        }

        return upperShadow > body * 2 && // Long upper shadow
               lowerShadow < body * 0.3 && // Small lower shadow
               body / range < 0.4; // Small body relative to range
    }

    /**
     * Check if a candle is a Shooting Star pattern (bearish reversal)
     */
    public static boolean isShootingStar(Candle candle) {
        return isInvertedHammer(candle) && candle.close() < candle.open();
    }

    /**
     * Check if a candle is a Doji pattern
     */
    public static boolean isDoji(Candle candle) {
        double body = Math.abs(candle.close() - candle.open());
        double range = candle.high() - candle.low();

        return range != 0 && (body / range) < DOJI_BODY_RATIO;
    }

    /**
     * Check if current candle forms a Bullish Engulfing pattern with previous
     */
    public static boolean isBullishEngulfing(Candle prev, Candle current) {
        if (prev == null || current == null) {
            return false;
        }

        boolean prevBearish = prev.close() < prev.open();
        boolean currentBullish = current.close() > current.open();

        return prevBearish && currentBullish &&
               current.open() < prev.close() &&
               current.close() > prev.open() &&
               Math.abs(current.close() - current.open()) >
               Math.abs(prev.close() - prev.open()) * ENGULFING_SIZE_FACTOR;
    }

    /**
     * Check if current candle forms a Bearish Engulfing pattern with previous
     */
    public static boolean isBearishEngulfing(Candle prev, Candle current) {
        if (prev == null || current == null) {
            return false;
        }

        boolean prevBullish = prev.close() > prev.open();
        boolean currentBearish = current.close() < current.open();

        return prevBullish && currentBearish &&
               current.open() > prev.close() &&
               current.close() < prev.open() &&
               Math.abs(current.close() - current.open()) >
               Math.abs(prev.close() - prev.open()) * ENGULFING_SIZE_FACTOR;
    }

    /**
     * Check for Morning Star pattern (3-candle bullish reversal)
     */
    public static boolean isMorningStar(List<Candle> candles, int index) {
        if (index < 2 || index >= candles.size()) {
            return false;
        }

        Candle first = candles.get(index - 2);
        Candle middle = candles.get(index - 1);
        Candle third = candles.get(index);

        // First: Large bearish candle
        boolean firstBearish = first.close() < first.open() &&
                              Math.abs(first.close() - first.open()) > (first.high() - first.low()) * 0.6;

        // Middle: Small body (star) gapped down
        boolean middleSmall = Math.abs(middle.close() - middle.open()) <
                            Math.abs(first.close() - first.open()) * 0.3;
        boolean gappedDown = Math.max(middle.open(), middle.close()) < first.close();

        // Third: Large bullish candle closing above mid-point of first
        boolean thirdBullish = third.close() > third.open() &&
                             third.close() > (first.open() + first.close()) / 2;

        return firstBearish && middleSmall && gappedDown && thirdBullish;
    }

    /**
     * Check for Evening Star pattern (3-candle bearish reversal)
     */
    public static boolean isEveningStar(List<Candle> candles, int index) {
        if (index < 2 || index >= candles.size()) {
            return false;
        }

        Candle first = candles.get(index - 2);
        Candle middle = candles.get(index - 1);
        Candle third = candles.get(index);

        // First: Large bullish candle
        boolean firstBullish = first.close() > first.open() &&
                             Math.abs(first.close() - first.open()) > (first.high() - first.low()) * 0.6;

        // Middle: Small body (star) gapped up
        boolean middleSmall = Math.abs(middle.close() - middle.open()) <
                            Math.abs(first.close() - first.open()) * 0.3;
        boolean gappedUp = Math.min(middle.open(), middle.close()) > first.close();

        // Third: Large bearish candle closing below mid-point of first
        boolean thirdBearish = third.close() < third.open() &&
                             third.close() < (first.open() + first.close()) / 2;

        return firstBullish && middleSmall && gappedUp && thirdBearish;
    }

    /**
     * Helper method to check if three candles have substantial bodies
     */
    private static boolean hasSubstantialBodies(Candle first, Candle second, Candle third) {
        return Math.abs(first.close() - first.open()) > (first.high() - first.low()) * 0.5 &&
               Math.abs(second.close() - second.open()) > (second.high() - second.low()) * 0.5 &&
               Math.abs(third.close() - third.open()) > (third.high() - third.low()) * 0.5;
    }

    /**
     * Check for Three White Soldiers pattern (bullish continuation)
     */
    public static boolean isThreeWhiteSoldiers(List<Candle> candles, int index) {
        if (index < 2 || index >= candles.size()) {
            return false;
        }

        Candle first = candles.get(index - 2);
        Candle second = candles.get(index - 1);
        Candle third = candles.get(index);

        // All three must be bullish
        boolean allBullish = first.close() > first.open() &&
                           second.close() > second.open() &&
                           third.close() > third.open();

        // Each opens within previous body and closes higher
        boolean progressive = second.open() > first.open() && second.open() < first.close() &&
                            second.close() > first.close() &&
                            third.open() > second.open() && third.open() < second.close() &&
                            third.close() > second.close();

        return allBullish && progressive && hasSubstantialBodies(first, second, third);
    }

    /**
     * Check for Three Black Crows pattern (bearish continuation)
     */
    public static boolean isThreeBlackCrows(List<Candle> candles, int index) {
        if (index < 2 || index >= candles.size()) {
            return false;
        }

        Candle first = candles.get(index - 2);
        Candle second = candles.get(index - 1);
        Candle third = candles.get(index);

        // All three must be bearish
        boolean allBearish = first.close() < first.open() &&
                           second.close() < second.open() &&
                           third.close() < third.open();

        // Each opens within previous body and closes lower
        boolean progressive = second.open() < first.open() && second.open() > first.close() &&
                            second.close() < first.close() &&
                            third.open() < second.open() && third.open() > second.close() &&
                            third.close() < second.close();

        return allBearish && progressive && hasSubstantialBodies(first, second, third);
    }

    /**
     * Check for Dark Cloud Cover pattern (bearish reversal)
     */
    public static boolean isDarkCloudCover(Candle prev, Candle current) {
        if (prev == null || current == null) {
            return false;
        }

        boolean prevBullish = prev.close() > prev.open();
        boolean currentBearish = current.close() < current.open();

        // Current opens above previous high and closes below midpoint
        return prevBullish && currentBearish &&
               current.open() > prev.high() &&
               current.close() < (prev.open() + prev.close()) / 2 &&
               current.close() > prev.open();
    }

    /**
     * Check for Piercing Pattern (bullish reversal)
     */
    public static boolean isPiercingPattern(Candle prev, Candle current) {
        if (prev == null || current == null) {
            return false;
        }

        boolean prevBearish = prev.close() < prev.open();
        boolean currentBullish = current.close() > current.open();

        // Current opens below previous low and closes above midpoint
        return prevBearish && currentBullish &&
               current.open() < prev.low() &&
               current.close() > (prev.open() + prev.close()) / 2 &&
               current.close() < prev.open();
    }

    /**
     * Check for Tweezer Bottom pattern
     */
    public static boolean isTweezerBottom(Candle prev, Candle current) {
        if (prev == null || current == null) {
            return false;
        }

        double tolerance = (prev.high() - prev.low()) * 0.01; // 1% tolerance

        return Math.abs(prev.low() - current.low()) < tolerance &&
               prev.close() < prev.open() && // First is bearish
               current.close() > current.open(); // Second is bullish
    }

    /**
     * Check for Tweezer Top pattern
     */
    public static boolean isTweezerTop(Candle prev, Candle current) {
        if (prev == null || current == null) {
            return false;
        }

        double tolerance = (prev.high() - prev.low()) * 0.01; // 1% tolerance

        return Math.abs(prev.high() - current.high()) < tolerance &&
               prev.close() > prev.open() && // First is bullish
               current.close() < current.open(); // Second is bearish
    }

    /**
     * Check if current candle is a strong bullish candle
     */
    public static boolean isStrongBullish(Candle candle) {
        double body = candle.close() - candle.open();
        double range = candle.high() - candle.low();

        return body > 0 &&
               body / range > BODY_TO_RANGE_RATIO &&
               candle.close() > (candle.high() + candle.low()) / 2;
    }

    /**
     * Check if current candle is a strong bearish candle
     */
    public static boolean isStrongBearish(Candle candle) {
        double body = candle.open() - candle.close();
        double range = candle.high() - candle.low();

        return body > 0 &&
               body / range > BODY_TO_RANGE_RATIO &&
               candle.close() < (candle.high() + candle.low()) / 2;
    }

    /**
     * Check for Bearish Harami pattern
     */
    public static boolean isBearishHarami(Candle prev, Candle current) {
        if (prev == null || current == null) {
            return false;
        }

        boolean prevBullish = prev.close() > prev.open();
        boolean currentBearish = current.close() < current.open();

        // Current candle body is contained within previous candle body
        return prevBullish && currentBearish &&
               current.open() < prev.close() &&
               current.close() > prev.open() &&
               Math.abs(current.close() - current.open()) <
               Math.abs(prev.close() - prev.open()) * 0.5;
    }

    /**
     * Check for Bullish Harami pattern
     */
    public static boolean isBullishHarami(Candle prev, Candle current) {
        if (prev == null || current == null) {
            return false;
        }

        boolean prevBearish = prev.close() < prev.open();
        boolean currentBullish = current.close() > current.open();

        // Current candle body is contained within previous candle body
        return prevBearish && currentBullish &&
               current.open() > prev.close() &&
               current.close() < prev.open() &&
               Math.abs(current.close() - current.open()) <
               Math.abs(prev.close() - prev.open()) * 0.5;
    }
}
