package com.vish.fno.model;

import com.vish.fno.model.order.StopLossType;
import com.vish.fno.model.order.StrikePolicy;

import java.util.List;

public interface Task {
    String getIndex();
    boolean isEnabled();
    boolean isExpiryDayOrders();

    /**
     * Get the number of lots to trade for this strategy.
     * This multiplier is applied to the lot size to calculate total quantity.
     *
     * @return number of lots (default: 1)
     */
    default int getLots() {
        return 1;
    }

    /**
     * Get the stop-loss strategy type for this task.
     *
     * @return stop-loss type (default: FIXED)
     */
    default StopLossType getStopLossStrategy() {
        return StopLossType.FIXED;
    }

    /**
     * Get the strike selection policy for option entry.
     *
     * @return strike policy (default: ATM)
     */
    default StrikePolicy getStrikePolicy() {
        return StrikePolicy.ATM;
    }

    /**
     * Get the allowed trading sessions for this strategy.
     * Empty list means all sessions are allowed.
     * Values: OPENING, MORNING, LUNCH, AFTERNOON, CLOSING
     *
     * @return list of allowed session names (default: empty = all allowed)
     */
    default List<String> getAllowedSessions() {
        return List.of();
    }
}
