package com.vish.fno.util.tick;

import com.vish.fno.model.Ticker;

import java.util.List;

/**
 * Read/write interface for real-time tick data.
 *
 * <p>Separated from {@link com.vish.fno.util.candle.store.CandleStore CandleStore} following the Interface Segregation Principle.
 * No consumer in the codebase needs both tick and candle operations, so the two interfaces
 * are fully independent — neither extends the other.
 *
 * <p>Consumers like {@code SellOrderExecutor}, {@code TickStrategyExecutor},
 * {@code AbstractTickHandler}, and {@code PCRCalculationService} depend on this interface.
 *
 * <h2>Thread safety:</h2>
 * <ul>
 *   <li>{@code appendTick} — single writer (WebSocket tick-processing thread)</li>
 *   <li>{@code getLatestTick}, {@code getTicks} — multiple concurrent readers (strategy threads)</li>
 *   <li>Implementations must be safe for single-writer / multiple-reader access</li>
 * </ul>
 *
 * @see com.vish.fno.util.candle.store.CandleStore candle-only interface for strategy handlers
 * @see TickStoreImpl default implementation using TickCircularBuffer
 */
public interface TickStore {

    /**
     * Append a tick to the store for the given symbol.
     *
     * @param tickSymbol the instrument symbol
     * @param ticker the tick data
     */
    void appendTick(String tickSymbol, Ticker ticker);

    /**
     * Get the most recent tick for the given symbol.
     *
     * @param symbol the instrument symbol
     * @return the latest tick, or null if no ticks have been received for this symbol
     */
    Ticker getLatestTick(String symbol);

    /**
     * Get the recent tick history for the given symbol.
     *
     * <p>Returns an unmodifiable view of the circular buffer (up to 500 ticks).
     * The view is a snapshot at call time — subsequent ticks do not appear in it.
     *
     * @param symbol the instrument symbol
     * @return list of recent ticks (oldest first, newest last), or empty list if none
     */
    List<Ticker> getTicks(String symbol);

    /**
     * Appends a tick and returns an immutable snapshot of the current state.
     * Used by the Disruptor pipeline to create the snapshot once in CacheHandler,
     * then share it across all downstream handlers without repeated {@code getTicks()} calls.
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
