package com.vish.fno.phase.wyckoff;

import com.vish.fno.model.Candle;
import com.vish.fno.model.wyckoff.WyckoffPhase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureSwingWyckoffPhaseIdentifierTest {

    /**
     * Build a list of candles with alternating high/low prices to produce identifiable swing points.
     * Candle constructor: Candle(String time, double open, double high, double low, double close, Long volume, Long oi)
     */
    private static List<Candle> buildCandles(int count) {
        List<Candle> candles = new ArrayList<>();
        double basePrice = 100.0;
        for (int i = 0; i < count; i++) {
            // Alternate between swing-high and swing-low candles so fractal pivots are detectable
            boolean isHighBar = (i % 4 == 2);
            boolean isLowBar  = (i % 4 == 0);
            double open  = basePrice + i * 0.1;
            double high  = isHighBar ? open + 3.0 : open + 1.0;
            double low   = isLowBar  ? open - 3.0 : open - 1.0;
            double close = (open + high + low) / 3.0;
            candles.add(new Candle(
                    "2025-01-01T09:" + String.format("%02d", i % 60) + ":00",
                    open,
                    high,
                    low,
                    close,
                    1000L,
                    500L
            ));
        }
        return candles;
    }

    @Test
    void identifyPhase_returnsNonNullPhase() {
        StructureSwingWyckoffPhaseIdentifier identifier = new StructureSwingWyckoffPhaseIdentifier();
        List<Candle> candles = buildCandles(30);

        WyckoffPhase result = identifier.identifyPhase(candles, 25);

        assertNotNull(result);
    }

    @Test
    void getPhaseConfidence_returnsValueBetweenZeroAndOne() {
        StructureSwingWyckoffPhaseIdentifier identifier = new StructureSwingWyckoffPhaseIdentifier();
        List<Candle> candles = buildCandles(30);

        // identifyPhase must be called first to populate the mutable swing state
        identifier.identifyPhase(candles, 25);

        double confidence = identifier.getPhaseConfidence(candles, 25);

        assertTrue(confidence >= 0.0 && confidence <= 1.0,
                "Confidence should be in [0.0, 1.0] but was: " + confidence);
    }

    @Test
    void identifyPhase_withNullData_returnsUnknown() {
        StructureSwingWyckoffPhaseIdentifier identifier = new StructureSwingWyckoffPhaseIdentifier();

        WyckoffPhase result = identifier.identifyPhase(null, 0);

        assertEquals(WyckoffPhase.UNKNOWN, result);
    }

    @Test
    void reset_doesNotThrow() {
        StructureSwingWyckoffPhaseIdentifier identifier = new StructureSwingWyckoffPhaseIdentifier();
        List<Candle> candles = buildCandles(30);

        identifier.identifyPhase(candles, 25);

        // reset() must not throw
        identifier.reset();
    }
}
