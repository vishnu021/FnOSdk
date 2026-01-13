package com.vish.fno.model.order.activeorder;

import com.vish.fno.model.Task;
import com.vish.fno.model.order.orderrequest.OptionBasedOrderRequest;
import com.vish.fno.model.util.ModelUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import static com.vish.fno.model.util.ModelUtils.INDENTED_TAB;
import static com.vish.fno.model.util.ModelUtils.getStringDate;
import static com.vish.fno.model.util.ModelUtils.getStringDateTime;
import static com.vish.fno.model.util.ModelUtils.round;
import static com.vish.fno.model.util.ModelUtils.roundTo5Paise;

// CPD-OFF
@Slf4j
@Getter
@SuppressWarnings("PMD.TooManyStaticImports")
public final class OptionBasedActiveOrder extends AbstractActiveOrder {
    private static final int estimated_buffer_size = 125;
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(estimated_buffer_size);
        sb.append(INDENTED_TAB)
                .append("OptionBasedActiveOrder{")
                .append("index=").append(index)
                .append(", tag=").append(tag)
                .append(", buyPrice=").append(buyPrice)
                .append(", target=").append(roundTo5Paise(target))
                .append(", stopLoss=").append(roundTo5Paise(stopLoss))
                .append(", buyQ=").append(buyQuantity)
                .append(", soldQ=").append(soldQuantity)
                .append("}");

        return sb.toString();
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
// CPD-ON