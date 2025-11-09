package com.vish.fno.model.order;

import com.vish.fno.model.order.activeorder.ActiveOrder;

public record OrderSellDetailModel(
    boolean sellOrder,
    int quantity,
    OrderSellReason reason,
    ActiveOrder order
) {
    public OrderSellDetailModel(boolean sellOrder) {
        this(sellOrder, 0, null, null);
        if (sellOrder) {
            throw new IllegalStateException("Please provide quantity and order as well");
        }
    }
}
