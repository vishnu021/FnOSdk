package com.vish.fno.model.order.orderrequest;

import com.vish.fno.model.Task;
import com.vish.fno.model.Ticker;
import com.vish.fno.model.helper.EntryVerifier;

import java.util.Optional;

public interface OrderRequest {
    String getIndex();
    double getBuyThreshold();
    double getTarget();
    int getQuantity();
    int getExpirationTimestamp();
    String getTag();
    Task getTask();
    Optional<OrderRequest> verifyBuyThreshold(Ticker tick);
    EntryVerifier getEntryVerifier();
    boolean hasMoveAlreadyHappened(double ltp);
    boolean isPlaceOrder(double ltp, double availableCash, boolean isExpiryDayForOption);
}
