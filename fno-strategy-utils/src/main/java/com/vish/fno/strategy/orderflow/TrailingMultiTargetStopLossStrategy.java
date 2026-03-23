package com.vish.fno.strategy.orderflow;

import com.vish.fno.model.order.OrderSellDetailModel;
import com.vish.fno.model.order.OrderSellReason;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.activeorder.MultiTargetOrder;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trailing multi-target stop-loss strategy: T1/T2 are fixed partial exits,
 * then the remaining position trails with SL revision on new price extremes.
 *
 * <p>Flow: T1 hit → sell group 1, SL→T1. T2 hit → sell group 2, SL→T2.
 * After all fixed targets: enter trailing mode — SL revises toward price on new highs (CE)
 * or new lows (PE) until the trailing SL is breached.
 *
 * <p>SL hit at any point (before or during trailing) → sell ALL remaining quantity.
 */
@Slf4j
public class TrailingMultiTargetStopLossStrategy extends AbstractTargetAndStopLossStrategy {

    private final Map<String, Double> trailingExtremes = new ConcurrentHashMap<>();

    @Override
    public OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp) {
        if (!(order instanceof MultiTargetOrder multiOrder)) {
            return new OrderSellDetailModel(false);
        }

        if (!multiOrder.hasMoreTargets()) {
            updateTrailingExtreme(order, ltp);
            reviseTrailingSL(order);
            return new OrderSellDetailModel(false);
        }

        double activeTarget = multiOrder.getCurrentTarget();
        boolean isCall = order.isCallOrder();

        if ((isCall && ltp > activeTarget) || (!isCall && ltp < activeTarget)) {
            int qty = multiOrder.getCurrentTargetQuantity();
            int targetIndex = multiOrder.getCurrentTargetIndex();
            multiOrder.advanceTarget();
            log.info("Multi-target T{} hit for order: {}, selling {} units, ltp: {}",
                    targetIndex + 1, order, qty, ltp);
            return new OrderSellDetailModel(true, qty, OrderSellReason.TARGET_HIT, order);
        }

        return new OrderSellDetailModel(false);
    }

    @Override
    public OrderSellDetailModel isStopLossHit(ActiveOrder order, double ltp) {
        if (checkStopLossHit(order, ltp)) {
            int remaining = order.getBuyQuantity() - order.getSoldQuantity();
            String key = buildKey(order);
            trailingExtremes.remove(key);
            log.info("StopLoss hit for order: {} ltp: {}, selling remaining {} quantity", order, ltp, remaining);
            return new OrderSellDetailModel(true, remaining, OrderSellReason.STOP_LOSS_HIT, order);
        }
        return new OrderSellDetailModel(false);
    }

    private void reviseTrailingSL(ActiveOrder order) {
        String key = buildKey(order);
        Double extreme = trailingExtremes.get(key);
        if (extreme == null) {
            return;
        }

        if (order.isCallOrder()) {
            double newSL = extreme - (extreme - order.getStopLoss()) * 0.5;
            if (newSL > order.getStopLoss()) {
                order.setStopLoss(newSL);
                log.debug("Trailing SL revised upward to {} for order: {}", newSL, order);
            }
        } else {
            double newSL = extreme + (order.getStopLoss() - extreme) * 0.5;
            if (newSL < order.getStopLoss()) {
                order.setStopLoss(newSL);
                log.debug("Trailing SL revised downward to {} for order: {}", newSL, order);
            }
        }
    }

    private void updateTrailingExtreme(ActiveOrder order, double ltp) {
        String key = buildKey(order);
        if (order.isCallOrder()) {
            trailingExtremes.merge(key, ltp, Math::max);
        } else {
            trailingExtremes.merge(key, ltp, Math::min);
        }
    }

    private static String buildKey(ActiveOrder order) {
        return order.getOrderRequest().getTag() + "_" + order.getOrderRequest().getIndex();
    }
}
