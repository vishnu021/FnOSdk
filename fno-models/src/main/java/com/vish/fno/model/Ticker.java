package com.vish.fno.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.vish.fno.model.util.ModelUtils.getStringTime;
import static com.vish.fno.model.util.ModelUtils.roundTo5Paise;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Ticker(
    String mode,
    boolean tradable,
    long instrumentToken,
    String instrumentSymbol,
    double lastTradedPrice,
    double highPrice,
    double lowPrice,
    double openPrice,
    double closePrice,
    double change,
    double lastTradedQuantity,
    double averageTradePrice,
    long volumeTradedToday,
    double totalBuyQuantity,
    double totalSellQuantity,
    Date lastTradedTime,
    double oi,
    double openInterestDayHigh,
    double openInterestDayLow,
    Date tickTimestamp,
    Date tickReceivedTime,
    Map<String, List<Depth>> depth
) implements Comparable<Ticker> {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Depth(
        int quantity,
        double price,
        int orders
    ) {
    }

    /**
     * Returns a copy of this Ticker with the depth map set to null.
     * Use this to avoid retaining depth data in long-lived caches (e.g., TickCircularBuffer)
     * where depth is never read — saves ~600 bytes/tick and ~15 object allocations.
     *
     * <p>Depth is preserved in tick files (via onTickReceived before this is called)
     * for future limit order logic and backtest replay.
     */
    public Ticker withoutDepth() {
        if (depth == null) {
            return this;
        }
        return new Ticker(mode, tradable, instrumentToken, instrumentSymbol,
                lastTradedPrice, highPrice, lowPrice, openPrice, closePrice, change,
                lastTradedQuantity, averageTradePrice, volumeTradedToday,
                totalBuyQuantity, totalSellQuantity, lastTradedTime,
                oi, openInterestDayHigh, openInterestDayLow,
                tickTimestamp, tickReceivedTime, null);
    }

    @Override
    public int compareTo(Ticker other) {
        return this.tickTimestamp.compareTo(other.tickTimestamp);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(100);
        sb.append("Ticker{")
          .append("mode='").append(mode).append('\'')
          .append(", token=").append(instrumentToken)
          .append(", symbol=").append(instrumentSymbol)
          .append(", ltp=").append(roundTo5Paise(lastTradedPrice))
          .append(", time=").append(getStringTime(tickTimestamp))
          .append(", received=").append(getStringTime(tickReceivedTime))
          .append('}');
        return sb.toString();
    }
}
