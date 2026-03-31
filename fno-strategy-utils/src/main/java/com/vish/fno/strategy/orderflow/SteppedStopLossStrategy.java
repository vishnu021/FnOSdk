package com.vish.fno.strategy.orderflow;

import com.vish.fno.model.order.OrderSellDetailModel;
import com.vish.fno.model.order.OrderSellReason;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.activeorder.MultiTargetOrder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stepped stop-loss strategy: auto-generates intermediate SL revision checkpoints
 * between entry and target. Works with any order type (1-lot safe, no partial sells).
 *
 * <p>For a {@link MultiTargetOrder}, uses the strategy-defined intermediate targets directly.
 * For standard orders (single target), auto-generates 3 steps at 33%/66%/100% of target distance.
 *
 * <p>As price crosses each step, SL ratchets to the previous step level, locking in
 * progressively more profit. Full exit only at final target or SL hit.
 *
 * <p>Example (CE, entry=22500, target=22600, SL=22475, auto-steps=[22533, 22566, 22600]):
 * <pre>
 *   Price crosses 22533 (33%) → SL moves to 22500 (entry = breakeven)
 *   Price crosses 22566 (66%) → SL moves to 22533 (33pts locked)
 *   Price reaches  22600 (100%) → SELL at full target
 *   If reverses from 22570  → SL hit at 22533 = +33pt profit (not -25pt loss)
 * </pre>
 *
 * <p>For PE orders, the steps are reversed (below entry, SL moves down).
 */
@Slf4j
public class SteppedStopLossStrategy extends AbstractTargetAndStopLossStrategy {

    private static final int DEFAULT_STEPS = 3;
    private final Map<String, StepState> stepStates = new ConcurrentHashMap<>();

    @Override
    public OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp) {
        // For MultiTargetOrder: use strategy-defined targets directly
        if (order instanceof MultiTargetOrder multiOrder) {
            return handleMultiTargetOrder(multiOrder, ltp);
        }

        // For standard orders: auto-generate steps from entry/target
        return handleStandardOrder(order, ltp);
    }

    @Override
    public OrderSellDetailModel isStopLossHit(ActiveOrder order, double ltp) {
        if (checkStopLossHit(order, ltp)) {
            String key = buildKey(order);
            StepState state = stepStates.remove(key);
            int remaining = order.getBuyQuantity() - order.getSoldQuantity();
            int stepsReached = state != null ? state.currentStep : 0;
            log.info("STEPPED_SL: SL hit at step {}, ltp={}, SL={}, order: {}",
                    stepsReached, ltp, order.getStopLoss(), order);
            return new OrderSellDetailModel(true, remaining, OrderSellReason.STOP_LOSS_HIT, order);
        }
        return new OrderSellDetailModel(false);
    }

    private OrderSellDetailModel handleStandardOrder(ActiveOrder order, double ltp) {
        String key = buildKey(order);
        StepState state = stepStates.computeIfAbsent(key, k -> createSteps(order));
        boolean isCall = order.isCallOrder();

        // Check each uncrossed step
        while (state.currentStep < state.steps.size()) {
            double stepPrice = state.steps.get(state.currentStep);
            boolean crossed = isCall ? ltp > stepPrice : ltp < stepPrice;

            if (!crossed) {
                break;
            }

            boolean isFinalStep = state.currentStep == state.steps.size() - 1;

            if (isFinalStep) {
                // Final target — sell
                stepStates.remove(key);
                int remaining = order.getBuyQuantity() - order.getSoldQuantity();
                log.info("STEPPED_SL: Final step {} hit, SELLING for order: {}, ltp: {}",
                        state.currentStep + 1, order, ltp);
                return new OrderSellDetailModel(true, remaining, OrderSellReason.TARGET_HIT, order);
            }

            // Intermediate step — revise SL to previous step (or entry for first step)
            double newSL = state.currentStep == 0 ? state.entryPrice : state.steps.get(state.currentStep - 1);
            double prevSL = order.getStopLoss();
            order.setStopLoss(newSL);
            state.currentStep++;

            log.info("STEPPED_SL: Step {} crossed at ltp={}, SL revised {:.2f} → {:.2f} for order: {}",
                    state.currentStep, ltp, prevSL, newSL, order);
        }

        return new OrderSellDetailModel(false);
    }

    private OrderSellDetailModel handleMultiTargetOrder(MultiTargetOrder multiOrder, double ltp) {
        boolean isCall = multiOrder.isCallOrder();

        while (multiOrder.hasMoreTargets()) {
            double activeTarget = multiOrder.getCurrentTarget();
            boolean crossed = isCall ? ltp > activeTarget : ltp < activeTarget;

            if (!crossed) {
                break;
            }

            int targetIndex = multiOrder.getCurrentTargetIndex();
            boolean isFinalTarget = targetIndex == multiOrder.getOrderRequest().getTarget().size() - 1;

            if (isFinalTarget) {
                int remaining = multiOrder.getBuyQuantity() - multiOrder.getSoldQuantity();
                multiOrder.advanceTarget();
                log.info("STEPPED_SL: Final target T{} hit, SELLING for order: {}, ltp: {}",
                        targetIndex + 1, multiOrder, ltp);
                return new OrderSellDetailModel(true, remaining, OrderSellReason.TARGET_HIT, multiOrder);
            }

            double prevSL = multiOrder.getStopLoss();
            multiOrder.advanceTarget();
            log.info("STEPPED_SL: T{} crossed at ltp={}, SL revised {:.2f} → {:.2f} for order: {}",
                    targetIndex + 1, ltp, prevSL, multiOrder.getStopLoss(), multiOrder);
        }

        return new OrderSellDetailModel(false);
    }

    private StepState createSteps(ActiveOrder order) {
        double entry = order.getBuyPrice();
        double target = order.getOrderRequest().getTarget().first();
        boolean isCall = order.isCallOrder();

        double distance = isCall ? target - entry : entry - target;
        List<Double> steps = new ArrayList<>();

        for (int i = 1; i <= DEFAULT_STEPS; i++) {
            double fraction = (double) i / DEFAULT_STEPS;
            double stepPrice = isCall ? entry + distance * fraction : entry - distance * fraction;
            steps.add(stepPrice);
        }

        log.debug("STEPPED_SL: Auto-generated {} steps for order: {} | entry={} target={} steps={}",
                DEFAULT_STEPS, order, entry, target, steps);

        return new StepState(entry, steps);
    }

    private static String buildKey(ActiveOrder order) {
        return order.getOrderRequest().getTag() + "_" + order.getOrderRequest().getIndex()
                + "_" + order.getEntryTimeStamp();
    }

    private static class StepState {
        final double entryPrice;
        final List<Double> steps;
        int currentStep;

        StepState(double entryPrice, List<Double> steps) {
            this.entryPrice = entryPrice;
            this.steps = steps;
            this.currentStep = 0;
        }
    }
}
