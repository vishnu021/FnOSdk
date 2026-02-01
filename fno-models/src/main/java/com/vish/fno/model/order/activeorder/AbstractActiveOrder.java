package com.vish.fno.model.order.activeorder;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.vish.fno.model.util.ModelUtils.INDENTED_TAB;
import static com.vish.fno.model.util.ModelUtils.roundTo5Paise;

@Getter
public abstract class AbstractActiveOrder implements ActiveOrder {
    @Getter
    protected final String tag;
    protected final Date date;
    protected final int entryTimeStamp;
    @Setter
    protected int exitTimeStamp;
    protected final double buyThreshold;
    protected double buyPrice;
    protected int buyQuantity;
    protected int soldQuantity;
    @Setter
    protected double sellPrice;
    protected double target;
    protected double stopLoss;
    protected final Map<String, String> extraData;
    protected int stopLossRevisionCount;
    protected final Map<Integer, Double> stopLossRevision = new HashMap<>();

    public AbstractActiveOrder(String tag,
                               Date date,
                               int entryTimeStamp,
                               double buyThreshold,
                               double buyPrice,
                               int buyQuantity,
                               double target,
                               double stopLoss,
                               Map<String, String> extraData) {
        this.tag = tag;
        this.date = date;
        this.entryTimeStamp = entryTimeStamp;
        this.buyThreshold = buyThreshold;
        this.buyPrice = buyPrice;
        this.buyQuantity = buyQuantity;
        this.soldQuantity = 0;
        this.target = target;
        this.stopLoss = stopLoss;
        this.extraData = extraData == null ? new HashMap<>() : extraData;
        this.stopLossRevisionCount = 0;
    }

    protected void updateStopLoss(double stopLoss) {
        stopLossRevision.put(++stopLossRevisionCount, this.stopLoss);
        this.stopLoss = stopLoss;
    }

    @Override
    public void appendExtraData(String key, String value) {
        extraData.put(key, value);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(128);
        sb.append(INDENTED_TAB)
                .append(getClass().getSimpleName()).append("{")
                .append("index=").append(getIndex())
                .append(", tag=").append(tag);
        appendToStringFields(sb);
        sb.append(", buyPrice=").append(buyPrice)
                .append(", target=").append(roundTo5Paise(target))
                .append(", stopLoss=").append(roundTo5Paise(stopLoss))
                .append(", buyQ=").append(buyQuantity)
                .append(", soldQ=").append(soldQuantity)
                .append("}");
        return sb.toString();
    }

    @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
    protected void appendToStringFields(StringBuilder sb) {
        // Override in subclasses to add extra fields
    }
}
