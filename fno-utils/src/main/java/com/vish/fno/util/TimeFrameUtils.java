package com.vish.fno.util;

import com.vish.fno.model.Candle;
import com.vish.fno.util.TimeUtils;
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
    public static List<Candle> mergeCandle(List<Candle> allCandles, int n) {
        List<Candle> mergedCandles = new ArrayList<>();

        Map<String, List<Candle>> groupedCandles = new TreeMap<>();
        for (Candle candle : allCandles) {
            String date = TimeUtils.getStringDate(TimeUtils.getDateObject(candle.getTime()));
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

        for (int i = 0; i < allCandles.size(); i += n) {
            if (allCandles.size() < i + n){
                break;
            }
            Candle mergedCandle = combine(allCandles.subList(i, i + n));
            candles.add(mergedCandle);
        }
        return candles;
    }

    public static Candle combine(List<Candle> candleList) {
        if (candleList == null || candleList.isEmpty()) {
            return null;
        }

        if(candleList.size() == 1) {
            return candleList.get(0);
        }

        double open = candleList.get(0).getOpen();
        double close = candleList.get(candleList.size() - 1).getClose();
        double high = candleList.stream().mapToDouble(Candle::getHigh).max().orElse(0d);
        double low = candleList.stream().mapToDouble(Candle::getLow).min().orElse(0d);
        long volume = (long) candleList.stream().mapToDouble(Candle::getVolume).sum();
        long oi = (long) candleList.stream().mapToDouble(Candle::getOi).sum();
        String time = candleList.get(0).getTime();

        return new Candle(time, open, high, low, close, volume, oi);
    }
}
