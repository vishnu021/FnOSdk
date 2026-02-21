package com.vish.fno.reader.util;

import com.vish.fno.model.Ticker;

import java.util.List;
import java.util.Map;

/**
 * Provides market depth data for a symbol. Decouples depth access
 * from the Ticker record, allowing depth-free tick pipelines while
 * preserving order-time depth logging.
 *
 * <p>Production implementations typically cache the latest depth per symbol
 * from the raw tick data, while backtest implementations may read depth
 * from tick files or return null for non-depth symbols.
 *
 * @see OrderDetailsLogger#logMarketDepth(String, MarketDepthProvider)
 */
public interface MarketDepthProvider {

    /**
     * Get the latest market depth for a symbol.
     *
     * @param symbol the instrument symbol (e.g., "NIFTY26FEB25500PE")
     * @return depth map with "buy" and "sell" keys, or null if unavailable
     */
    Map<String, List<Ticker.Depth>> getDepth(String symbol);
}
