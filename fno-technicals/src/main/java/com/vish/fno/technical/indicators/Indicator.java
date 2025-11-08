package com.vish.fno.technical.indicators;

import com.vish.fno.model.Candle;

import java.util.List;

public interface Indicator {
    List<Double> calculate(List<Candle> candles);
    List<Double> calculate(List<Candle> candles, List<Candle> prevCandles);
    List<Double> calculateFromClosedPrice(List<Double> candles);
    List<Double> calculateFromClosedPrice(List<Double> candles, List<Double> prevCandles);
}
