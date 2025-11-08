package com.vish.fno.model.strategy;

import com.vish.fno.model.Ticker;
import com.vish.fno.model.order.orderrequest.OrderRequest;

import java.util.List;
import java.util.Optional;

public interface TickBasedStrategy extends Strategy {
    Optional<OrderRequest> test(List<Ticker> tickers, int timestamp);
}
