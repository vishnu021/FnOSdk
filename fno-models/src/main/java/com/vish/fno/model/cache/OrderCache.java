package com.vish.fno.model.cache;

import com.vish.fno.model.Ticker;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.orderrequest.OrderRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static com.vish.fno.model.util.ModelUtils.roundTo5Paise;

@Slf4j
public class OrderCache {
    private final List<OrderRequest> orderRequests;
    private final List<ActiveOrder> activeOrders;
    @Getter
    @Setter
    private double availableCash;

    public OrderCache(double availableCash) {
        this.availableCash = availableCash;
        orderRequests = new ArrayList<>();
        activeOrders = new ArrayList<>();
        log.info("Initialising order cache with available cash: {}", this.availableCash);
    }

    public void logOpenOrders() {
        if(!orderRequests.isEmpty()) {
            log.info("list of open orders({}) : {}", orderRequests.size(), orderRequests);
        }
        if(!activeOrders.isEmpty()) {
            log.info("list of active orders({}) : {}", activeOrders.size(), activeOrders);
        }
    }

    public Optional<OrderRequest> checkEntryInOpenOrders(Ticker tick, final String tickSymbol) {
        if(orderRequests.isEmpty()) {
            return Optional.empty();
        }

        List<OrderRequest> tickSymbolOrders = orderRequests
                .stream()
                .filter(e -> e.getIndex().contentEquals(tickSymbol) && isNotInActiveOrders(e))
                .toList();

        for (OrderRequest order : tickSymbolOrders) {
            Optional<OrderRequest> openOrderOptional = order.verifyBuyThreshold(tick);
            if(!orderRequests.isEmpty()) {
                return openOrderOptional;
            }
        }
        return Optional.empty();
    }

    public boolean isNotInActiveOrders(OrderRequest tickOrderRequest) {
        boolean isNotInActiveOrder = activeOrders
                .stream()
                .noneMatch(a -> a.getTag().equalsIgnoreCase(tickOrderRequest.getTag())
                        && a.getIndex().equalsIgnoreCase(tickOrderRequest.getIndex()));
        if(!isNotInActiveOrder) {
            log.info("Already an active order present for symbol: {}, open order: {}",
                    tickOrderRequest.getIndex(), tickOrderRequest);
        }
        return isNotInActiveOrder;
    }

    public void removeActiveOrder(ActiveOrder order) {
        activeOrders.remove(order);
    }

    public List<ActiveOrder> getActiveOrderForSymbol(String symbol) {
        return activeOrders.stream().filter(o -> o.getIndex().contentEquals(symbol)).toList();
    }

    public void removeOrderRequest(OrderRequest order) {
        orderRequests.remove(order);
    }

    public void addOrderRequest(OrderRequest order) {
        orderRequests.removeIf(o -> o.equals(order));
        orderRequests.add(order);
    }

    public void removeExpiredOpenOrders(int timestamp) {
        orderRequests.removeIf(o -> {
            boolean isOrderExpired = timestamp > o.getExpirationTimestamp();
            if(isOrderExpired) {
                log.info("timestamp: {} crossed, removing open order: {}", timestamp, o);
            }
            return isOrderExpired;
        });
    }

    public void appendActiveOrder(ActiveOrder activeOrder) {
        this.activeOrders.add(activeOrder);
    }

    public List<ActiveOrder> getActiveOrders() {
        return activeOrders;
    }
}
