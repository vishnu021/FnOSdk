package com.vish.fno.technical.indicators.ma;

import com.vish.fno.technical.indicators.AbstractIndicator;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

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
        List<Double> result = new ArrayList<>(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            result.add(movingAverage(candles, i));
        }
        return result;
    }

    public List<Double> calculateFromClosedPrice(List<Double> candles, List<Double> prevCandles) {
        List<Double> result = new ArrayList<>(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            result.add(movingAverage(candles, prevCandles, i));
        }
        return result;
    }

    private double movingAverage(List<Double> candleClosed, List<Double> prevCandleClosed, int i) {
        if (i < duration) {
            double sum = sumRange(candleClosed, 0, i + 1);
            int prevCount = duration - i - 1;
            sum += sumRange(prevCandleClosed, prevCandleClosed.size() - prevCount, prevCandleClosed.size());
            return sum / duration;
        }
        return sumRange(candleClosed, i - duration + 1, i + 1) / duration;
    }

    private double movingAverage(List<Double> candleClose, int i) {
        int startIndex = i >= duration ? i - duration + 1 : 0;
        int count = i + 1 - startIndex;
        return sumRange(candleClose, startIndex, i + 1) / count;
    }

    private static double sumRange(List<Double> values, int from, int to) {
        double sum = 0;
        for (int i = from; i < to; i++) {
            sum += values.get(i);
        }
        return sum;
    }
}
