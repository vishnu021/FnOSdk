package com.vish.fno.model.order;

/**
 * Enum representing the stop-loss exit strategy for an order.
 *
 * <p>Determines how target and stop-loss levels are evaluated:
 * <ul>
 *   <li>{@link #FIXED} — exits full position when target or stop-loss is hit</li>
 *   <li>{@link #PARTIAL_REVISING} — partial profit-taking at target with trailing stop-loss</li>
 *   <li>{@link #DUAL_TARGET} — 2-target partial exit: T1 sells group 1, SL revises to T1, T2 sells rest</li>
 *   <li>{@link #TRIPLE_TARGET} — 3-target partial exit: T1/T2/T3 with SL revision at each level</li>
 *   <li>{@link #TRAILING_MULTITARGET} — T1/T2 fixed exits, remainder trails with SL revision on new highs</li>
 * </ul>
 */
public enum StopLossType {
    FIXED,
    PARTIAL_REVISING,
    DUAL_TARGET,
    TRIPLE_TARGET,
    TRAILING_MULTITARGET
}
