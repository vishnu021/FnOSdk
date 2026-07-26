package com.vish.fno.util.candle;

import com.vish.fno.model.Candle;
import com.vish.fno.util.time.TimeUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TimeFrameUtils {

    /**
     * Merges a list of Candle objects into a new list based on a specified group size.
     * <p>
     * This method groups the given Candle objects by their dates, and then combines every 'n' candles
     * within the same date into a single Candle object. The combination of candles is done using the
     * 'combine' method.
     *
     * @param allCandles the list of Candle objects to be merged. Each Candle object is assumed to have
     *                   a time attribute which is used for grouping by date.
     * @param n the number of candles to be merged into one in each group. If there are fewer than 'n'
     *          candles in the last group of a particular day, all of them are merged together.
     * @return a List of Candle objects, where each Candle represents a merged group of 'n' candles
     *         from the same day. The list is ordered by the dates of the candles.
     * @see Candle
     * @see TimeUtils
     */
    @SuppressWarnings("PMD.UseConcurrentHashMap")
    public static List<Candle> mergeCandle(List<Candle> allCandles, int n) {
        List<Candle> mergedCandles = new ArrayList<>();

        Map<String, List<Candle>> groupedCandles = new TreeMap<>();
        for (Candle candle : allCandles) {
            String date = TimeUtils.getDateObject(candle.time()).map(TimeUtils::getStringDate).orElse("");
            groupedCandles.putIfAbsent(date, new ArrayList<>());
            groupedCandles.get(date).add(candle);
        }

        for(String date: groupedCandles.keySet()) {
            List<Candle> sameDayCandles = groupedCandles.get(date);
            for (int i = 0; i < sameDayCandles.size(); i += n) {
                int lastIndex = Math.min(i + n, sameDayCandles.size());
                Candle mergedCandle = combine(sameDayCandles.subList(i, lastIndex));
                mergedCandles.add(mergedCandle);
            }
        }
        return mergedCandles;
    }

    /**
     * Merges a list of Candle objects into a new list based on a specified group size.
     * <p>
     * This method groups the given Candle combining every 'n' candles into a single Candle object.
     * The combination of candles is done using the 'combine' method.
     *
     * @param allCandles the list of Candle objects to be merged. Each Candle object is assumed to have
     *                   a time attribute which is used for grouping by date.
     * @param n the number of candles to be merged into one in each group. If there are fewer than 'n'
     *          candles in the last group of a particular day, all of them are merged together.
     * @return a List of Candle objects, where each Candle represents a merged group of 'n' candles
     *         from the same day. The list is ordered by the dates of the candles.
     * @see Candle
     * @see TimeUtils
     */
    public static List<Candle> mergeIntradayCompleteCandle(List<Candle> allCandles, int n) {
        List<Candle> candles = new ArrayList<>();
        if (allCandles == null || allCandles.isEmpty() || n <= 0) {
            return candles;
        }

        // Chunk WITHIN each day, never across a day boundary.
        //
        // This method chunked purely positionally (i += n over the whole list), despite the javadoc
        // above promising groups "from the same day". With a single day of input the two are
        // identical, which is why ~60 callers passing today-only candles are unaffected. With
        // multi-day input they are not: one merged bar straddled the overnight gap, so its range
        // was the gap itself.
        //
        // Measured 2026-07-24 after RegimeClassifier began seeding warm-up from prior-day history:
        // the straddling bar drove NIFTY 50 atrRatio to 1.95, past the 1.5 HIGH_VOLATILITY
        // threshold, pinning the index in HIGH_VOLATILITY through the opening window and blocking
        // RegimeBBReversionHighAdx — an enabled:true REAL leg that requires RANGE_BOUND.
        //
        // Trailing partial groups are still DROPPED, per day. That preserves the non-repainting
        // guarantee documented by MtfStructPullbackReversalStrategy and TrendBlipResumeStrategy:
        // an incomplete final block never becomes a bar, so a bar cannot change after it is emitted.
        int dayStart = 0;
        for (int i = 1; i <= allCandles.size(); i++) {
            boolean endOfList = i == allCandles.size();
            if (!endOfList && sameDay(allCandles.get(i - 1), allCandles.get(i))) {
                continue;
            }
            for (int j = dayStart; j + n <= i; j += n) {
                candles.add(combine(allCandles.subList(j, j + n)));
            }
            dayStart = i;
        }
        return candles;
    }

    /**
     * Two candles belong to the same session when their time strings share a {@code yyyy-MM-dd}
     * prefix. Mirrors the prior-day isolation already used by {@code CprWidthCalculator} and
     * {@code StrategyContextEnricher.priorSessionClose}. A null or short time is treated as
     * same-day so that malformed input degrades to the previous positional behaviour rather than
     * fragmenting into single-candle groups.
     */
    private static boolean sameDay(Candle a, Candle b) {
        String ta = a == null ? null : a.time();
        String tb = b == null ? null : b.time();
        if (ta == null || tb == null || ta.length() < 10 || tb.length() < 10) {
            return true;
        }
        return ta.regionMatches(0, tb, 0, 10);
    }

    public static Candle combine(List<Candle> candleList) {
        if (candleList == null || candleList.isEmpty()) {
            return null;
        }

        if(candleList.size() == 1) {
            return candleList.getFirst();
        }

        Candle first = candleList.getFirst();
        double high = first.high();
        double low = first.low();
        long volume = first.volume() != null ? first.volume() : 0;
        long oi = first.oi() != null ? first.oi() : 0;

        for (int i = 1; i < candleList.size(); i++) {
            Candle c = candleList.get(i);
            if (c.high() > high) {
                high = c.high();
            }
            if (c.low() < low) {
                low = c.low();
            }
            volume += c.volume() != null ? c.volume() : 0;
            oi += c.oi() != null ? c.oi() : 0;
        }

        return new Candle(first.time(), first.open(), high, low,
                candleList.getLast().close(), volume, oi);
    }
}
