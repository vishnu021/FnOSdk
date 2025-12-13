package com.vish.fno.reader.util;

import com.vish.fno.model.Ticker;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.zerodhatech.models.Order;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

import static com.vish.fno.util.JsonUtils.getFormattedObject;
import static com.vish.fno.util.JsonUtils.getNonFormattedObject;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OrderDetailsLogger {
    private static final String BUY = "BUY";
    private static final String COMPLETE = "COMPLETE";
    private static final String SELL = "SELL";
    private static final String KITE_ORDER_ID = "kiteOrderId";

    public static void logMarketDepth(Ticker tick) {
        Map<String, List<Ticker.Depth>> marketDepth = tick.depth();
        if(marketDepth == null) {
            return;
        }
        log.info("Market depth({}): {}", tick, getNonFormattedObject(marketDepth));
        if(marketDepth.containsKey("buy") && !marketDepth.get("buy").isEmpty()) {
            List<Ticker.Depth> buyMarketDepth = marketDepth.get("buy");
            log.info("next buy price: {}", getFormattedObject(buyMarketDepth.get(0)));
        }
        if(marketDepth.containsKey("sell") && !marketDepth.get("sell").isEmpty()) {
            List<Ticker.Depth> sellMarketDepth = marketDepth.get("sell");
            log.info("next sell price: {}", getFormattedObject(sellMarketDepth.get(0)));
        }
    }

    public static void logOrderLifeCycle(List<ActiveOrder> activeOrders,
                                          Order order,
                                          String orderId) {
        if(BUY.contentEquals(order.transactionType)) {
            if (COMPLETE.contentEquals(order.status)) {
                List<ActiveOrder> ordersForOrderId = getActiveOrdersByOrderId(activeOrders, orderId);
                log.info("Buy order completed for : {}", ordersForOrderId);
            } else {
                List<ActiveOrder> ordersForOrderId = getActiveOrdersByOrderId(activeOrders, orderId);
                log.info("Buy order placed for : {}", ordersForOrderId);
            }
        }
        if(SELL.contentEquals(order.transactionType)) {
            if (COMPLETE.contentEquals(order.status)) {
                List<ActiveOrder> ordersForOrderId = getActiveOrdersByOrderId(activeOrders, orderId);
                log.info("Sell order completed for : {}", ordersForOrderId);
            } else {
                List<ActiveOrder> ordersForOrderId = getActiveOrdersByOrderId(activeOrders, orderId);
                log.info("Sell order placed for : {}", ordersForOrderId);
            }
        }
    }

    @NotNull
    public static List<ActiveOrder> getActiveOrdersByOrderId(List<ActiveOrder> activeOrders, String orderId) {
        return activeOrders
                .stream()
                .filter(o -> o.getExtraData() != null
                        && o.getExtraData().containsKey(KITE_ORDER_ID)
                        && o.getExtraData().get(KITE_ORDER_ID).contentEquals(orderId))
                .toList();
    }
}
