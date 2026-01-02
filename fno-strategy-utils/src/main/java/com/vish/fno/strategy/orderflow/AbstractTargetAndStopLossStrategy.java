package com.vish.fno.strategy.orderflow;

import com.vish.fno.model.order.activeorder.ActiveIndexOrder;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.activeorder.TickBasedActiveOrder;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract base class for target and stop-loss strategies.
 * Provides common logic for checking target and stop-loss conditions
 * based on order type (call/put).
 */
@Slf4j
public abstract class AbstractTargetAndStopLossStrategy implements TargetAndStopLossStrategy {

    /**
     * Checks if target is achieved based on order type.
     *
     * @param order Active order with target information
     * @param ltp Last traded price
     * @return true if target is achieved, false otherwise
     */
    protected boolean checkTargetAchieved(ActiveOrder order, double ltp) {
        final double target = order.getTarget();
        final boolean isCallOrder = isCallOrder(order);

        if (isCallOrder) {
            // For call orders: target achieved when LTP > target
            return ltp > target;
        } else {
            // For put orders: target achieved when LTP < target
            return ltp < target;
        }
    }

    /**
     * Checks if stop-loss is hit based on order type.
     *
     * @param order Active order with stop-loss information
     * @param ltp Last traded price
     * @return true if stop-loss is hit, false otherwise
     */
    protected boolean checkStopLossHit(ActiveOrder order, double ltp) {
        final double stopLoss = order.getStopLoss();
        final boolean isCallOrder = isCallOrder(order);

        if (isCallOrder) {
            // For call orders: stop-loss hit when LTP < stop-loss
            return ltp < stopLoss;
        } else {
            // For put orders: stop-loss hit when LTP > stop-loss
            return ltp > stopLoss;
        }
    }

    /**
     * Determines if the order is a call order.
     * Handles different order types:
     * - ActiveIndexOrder: has isCallOrder() method
     * - TickBasedActiveOrder: has isCallOrder() method
     * - OptionBasedActiveOrder: always treated as call
     * - Default: treated as call
     *
     * @param order Active order to check
     * @return true if call order, false if put order
     */
    private boolean isCallOrder(ActiveOrder order) {
        if (order instanceof ActiveIndexOrder) {
            return ((ActiveIndexOrder) order).isCallOrder();
        } else if (order instanceof TickBasedActiveOrder) {
            return ((TickBasedActiveOrder) order).isCallOrder();
        } else {
            // OptionBasedActiveOrder and others default to call
            return true;
        }
    }
}
