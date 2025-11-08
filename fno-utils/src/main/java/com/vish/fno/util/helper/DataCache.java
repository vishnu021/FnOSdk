package com.vish.fno.util.helper;

import com.vish.fno.model.Candle;
import com.vish.fno.model.Ticker;

import java.util.Date;
import java.util.List;

public interface DataCache {
    List<Candle> updateAndGetMinuteData(String symbol);
    List<Candle> updateAndGetHistoryMinuteData(String date, String symbol);
    List<Candle> getNCandles(final String symbol, final Date date, final int n);
    List<Candle> getNCandles(final String symbol, final Date date, final int n, List<Candle> todaysCandles);

    void appendTick(String tickSymbol, Ticker ticker);

    Ticker getLatestTick(String symbol);

    List<Ticker> getTicks(String symbol);
}
