package com.vish.fno.phase.util;

import com.vish.fno.model.Candle;

import java.util.List;

/**
 * Utility class for calculating Average True Range (ATR).
 * This utility is shared across multiple Wyckoff phase identifiers.
 */
public final class ATRCalculator {

    private ATRCalculator() {
        // Private constructor to prevent instantiation
    }

    /**
     * Calculate the Average True Range (ATR) for a list of candles.
     *
     * @param candles The list of candles to calculate ATR for
     * @param period The period for ATR calculation
     * @return The ATR value, or 0.0 if insufficient data
     */
    public static double calculateATR(List<Candle> candles, int period) {
        if (candles.size() < period + 1) {
            return 0.0;
        }

        double atr = 0.0;
        for (int i = 1; i < candles.size(); i++) {
            Candle current = candles.get(i);
            Candle previous = candles.get(i - 1);

            double tr = Math.max(
                current.high() - current.low(),
                Math.max(
                    Math.abs(current.high() - previous.close()),
                    Math.abs(current.low() - previous.close())
                )
            );
            atr += tr;
        }
        return atr / (candles.size() - 1);
    }
}
