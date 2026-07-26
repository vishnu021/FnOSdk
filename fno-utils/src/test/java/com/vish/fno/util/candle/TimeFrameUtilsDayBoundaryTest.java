package com.vish.fno.util.candle;

import com.vish.fno.model.Candle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Day-boundary contract for {@link TimeFrameUtils#mergeIntradayCompleteCandle}.
 *
 * <p>The method chunked purely positionally ({@code i += n} across the whole list) while its javadoc
 * promised groups "from the same day". Identical for single-day input — which is why the ~60
 * existing callers were unaffected — but with multi-day input one merged bar straddled the overnight
 * gap and took the gap as its range.
 *
 * <p>Measured on 2026-07-24 once {@code RegimeClassifier} began seeding warm-up from prior-day
 * history: the straddling bar pushed NIFTY 50 {@code atrRatio} to 1.95, past the 1.5
 * HIGH_VOLATILITY threshold, holding the index in HIGH_VOLATILITY through the opening window and
 * blocking {@code RegimeBBReversionHighAdx} — an {@code enabled: true} REAL leg gated on
 * RANGE_BOUND.
 */
class TimeFrameUtilsDayBoundaryTest {

    private static Candle at(String date, int hh, int mm, double open, double high,
                             double low, double close) {
        return new Candle(String.format("%sT%02d:%02d:00+0530", date, hh, mm),
                open, high, low, close, 100L, 0L);
    }

    /** n minute-candles for one session, tight range around {@code base}. */
    private static List<Candle> session(String date, int count, double base) {
        List<Candle> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(at(date, 9 + (15 + i) / 60, (15 + i) % 60,
                    base, base + 2, base - 2, base + 1));
        }
        return out;
    }

    @Test
    @DisplayName("single-day input is unchanged — the ~60 existing callers cannot be affected")
    void singleDayBehaviourIsUnchanged() {
        List<Candle> day = session("2026-07-24", 12, 24_000);

        List<Candle> merged = TimeFrameUtils.mergeIntradayCompleteCandle(day, 5);

        assertEquals(2, merged.size(), "12 candles at n=5 -> 2 complete groups, trailing 2 dropped");
        assertEquals(day.get(0).open(), merged.get(0).open(), 1e-9);
        assertEquals(day.get(4).close(), merged.get(0).close(), 1e-9);
        assertEquals(day.get(5).open(), merged.get(1).open(), 1e-9);
    }

    @Test
    @DisplayName("no merged bar straddles the overnight gap")
    void noBarSpansTwoSessions() {
        // Prior session near 24,000; today gaps up to 25,000. A straddling bar would show a
        // ~1,000-point range; a correctly grouped one stays within its own session's ~4 points.
        List<Candle> candles = new ArrayList<>();
        candles.addAll(session("2026-07-23", 7, 24_000));   // 7 -> one group of 5, 2 dropped
        candles.addAll(session("2026-07-24", 5, 25_000));   // 5 -> one group of 5

        List<Candle> merged = TimeFrameUtils.mergeIntradayCompleteCandle(candles, 5);

        assertEquals(2, merged.size(), "one complete group per day; partials dropped per day");
        for (Candle c : merged) {
            assertTrue(c.high() - c.low() < 100,
                    "a bar spanning the overnight gap would have a ~1000pt range; got "
                            + (c.high() - c.low()));
        }
        assertTrue(merged.get(0).close() < 24_100, "first bar belongs to the prior session");
        assertTrue(merged.get(1).open() > 24_900, "second bar belongs to today");
    }

    @Test
    @DisplayName("the OLD positional chunking would have produced a straddling bar here")
    void demonstratesTheDefectItFixes() {
        // 3 candles on day 1 + 3 on day 2, n=5. Positional chunking takes indices 0..4 — three from
        // day 1 and two from day 2 — one bar covering both sessions. Day-grouped chunking emits
        // NOTHING, because neither day has a complete group of 5.
        List<Candle> candles = new ArrayList<>();
        candles.addAll(session("2026-07-23", 3, 24_000));
        candles.addAll(session("2026-07-24", 3, 25_000));

        List<Candle> merged = TimeFrameUtils.mergeIntradayCompleteCandle(candles, 5);

        assertEquals(0, merged.size(),
                "neither session has 5 complete candles, so no bar is emitted — the old code "
                        + "emitted one bar spanning both sessions");
    }

    @Test
    @DisplayName("trailing partial groups are still dropped PER DAY (non-repainting guarantee)")
    void trailingPartialsDroppedPerDay() {
        // 6 + 6 at n=5 -> one complete group each day, one trailing candle each day dropped.
        List<Candle> candles = new ArrayList<>();
        candles.addAll(session("2026-07-23", 6, 24_000));
        candles.addAll(session("2026-07-24", 6, 25_000));

        List<Candle> merged = TimeFrameUtils.mergeIntradayCompleteCandle(candles, 5);

        assertEquals(2, merged.size(),
                "MtfStructPullbackReversalStrategy and TrendBlipResumeStrategy document a "
                        + "non-repainting guarantee that rests on incomplete blocks being dropped");
    }

    @Test
    @DisplayName("degenerate input does not throw")
    void degenerateInputIsSafe() {
        assertEquals(0, TimeFrameUtils.mergeIntradayCompleteCandle(null, 5).size());
        assertEquals(0, TimeFrameUtils.mergeIntradayCompleteCandle(List.of(), 5).size());
        assertEquals(0,
                TimeFrameUtils.mergeIntradayCompleteCandle(session("2026-07-24", 3, 24_000), 0).size());
    }
}
