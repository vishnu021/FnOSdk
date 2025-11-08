package com.vish.fno.technical.indicators.ma;

import com.vish.fno.model.Candle;
import com.vish.fno.technical.indicators.AbstractIndicator;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
public abstract class MovingAverage extends AbstractIndicator {
    protected int duration = 14;

    protected abstract double getMultiplier();

    protected abstract double maValue(double lastMa, double closePrice, double multiplier);

    public List<Double> calculateFromClosedPrice(List<Double> closePrices) {
        double multiplier = getMultiplier();
        double lastMA = getLastSMA(closePrices);

        List<Double> maList = new ArrayList<>();
        for(int i = 0; i< closePrices.size(); i++) {
            if (skipPresetValues(lastMA, maList, i)) {
                continue;
            }

            lastMA = maValue(maList.get(i - 1), closePrices.get(i), multiplier);
            maList.add(lastMA);
        }
        return maList;
    }


    public List<Double> calculate(List<Candle> candles, List<Candle> prevCandles) {
        List<Double> closedPrices = getClosedPrices(candles);
        List<Double> prevClosedPrices = getClosedPrices(prevCandles);
        return calculateFromClosedPrice(closedPrices, prevClosedPrices);
    }

    public List<Double> calculateFromClosedPrice(List<Double> closedPrices, List<Double> prevDayCandles) {
        double multiplier = getMultiplier();
        double lastMA = getLastMA(prevDayCandles);

        List<Double> maList = new ArrayList<>();
        for (double closedPrice : closedPrices) {
            lastMA = maValue(lastMA, closedPrice, multiplier);
            maList.add(lastMA);
        }
        return maList;
    }

    private double getLastMA(List<Double> prevDayCandles) {
        List<Double> lastMAList = this.calculateFromClosedPrice(prevDayCandles);
        return lastMAList.get(lastMAList.size()-1);
    }

    // when previous value is not available, EMA is taken as SMA
    private double getLastSMA(List<Double> candles) {
        SimpleMovingAverage sma = new SimpleMovingAverage(duration);
        List<Double> sma14 = sma.calculateFromClosedPrice(candles);
        int smaListSize = Math.min(duration, sma14.size());
        return sma14.get(smaListSize - 1);
    }

    private boolean skipPresetValues(double lastMA, List<Double> ma, int i) {
        if(i < duration - 1) {
            ma.add(-1d);
            return true;
        }

        if(i == duration - 1) {
            ma.add(lastMA);
            return true;
        }

        return false;
    }
}
