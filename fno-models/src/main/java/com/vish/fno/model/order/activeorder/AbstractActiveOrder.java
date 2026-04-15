package com.vish.fno.model.order.activeorder;

import com.vish.fno.model.order.orderrequest.OrderRequest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.vish.fno.model.util.ModelUtils.INDENTED_TAB;
import static com.vish.fno.model.util.ModelUtils.roundTo5Paise;

@Slf4j
@Getter
public abstract class AbstractActiveOrder implements ActiveOrder {
    protected final OrderRequest orderRequest;
    protected final int entryTimeStamp;
    protected int exitTimeStamp;
    protected double buyPrice;
    protected int buyQuantity;
    protected int soldQuantity;
    protected double sellPrice;
    protected double stopLoss;
    protected final Map<String, String> extraData;
    protected int stopLossRevisionCount;
    protected final Map<Integer, Double> stopLossRevision = new ConcurrentHashMap<>();
    protected double realisedProfit;

    /**
     * Broker-confirmed option buy price from Kite order-update callback.
     * Zero until the BUY leg completes with status=COMPLETE on a live broker.
     * Daily-analysis reports prefer this over {@code optionBuyPrice} / {@code buyPrice}
     * when computing real P&L because it removes the signal-time-vs-fill-time slippage bias.
     */
    protected double actualOptionBuyPrice;

    /** Broker-confirmed option sell price from Kite order-update callback. Zero until fill arrives. */
    protected double actualOptionSellPrice;

    protected AbstractActiveOrder(OrderRequest orderRequest,
                               double buyPrice,
                               int entryTimeStamp,
                               int buyQuantity,
                               String entryTimestamp) {
        this.orderRequest = orderRequest;
        this.entryTimeStamp = entryTimeStamp;
        this.buyPrice = buyPrice;
        this.buyQuantity = buyQuantity;
        this.soldQuantity = 0;
        this.stopLoss = orderRequest.getStopLoss();
        this.extraData = new HashMap<>(orderRequest.getExtraData());
        this.stopLossRevisionCount = 0;
        this.realisedProfit = 0;
        this.extraData.put("entryDateTime", entryTimestamp);
    }

    protected void updateStopLoss(double stopLoss) {
        stopLossRevision.put(++stopLossRevisionCount, this.stopLoss);
        this.stopLoss = stopLoss;
    }

    @Override
    public void setActualOptionBuyPrice(double actualOptionBuyPrice) {
        this.actualOptionBuyPrice = actualOptionBuyPrice;
    }

    @Override
    public void setActualOptionSellPrice(double actualOptionSellPrice) {
        this.actualOptionSellPrice = actualOptionSellPrice;
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
        this.exitTimeStamp = timeIndex;
        this.sellPrice = closePrice;
        this.extraData.put("exitDateTime", timestamp);
        this.extraData.put("profit", String.valueOf(roundTo5Paise(getProfit())));
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
                .append("index=").append(orderRequest.getIndex())
                .append(", tag=").append(orderRequest.getTag());
        appendToStringFields(sb);
        sb.append(", buyPrice=").append(buyPrice)
                .append(", target=").append(orderRequest.getTarget())
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
