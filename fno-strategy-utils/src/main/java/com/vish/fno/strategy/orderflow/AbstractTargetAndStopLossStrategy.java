package com.vish.fno.strategy.orderflow;

import com.vish.fno.model.order.OrderSellDetailModel;
import com.vish.fno.model.order.OrderSellReason;
import com.vish.fno.model.order.activeorder.ActiveIndexOrder;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.activeorder.OptionBasedActiveOrder;
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

    @Override
    public OrderSellDetailModel isStopLossHit(ActiveOrder order, double ltp) {
        if(checkStopLossHit(order, ltp)) {
            int quantitiesRemaining = order.getBuyQuantity() - order.getSoldQuantity();
            log.info("StopLoss hit for order : {} ltp: {}, selling remaining {} quantity", order, ltp, quantitiesRemaining);
            return new OrderSellDetailModel(true, quantitiesRemaining, OrderSellReason.STOP_LOSS_HIT, order);
        }
        return new OrderSellDetailModel(false);
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
     * Determines if the order is a call order using pattern matching (Java 17+).
     * Handles sealed ActiveOrder hierarchy:
     * - ActiveIndexOrder: has isCallOrder() method
     * - TickBasedActiveOrder: has isCallOrder() method
     * - OptionBasedActiveOrder: always treated as call
     *
     * @param order Active order to check
     * @return true if call order, false if put order
     */
    private boolean isCallOrder(ActiveOrder order) {
        if (order instanceof ActiveIndexOrder indexOrder) {
            return indexOrder.isCallOrder();
        }
        if (order instanceof TickBasedActiveOrder tickOrder) {
            return tickOrder.isCallOrder();
        }
        // OptionBasedActiveOrder: always treated as call
        return true;
    }
}
