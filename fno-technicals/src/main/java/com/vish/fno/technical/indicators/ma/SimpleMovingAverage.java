package com.vish.fno.technical.indicators.ma;

import com.vish.fno.technical.indicators.AbstractIndicator;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.IntStream;

@Slf4j
public class SimpleMovingAverage extends AbstractIndicator {

    private final int duration;


    public SimpleMovingAverage() {
        this.duration = 14;
    }

    public SimpleMovingAverage(int duration) {
        this.duration = duration;
    }

    public List<Double> calculateFromClosedPrice(List<Double> candles) {
        return IntStream
                .range(0, candles.size())
                .boxed()
                .map(i -> movingAverage(candles, i))
                .toList();
    }

    public List<Double> calculateFromClosedPrice(List<Double> candles, List<Double> prevCandles) {
        return IntStream
                .range(0, candles.size())
                .boxed()
                .map(i -> movingAverage(candles, prevCandles, i))
                .toList();

    }

    private double movingAverage(List<Double> candleClosed, List<Double> prevCandleClosed, int i) {
        int startIndex = i >= duration ? i - duration + 1 : 0;

        if (i < duration) {
           double sum = candleClosed.subList(startIndex, i + 1).stream().mapToDouble(d -> d).sum();
            sum += prevCandleClosed.subList(prevCandleClosed.size() - (duration - i) + 1, prevCandleClosed.size()).stream().mapToDouble(d -> d).sum();
            return sum / duration;
        }

        return candleClosed.subList(startIndex, i + 1).stream().mapToDouble(d -> d).average().orElse(0);
    }

    private double movingAverage(List<Double> candleClose, int i) {
        int startIndex = i >= duration ? i - duration + 1 : 0;
        return candleClose.subList(startIndex, i + 1).stream().mapToDouble(d -> d).average().orElse(0);
    }
}
