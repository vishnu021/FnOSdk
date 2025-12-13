package com.vish.fno.reader.util;

import com.vish.fno.model.Ticker;
import com.zerodhatech.models.Tick;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TickMapper - Converts Kite Tick objects to internal Ticker format.
 *
 * Shared utility for backtesting to avoid OrderManager dependency.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TickMapper {

    public static Ticker mapTick(Tick tick, String tickSymbol) {
        return mapTicker(tick, tickSymbol);
    }

    private static Ticker mapTicker(Tick tick, String tickSymbol) {
        Map<String, List<Ticker.Depth>> marketDepth = Optional.ofNullable(tick.getMarketDepth())
                .orElse(Collections.emptyMap())
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Optional.ofNullable(entry.getValue())
                                .orElse(new ArrayList<>())
                                .stream()
                                .map(depth -> new Ticker.Depth(
                                        Optional.ofNullable(depth.getQuantity()).orElse(0),
                                        Optional.ofNullable(depth.getPrice()).orElse(0.0),
                                        Optional.ofNullable(depth.getOrders()).orElse(0)
                                ))
                                .collect(Collectors.toList())
                ));

        return new Ticker(
                Optional.ofNullable(tick.getMode()).orElse(""),
                false,
                Optional.ofNullable(tick.getInstrumentToken()).orElse(0L),
                tickSymbol,
                Optional.ofNullable(tick.getLastTradedPrice()).orElse(0.0),
                Optional.ofNullable(tick.getHighPrice()).orElse(0.0),
                Optional.ofNullable(tick.getLowPrice()).orElse(0.0),
                Optional.ofNullable(tick.getOpenPrice()).orElse(0.0),
                Optional.ofNullable(tick.getClosePrice()).orElse(0.0),
                Optional.ofNullable(tick.getChange()).orElse(0.0),
                Optional.ofNullable(tick.getLastTradedQuantity()).orElse(0.0),
                Optional.ofNullable(tick.getAverageTradePrice()).orElse(0.0),
                Optional.ofNullable(tick.getVolumeTradedToday()).orElse(0L),
                Optional.ofNullable(tick.getTotalBuyQuantity()).orElse(0.0),
                Optional.ofNullable(tick.getTotalSellQuantity()).orElse(0.0),
                Optional.ofNullable(tick.getLastTradedTime()).orElse(new Date(0)),
                Optional.ofNullable(tick.getOi()).orElse(0.0),
                Optional.ofNullable(tick.getOpenInterestDayHigh()).orElse(0.0),
                Optional.ofNullable(tick.getOpenInterestDayLow()).orElse(0.0),
                Optional.ofNullable(tick.getTickTimestamp()).orElse(new Date(0)),
                marketDepth
        );
    }
}
