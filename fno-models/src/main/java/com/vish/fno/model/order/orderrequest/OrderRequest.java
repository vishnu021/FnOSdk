package com.vish.fno.model.order.orderrequest;

import com.vish.fno.model.Task;
import com.vish.fno.model.Ticker;
import com.vish.fno.model.order.Target;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

public interface OrderRequest {
    String getIndex();
    double getBuyThreshold();
    Target getTarget();
    double getStopLoss();
    int getExpirationTimestamp();
    String getTag();
    Task getTask();
    Date getDate();
    Optional<OrderRequest> verifyBuyThreshold(Ticker tick);

    default int getMaxHoldDuration() {
        return 0;
    }

    default Map<String, String> getExtraData() {
        return Map.of();
    }

    default boolean isCallOrder() {
        return true;
    }
}

