package com.vish.fno.model;

public interface Task {
    String getIndex();
    boolean isEnabled();
    boolean isExpiryDayOrders();

    /**
     * Get the number of lots to trade for this strategy.
     * This multiplier is applied to the lot size to calculate total quantity.
     *
     * @return number of lots (default: 1)
     */
    default int getLots() {
        return 1;
    }
}
