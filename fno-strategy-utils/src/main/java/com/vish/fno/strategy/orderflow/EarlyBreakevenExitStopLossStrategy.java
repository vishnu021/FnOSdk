package com.vish.fno.strategy.orderflow;

import com.vish.fno.model.order.OrderSellDetailModel;
import com.vish.fno.model.order.OrderSellReason;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import lombok.extern.slf4j.Slf4j;

/**
 * Early-breakeven-exit stop-loss strategy: converts the breakeven trigger itself
 * into the exit point. Mirrors {@link BreakevenTrailingStopLossStrategy}'s
 * pre-trigger phase exactly, but instead of activating a trailing SL after the
 * trigger fires, this strategy <b>closes the position immediately</b> at
 * {@code entry + buffer}.
 *
 * <p>Two phases:
 * <ol>
 *   <li><b>Pre-trigger</b>: Standard FIXED behavior — original target and SL apply.</li>
 *   <li><b>Trigger fires</b>: LTP reaches {@code entry + buffer} → exit immediately
 *       at the trigger level (TARGET_HIT). No trailing, no further SL movement.</li>
 * </ol>
 *
 * <p>Hypothesis: For high-WR strategies whose BREAKEVEN_TRAILING wins are mostly
 * tiny trail-SL exits at midpoint(entry, extreme), locking in the buffer-level
 * profit removes the give-back tail without sacrificing much upside, simplifies
 * exits, and improves R:R consistency.
 *
 * <p>Trade-off: Sacrifices the rare large TARGET_HIT exits that BREAKEVEN_TRAILING
 * occasionally captures on strong trend days. Best for choppy/consolidating regimes.
 */
@Slf4j
public class EarlyBreakevenExitStopLossStrategy extends AbstractTargetAndStopLossStrategy {

    /** Default buffer: 3.0 index points beyond entry — same default as BREAKEVEN_TRAILING. */
    private static final double DEFAULT_BUFFER = 3.0;

    /**
     * Buffer above (CE) or below (PE) entry that triggers the early exit.
     * Higher buffer = bigger guaranteed profit per exit, but fewer trades reach the trigger.
     */
    private final double buffer;

    /** Default constructor — uses 3.0pt buffer. */
    public EarlyBreakevenExitStopLossStrategy() {
        this(DEFAULT_BUFFER);
    }

    /**
     * Constructor with configurable buffer.
     * @param buffer index points beyond entry to trigger the exit
     */
    public EarlyBreakevenExitStopLossStrategy(double buffer) {
        this.buffer = buffer;
    }

    @Override
    public OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp) {
        final double entryPrice = order.getBuyPrice();
        final boolean isCall = order.isCallOrder();
        final double triggerLevel = isCall ? entryPrice + buffer : entryPrice - buffer;
        final boolean reachedTrigger = isCall ? ltp >= triggerLevel : ltp <= triggerLevel;

        if (reachedTrigger) {
            int remaining = order.getBuyQuantity() - order.getSoldQuantity();
            log.info("BREAKEVEN_EXIT: Trade reached trigger {} (entry={}, buffer={}), exiting at TARGET_HIT, ltp: {}, order: {}",
                    triggerLevel, entryPrice, buffer, ltp, order);
            return new OrderSellDetailModel(true, remaining, OrderSellReason.TARGET_HIT, order);
        }

        // Standard target check — fires only if LTP gaps past trigger straight to original target
        if (checkTargetAchieved(order, ltp)) {
            int remaining = order.getBuyQuantity() - order.getSoldQuantity();
            log.info("TARGET_HIT (gap-through trigger) for order: {}, ltp: {}", order, ltp);
            return new OrderSellDetailModel(true, remaining, OrderSellReason.TARGET_HIT, order);
        }

        return new OrderSellDetailModel(false);
    }
}
