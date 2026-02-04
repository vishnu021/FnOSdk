package com.vish.fno.model.order.activeorder;

import com.vish.fno.model.Task;
import com.vish.fno.model.order.orderrequest.OptionBasedOrderRequest;
import lombok.Getter;

@Getter
public final class OptionBasedActiveOrder extends AbstractActiveOrder {
    private final Task task;
    private final String index;
    private final int lotSize;

    public OptionBasedActiveOrder(OptionBasedOrderRequest openOrder, double buyPrice, int timestampIndex, String timestamp,
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
        this.task = openOrder.getTask();
        this.lotSize = lotSize;
        // Initialize realised profit directly from buy price (no separate option price)
        this.realisedProfit = -1 * (this.buyQuantity * this.buyPrice);
        this.extraData.put("entryDateTime", timestamp);
    }

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
        return this.getIndex();
    }
}
