package com.vish.fno.model.order;

import com.vish.fno.model.order.activeorder.ActiveIndexOrder;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.activeorder.OptionBasedActiveOrder;
import com.vish.fno.model.order.activeorder.TickBasedActiveOrder;
import com.vish.fno.model.order.orderrequest.IndexOrderRequest;
import com.vish.fno.model.order.orderrequest.OptionBasedOrderRequest;
import com.vish.fno.model.order.orderrequest.OrderRequest;
import com.vish.fno.model.order.orderrequest.TickBasedOrderRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ActiveOrderFactory {

    public static ActiveOrder createOrder(OrderRequest orderRequest, double ltp, int timestamp, String orderEntryTimestamp,
                                          int quantity, int lotSize) {
        return switch (orderRequest) {
            case IndexOrderRequest r ->
                    new ActiveIndexOrder(r, ltp, timestamp, orderEntryTimestamp, quantity, lotSize);
            case OptionBasedOrderRequest r ->
                    new OptionBasedActiveOrder(r, ltp, timestamp, orderEntryTimestamp, quantity, lotSize);
            case TickBasedOrderRequest r ->
                    new TickBasedActiveOrder(r, ltp, timestamp, orderEntryTimestamp, quantity, lotSize);
            default ->
                    throw new IllegalArgumentException("Unknown OrderRequest type: " + orderRequest.getClass().getSimpleName());
        };
    }
}
