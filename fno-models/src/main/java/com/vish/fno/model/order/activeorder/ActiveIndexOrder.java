package com.vish.fno.model.order.activeorder;

import com.vish.fno.model.Task;
import com.vish.fno.model.order.orderrequest.IndexOrderRequest;
import com.vish.fno.model.util.ModelUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

import static com.vish.fno.model.util.ModelUtils.INDENTED_TAB;
import static com.vish.fno.model.util.ModelUtils.getStringDate;
import static com.vish.fno.model.util.ModelUtils.getStringDateTime;
import static com.vish.fno.model.util.ModelUtils.roundTo5Paise;

// CPD-OFF
@Slf4j
@Getter
public class ActiveIndexOrder extends AbstractActiveOrder {
    private static final int estimated_buffer_size = 125;
    private final Task task;
    private final String index;
    private final String optionSymbol;
    private final int lotSize;
    private final boolean callOrder;
    private double buyOptionPrice;
    @Setter
    private double sellOptionPrice;
    @Setter
    private boolean isActive;
    private double realisedProfit;

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
        this.isActive = true;
        this.realisedProfit = 0f;
        this.extraData.put("entryDateTime", timestamp);
    }

    public void setStopLoss(double stopLoss) {
        if(this.isCallOrder()) {
            if(this.stopLoss < stopLoss) {
                updateStopLoss(stopLoss);
            }
        } else {
            if(this.stopLoss > stopLoss) {
                updateStopLoss(stopLoss);
            }
        }
    }

    public void closeOrder(double closePrice, int timeIndex, String timestamp) {
        setActive(false);
        setExitTimeStamp(timeIndex);
        setSellPrice(closePrice);
        this.extraData.put("exitDateTime", timestamp);
    }

    @Override
    public String getTradingSymbol() {
        return this.getOptionSymbol();
    }

    public double getProfit() {
        if(isCallOrder()) {
            return (getSellPrice() - getBuyPrice()) * this.getBuyQuantity();
        } else {
            return (getBuyPrice() - getSellPrice()) * this.getBuyQuantity();
        }
    }

    public void setBuyOptionPrice(double buyOptionPrice) {
        this.buyOptionPrice = buyOptionPrice;
        this.realisedProfit = -1 * (this.buyQuantity * this.buyOptionPrice);
        log.info("Initialising realised profit with: {}, buyOptionPrice: {} for order: {}", this.realisedProfit, buyOptionPrice, this);
    }

    @Override
    public void incrementSoldQuantity(int soldQuantity, double sellOptionPrice) {
        this.soldQuantity += soldQuantity;
        this.realisedProfit += soldQuantity * sellOptionPrice;
        this.sellOptionPrice = sellOptionPrice;
        log.info("Updating realised profit to: {}, sellOptionPrice: {} for order: {}", this.realisedProfit, sellOptionPrice, this);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(estimated_buffer_size);
        sb.append(INDENTED_TAB)
                .append("ActiveIndexOrder{")
                .append("index=").append(index)
                .append(", tag=").append(tag)
                .append(", optionSymbol=").append(optionSymbol)
                .append(", buyPrice=").append(buyPrice)
                .append(", target=").append(roundTo5Paise(target))
                .append(", stopLoss=").append(roundTo5Paise(stopLoss))
                .append(", buyQ=").append(buyQuantity)
                .append(", soldQ=").append(soldQuantity)
                .append("}");

        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)  {
            return true;
        }
        if (o == null || getClass() != o.getClass())  {
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
// CPD-ON