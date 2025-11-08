package com.vish.fno.util.chart;

import com.vish.fno.model.Candle;
import com.vish.fno.util.TimeFrameUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class HeikinAshi {

    public static List<Candle> getCandles(List<Candle> allCandles) {
        return getCandles(allCandles, 1);
    }

    public static List<Candle> getCandles(List<Candle> allCandles, int timeFrame) {
        List<Candle> candles = TimeFrameUtils.mergeCandle(allCandles, timeFrame);
        return convertToHeikinAshi(candles);
    }

    public static List<Candle> getIntradayCompleteCandle(List<Candle> allCandles, int timeFrame) {
        List<Candle> candles = TimeFrameUtils.mergeIntradayCompleteCandle(allCandles, timeFrame);
        return convertToHeikinAshi(candles);
    }

    @NotNull
    private static List<Candle> convertToHeikinAshi(List<Candle> candles) {
        List<Candle> heikinAshiCandles = new ArrayList<>();

        for (int i = 0; i < candles.size(); i++) {
            Candle currentCandle = candles.get(i);
            double haOpen, haClose;

            if (i == 0) {
                haOpen = currentCandle.getOpen();
                haClose = currentCandle.getClose();
            } else {
                Candle previousHA = heikinAshiCandles.get(i - 1);
                haOpen = (previousHA.getOpen() + previousHA.getClose()) / 2;
                haClose = (currentCandle.getOpen() + currentCandle.getHigh() + currentCandle.getLow() + currentCandle.getClose()) / 4;
            }

            double haHigh = Math.max(Math.max(currentCandle.getHigh(), haOpen), haClose);
            double haLow = Math.min(Math.min(currentCandle.getLow(), haOpen), haClose);

            heikinAshiCandles.add(new Candle(currentCandle.getTime(), haOpen, haHigh, haLow, haClose, currentCandle.getVolume(), currentCandle.getOi()));
        }
        return heikinAshiCandles;
    }
}
