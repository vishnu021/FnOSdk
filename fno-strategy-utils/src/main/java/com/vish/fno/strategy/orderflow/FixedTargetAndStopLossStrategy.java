package com.vish.fno.strategy.orderflow;

import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.OrderSellDetailModel;
import com.vish.fno.model.order.OrderSellReason;
import lombok.extern.slf4j.Slf4j;

/**
 * Fixed target and stop-loss strategy that exits the full position
 * when either target or stop-loss is hit, without any revision.
 */
@Slf4j
public class FixedTargetAndStopLossStrategy extends AbstractTargetAndStopLossStrategy {

    @Override
    public OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp) {
        if(checkTargetAchieved(order, ltp)) {
            log.info("Target achieved for order: {} ltp: {}", order, ltp);
            return new OrderSellDetailModel(true, order.getBuyQuantity(), OrderSellReason.TARGET_HIT, order);
        }
        return new OrderSellDetailModel(false);
    }
}
