package com.vish.fno.strategy.orderflow;

import com.vish.fno.model.order.OrderSellDetailModel;
import com.vish.fno.model.order.OrderSellReason;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Breakeven trailing stop-loss strategy: once a trade goes green (LTP crosses entry
 * in favorable direction), SL is moved to entry price (breakeven). After that, the SL
 * trails toward new price extremes using 50% convergence.
 *
 * <p>Two phases:
 * <ol>
 *   <li><b>Pre-breakeven</b>: Standard FIXED behavior — check target and SL as normal</li>
 *   <li><b>Post-breakeven</b>: SL = entry price (minimum). On each new favorable extreme,
 *       SL moves to midpoint between entry and extreme, locking in progressively more profit</li>
 * </ol>
 *
 * <p>Works with 1 lot (no minimum lot requirement unlike PARTIAL_REVISING).
 * Implements the Gann principle: "never let a winning trade turn into a loser."
 *
 * <p>Best suited for wide-SL strategies (SLPanicFE, TkExtremaGold) where trades that
 * go green often reverse to full SL losses.
 */
@Slf4j
public class BreakevenTrailingStopLossStrategy extends AbstractTargetAndStopLossStrategy {

    /**
     * Default buffer: 3.0 index points beyond entry when setting breakeven SL.
     * At ITM_1 delta 0.65 with NIFTY 65 lots: captures ~Rs 127 per BREAKEVEN exit.
     */
    private static final double DEFAULT_BREAKEVEN_BUFFER = 3.0;

    /**
     * Buffer added beyond entry price when setting breakeven SL.
     * CE: SL = entry + breakevenBuffer, PE: SL = entry - breakevenBuffer.
     * Higher buffer = more guaranteed profit per exit, but fewer trades reach the threshold.
     */
    private final double breakevenBuffer;

    private final Map<String, Boolean> breakevenAchieved = new ConcurrentHashMap<>();
    private final Map<String, Double> trailingExtremes = new ConcurrentHashMap<>();

    /** Default constructor — uses 3.0pt buffer (backward compatible). */
    public BreakevenTrailingStopLossStrategy() {
        this(DEFAULT_BREAKEVEN_BUFFER);
    }

    /**
     * Constructor with configurable buffer.
     * @param breakevenBuffer index points beyond entry for the breakeven SL level
     */
    public BreakevenTrailingStopLossStrategy(double breakevenBuffer) {
        this.breakevenBuffer = breakevenBuffer;
    }

    @Override
    public OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp) {
        String key = buildKey(order);
        double entryPrice = order.getBuyPrice();
        boolean isCall = order.isCallOrder();

        // Check if trade has gone green (LTP crossed entry + buffer in favorable direction)
        double breakevenLevel = isCall ? entryPrice + breakevenBuffer : entryPrice - breakevenBuffer;
        boolean isGreen = isCall ? ltp > breakevenLevel : ltp < breakevenLevel;

        if (isGreen && !breakevenAchieved.containsKey(key)) {
            // First time going green past buffer — move SL to entry + buffer (guaranteed option profit)
            double oldSL = order.getStopLoss();
            order.setStopLoss(breakevenLevel);
            breakevenAchieved.put(key, Boolean.TRUE);
            log.info("BREAKEVEN_SL: Trade went green, SL moved to {} (entry={}, buffer={}, was {}), order: {}",
                    breakevenLevel, entryPrice, breakevenBuffer, oldSL, order);
        }

        if (breakevenAchieved.containsKey(key)) {
            // Post-breakeven: track extremes and trail SL from buffer level
            updateTrailingExtreme(order, ltp, key);
            reviseTrailingSL(order, key, breakevenLevel);
        }

        // Check if actual target is achieved (standard check)
        if (checkTargetAchieved(order, ltp)) {
            cleanup(key);
            int remaining = order.getBuyQuantity() - order.getSoldQuantity();
            log.info("TARGET_HIT with breakeven trailing for order: {}, ltp: {}", order, ltp);
            return new OrderSellDetailModel(true, remaining, OrderSellReason.TARGET_HIT, order);
        }

        return new OrderSellDetailModel(false);
    }

    @Override
    public OrderSellDetailModel isStopLossHit(ActiveOrder order, double ltp) {
        if (checkStopLossHit(order, ltp)) {
            String key = buildKey(order);
            boolean wasBreakeven = breakevenAchieved.containsKey(key);
            cleanup(key);
            int remaining = order.getBuyQuantity() - order.getSoldQuantity();

            if (wasBreakeven) {
                log.info("BREAKEVEN_SL_HIT: Trade stopped at breakeven/trailing SL for order: {}, ltp: {}",
                        order, ltp);
            } else {
                log.info("StopLoss hit (pre-breakeven) for order: {}, ltp: {}, selling {} quantity",
                        order, ltp, remaining);
            }
            return new OrderSellDetailModel(true, remaining, OrderSellReason.STOP_LOSS_HIT, order);
        }
        return new OrderSellDetailModel(false);
    }

    private void reviseTrailingSL(ActiveOrder order, String key, double entryPrice) {
        Double extreme = trailingExtremes.get(key);
        if (extreme == null) {
            return;
        }

        if (order.isCallOrder()) {
            // CE: trail SL upward — midpoint between entry and highest price seen
            double newSL = entryPrice + (extreme - entryPrice) * 0.5;
            if (newSL > order.getStopLoss()) {
                order.setStopLoss(newSL);
                log.debug("Breakeven trailing SL revised upward to {} for order: {}", newSL, order);
            }
        } else {
            // PE: trail SL downward — midpoint between entry and lowest price seen
            double newSL = entryPrice - (entryPrice - extreme) * 0.5;
            if (newSL < order.getStopLoss()) {
                order.setStopLoss(newSL);
                log.debug("Breakeven trailing SL revised downward to {} for order: {}", newSL, order);
            }
        }
    }

    private void updateTrailingExtreme(ActiveOrder order, double ltp, String key) {
        if (order.isCallOrder()) {
            trailingExtremes.merge(key, ltp, Math::max);
        } else {
            trailingExtremes.merge(key, ltp, Math::min);
        }
    }

    private void cleanup(String key) {
        breakevenAchieved.remove(key);
        trailingExtremes.remove(key);
    }

    private static String buildKey(ActiveOrder order) {
        return order.getOrderRequest().getTag() + "_" + order.getOrderRequest().getIndex()
                + "_" + order.getEntryTimeStamp();
    }
}
