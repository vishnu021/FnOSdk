package com.vish.fno.util.position;

/**
 * Functional interface for dynamic lot size lookup.
 * Allows integration with InstrumentCache or other dynamic sources.
 */
@FunctionalInterface
public interface LotSizeProvider {
    /**
     * Get lot size for a symbol.
     *
     * @param symbol the symbol name
     * @return lot size, or null if not found
     */
    Integer getLotSize(String symbol);
}
