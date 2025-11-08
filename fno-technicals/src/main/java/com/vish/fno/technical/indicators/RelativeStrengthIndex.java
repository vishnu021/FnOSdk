package com.vish.fno.technical.indicators;

import com.vish.fno.technical.indicators.ma.SmoothedMovingAverage;
import com.vish.fno.model.Candle;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class RelativeStrengthIndex extends AbstractIndicator {

    private final int duration;

    public RelativeStrengthIndex() {
        this.duration = 14;
    }

    public RelativeStrengthIndex(int duration) {
        this.duration = duration;
    }

    public List<Double> getClosedPrices(List<Candle> candles) {
        return candles.stream().map(Candle::getClose).collect(Collectors.toList());
    }

    @Override
    public List<Double> calculateFromClosedPrice(List<Double> closedPrice) {
        List<Double> gains = calculateAverageDifference(closedPrice, (i) -> {
            double diff = closedPrice.get(i) - closedPrice.get(i - 1);
            return diff > 0 ? diff : 0;
        });

        List<Double> losses = calculateAverageDifference(closedPrice, (i) -> {
            double diff = closedPrice.get(i - 1) - closedPrice.get(i);
            return diff > 0 ? diff : 0;
        });

        SmoothedMovingAverage sma = new SmoothedMovingAverage(duration);

        List<Double> averageGains = sma.calculateFromClosedPrice(gains);
        List<Double> averageLosses = sma.calculateFromClosedPrice(losses);

        return IntStream.range(0, averageGains.size()).boxed()
                .map(i -> {
                    double rs = averageLosses.get(i) == 0 ? 100 : averageGains.get(i)/averageLosses.get(i);
                    return 100 - 100 / (1 + rs);
                }).toList();
    }

    public List<Double> calculateFromClosedPrice(List<Double> closedPrices, List<Double> prevDaysClosedPrices) {
        int startIndex = closedPrices.size();
        prevDaysClosedPrices.addAll(closedPrices);
        return calculateFromClosedPrice(prevDaysClosedPrices).subList(startIndex-1, prevDaysClosedPrices.size()-1);
    }

    private List<Double> calculateAverageDifference(List<Double> closedPrice, Function<Integer, Double> function) {
        return IntStream.range(1, closedPrice.size()).boxed().map(function).collect(Collectors.toList());
    }
}

