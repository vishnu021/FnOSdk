package com.vish.fno.model;

import com.vish.fno.model.order.SteppedStepProfile;
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

    /**
     * Get the stepped stop-loss revision ratios for this strategy.
     * Each value is a fraction of target distance (e.g., 0.382, 0.618, 1.0 for Fibonacci).
     * Empty list means use default ratios (33%/66%/100%).
     * The last value should be 1.0 (full target). If omitted, 1.0 is auto-appended.
     *
     * @return list of step fractions (default: empty = use 33%/66%/100%)
     */
    default List<Double> getSteppedStepRatios() {
        return List.of();
    }

    /**
     * Get the stepped stop-loss step profile for this strategy.
     * Controls where SL revision checkpoints are placed as fractions of target distance.
     * When set to a non-EVEN value, overrides {@link #getSteppedStepRatios()}.
     *
     * @return step profile (default: EVEN = 33%/66%/100%)
     * @see SteppedStepProfile
     */
    default SteppedStepProfile getSteppedStepProfile() {
        return SteppedStepProfile.EVEN;
    }
}
