package com.vish.fno.model.helper;

import com.vish.fno.model.Ticker;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.orderrequest.OrderRequest;

import java.util.List;
import java.util.Optional;

public interface EntryVerifier {
    String ORDER_EXECUTED = "orderExecuted";
    boolean isPlaceOrder(OrderRequest orderRequest, double ltp, boolean isExpiryDayForOption, double availableCash);
    boolean hasMoveAlreadyHappened(double ltp, OrderRequest order);
    Optional<OrderRequest> verifyBuyThreshold(Ticker tick, OrderRequest orderRequest);
}
