package com.vish.fno.model.order.activeorder;

import com.vish.fno.model.order.orderrequest.OrderRequest;

import java.util.Map;

public interface ActiveOrder {

    // --- Identity ---
    OrderRequest getOrderRequest();
    String getTag();
    String getIndex();
    String getTradingSymbol();

    // --- Entry state ---
    double getBuyPrice();
    int getBuyQuantity();
    int getLotSize();
    int getEntryTimeStamp();

    // --- Risk management ---
    double getTarget();
    double getStopLoss();
    void setStopLoss(double stopLoss);

    // --- Exit / sell state ---
    double getSellPrice();
    int getExitTimeStamp();
    int getSoldQuantity();
    void incrementSoldQuantity(int soldQuantity, double sellOptionPrice);
    void closeOrder(double closePrice, int timeIndex, String timestamp);

    // --- Computed ---
    double getProfit();
    double getRealisedProfit();

    /**
     * Returns whether this is a call order (true) or put order (false).
     * Default implementation returns true (call order).
     */
    default boolean isCallOrder() {
        return true;
    }

    // --- Runtime diagnostics ---
    Map<String, String> getExtraData();
    void appendExtraData(String key, String value);
}
