package com.vish.fno.strategy.orderflow;

import com.vish.fno.model.order.OrderSellDetailModel;
import com.vish.fno.model.order.OrderSellReason;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.activeorder.MultiTargetOrder;
import lombok.extern.slf4j.Slf4j;

// CPD-OFF - Intentional structural similarity with DualTargetStopLossStrategy (same iteration logic)
/**
 * Triple-target stop-loss strategy for 3-target partial exits.
 *
 * <p>Flow: T1 hit → sell group 1, SL→T1. T2 hit → sell group 2, SL→T2. T3 hit → sell group 3.
 * SL hit at any point → sell ALL remaining quantity.
 *
 * <p>The core logic is identical to {@link DualTargetStopLossStrategy} — the
 * {@link MultiTargetOrder} interface handles target iteration internally.
 * This class exists as a separate StopLossType dispatch target for clarity.
 */
@Slf4j
public class TripleTargetStopLossStrategy extends AbstractTargetAndStopLossStrategy {

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
