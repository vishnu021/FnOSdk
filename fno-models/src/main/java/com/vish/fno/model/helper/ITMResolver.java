package com.vish.fno.model.helper;

/**
 * ITMResolver - Interface for resolving ITM/OTM option symbols.
 *
 * This interface provides a single-responsibility abstraction for option symbol resolution,
 * enabling both production and backtest to use the same strategy executor with unified
 * option strategy execution.
 *
 * Implementations:
 * - KiteITMResolver (fno-kite-reader): Production implementation using KiteService for live symbol resolution
 * - BacktestITMResolver (OptionsAnalyzer Backtest): Backtest implementation using pre-configured symbol mappings
 *
 * Usage:
 * <pre>
 * // Production - uses KiteService for live resolution
 * ITMResolver resolver = new KiteITMResolver(kiteService);
 *
 * // Backtest - uses configured symbol mappings
 * ITMResolver resolver = new BacktestITMResolver(symbolMap);
 *
 * // Both use same strategy executor
 * StrategyExecutor executor = new StrategyExecutor(..., resolver, ...);
 * </pre>
 *
 * @see KiteITMResolver for production implementation
 */
public interface ITMResolver {

    /**
     * Resolve the ITM (In-The-Money) option symbol for the given index.
     *
     * @param index the underlying index symbol (e.g., "NIFTY 50", "NIFTY BANK")
     * @param price the current price of the index
     * @param isCall true for CALL option, false for PUT option
     * @return the ITM option symbol (e.g., "NIFTY25D0926000CE")
     */
    String resolveITMSymbol(String index, double price, boolean isCall);

    /**
     * Resolve the OTM (Out-of-The-Money) option symbol for the given index.
     *
     * @param index the underlying index symbol (e.g., "NIFTY 50", "NIFTY BANK")
     * @param price the current price of the index
     * @param isCall true for CALL option, false for PUT option
     * @return the OTM option symbol (e.g., "NIFTY25D0926000CE")
     */
    String resolveOTMSymbol(String index, double price, boolean isCall);

    /**
     * Prepare option symbols before strategy execution.
     * Called once per minute before processing strategies.
     *
     * Production: May call kiteService.appendIndexITMOptions() to refresh symbol mappings
     * Backtest: Typically no-op (symbols are pre-configured)
     */
    void prepareSymbols();
}
