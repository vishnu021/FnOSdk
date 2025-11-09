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
                haOpen = currentCandle.open();
                haClose = currentCandle.close();
            } else {
                Candle previousHA = heikinAshiCandles.get(i - 1);
                haOpen = (previousHA.open() + previousHA.close()) / 2;
                haClose = (currentCandle.open() + currentCandle.high() + currentCandle.low() + currentCandle.close()) / 4;
            }

            double haHigh = Math.max(Math.max(currentCandle.high(), haOpen), haClose);
            double haLow = Math.min(Math.min(currentCandle.low(), haOpen), haClose);

            heikinAshiCandles.add(new Candle(currentCandle.time(), haOpen, haHigh, haLow, haClose, currentCandle.volume(), currentCandle.oi()));
        }
        return heikinAshiCandles;
    }
}
