package com.vish.fno.technical.indicators;

import com.vish.fno.model.Candle;
import com.vish.fno.technical.indicators.ma.SimpleMovingAverage;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class BollingerBands extends AbstractIndicator {

    private final int duration;
    private final double MULTIPLIER;

    public BollingerBands() {
        this.duration = 20;
        this.MULTIPLIER = 2.0;
    }

    public BollingerBands(int duration, double MULTIPLIER) {
        this.duration = duration;
        this.MULTIPLIER = MULTIPLIER;
    }

    @Override
    public List<Double> calculate(List<Candle> candles) {
        List<Double> typicalPrices = candles.stream().map(Candle::getClose).toList();
        return calculateFromClosedPrice(typicalPrices);
    }

    @Override
    public List<Double> calculateFromClosedPrice(List<Double> typicalPrices) {
        SimpleMovingAverage sma = new SimpleMovingAverage(duration);
        List<Double> smaValues = sma.calculateFromClosedPrice(typicalPrices);

        List<BBValue> bollingerBandsList = IntStream.range(0, typicalPrices.size()).boxed()
                .map(p -> {
                    if(p - duration <= 0){
                        return new BBValue(-1, 0, 0);
                    }
                    List<Double> lastPrices = typicalPrices.subList(p - duration, p);
                    double average = smaValues.get(p);
                    double stdDev = Math.sqrt(lastPrices.stream().mapToDouble(a -> a).map(a -> Math.pow(a - average, 2)).sum() / lastPrices.size());
                    return new BBValue(average + MULTIPLIER * stdDev, average, average - MULTIPLIER * stdDev);
                }).toList();
        return bollingerBandsList.stream().map(b -> b.upper - b.lower).toList();
    }

    @Override
    public List<Double> calculateFromClosedPrice(List<Double> candles, List<Double> prevCandles) {
        List<Double> typicalPrices = new ArrayList<>(prevCandles);
        typicalPrices.addAll(candles);
        return calculateFromClosedPrice(typicalPrices);
    }

    @ToString
    static class BBValue {
        double upper;
        double middle;
        double lower;

        public BBValue(double upper, double middle, double lower) {
            this.upper = upper;
            this.middle = middle;
            this.lower = lower;
        }
    }
}
