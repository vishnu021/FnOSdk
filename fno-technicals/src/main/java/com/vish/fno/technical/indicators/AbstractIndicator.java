package com.vish.fno.technical.indicators;

import com.vish.fno.model.Candle;

import java.util.List;

public abstract class AbstractIndicator implements Indicator {

    public List<Double> getClosedPrices(List<Candle> candles) {
        return candles.stream().map(Candle::close).toList();
    }

    public List<Double> calculate(List<Candle> candles) {
        return calculateFromClosedPrice(getClosedPrices(candles));
    }

    public List<Double> calculate(List<Candle> candles, List<Candle> prevCandles) {
        return calculateFromClosedPrice(getClosedPrices(candles), getClosedPrices(prevCandles));
    }
}
