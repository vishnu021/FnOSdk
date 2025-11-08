package com.vish.fno.model.strategy;

import com.vish.fno.model.Candle;
import com.vish.fno.model.order.orderrequest.OrderRequest;

import java.util.List;
import java.util.Optional;

public interface MinuteStrategy extends Strategy {
    Optional<OrderRequest> test(List<Candle> candles, int timestamp);
}
