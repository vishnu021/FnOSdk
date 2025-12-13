package com.vish.fno.model.order.activeorder;

import com.vish.fno.model.Task;
import com.vish.fno.model.order.orderrequest.OptionBasedOrderRequest;
import com.vish.fno.model.util.ModelUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import static com.vish.fno.model.util.ModelUtils.*;
import static com.vish.fno.model.util.ModelUtils.getStringDate;

// CPD-OFF
@Slf4j
@Getter
public class OptionBasedActiveOrder extends AbstractActiveOrder {
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

    @Override
    public String csvHeader() {
        final int estimatedBufferSize = 250;
        final StringBuilder sb = new StringBuilder(estimatedBufferSize);

        sb.append("symbol").append(",")
                .append("entryTimeStamp").append(",")
                .append("exitTimeStamp").append(",")
                .append("buyThreshold").append(",")
                .append("buyPrice").append(",")
                .append("target").append(",")
                .append("sellPrice").append(",")
                .append("stopLoss").append(",")
                .append("profit").append(",")
                .append("quantity").append(",")
                .append("reward").append(",")
                .append("call");

        if (extraData != null) {
            for (String key : extraData.keySet()) {
                sb.append(",").append(key);
            }
        }
        return sb.toString();
    }

    @Override
    public String toCSV() {
        final StringBuilder sb = new StringBuilder(estimated_buffer_size);
        sb.append(index)
                .append(',').append(' ').append(getStringDate(date))
                .append(' ').append(entryTimeStamp)
                .append(',').append(' ').append(getStringDate(date))
                .append(' ').append(exitTimeStamp)
                .append(',').append(' ').append(roundTo5Paise(buyThreshold))
                .append(',').append(' ').append(roundTo5Paise(buyPrice))
                .append(',').append(' ').append(roundTo5Paise(target))
                .append(',').append(' ').append(roundTo5Paise(sellPrice))
                .append(',').append(' ').append(roundTo5Paise(stopLoss))
                .append(',').append(' ').append(roundTo5Paise(getProfit()))
                .append(',').append(' ').append(roundTo5Paise(buyQuantity))
                .append(',').append(' ').append(roundTo5Paise(target - buyThreshold));
        if (extraData != null) {
            for (String key : extraData.keySet()) {
                sb.append(',').append(' ').append(extraData.get(key));
            }
        }
        return sb.toString();
    }

    @Override
    public String orderLog() {
        final StringBuilder sb = new StringBuilder(estimated_buffer_size);
        sb.append("OrderLog{")
                .append("index='").append(index).append("'")
                .append(",\ttag=").append(tag)
                .append(",\tentry=").append(getStringDateTime(date))
                .append(",\texit=").append(exitTimeStamp)
                .append(",\tbuy=").append(roundTo5Paise(buyPrice))
                .append(",\ttarget=").append(roundTo5Paise(target))
                .append(",\tsell=").append(roundTo5Paise(sellPrice));

        if (getProfit() > 0) {
            sb.append(",\tprofit=");
        } else {
            sb.append(",\tloss=");
        }

        sb.append(round(getProfit())).append("}");

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

    @Override
    public boolean isTargetAchieved(double ltp) {
        return getTarget() < ltp;
    }

    @Override
    public boolean isStopLossHit(double ltp) {
        if(this.getStopLoss() > ltp) {
            log.info("StopLoss hit for order : {} ltp: {}", this, ltp);
            return true;
        }
        return false;
    }
}
// CPD-ON