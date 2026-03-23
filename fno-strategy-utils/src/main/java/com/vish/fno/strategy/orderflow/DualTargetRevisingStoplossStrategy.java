package com.vish.fno.strategy.orderflow;

import com.vish.fno.model.order.OrderSellDetailModel;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DualTargetRevisingStoplossStrategy extends AbstractTargetAndStopLossStrategy {

    /**
     * Dual-target strategy where first target is the stop-loss in reverse direction.
     * Not yet implemented - throws UnsupportedOperationException.
     *
     * @param order the active order to check
     * @param ltp the last traded price
     * @return OrderSellDetailModel if target achieved
     * @throws UnsupportedOperationException always, as this strategy is not yet implemented
     */
    @Override
    public OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp) {
        throw new UnsupportedOperationException("DualTargetRevisingStoplossStrategy is not yet implemented. "
                + "Expected behavior: first target is the stop-loss in reverse direction.");
    }
}
