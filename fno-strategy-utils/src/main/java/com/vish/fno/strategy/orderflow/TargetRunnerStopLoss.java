package com.vish.fno.strategy.orderflow;

import com.vish.fno.model.order.OrderSellDetailModel;
import com.vish.fno.model.order.OrderSellReason;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import lombok.extern.slf4j.Slf4j;

/**
 * Target-runner stop-loss: removes the hard 2.5R profit ceiling without touching anything
 * below the target. Below target this class is behaviourally identical to
 * {@link FixedTargetAndStopLossStrategy} — it makes no {@code setStopLoss} call at all.
 * At the target it plants a floor at {@code target - floorGapR * R} instead of selling, then
 * trails {@code trailGapR * R} behind the running high-water mark until the floor or the trail
 * is taken out.
 *
 * <h2>Hypothesis</h2>
 * The measured 4-arm A/B on ThreeSoldiers (2026-01-01..07-22, 152 paired trades, run set
 * {@code 2026-07-23-3sold-sl-ab}) produced a clean dose-response: net tracks AvgWin monotonically
 * with how <b>early</b> and how <b>aggressively</b> the stop ratchets.
 * <pre>
 *   ratchets from entry      (BREAKEVEN_TRAILING_5) -> AvgWin -90%  net -Rs 6,118   strategy destroyed
 *   ratchets at 33/66%       (STEPPED)              -> AvgWin -41%  net +Rs 32,404
 *   ratchets only after      (PARTIAL_REVISING)     -> AvgWin  +1%  net +Rs 51,973  the only arm to RAISE it
 *     banking partial profit
 *   never ratchets           (FIXED)                -> AvgWin base  net +Rs 50,824
 * </pre>
 * PARTIAL_REVISING is the only arm that raised AvgWin and it is the only arm that arms
 * <b>at or after</b> the target. This class isolates that single property: arm at the target,
 * and nowhere earlier. The remaining loss it targets is the give-back on the runner leg — of the
 * 66 PARTIAL_REVISING runners, 36 exited <em>below</em> the target index level for a measured
 * -Rs 18,512, against +Rs 21,958 contributed by the 30 that held at or above it.
 *
 * <h2>Exact rule</h2>
 * Let {@code entry = order.getBuyPrice()} (the actual fill),
 * {@code origSl = order.getOrderRequest().getStopLoss()} (immutable — the request is never
 * mutated, only the order's own copy ratchets), {@code R = |entry - origSl|},
 * {@code target = order.getOrderRequest().getTarget().first()}, {@code s = +1} for CE / {@code -1} for PE,
 * and {@code floor = target - s * floorGapR * R}.
 * <ol>
 *   <li><b>Below target</b> — {@code isTargetAchieved} returns no-sell and calls nothing.
 *       {@code isStopLossHit} sells the remaining quantity as STOP_LOSS_HIT. Byte-identical to FIXED.</li>
 *   <li><b>At the target</b> — instead of selling, {@code setStopLoss(floor)}, then trail on the
 *       same tick.</li>
 *   <li><b>Above target</b> — every tick, {@code candidate = ltp - s * trailGapR * R} and
 *       {@code setStopLoss(max(floor, candidate))} for CE / {@code min(...)} for PE.
 *       Never sells at target; the position is a runner.</li>
 *   <li><b>Exit</b> — {@code isStopLossHit} fires on the ratcheted stop. Reason is TARGET_HIT when the
 *       stop sits beyond entry (a profitable ratchet exit), STOP_LOSS_HIT otherwise.</li>
 * </ol>
 *
 * <h2>Why this does NOT truncate the right tail</h2>
 * Three independent reasons, each measured rather than assumed.
 * <ul>
 *   <li><b>Nothing below the target is touched.</b> No {@code setStopLoss} call happens before
 *       {@code checkTargetAchieved} is true, so every trade that FIXED lost, this loses identically,
 *       and every trade that FIXED won is guaranteed {@code target - floorGapR * R} versus FIXED's
 *       {@code target}. Downside is bounded near zero <em>by construction</em>, not by tuning. This is
 *       the exact property BREAKEVEN_TRAILING_5 and STEPPED lack: both ratchet inside the excursion the
 *       tail needs. <b>Note on the anchor:</b> {@code R} here is {@code |fill - origSl|}, not the
 *       strategy's own {@code |buyThreshold - origSl|}, so {@code target - entry} is close to but not
 *       exactly 2.5R. On the measured 3Sold population (risk median 45.2 pts, target distance from the
 *       actual fill median 110.6 pts) the 0.50R floor lands at roughly 1.9R above the fill rather than
 *       exactly 2.0R. The difference is the entry slippage and it is immaterial next to the ~Rs 60/lot
 *       charge floor, but do not quote "2.0R" as an exact guarantee.</li>
 *   <li><b>The retracement tolerance is wider than the initial stop.</b> Measured on the same run set,
 *       22.7% of eventual winners retrace a full 0.83R back to entry <em>after</em> crossing 0.83R and
 *       still reach 2.5R, and a further 25.5% of the survivors retrace 0.83R again after crossing 1.67R.
 *       Any ratchet armed inside the move must tolerate {@literal >} 0.83R — i.e. no tighter than the
 *       original stop. This class only ever arms <em>outside</em> that region: the entire trail operates
 *       above {@code target + trailGapR * R}, a price band no FIXED trade ever visited because FIXED
 *       closes them all at 2.5R. The trail is spending excursion FIXED throws away.</li>
 *   <li><b>The cost/save arithmetic cannot bite.</b> Both ratchet arms traded tail-rupees for
 *       loser-rupees at the strategy's own payoff ratio (BE_TRAIL 1.98, STEPPED 2.08, payoff 2.67) —
 *       a guaranteed negative-EV transformation. Here the "save" side is empty: the 86 losers and the
 *       53 trades that never reach even 33% of target (-Rs 35,685 = 59.4% of all loss) are bit-identical
 *       to FIXED. There is no loser cohort to trade against, so there is no exchange rate to lose on.</li>
 * </ul>
 * Win rate is <b>provably invariant</b> at 43.4% (66W / 86L): no loser's code path is touched and no
 * winner can fall below {@code target - floorGapR * R}. A WR move of more than +/-1 trade is a bug,
 * not a result. This is deliberate — the dose-response identified AvgWin, not WR, as the causal metric
 * (STEPPED lifted WR +7.2pp at equal PF and still lost Rs 129/trade, because AvgWin fell 41% while the
 * per-trade charge is fixed).
 *
 * <h2>Lots requirement: 1 lot, fully active</h2>
 * There is no lot-count branch anywhere in this class; behaviour is identical at 1, 2, 3 or N lots.
 * It banks by moving the <b>stop</b> to a profit level rather than by selling quantity, so it never
 * needs a partial and never adds an exit leg — exactly one buy and one sell at every lot count, i.e.
 * the FIXED charge model unchanged (~Rs 61.8/lot at the Rs 150 median premium, NIFTY lot 65). This is
 * the specific fix for PARTIAL_REVISING, which degenerates silently at low lot counts:
 * {@code PartialRevisingStopLoss} line 31 returns a full-quantity TARGET_HIT at {@code totalLots < 2},
 * and at exactly 2 lots both line 31 and line 37 are false so control falls through to line 52 and again
 * sells everything — {@code reviseStopLoss} is unreachable. Most of the fleet runs 1 lot, where
 * PARTIAL_REVISING <em>is</em> FIXED and TARGET_RUNNER is fully operative.
 *
 * <h2>Per-order state lifetime: there is none</h2>
 * No map, no key, no cleanup, no {@code onOrderClosed} hook, nothing to purge at a day boundary.
 * The class holds two final doubles and no mutable field of any kind, so sharing one instance across
 * every strategy, every index and every simulated day is trivially correct with no synchronisation.
 * Two mechanisms make that possible:
 * <ol>
 *   <li><b>The high-water mark lives in {@code order.stopLoss} itself</b>, maintained by the ratchet-only
 *       setter (accepts a write only when it improves — {@code ActiveIndexOrder} lines 32-43, and the
 *       identical body on {@code TickBasedActiveOrder}, {@code OptionBasedActiveOrder},
 *       {@code MultiTargetActiveIndexOrder}, {@code MultiTargetTickActiveOrder}). Feeding it
 *       {@code ltp - s * trailGapR * R} every tick yields, after n ticks, exactly
 *       {@code max(ltp_i) - trailGapR * R} for a CE — a true high-water-mark trail whose state lives on
 *       the order and dies with the order. The write is idempotent and monotone, so a duplicate or
 *       out-of-order tick cannot corrupt it. It is deliberately NOT self-referential: {@code newSl} is
 *       derived from {@code ltp} and {@code entry} only, never from {@code order.getStopLoss()}, so it
 *       cannot exhibit {@code TrailingMultiTargetStopLossStrategy} line 74's geometric convergence onto
 *       the extreme.</li>
 *   <li><b>"Armed" is derived, not stored</b> — armed iff the stop has reached the floor. Unambiguous
 *       because {@code origSl} sits 1R on the losing side of entry while {@code floor} sits roughly 1.9R
 *       on the winning side at the registered default — a separation of nearly 3R, so the two states
 *       cannot be confused by rounding. {@link #isDegenerateGeometry} additionally refuses to arm at all
 *       unless the floor strictly improves on {@code origSl}, so the derivation can never be ambiguous
 *       even for a geometry this class was not designed for.</li>
 * </ol>
 * Consequently this class is immune to: the cross-day key collision that hits
 * {@code BreakevenTrailingStopLossStrategy} and {@code SteppedStopLossStrategy} (keys embed
 * {@code getEntryTimeStamp()}, a per-day minute ordinal 0..375 that repeats every simulated day in a
 * 140-day single-JVM run, and cleanup runs only on the target/SL paths); the within-day collision in
 * {@code TrailingMultiTargetStopLossStrategy} (key omits {@code entryTimeStamp} entirely); the
 * never-removed {@code lastRevisionCandleCount} map in {@code PartialRevisingStopLoss}; the two exits
 * that bypass the strategy entirely ({@code OrderManagerUtils} EXPIRY_TIME_REACHED and
 * MAX_HOLD_DURATION_REACHED both return before the strategy is invoked); and the backtest's per-day
 * carry-over force-close, which calls {@code orderCache.removeActiveOrder} directly and never routes
 * through {@code SellOrderExecutor}. It also does not depend on ADR-0058's {@code rebuildStrategies()},
 * which does not rebuild the stop-loss map (built once per JVM in a {@code @Bean}).
 *
 * <h2>Exit reason is load-bearing, not cosmetic</h2>
 * {@code SellOrderExecutor} branches on the literal {@code OrderSellReason.STOP_LOSS_HIT} to call
 * {@code StrategyContext.recordStopLoss}, which drives the ADR-0054 {@code lossStreakBreaker} and
 * {@code sameStrikeCooldown} rails. Measured scale of the existing defect in this run set: in the
 * BREAKEVEN_TRAILING arm 117 of 118 profitable exits are logged STOP_LOSS_HIT, and in the
 * PARTIAL_REVISING arm all 66 profitable exits are — every one of those increments the index-level loss
 * counter. Because this design only ever moves the stop <em>beyond</em> entry, the
 * {@code stop-vs-entry} test is exact in index terms and needs no new enum value.
 *
 * <h2>Hot-path cost</h2>
 * ~6 flops, 2 comparisons, no map lookup, no string building, no candle access, and no allocation
 * beyond the pre-existing {@code new OrderSellDetailModel(false)}. Strictly cheaper than
 * {@code PartialRevisingStopLoss} (candle-store read + Heikin-Ashi reconstruction). This matters because
 * the stop-loss runs on the Kite WebSocket ReadingThread in prod — {@code AbstractTickHandler} calls
 * {@code verifyAndSellOrder} before the strategy-queue hand-off — while backtest runs it synchronously
 * on the single loop thread.
 *
 * <p><b>Known, accepted cost.</b> Each <em>accepted</em> ratchet allocates inside
 * {@code AbstractActiveOrder.updateStopLoss}, which boxes the previous level into the
 * {@code stopLossRevision} map. That map is not {@code @JsonIgnore}d, so it is serialised into the
 * orderLog. Because the trail feeds a new candidate every tick, the accepted-write count is the number
 * of new high-water marks — simulated at NIFTY scale (R = 45.2, trail 0.5R, ~1 tick/s) this is ~39
 * writes for a 15-minute runner, ~84 for 60 minutes and ~121 for 120 minutes, against 0-2 for FIXED.
 * It is sub-linear in hold time (running maxima of a random walk), it does not affect any exit
 * decision, and it costs a few hundred small allocations spread over an hour. Two consequences to be
 * aware of rather than to fix: the orderLog grows by roughly 1-3 KB per runner, and
 * {@code stopLossRevisionCount} stops being the compact "how many ratchets" probe it is for STEPPED
 * (0/1/2). If either becomes a problem, gate the trail write on a minimum improvement expressed as a
 * fraction of R — but note that changes the trail granularity and therefore the A/B result, so it must
 * not be done mid-experiment.
 *
 * <h2>Multi-target caveat</h2>
 * This anchors on {@code Target.first()}, which for a {@code MultiTargetOrderRequest} is T1 (0.5R on
 * MomentumCascade). ThreeSoldiers emits plain {@code IndexOrderRequest}s so it is irrelevant there, but
 * do NOT point this type at a {@code lots >= 2} MomentumCascade or NR4Sweep leg without re-deriving the
 * geometry. No {@code MultiTargetOrder} branch exists here and {@code advanceTarget()} is never called.
 *
 * <h2>Kill criterion</h2>
 * <ul>
 *   <li><b>Hard pre-check (bug gate, not a result gate).</b> Paired 1:1 against the 01-fixed arm on
 *       {@code extraData.entryDateTime}. All 86 FIXED losers AND the single EXPIRY_TIME_REACHED trade
 *       must be identical to the rupee, and the matched count must be 152/152. Any loser delta above
 *       Rs 1 means the pre-target path is not FIXED-identical — stop and fix, do not read the P&amp;L.</li>
 *   <li><b>KILL:</b> net/lot below FIXED's Rs 50,824 at floorGapR = 0.25 AND 0.50 AND 1.00. Three points,
 *       one sweep, then abandon the post-target-extension idea. Do not keep tuning — sweeping an exit
 *       rule on n=66 winners is the overfitting pattern the walk-forward discipline exists to prevent.</li>
 *   <li><b>KILL ON MECHANISM:</b> if AvgWin does not strictly rise above Rs 1,866. The only claim this
 *       design makes is AvgWin up at flat WR; if AvgWin falls, the trail is truncating and the mechanism
 *       is wrong.</li>
 *   <li><b>KILL AS BUG:</b> if win rate moves by more than +/-1 trade (most likely cause: the sign
 *       convention applied wrongly for PE orders, which are 107 of the 152 trades, so the error would be
 *       silent rather than fatal). Or if ANY exit with {@code realisedProfit > 0} is logged
 *       {@code orderExitReason} STOP_LOSS_HIT.</li>
 *   <li><b>DOWNGRADE to not-worth-shipping:</b> positive but under +Rs 2,500. The top 10 of 152 trades
 *       carry 61.8% of net gross and removing the single best moves FIXED net by 8.6%, so anything
 *       smaller is inside the noise. Require a walk-forward H1-vs-H2 split with the same sign in both
 *       halves before promoting to any live leg.</li>
 *   <li><b>Diagnostic that directs the next move rather than killing:</b> bucket the 66 winners by exit
 *       path — (i) exited on the floor, (ii) exited on the trail above {@code target + trailGapR * R},
 *       (iii) exited at the 15:23 square-off. A large negative bucket (iii) means runners are being
 *       handed to a forced market exit and the fix is a tighter {@code trailGapR}, not a different floor.
 *       All three are derivable from the orderLog's final {@code stopLoss}, {@code stopLossRevisionCount}
 *       and {@code orderExitReason} — no extra logging is emitted per tick.</li>
 * </ul>
 *
 * @see FixedTargetAndStopLossStrategy the control arm this must reproduce exactly below the target
 * @see PartialRevisingStopLoss the only measured arm that raised AvgWin, and the source of this design
 */
@Slf4j
public class TargetRunnerStopLoss extends AbstractTargetAndStopLossStrategy {

    /**
     * Default floor gap, in R-multiples below the target, at which the stop is planted when the
     * target is first crossed.
     *
     * <p>Provenance: measured on the 04-partialrev arm of run set {@code 2026-07-23-3sold-sl-ab}.
     * Of the 66 runners, 36 exited below the target index level with mean give-back 0.616R
     * (median 0.543R) costing -Rs 18,512; the other 30 exited at or above target contributing
     * +Rs 21,958. A 0.50R floor caps the first cohort's give-back below its own measured mean.
     * Upper-bound clamp counterfactuals for the runner leg versus selling at target:
     * 0.00R +Rs 16,688 / 0.25R +Rs 11,425 / <b>0.50R +Rs 7,539</b> / 0.75R +Rs 5,495 / 1.00R +Rs 4,214,
     * against the actual Heikin-Ashi trail's +Rs 3,445. Tighter floors capture more give-back but put
     * more extension at risk: 30 runners ended above a 2.50R floor carrying +Rs 21,958, 37 above 2.25R
     * carrying +Rs 21,877, 45 above 2.00R carrying +Rs 18,249. This is therefore THE sweep parameter and
     * 0.50R is the middle of a real trade-off, not a tuned number.
     */
    private static final double DEFAULT_FLOOR_GAP_R = 0.50;

    /**
     * Default trail gap, in R-multiples behind the running high-water mark.
     *
     * <p>Provenance: deliberately set equal to {@link #DEFAULT_FLOOR_GAP_R} so the trail only becomes
     * the binding constraint above {@code target + 0.50R} — a region no FIXED trade ever visited, since
     * FIXED closes them all at 2.5R. The trail therefore operates entirely on excursion FIXED throws
     * away, which is why it cannot truncate the measured right tail.
     */
    private static final double DEFAULT_TRAIL_GAP_R = 0.50;

    /**
     * Floating-point tolerance for the "has the stop reached the floor" equality test. This is a
     * comparison tolerance, not a trading threshold: the floor is recomputed each tick from values that
     * never change ({@code target}, {@code entry}, {@code origSl}), so the comparison is exact by
     * construction and this only guards against a compiler reassociating the arithmetic. It is ~280 ulps
     * at a NIFTY index level of 24,000 and four orders of magnitude below the Rs 0.05 tick size, so it
     * cannot alias a real price difference.
     */
    private static final double ARMED_TOLERANCE = 1e-9;

    /** Gap below (CE) / above (PE) the target, in R-multiples, at which the profit floor is planted. */
    private final double floorGapR;

    /** Gap behind the running high-water mark, in R-multiples, at which the trail follows price. */
    private final double trailGapR;

    /** Default constructor — 0.50R floor, 0.50R trail (the registered TARGET_RUNNER geometry). */
    public TargetRunnerStopLoss() {
        this(DEFAULT_FLOOR_GAP_R, DEFAULT_TRAIL_GAP_R);
    }

    /**
     * Constructor with configurable geometry.
     *
     * <p>Precondition: {@code 0 < floorGapR <= 1.0}. Above 1.0 the floor can fall on the wrong side of
     * the original stop for a tight-target request, in which case the arming write would be rejected by
     * the ratchet and the position would never take profit. That case is detected and degraded to FIXED
     * by {@link #isDegenerateGeometry}, so it is safe, but it is not a useful configuration.
     *
     * @param floorGapR gap below (CE) / above (PE) the target, in R-multiples, at which the floor is planted
     * @param trailGapR gap behind the running high-water mark, in R-multiples, at which the trail follows
     */
    public TargetRunnerStopLoss(double floorGapR, double trailGapR) {
        this.floorGapR = floorGapR;
        this.trailGapR = trailGapR;
    }

    @Override
    public OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp) {
        final double entry = order.getBuyPrice();
        final double origSl = order.getOrderRequest().getStopLoss();
        final double riskR = Math.abs(entry - origSl);
        final double target = order.getOrderRequest().getTarget().first();
        final double sign = order.isCallOrder() ? 1.0 : -1.0;
        final double profitFloor = target - sign * floorGapR * riskR;

        if (isDegenerateGeometry(riskR, sign, entry, origSl, target, profitFloor)) {
            return fixedTargetFallback(order, ltp);
        }

        final boolean armed = sign * (order.getStopLoss() - profitFloor) >= -ARMED_TOLERANCE;

        if (!armed) {
            if (!checkTargetAchieved(order, ltp)) {
                // Pre-target: no setStopLoss, no state, no allocation beyond the record. FIXED-identical.
                return new OrderSellDetailModel(false);
            }
            // Arm: plant the floor instead of selling. Always accepted by the ratchet — origSl is 1R on
            // the losing side of entry while the floor is (targetDistance - floorGapR * R) on the
            // winning side, and isDegenerateGeometry has already refused any request where that is not
            // strictly true.
            order.setStopLoss(profitFloor);
            log.info("TARGET_RUNNER: target {} crossed at ltp {}, floor planted at {} (entry={}, R={}, floorGapR={}), order: {}",
                    target, ltp, profitFloor, entry, riskR, floorGapR, order);
            // fall through and trail on this same tick
        }

        // High-water-mark trail. Derived from ltp only — never from order.getStopLoss() — so it cannot
        // converge geometrically onto the extreme. The ratchet-only setter discards any non-improving
        // write, which is what turns a per-tick candidate into max(ltp_i) - trailGapR * R.
        final double candidate = ltp - sign * trailGapR * riskR;
        order.setStopLoss(sign > 0 ? Math.max(profitFloor, candidate) : Math.min(profitFloor, candidate));

        // Never sells at target — the position is now a runner.
        return new OrderSellDetailModel(false);
    }

    @Override
    public OrderSellDetailModel isStopLossHit(ActiveOrder order, double ltp) {
        if (!checkStopLossHit(order, ltp)) {
            return new OrderSellDetailModel(false);
        }

        final double entry = order.getBuyPrice();
        final double origSl = order.getOrderRequest().getStopLoss();
        final double riskR = Math.abs(entry - origSl);
        final double target = order.getOrderRequest().getTarget().first();
        final double sign = order.isCallOrder() ? 1.0 : -1.0;
        final double profitFloor = target - sign * floorGapR * riskR;

        if (isDegenerateGeometry(riskR, sign, entry, origSl, target, profitFloor)) {
            return super.isStopLossHit(order, ltp);
        }

        final int remaining = order.getBuyQuantity() - order.getSoldQuantity();
        final boolean inProfit = sign * (order.getStopLoss() - entry) > 0.0;

        if (inProfit) {
            // The stop only ever moves beyond entry after the target was crossed, so this is a
            // profitable ratchet exit. Emitting TARGET_HIT keeps StrategyContext.recordStopLoss — and
            // therefore the ADR-0054 lossStreakBreaker / sameStrikeCooldown rails — from being fed a
            // false loss event.
            log.info("TARGET_RUNNER: runner stop hit IN PROFIT at ltp {} (stop={}, entry={}, floor={}), selling {} as TARGET_HIT, order: {}",
                    ltp, order.getStopLoss(), entry, profitFloor, remaining, order);
            return new OrderSellDetailModel(true, remaining, OrderSellReason.TARGET_HIT, order);
        }

        log.info("TARGET_RUNNER: stop hit below entry at ltp {} (stop={}, entry={}), selling {} as STOP_LOSS_HIT, order: {}",
                ltp, order.getStopLoss(), entry, remaining, order);
        return new OrderSellDetailModel(true, remaining, OrderSellReason.STOP_LOSS_HIT, order);
    }

    /**
     * True when the request's geometry cannot support a runner, in which case both methods degrade to
     * {@link FixedTargetAndStopLossStrategy} behaviour rather than to undefined behaviour.
     *
     * <p>Three cases: a zero/negative risk leg (no R to scale by); a target on the wrong side of the
     * fill (already through, or malformed); and a floor that would not improve on the original stop
     * (only reachable at {@code floorGapR > 1.0} with a tight target), which would otherwise leave the
     * arming write rejected by the ratchet and the position unable to ever take profit.
     */
    private static boolean isDegenerateGeometry(double riskR, double sign, double entry, double origSl,
                                                double target, double profitFloor) {
        return riskR <= 0.0
                || sign * (target - entry) <= 0.0
                || sign * (profitFloor - origSl) <= 0.0;
    }

    /**
     * Control-arm behaviour for a degenerate request: full exit at the original target, exactly as
     * {@link FixedTargetAndStopLossStrategy} does. Uses the remaining quantity rather than
     * {@code getBuyQuantity()} — numerically identical here because this class never partial-sells, but
     * it avoids propagating the over-sell idiom at {@code FixedTargetAndStopLossStrategy} line 19.
     * Logged at WARN once per order (only on the target-crossing tick) so a silent degradation to the
     * control arm is visible in the run log.
     */
    private OrderSellDetailModel fixedTargetFallback(ActiveOrder order, double ltp) {
        if (!checkTargetAchieved(order, ltp)) {
            return new OrderSellDetailModel(false);
        }
        final int remaining = order.getBuyQuantity() - order.getSoldQuantity();
        log.warn("TARGET_RUNNER: degenerate geometry (entry={}, requestSL={}, target={}, floorGapR={}), falling back to FIXED full exit at ltp {}, order: {}",
                order.getBuyPrice(), order.getOrderRequest().getStopLoss(),
                order.getOrderRequest().getTarget().first(), floorGapR, ltp, order);
        return new OrderSellDetailModel(true, remaining, OrderSellReason.TARGET_HIT, order);
    }
}
