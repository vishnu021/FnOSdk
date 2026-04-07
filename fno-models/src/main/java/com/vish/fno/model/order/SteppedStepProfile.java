package com.vish.fno.model.order;

import java.util.List;

/**
 * Predefined step ratio profiles for {@link StopLossType#STEPPED} stop-loss strategy.
 * Controls where SL revision checkpoints are placed as fractions of target distance.
 *
 * <p>Each profile defines a list of fractions (0.0 to 1.0) where:
 * <ul>
 *   <li>Each fraction marks a checkpoint along the entry→target path</li>
 *   <li>When price crosses a checkpoint, SL ratchets to the previous checkpoint level</li>
 *   <li>The last fraction (1.0) is the full target — triggers sell</li>
 * </ul>
 *
 * <p>Example (FIBONACCI, CE, entry=22500, target=22600):
 * <pre>
 *   Step 1 at 38.2% → 22538.2  (SL moves to 22500 = breakeven)
 *   Step 2 at 61.8% → 22561.8  (SL moves to 22538.2)
 *   Step 3 at 100%  → 22600    (SELL at full target)
 * </pre>
 *
 * <p>Data-driven selection (100-day backtest, Nov 2025 - Mar 2026):
 * <ul>
 *   <li>FIBONACCI: Best for wide structural targets (50-150pt) — TkExtremaGold +26%</li>
 *   <li>EVEN: Best for medium targets (30-50pt) — WyckSpring, NR4Sweep baseline</li>
 *   <li>CONSERVATIVE: Marginal improvement for short targets (20-30pt)</li>
 * </ul>
 */
public enum SteppedStepProfile {
    /** Equal spacing: 33.3% / 66.7% / 100%. Default profile. */
    EVEN(List.of(1.0 / 3, 2.0 / 3, 1.0)),

    /** Fibonacci retracement levels: 38.2% / 61.8% / 100%. Best for wide structural targets. */
    FIBONACCI(List.of(0.382, 0.618, 1.0)),

    /** Conservative: 50% / 75% / 100%. Maximum breathing room before first SL revision. */
    CONSERVATIVE(List.of(0.50, 0.75, 1.0)),

    /** Aggressive: 25% / 50% / 100%. Earliest breakeven protection. */
    AGGRESSIVE(List.of(0.25, 0.50, 1.0)),

    /** Late 2/3: single revision at 67% / 100%. For retest-pattern strategies (GapAndGo, NR7BO, SMCHyb). */
    LATE_67(List.of(2.0 / 3, 1.0)),

    /** Late 3/4: single revision at 75% / 100%. Maximum retest breathing room. */
    LATE_75(List.of(0.75, 1.0));

    private final List<Double> fractions;

    SteppedStepProfile(List<Double> fractions) {
        this.fractions = fractions;
    }

    public List<Double> getFractions() {
        return fractions;
    }
}
