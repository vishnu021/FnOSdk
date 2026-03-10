package com.vish.fno.model.order.orderrequest;

import com.vish.fno.model.Task;
import com.vish.fno.model.Ticker;
import com.vish.fno.model.order.OrderMetadata;

import java.util.Date;
import java.util.Optional;

public interface OrderRequest {
    String getIndex();
    double getBuyThreshold();
    double getTarget();
    double getStopLoss();
    int getExpirationTimestamp();
    String getTag();
    Task getTask();
    Date getDate();
    OrderMetadata getOrderMetadata();
    Optional<OrderRequest> verifyBuyThreshold(Ticker tick);

    default boolean isCallOrder() {
        return true;
    }
}

