package com.vish.fno.model.order.activeorder;

import com.vish.fno.model.order.orderrequest.TickBasedOrderRequest;
import lombok.Getter;
import lombok.Setter;

import com.vish.fno.model.order.OrderMetadata;

import java.util.Objects;

// CPD-OFF - Intentional structural similarity with ActiveIndexOrder (both handle option symbols)
@Getter
public final class TickBasedActiveOrder extends AbstractActiveOrder {
    private final String optionSymbol;
    private final int lotSize;
    private final boolean callOrder;
    private double buyOptionPrice;
    @Setter
    private double sellOptionPrice;

    public TickBasedActiveOrder(TickBasedOrderRequest openOrder, double buyPrice, int timestampIndex, String timestamp,
                                int quantity, int lotSize) {
        super(openOrder, buyPrice, timestampIndex, quantity, timestamp);
        this.optionSymbol = openOrder.getOptionSymbol();
        this.callOrder = openOrder.isCallOrder();
        this.lotSize = lotSize;
        copyExtras(openOrder.getOrderMetadata());
    }

    private void copyExtras(OrderMetadata orderMetadata) {
        if (orderMetadata != null && orderMetadata.getSubSignal() != null) {
            this.extraData.put("subSignal", orderMetadata.getSubSignal());
        }
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

    @Override
    public double getProfit() {
        if (isCallOrder()) {
            return (getSellPrice() - getBuyPrice()) * this.getBuyQuantity();
        } else {
            return (getBuyPrice() - getSellPrice()) * this.getBuyQuantity();
        }
    }

    public void setBuyOptionPrice(double buyOptionPrice) {
        this.buyOptionPrice = buyOptionPrice;
        initializeRealisedProfit(buyOptionPrice);
    }

    @Override
    public void incrementSoldQuantity(int soldQuantity, double sellOptionPrice) {
        super.incrementSoldQuantity(soldQuantity, sellOptionPrice);
        this.sellOptionPrice = sellOptionPrice;
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
        TickBasedActiveOrder that = (TickBasedActiveOrder) o;
        return Objects.equals(getTag(), that.getTag())
                && Objects.equals(getIndex(), that.getIndex())
                && Objects.equals(callOrder, that.callOrder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTag(), getIndex(), callOrder);
    }
}
