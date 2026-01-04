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
        if(orderRequest instanceof IndexOrderRequest indexOrder) {
            return new ActiveIndexOrder(indexOrder, ltp, timestamp, orderEntryTimestamp, quantity, lotSize);
        }

        if(orderRequest instanceof OptionBasedOrderRequest optionBasedOrderRequest) {
            return new OptionBasedActiveOrder(optionBasedOrderRequest, ltp, timestamp, orderEntryTimestamp, quantity, lotSize);
        }

        if(orderRequest instanceof TickBasedOrderRequest tickBasedOrderRequest) {
            return new TickBasedActiveOrder(tickBasedOrderRequest, ltp, timestamp, orderEntryTimestamp, quantity, lotSize);
        }
        return null;
    }
}
