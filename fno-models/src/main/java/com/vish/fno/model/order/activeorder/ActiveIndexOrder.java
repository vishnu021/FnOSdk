package com.vish.fno.model.order.activeorder;

import com.vish.fno.model.Task;
import com.vish.fno.model.order.orderrequest.IndexOrderRequest;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

// CPD-OFF - Intentional structural similarity with TickBasedActiveOrder (both handle option symbols)
@Getter
public final class ActiveIndexOrder extends AbstractActiveOrder {
    private final Task task;
    private final String index;
    private final String optionSymbol;
    private final int lotSize;
    private final boolean callOrder;
    private double buyOptionPrice;
    @Setter
    private double sellOptionPrice;

    public ActiveIndexOrder(IndexOrderRequest openOrder, double buyPrice, int timestampIndex, String timestamp,
                            int quantity, int lotSize) {
        super(openOrder.getTag(),
                openOrder.getDate(),
                timestampIndex,
                openOrder.getBuyThreshold(),
                buyPrice,
                quantity,
                openOrder.getTarget(),
                openOrder.getStopLoss(),
                openOrder.getExtraData());
        this.index = openOrder.getIndex();
        this.optionSymbol = openOrder.getOptionSymbol();
        this.callOrder = openOrder.isCallOrder();
        this.task = openOrder.getTask();
        this.lotSize = lotSize;
        this.extraData.put("entryDateTime", timestamp);
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
        ActiveIndexOrder that = (ActiveIndexOrder) o;
        return Objects.equals(tag, that.tag)
                && Objects.equals(index, that.index)
                && Objects.equals(callOrder, that.callOrder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tag, index, callOrder);
    }
}
