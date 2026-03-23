package com.vish.fno.strategy.orderflow;

import com.vish.fno.model.order.OrderSellDetailModel;
import com.vish.fno.model.order.OrderSellReason;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.activeorder.MultiTargetOrder;
import lombok.extern.slf4j.Slf4j;

// CPD-OFF - Intentional structural similarity with TripleTargetStopLossStrategy (same iteration logic)
/**
 * Dual-target stop-loss strategy for 2-target partial exits.
 *
 * <p>Flow: T1 hit → sell group 1, revise SL to T1 price. T2 hit → sell remaining.
 * SL hit at any point → sell ALL remaining quantity.
 *
 * <p>Only operates on {@link MultiTargetOrder} instances. Returns no-sell for
 * standard active orders (non-multi-target orders should not use this strategy).
 */
@Slf4j
public class DualTargetStopLossStrategy extends AbstractTargetAndStopLossStrategy {

    @Override
    public OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp) {
        if (!(order instanceof MultiTargetOrder multiOrder)) {
            return new OrderSellDetailModel(false);
        }
        if (!multiOrder.hasMoreTargets()) {
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
}
