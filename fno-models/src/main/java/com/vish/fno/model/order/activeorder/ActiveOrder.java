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

    /**
     * Sets the option buy price for this order.
     * For index/tick orders, delegates to the subtype's buyOptionPrice field.
     * For option-based orders, this is a no-op (buyPrice IS the option price).
     */
    void setOptionBuyPrice(double price);

    /**
     * Returns the option buy price for this order.
     * For index/tick orders, returns the separate buyOptionPrice field.
     * For option-based orders, returns buyPrice (the option price itself).
     */
    double getOptionBuyPrice();


    // --- Risk management ---
    double getTarget();
    double getStopLoss();
    void setStopLoss(double stopLoss);

    // --- Exit / sell state ---
    double getSellPrice();

    /**
     * Returns the option sell price for this order.
     * For index/tick orders, returns the separate sellOptionPrice field.
     * For option-based orders, returns sellPrice (the option price itself).
     */
    double getOptionSellPrice();

    int getExitTimeStamp();
    int getSoldQuantity();
    void incrementSoldQuantity(int soldQuantity, double sellOptionPrice);
    void closeOrder(double closePrice, int timeIndex, String timestamp);

    // --- Computed ---
    double getProfit();
    double getRealisedProfit();

    /**
     * Returns whether this is a call order (true) or put order (false).
     */
    boolean isCallOrder();

    // --- Runtime diagnostics ---
    Map<String, String> getExtraData();
    void appendExtraData(String key, String value);
}
