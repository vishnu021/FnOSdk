package com.vish.fno.model.order;

import com.vish.fno.model.order.activeorder.ActiveOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderSellDetailModel {
    private final boolean sellOrder;
    private final int quantity;
    private final OrderSellReason reason;
    private final ActiveOrder order;

    public OrderSellDetailModel(boolean sellOrder) {
        if(sellOrder) {
            throw new IllegalStateException("Please provide quantity and order as well");
        }
        this.sellOrder = sellOrder;
        this.quantity = 0;
        this.reason = null;
        this.order = null;
    }
}
