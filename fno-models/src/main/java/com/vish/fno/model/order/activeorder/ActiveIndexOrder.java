package com.vish.fno.model.order.activeorder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vish.fno.model.order.orderrequest.IndexOrderRequest;
import lombok.Getter;
import lombok.Setter;

import com.vish.fno.model.order.OrderMetadata;

import java.util.Objects;

// CPD-OFF - Intentional structural similarity with TickBasedActiveOrder (both handle option symbols)
@Getter
public final class ActiveIndexOrder extends AbstractActiveOrder {
    private final String optionSymbol;
    private final int lotSize;
    private double optionBuyPrice;
    @Setter
    private double optionSellPrice;

    public ActiveIndexOrder(IndexOrderRequest openOrder, double buyPrice, int timestampIndex, String timestamp,
                            int quantity, int lotSize) {
        super(openOrder, buyPrice, timestampIndex, quantity, timestamp);
        this.optionSymbol = openOrder.getOptionSymbol();
        this.lotSize = lotSize;
        copyExtras(openOrder.getOrderMetadata());
    }

    private void copyExtras(OrderMetadata orderMetadata) {
        if (orderMetadata != null && orderMetadata.getSubSignal() != null) {
            this.extraData.put("subSignal", orderMetadata.getSubSignal());
        }
    }

    @JsonIgnore
    @Override
    public boolean isCallOrder() {
        return orderRequest.isCallOrder();
    }

    @Override
    public void setStopLoss(double stopLoss) {
        if (this.isCallOrder()) {
            if (this.stopLoss < stopLoss) {
                updateStopLoss(stopLoss);
            }
        } else {
            if (this.stopLoss > stopLoss) {
                updateStopLoss(stopLoss);
            }
        }
    }

    @Override
    public String getTradingSymbol() {
        return this.getOptionSymbol();
    }

    @JsonIgnore
    @Override
    public double getProfit() {
        if (isCallOrder()) {
            return (getSellPrice() - getBuyPrice()) * this.getBuyQuantity();
        } else {
            return (getBuyPrice() - getSellPrice()) * this.getBuyQuantity();
        }
    }

    @Override
    public void setOptionBuyPrice(double price) {
        this.optionBuyPrice = price;
        initializeRealisedProfit(price);
    }

    @Override
    public void incrementSoldQuantity(int soldQuantity, double sellOptionPrice) {
        super.incrementSoldQuantity(soldQuantity, sellOptionPrice);
        this.optionSellPrice = sellOptionPrice;
    }

    @Override
    protected void appendToStringFields(StringBuilder sb) {
        sb.append(", optionSymbol=").append(optionSymbol);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ActiveIndexOrder that = (ActiveIndexOrder) o;
        return Objects.equals(orderRequest.getTag(), that.orderRequest.getTag())
                && Objects.equals(orderRequest.getIndex(), that.orderRequest.getIndex())
                && Objects.equals(isCallOrder(), that.isCallOrder());
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderRequest.getTag(), orderRequest.getIndex(), isCallOrder());
    }
}
