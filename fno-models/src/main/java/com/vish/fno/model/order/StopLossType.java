package com.vish.fno.model.order;

/**
 * Enum representing the stop-loss exit strategy for an order.
 *
 * <p>Determines how target and stop-loss levels are evaluated:
 * <ul>
 *   <li>{@link #FIXED} — exits full position when target or stop-loss is hit</li>
 *   <li>{@link #PARTIAL_REVISING} — partial profit-taking at target with trailing stop-loss</li>
 * </ul>
 */
public enum StopLossType {
    FIXED,
    PARTIAL_REVISING
}
