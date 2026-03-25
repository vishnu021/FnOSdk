package com.vish.fno.model.order.activeorder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vish.fno.model.order.orderrequest.OptionBasedOrderRequest;
import lombok.Getter;

@Getter
public final class OptionBasedActiveOrder extends AbstractActiveOrder {
    private final int lotSize;

    public OptionBasedActiveOrder(OptionBasedOrderRequest openOrder, double buyPrice, int timestampIndex, String timestamp,
                                  int quantity, int lotSize) {
        super(openOrder, buyPrice, timestampIndex, quantity, timestamp);
        this.lotSize = lotSize;
        // Initialize realised profit directly from buy price (no separate option price)
        this.realisedProfit = -1 * (this.buyQuantity * this.buyPrice);
    }

    @JsonIgnore
    @Override
    public double getProfit() {
        return (getSellPrice() - getBuyPrice()) * this.getBuyQuantity();
    }

    @Override
    public void setStopLoss(double stopLoss) {
        // OptionBasedActiveOrder uses default call order behavior (only increase stop-loss)
        if (this.stopLoss < stopLoss) {
            updateStopLoss(stopLoss);
        }
    }

    @Override
    public String getTradingSymbol() {
        return orderRequest.getIndex();
    }

    @Override
    public void setOptionBuyPrice(double price) {
        // No-op: buyPrice IS the option price for option-based orders
    }

    @Override
    public double getOptionBuyPrice() {
        return getBuyPrice();
    }

    @Override
    public double getOptionSellPrice() {
        return getSellPrice();
    }

    @Override
    public boolean isCallOrder() {
        return true;
    }
}
