package com.vish.fno.model.order.activeorder;

import com.vish.fno.model.Task;
import com.vish.fno.model.order.orderrequest.OptionBasedOrderRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public final class OptionBasedActiveOrder extends AbstractActiveOrder {
    private final Task task;
    private final String index;
    private final int lotSize;
    @Setter
    private boolean isActive;
    private double realisedProfit;

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
        this.isActive = true;
        this.realisedProfit = -1 * (this.buyQuantity * this.buyPrice);
        this.extraData.put("entryDateTime", timestamp);
    }

    @Override
    public void incrementSoldQuantity(int soldQuantity, double sellOptionPrice) {
        this.soldQuantity += soldQuantity;
        this.realisedProfit += soldQuantity * sellOptionPrice;
        log.info("updating realised profit to: {} for order: {}", this.realisedProfit, this);
    }

    public double getProfit() {
        return (getSellPrice() - getBuyPrice()) * this.getBuyQuantity();
    }

    public void setStopLoss(double stopLoss) {
        if(this.stopLoss < stopLoss) {
            updateStopLoss(stopLoss);
        }
    }
    @Override
    public void closeOrder(double closePrice, int timeIndex, String timestamp) {
        setActive(false);
        setExitTimeStamp(timeIndex);
        setSellPrice(closePrice);
        this.extraData.put("exitDateTime", timestamp);
    }

    @Override
    public String getTradingSymbol() {
        return this.getIndex();
    }
}
