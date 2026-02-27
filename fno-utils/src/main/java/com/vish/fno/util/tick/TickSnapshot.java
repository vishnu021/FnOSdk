package com.vish.fno.util.tick;

import com.vish.fno.model.Ticker;

import java.util.List;

/**
 * Immutable snapshot of tick state after cache append.
 * Created once per tick, shared across Disruptor handlers to avoid
 * repeated {@code getTicks()} calls.
 *
 * @param symbol the instrument symbol
 * @param ticker the current tick
 * @param recentTicks unmodifiable list of recent ticks for this symbol
 * @param tickCount total number of ticks in the buffer for this symbol
 */
public record TickSnapshot(
    String symbol,
    Ticker ticker,
    List<Ticker> recentTicks,
    int tickCount
) { }
