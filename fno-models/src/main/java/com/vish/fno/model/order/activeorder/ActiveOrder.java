package com.vish.fno.model.order.activeorder;

import com.vish.fno.model.order.orderrequest.OrderRequest;

import java.util.Map;

public interface ActiveOrder {

    // --- Identity ---
    OrderRequest getOrderRequest();
    String getTradingSymbol();

    // --- Entry state ---
    double getBuyPrice();
    int getBuyQuantity();
    int getLotSize();
    int getEntryTimeStamp();

    /**
     * Sets the option buy price for this order.
     * For index/tick orders, sets the optionBuyPrice field.
     * For option-based orders, this is a no-op (buyPrice IS the option price).
     */
    void setOptionBuyPrice(double price);

    /**
     * Returns the option buy price for this order.
     * For index/tick orders, returns the optionBuyPrice field.
     * For option-based orders, returns buyPrice (the option price itself).
     */
    double getOptionBuyPrice();


    // --- Broker-confirmed fill prices (set from Kite onOrderUpdate callback) ---

    /**
     * Returns the actual broker-confirmed option buy price from Kite's order-update
     * callback (averagePrice on status=COMPLETE for BUY). Zero until a fill arrives
     * or if running in a mode without a live broker (backtest, mock).
     *
     * <p>Daily-analysis reports should prefer this over {@link #getOptionBuyPrice()}
     * when computing real broker P&L — the latter is the price snapshot at signal
     * generation time, which can drift several rupees from the actual fill in the
     * milliseconds-to-seconds of order routing (the "~Rs 29/trade slippage gap"
     * documented in the Apr 15 prod analysis).
     */
    double getActualOptionBuyPrice();

    /**
     * Sets the actual broker-confirmed option buy price. Called from the Kite
     * order-update callback when the BUY leg completes.
     */
    void setActualOptionBuyPrice(double actualOptionBuyPrice);

    /**
     * Returns the actual broker-confirmed option sell price from Kite's order-update
     * callback (averagePrice on status=COMPLETE for SELL). Zero until a fill arrives.
     */
    double getActualOptionSellPrice();

    /**
     * Sets the actual broker-confirmed option sell price. Called from the Kite
     * order-update callback when the SELL leg completes.
     */
    void setActualOptionSellPrice(double actualOptionSellPrice);

    // --- Risk management ---
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

    /**
     * Records the running favourable/adverse excursion for this order from the given LTP.
     * Pure recorder — never influences any exit decision. Call on every tick of the symbol
     * that drives this order's sell loop, BEFORE the exit-condition check, so the excursion
     * is never censored by the exit itself. Full contract, sign convention, and the FIXED-arm
     * kill criterion: {@link AbstractActiveOrder} class Javadoc.
     */
    void updateExcursion(double ltp);
}
