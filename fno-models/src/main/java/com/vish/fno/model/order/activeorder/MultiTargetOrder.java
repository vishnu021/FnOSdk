package com.vish.fno.model.order.activeorder;

/**
 * Interface for active orders that support multiple target levels with partial exits.
 * Implemented by MultiTargetActiveIndexOrder and MultiTargetTickActiveOrder.
 * Used by DualTargetStopLossStrategy, TripleTargetStopLossStrategy, and
 * TrailingMultiTargetStopLossStrategy to drive partial exit logic.
 */
public interface MultiTargetOrder extends ActiveOrder {

    /**
     * Advances to the next target level. Revises SL to the current target price.
     * Called by the strategy after a partial sell at the current target.
     */
    void advanceTarget();

    /**
     * Returns the quantity to sell at the current target level (lot-aligned).
     */
    int getCurrentTargetQuantity();

    /**
     * Returns true if there are more targets to hit.
     */
    boolean hasMoreTargets();

    /**
     * Returns the price of the current active target.
     */
    double getCurrentTarget();

    /**
     * Returns the index of the current target (0-based).
     */
    int getCurrentTargetIndex();
}
