package com.vish.fno.util.helper;

import com.vish.fno.model.Candle;

import java.util.Date;
import java.util.List;

/**
 * Interface for candlestick data retrieval — intraday minute data and historical lookback.
 *
 * <p>Fully independent from {@link TickStore}. No consumer in the codebase needs both
 * tick and candle operations, so the two interfaces are separate (not inherited).
 *
 * <p>Consumers: {@code IndexStrategyHandlerImpl}, {@code OptionStrategyHandlerImpl},
 * {@code PartialRevisingStopLoss}, and all index-based strategies that need historical lookback.
 *
 * @see TickStore tick-only interface for order executors and tick handlers
 * @see CandleStoreImpl implementation with per-symbol locking and date-boundary detection
 */
public interface CandleStore {

    /**
     * Fetch (or return cached) today's minute candles for the given symbol.
     * Triggers an API call on first access per symbol per trading day.
     *
     * @param symbol the instrument symbol
     * @return list of today's minute candles, or null if unavailable
     */
    List<Candle> updateAndGetMinuteData(String symbol);

    /**
     * Fetch (or return cached) historical minute candles for a specific date.
     *
     * @param date the date string (yyyy-MM-dd)
     * @param symbol the instrument symbol
     * @return list of minute candles for the given date
     */
    List<Candle> updateAndGetHistoryMinuteData(String date, String symbol);

    /**
     * Get the most recent N candles for a symbol, spanning multiple days if needed.
     * Uses holiday calendar to skip non-trading days.
     *
     * @param symbol the instrument symbol
     * @param date the reference date
     * @param n number of candles to retrieve
     * @return list of N candles (oldest first), may span multiple trading days
     */
    List<Candle> getNCandles(String symbol, Date date, int n);

    /**
     * Get the most recent N candles with today's candles provided.
     * Overload for callers that already have today's candle data.
     *
     * @param symbol the instrument symbol
     * @param date the reference date
     * @param n number of candles to retrieve
     * @param todaysCandles today's candles (currently unused — delegates to {@link #getNCandles(String, Date, int)})
     * @return list of N candles (oldest first)
     */
    List<Candle> getNCandles(String symbol, Date date, int n, List<Candle> todaysCandles);
}
