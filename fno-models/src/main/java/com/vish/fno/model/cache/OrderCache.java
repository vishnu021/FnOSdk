package com.vish.fno.model.cache;

import com.vish.fno.model.Ticker;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.orderrequest.OrderRequest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe cache for order requests, active orders, and available cash.
 *
 * <p>Maintains secondary symbol indices for O(1) lookups by index symbol.
 * The hot-path methods {@link #checkEntryInOpenOrders} and {@link #getActiveOrderForSymbol}
 * are called on every tick (~520/sec); the symbol index avoids full-list scans.
 */
@Slf4j
public class OrderCache {
    private final List<OrderRequest> orderRequests;
    @Getter
    private final List<ActiveOrder> activeOrders;
    @Getter
    private final List<ActiveOrder> completedOrders;
    private final Object cashLock = new Object();
    private double availableCash;

    // Symbol indices for O(1) lookup — updated on every mutation
    private final Map<String, List<OrderRequest>> orderRequestsBySymbol;
    private final Map<String, List<ActiveOrder>> activeOrdersBySymbol;

    public OrderCache(double availableCash) {
        this.availableCash = availableCash;
        orderRequests = new CopyOnWriteArrayList<>();
        activeOrders = new CopyOnWriteArrayList<>();
        completedOrders = new CopyOnWriteArrayList<>();
        orderRequestsBySymbol = new ConcurrentHashMap<>();
        activeOrdersBySymbol = new ConcurrentHashMap<>();
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
        // O(1) index lookup instead of streaming all orderRequests
        List<OrderRequest> symbolOrders = orderRequestsBySymbol.get(tickSymbol);
        if (symbolOrders == null || symbolOrders.isEmpty()) {
            return Optional.empty();
        }

        List<ActiveOrder> symbolActiveOrders = activeOrdersBySymbol.getOrDefault(tickSymbol, List.of());

        for (OrderRequest order : symbolOrders) {
            if (isNotInActiveOrders(order, symbolActiveOrders)) {
                Optional<OrderRequest> openOrderOptional = order.verifyBuyThreshold(tick);
                if (!orderRequests.isEmpty()) {
                    return openOrderOptional;
                }
            }
        }
        return Optional.empty();
    }

    public boolean isNotInActiveOrders(OrderRequest tickOrderRequest) {
        List<ActiveOrder> symbolActiveOrders = activeOrdersBySymbol.getOrDefault(
                tickOrderRequest.getIndex(), List.of());
        return isNotInActiveOrders(tickOrderRequest, symbolActiveOrders);
    }

    private boolean isNotInActiveOrders(OrderRequest tickOrderRequest,
                                         List<ActiveOrder> symbolActiveOrders) {
        boolean isNotInActiveOrder = symbolActiveOrders
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
        removeFromIndex(activeOrdersBySymbol, order.getIndex(), order);
        completedOrders.add(order);
        log.debug("Order completed and moved to completedOrders: {}", order.getTag());
    }

    public List<ActiveOrder> getActiveOrderForSymbol(String symbol) {
        // O(1) index lookup instead of streaming all activeOrders
        List<ActiveOrder> result = activeOrdersBySymbol.get(symbol);
        return result == null ? List.of() : List.copyOf(result);
    }

    public void removeOrderRequest(OrderRequest order) {
        orderRequests.remove(order);
        removeFromIndex(orderRequestsBySymbol, order.getIndex(), order);
    }

    public void addOrderRequest(OrderRequest order) {
        orderRequests.removeIf(o -> o.equals(order));
        // Rebuild the index entry for this symbol to handle the removeIf
        removeFromIndex(orderRequestsBySymbol, order.getIndex(), order);
        orderRequests.add(order);
        addToIndex(orderRequestsBySymbol, order.getIndex(), order);
    }

    public void removeExpiredOpenOrders(int timestamp) {
        orderRequests.removeIf(o -> {
            boolean isOrderExpired = timestamp > o.getExpirationTimestamp();
            if(isOrderExpired) {
                log.info("timestamp: {} crossed, removing open order: {}", timestamp, o);
                removeFromIndex(orderRequestsBySymbol, o.getIndex(), o);
            }
            return isOrderExpired;
        });
    }

    public void appendActiveOrder(ActiveOrder activeOrder) {
        this.activeOrders.add(activeOrder);
        addToIndex(activeOrdersBySymbol, activeOrder.getIndex(), activeOrder);
    }

    private <T> void addToIndex(Map<String, List<T>> index, String key, T value) {
        index.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(value);
    }

    private <T> void removeFromIndex(Map<String, List<T>> index, String key, T value) {
        List<T> list = index.get(key);
        if (list != null) {
            list.remove(value);
        }
    }

    public double getAvailableCash() {
        synchronized (cashLock) {
            return this.availableCash;
        }
    }

    public void deductCash(double amount) {
        synchronized (cashLock) {
            this.availableCash -= amount;
            log.debug("Deducted {} from available cash, new balance: {}", amount, this.availableCash);
        }
    }

    public void addCash(double amount) {
        synchronized (cashLock) {
            this.availableCash += amount;
            log.debug("Added {} to available cash, new balance: {}", amount, this.availableCash);
        }
    }
}
