package com.vish.fno.model.helper;

import com.vish.fno.model.Ticker;
import com.vish.fno.model.order.orderrequest.OrderRequest;

public interface OrderFlowHandler {
    void verifyAndSellOrder(String tickSymbol, Ticker tick);
    void verifyAndBuyOrder(Ticker tick, OrderRequest order);
}
