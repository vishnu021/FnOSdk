package com.vish.fno.model.cache;

import com.vish.fno.model.Ticker;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.orderrequest.OrderRequest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

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
    // ReentrantLock instead of synchronized to avoid pinning virtual threads to carrier threads.
    // synchronized pins because intrinsic monitors are tied to the OS thread's stack frame;
    // ReentrantLock uses LockSupport.park() which the JVM recognizes as a virtual thread yield point.
    private final ReentrantLock cashLock = new ReentrantLock();
    private double availableCash;

    // Symbol indices for O(1) lookup — updated on every mutation
    private final Map<String, List<OrderRequest>> orderRequestsBySymbol;
    private final Map<String, List<ActiveOrder>> activeOrdersBySymbol;
    // Composite key set for O(1) "is this (tag, index) already active?" checks
    private final Set<String> activeOrderKeys;

    public OrderCache(double availableCash) {
        this.availableCash = availableCash;
        orderRequests = new CopyOnWriteArrayList<>();
        activeOrders = new CopyOnWriteArrayList<>();
        completedOrders = new CopyOnWriteArrayList<>();
        orderRequestsBySymbol = new ConcurrentHashMap<>();
        activeOrdersBySymbol = new ConcurrentHashMap<>();
        activeOrderKeys = ConcurrentHashMap.newKeySet();
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

        for (OrderRequest order : symbolOrders) {
            if (isNotInActiveOrders(order)) {
                Optional<OrderRequest> openOrderOptional = order.verifyBuyThreshold(tick);
                if (!orderRequests.isEmpty()) {
                    return openOrderOptional;
                }
            }
        }
        return Optional.empty();
    }

    public boolean isNotInActiveOrders(OrderRequest tickOrderRequest) {
        String key = activeOrderKey(tickOrderRequest.getTag(), tickOrderRequest.getIndex());
        boolean isNotInActiveOrder = !activeOrderKeys.contains(key);
        if(!isNotInActiveOrder) {
            log.info("Already an active order present for symbol: {}, open order: {}",
                    tickOrderRequest.getIndex(), tickOrderRequest);
        }
        return isNotInActiveOrder;
    }

    public void removeActiveOrder(ActiveOrder order) {
        activeOrders.remove(order);
        removeFromIndex(activeOrdersBySymbol, order.getIndex(), order);
        activeOrderKeys.remove(activeOrderKey(order.getTag(), order.getIndex()));
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
        activeOrderKeys.add(activeOrderKey(activeOrder.getTag(), activeOrder.getIndex()));
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

    private static String activeOrderKey(String tag, String index) {
        return tag.toUpperCase(Locale.ENGLISH) + ":" + index.toUpperCase(Locale.ENGLISH);
    }

    public double getAvailableCash() {
        cashLock.lock();
        try {
            return this.availableCash;
        } finally {
            cashLock.unlock();
        }
    }

    public void deductCash(double amount) {
        cashLock.lock();
        try {
            this.availableCash -= amount;
            log.debug("Deducted {} from available cash, new balance: {}", amount, this.availableCash);
        } finally {
            cashLock.unlock();
        }
    }

    public void addCash(double amount) {
        cashLock.lock();
        try {
            this.availableCash += amount;
            log.debug("Added {} to available cash, new balance: {}", amount, this.availableCash);
        } finally {
            cashLock.unlock();
        }
    }
}
