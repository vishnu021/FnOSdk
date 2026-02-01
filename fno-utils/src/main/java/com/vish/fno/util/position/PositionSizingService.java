package com.vish.fno.util.position;

import com.vish.fno.model.Task;
import com.vish.fno.model.order.orderrequest.OrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Map;

/**
 * Centralized service for calculating position sizes (quantity and lot size) for orders.
 * This separates the concern of "what to trade" (strategy) from "how much to trade" (position sizing).
 */
@Slf4j
@RequiredArgsConstructor
public class PositionSizingService {

    private final LotSizeProvider lotSizeProvider;
    private final Map<String, Integer> lotSizeMap;
    private final int defaultLotSize;
    private final Map<String, Integer> quantityMultiplierMap;
    private final int defaultMultiplier;

    /**
     * Calculates position size for an order request.
     * The OrderRequest from strategy contains only signal information.
     * This method calculates the position sizing based on symbol and strategy.
     *
     * @param orderRequest the order request from strategy
     * @return PositionSize containing calculated quantity and lotSize
     */
    public PositionSize calculatePositionSize(OrderRequest orderRequest) {
        String symbol = orderRequest.getIndex();
        String strategyTag = orderRequest.getTag();
        Task task = orderRequest.getTask();

        int lotSize = getLotSize(symbol);
        int multiplier = getMultiplier(task, strategyTag);
        int quantity = lotSize * multiplier;

        log.debug("Position sizing for {}/{}: lotSize={}, multiplier={}, quantity={}",
                symbol, strategyTag, lotSize, multiplier, quantity);

        return new PositionSize(quantity, lotSize);
    }

    /**
     * Calculates position size with cash constraint.
     * If the configured multiplier requires more cash than available,
     * this calculates the maximum affordable quantity.
     *
     * @param orderRequest the order request from strategy
     * @param availableCash available cash for trading
     * @param optionPrice the price per unit (option price or index price)
     * @return PositionSize containing calculated quantity and lotSize
     */
    public PositionSize calculatePositionSizeWithCashConstraint(OrderRequest orderRequest,
                                                                 double availableCash,
                                                                 double optionPrice) {
        String symbol = orderRequest.getIndex();
        String strategyTag = orderRequest.getTag();
        Task task = orderRequest.getTask();

        int lotSize = getLotSize(symbol);
        int configuredMultiplier = getMultiplier(task, strategyTag);
        int configuredQuantity = lotSize * configuredMultiplier;

        double requiredCash = configuredQuantity * optionPrice;

        // If we have enough cash for configured quantity, use it
        if (availableCash >= requiredCash) {
            log.debug("Position sizing for {}/{}: lotSize={}, multiplier={}, quantity={} (sufficient cash)",
                    symbol, strategyTag, lotSize, configuredMultiplier, configuredQuantity);
            return new PositionSize(configuredQuantity, lotSize);
        }

        // Calculate maximum affordable lots
        int maxAffordableLots = (int) (availableCash / (optionPrice * lotSize));
        int affordableQuantity = maxAffordableLots * lotSize;

        if (maxAffordableLots < 1) {
            log.warn("Insufficient cash for even 1 lot. Symbol: {}, tag: {}, required: {}, available: {}",
                    symbol, strategyTag, lotSize * optionPrice, availableCash);
            return new PositionSize(0, lotSize);
        }

        log.info("Reduced quantity due to cash constraint. Symbol: {}, tag: {}, " +
                "configured quantity: {} (multiplier={}), affordable quantity: {} (lots={}), " +
                "option price: {}, available cash: {}",
                symbol, strategyTag, configuredQuantity, configuredMultiplier,
                affordableQuantity, maxAffordableLots, optionPrice, availableCash);

        return new PositionSize(affordableQuantity, lotSize);
    }

    /**
     * Get the lot size for a given symbol.
     * Lookup order: 1) Dynamic provider (InstrumentCache), 2) Static config map, 3) Default
     */
    private int getLotSize(String symbol) {
        // Normalize symbol by removing spaces (YAML keys with spaces are normalized)
        String normalizedSymbol = symbol.replace(" ", "");

        // First, try dynamic provider (InstrumentCache via KiteService)
        if (lotSizeProvider != null) {
            Integer providerLotSize = lotSizeProvider.getLotSize(normalizedSymbol);
            if (providerLotSize != null && providerLotSize > 0) {
                log.debug("Lot size for '{}' from InstrumentCache: {}", symbol, providerLotSize);
                return providerLotSize;
            }
        }

        // Second, try static configuration map (fallback)
        if (lotSizeMap != null && lotSizeMap.containsKey(normalizedSymbol)) {
            int configLotSize = lotSizeMap.get(normalizedSymbol);
            log.debug("Lot size for '{}' from configuration fallback: {}", symbol, configLotSize);
            return configLotSize;
        }

        // Finally, use default
        log.debug("Lot size for '{}' using default: {}", symbol, defaultLotSize);
        return defaultLotSize;
    }

    /**
     * Get the multiplier for a given task and strategy tag.
     * Priority: 1) Task.getLots() if > 1, 2) Map lookup by tag, 3) Default multiplier
     *
     * @param task the task containing lots configuration (can be null)
     * @param strategyTag the strategy tag for fallback map lookup
     * @return the multiplier to use
     */
    private int getMultiplier(Task task, String strategyTag) {
        // First priority: Use Task.getLots() if explicitly configured (> 1)
        if (task != null && task.getLots() > 1) {
            log.debug("Using lots from task configuration: {} for strategy '{}'", task.getLots(), strategyTag);
            return task.getLots();
        }

        // Second priority: Map lookup by strategy tag (legacy/fallback)
        if (strategyTag != null) {
            // Try exact match first
            Integer multiplier = quantityMultiplierMap.get(strategyTag);
            if (multiplier != null) {
                return multiplier;
            }

            // Try uppercase match as fallback
            multiplier = quantityMultiplierMap.get(strategyTag.toUpperCase(Locale.ENGLISH));
            if (multiplier != null) {
                return multiplier;
            }
        }

        // Third priority: Default multiplier
        log.debug("No multiplier configured for strategy '{}', using default: {}", strategyTag, defaultMultiplier);
        return defaultMultiplier;
    }
}
