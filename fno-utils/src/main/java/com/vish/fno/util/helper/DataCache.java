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

    /**
     * Appends a tick and returns an immutable snapshot of the current state.
     * Default implementation delegates to {@code appendTick} and {@code getTicks}.
     *
     * @param symbol the instrument symbol
     * @param ticker the tick to append
     * @return immutable snapshot containing the ticker and recent tick history
     */
    default TickSnapshot appendAndSnapshot(String symbol, Ticker ticker) {
        appendTick(symbol, ticker);
        List<Ticker> ticks = getTicks(symbol);
        return new TickSnapshot(symbol, ticker, ticks, ticks.size());
    }
}
