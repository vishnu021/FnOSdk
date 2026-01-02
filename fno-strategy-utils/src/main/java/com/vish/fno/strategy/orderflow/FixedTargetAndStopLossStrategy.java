package com.vish.fno.strategy.orderflow;

import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.OrderSellDetailModel;
import com.vish.fno.model.order.OrderSellReason;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FixedTargetAndStopLossStrategy implements TargetAndStopLossStrategy {

    @Override
    public OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp) {
        if(order.isTargetAchieved(ltp)) {
            return new OrderSellDetailModel(true, order.getBuyQuantity(), OrderSellReason.TARGET_HIT, order);
        }
        return new OrderSellDetailModel(false);
    }

    @Override
    public OrderSellDetailModel isStopLossHit(ActiveOrder order, double ltp) {
        if(order.isStopLossHit(ltp)) {
            log.info("Call stopLoss hit for order : {} ltp: {}", order, ltp);
            return new OrderSellDetailModel(true, order.getBuyQuantity(), OrderSellReason.STOP_LOSS_HIT, order);
        }
        if(order.isStopLossHit(ltp)) {
            log.info("Put stopLoss hit for order : {} ltp: {}", order, ltp);
            return new OrderSellDetailModel(true, order.getBuyQuantity(), OrderSellReason.STOP_LOSS_HIT, order);
        }
        return new OrderSellDetailModel(false);
    }
}
