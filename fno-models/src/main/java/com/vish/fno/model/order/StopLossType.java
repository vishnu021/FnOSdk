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
 *   <li>{@link #BREAKEVEN_TRAILING} — moves SL to entry+3pts (breakeven) once trade goes green, then trails</li>
 *   <li>{@link #BREAKEVEN_TRAILING_5} — same as BREAKEVEN_TRAILING but with 5pt buffer (higher guaranteed profit per exit)</li>
 *   <li>{@link #BREAKEVEN_TRAILING_7} — same as BREAKEVEN_TRAILING but with 7pt buffer (wider for momentum strategies)</li>
 *   <li>{@link #STEPPED} — uses intermediate targets as SL revision checkpoints (no partial sell, 1-lot safe)</li>
 *   <li>{@link #STEPPED_LATE_67} — conservative stepped: single SL revision at 67% of target + 5pt buffer. For retest-pattern strategies.</li>
 *   <li>{@link #STEPPED_7} — Stepped SL with 7pt breakeven buffer — revises SL to entry+7pts when first intermediate target (33%) is crossed. Best for momentum strategies: full target on winners, entry+7 protection on partial successes.</li>
 * </ul>
 */
public enum StopLossType {
    FIXED,
    PARTIAL_REVISING,
    DUAL_TARGET,
    TRIPLE_TARGET,
    TRAILING_MULTITARGET,
    BREAKEVEN_TRAILING,
    BREAKEVEN_TRAILING_5,
    BREAKEVEN_TRAILING_7,
    STEPPED,
    STEPPED_LATE_67,
    STEPPED_7,
    /** Stepped SL with proportional buffer: buffer = 20% of target distance (min 3pt). Scales with trade size — works for both small and large targets. */
    STEPPED_PROPORTIONAL,
    /** Exits at entry+3pts when trigger fires (no trailing). Companion to BREAKEVEN_TRAILING for testing whether locking in trigger-level profit beats trailing for choppy regimes. */
    BREAKEVEN_EXIT,
    /** Same as BREAKEVEN_EXIT with 5pt buffer (matches BREAKEVEN_TRAILING_5 pre-trigger geometry). */
    BREAKEVEN_EXIT_5
}
