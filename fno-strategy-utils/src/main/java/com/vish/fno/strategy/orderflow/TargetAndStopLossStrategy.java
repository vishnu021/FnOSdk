package com.vish.fno.strategy.orderflow;

import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.OrderSellDetailModel;

public interface TargetAndStopLossStrategy {
    OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp);
    OrderSellDetailModel isStopLossHit(ActiveOrder order, double ltp);
}
