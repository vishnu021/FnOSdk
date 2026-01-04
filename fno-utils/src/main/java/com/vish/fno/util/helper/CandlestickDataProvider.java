package com.vish.fno.util.helper;

import com.vish.fno.model.SymbolData;

import java.util.Optional;

/**
 * Interface for providing candlestick data.
 *
 * Implementations:
 * - CandlestickService (OrderManager) - Production implementation using Kite API
 * - BacktestCandlestickService (Backtest) - Backtest implementation using file-based data
 */
public interface CandlestickDataProvider {

    /**
     * Get entire day's candlestick data for a symbol (default minute interval).
     *
     * @param date   The date in yyyy-MM-dd format
     * @param symbol The symbol to get data for
     * @return Optional containing SymbolData if available
     */
    Optional<SymbolData> getEntireDayHistoryData(String date, String symbol);

    /**
     * Get entire day's candlestick data for a symbol with specified interval.
     *
     * @param date     The date in yyyy-MM-dd format
     * @param symbol   The symbol to get data for
     * @param interval The interval (e.g., "minute", "5minute", "day")
     * @return Optional containing SymbolData if available
     */
    Optional<SymbolData> getEntireDayHistoryData(String date, String symbol, String interval);
}
