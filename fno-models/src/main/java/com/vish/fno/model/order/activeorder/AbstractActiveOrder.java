package com.vish.fno.model.order.activeorder;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Getter
public abstract class AbstractActiveOrder implements ActiveOrder {
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
    public String getTag() {
        return tag.replaceAll("[a-z]", "");
    }
}
