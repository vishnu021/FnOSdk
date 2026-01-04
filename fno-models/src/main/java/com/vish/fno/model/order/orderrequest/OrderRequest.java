package com.vish.fno.model.order.orderrequest;

import com.vish.fno.model.Task;
import com.vish.fno.model.Ticker;

import java.util.Optional;

public interface OrderRequest {
    String getIndex();
    double getBuyThreshold();
    double getTarget();
    int getExpirationTimestamp();
    String getTag();
    Task getTask();
    Optional<OrderRequest> verifyBuyThreshold(Ticker tick);
}

