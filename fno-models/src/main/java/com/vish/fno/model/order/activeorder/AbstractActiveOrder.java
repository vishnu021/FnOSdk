package com.vish.fno.model.order.activeorder;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.vish.fno.model.util.ModelUtils.INDENTED_TAB;
import static com.vish.fno.model.util.ModelUtils.roundTo5Paise;

@Slf4j
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
    @Setter
    protected boolean isActive;
    protected double realisedProfit;

    protected AbstractActiveOrder(String tag,
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
        this.isActive = true;
        this.realisedProfit = 0;
    }

    protected void updateStopLoss(double stopLoss) {
        stopLossRevision.put(++stopLossRevisionCount, this.stopLoss);
        this.stopLoss = stopLoss;
    }

    @Override
    public Map<String, String> getExtraData() {
        return Collections.unmodifiableMap(extraData);
    }

    @Override
    public void appendExtraData(String key, String value) {
        extraData.put(key, value);
    }

    @Override
    public void closeOrder(double closePrice, int timeIndex, String timestamp) {
        setActive(false);
        setExitTimeStamp(timeIndex);
        setSellPrice(closePrice);
        this.extraData.put("exitDateTime", timestamp);
    }

    @Override
    public void incrementSoldQuantity(int soldQuantity, double sellOptionPrice) {
        this.soldQuantity += soldQuantity;
        this.realisedProfit += soldQuantity * sellOptionPrice;
        log.info("Updated realised profit to: {} for order: {}", this.realisedProfit, this);
    }

    /**
     * Initializes realised profit based on buy price and quantity.
     * Called when the actual option buy price is set after order creation.
     *
     * @param buyOptionPrice the price at which the option was bought
     */
    protected void initializeRealisedProfit(double buyOptionPrice) {
        this.realisedProfit = -1 * (this.buyQuantity * buyOptionPrice);
        log.info("Initialized realised profit with: {}, buyOptionPrice: {} for order: {}",
                this.realisedProfit, buyOptionPrice, this);
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
                .append(", soldQ=").append(soldQuantity);
        if(this.extraData.containsKey("kiteOrderId")) {
            sb.append(", kiteOrderId=").append(extraData.get("kiteOrderId"));
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
    protected void appendToStringFields(StringBuilder sb) {
        // Override in subclasses to add extra fields
    }
}
