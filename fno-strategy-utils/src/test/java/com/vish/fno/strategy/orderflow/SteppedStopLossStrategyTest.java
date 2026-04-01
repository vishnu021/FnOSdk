package com.vish.fno.strategy.orderflow;

import com.vish.fno.model.Task;
import com.vish.fno.model.order.OrderSellDetailModel;
import com.vish.fno.model.order.OrderSellReason;
import com.vish.fno.model.order.activeorder.ActiveIndexOrder;
import com.vish.fno.model.order.orderrequest.IndexOrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SteppedStopLossStrategyTest {

    private static final double DELTA = 0.01;
    private SteppedStopLossStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new SteppedStopLossStrategy();
    }

    // ---- Helper methods ----

    private Task mockTaskWithRatios(List<Double> ratios) {
        Task task = mock(Task.class);
        when(task.getSteppedStepRatios()).thenReturn(ratios);
        return task;
    }

    private Task mockTaskWithDefaultRatios() {
        Task task = mock(Task.class);
        when(task.getSteppedStepRatios()).thenReturn(List.of());
        return task;
    }

    private ActiveIndexOrder createCallOrder(Task task, double buyPrice, double target, double stopLoss) {
        IndexOrderRequest request = IndexOrderRequest.builder("TestStrategy", "NIFTY 50", task)
                .buyThreshold(buyPrice)
                .target(target)
                .stopLoss(stopLoss)
                .callOrder(true)
                .expirationTimestamp(375)
                .build();

        return new ActiveIndexOrder(request, buyPrice, 100, "2026-03-31 10:00:00", 25, 25);
    }

    private ActiveIndexOrder createPutOrder(Task task, double buyPrice, double target, double stopLoss) {
        IndexOrderRequest request = IndexOrderRequest.builder("TestStrategy", "NIFTY 50", task)
                .buyThreshold(buyPrice)
                .target(target)
                .stopLoss(stopLoss)
                .callOrder(false)
                .expirationTimestamp(375)
                .build();

        return new ActiveIndexOrder(request, buyPrice, 100, "2026-03-31 10:00:00", 25, 25);
    }

    // ---- Test 1: Default behavior (empty ratios -> 33%/66%/100%) ----

    @Test
    void defaultRatios_stepsCrossedProgressively_revisesStopLossAndSellsAtTarget() {
        // entry=22500, target=22600 -> distance=100
        // steps at 33%=22533.33, 66%=22566.67, 100%=22600
        Task task = mockTaskWithDefaultRatios();
        ActiveIndexOrder order = createCallOrder(task, 22500, 22600, 22475);

        // LTP below first step - no action
        OrderSellDetailModel result = strategy.isTargetAchieved(order, 22520);
        assertFalse(result.sellOrder());

        // Cross first step (33%) at 22534 -> SL revised to entry (22500)
        result = strategy.isTargetAchieved(order, 22534);
        assertFalse(result.sellOrder());
        assertEquals(22500, order.getStopLoss(), DELTA);

        // Cross second step (66%) at 22567 -> SL revised to first step (~22533.33)
        result = strategy.isTargetAchieved(order, 22567);
        assertFalse(result.sellOrder());
        assertEquals(22533.33, order.getStopLoss(), DELTA);

        // Cross final step (100%) at 22601 -> SELL at target
        result = strategy.isTargetAchieved(order, 22601);
        assertTrue(result.sellOrder());
        assertEquals(25, result.quantity());
        assertEquals(OrderSellReason.TARGET_HIT, result.reason());
    }

    // ---- Test 2: Fibonacci ratios (0.382/0.618/1.0) ----

    @Test
    void fibonacciRatios_stepsAtFibLevels_revisesStopLossCorrectly() {
        // entry=22500, target=22600 -> distance=100
        // steps at 38.2%=22538.2, 61.8%=22561.8, 100%=22600
        Task task = mockTaskWithRatios(List.of(0.382, 0.618, 1.0));
        ActiveIndexOrder order = createCallOrder(task, 22500, 22600, 22475);

        // Below first fib step - no action
        OrderSellDetailModel result = strategy.isTargetAchieved(order, 22530);
        assertFalse(result.sellOrder());

        // Cross first fib step (38.2%) -> SL to entry
        result = strategy.isTargetAchieved(order, 22539);
        assertFalse(result.sellOrder());
        assertEquals(22500, order.getStopLoss(), DELTA);

        // Cross second fib step (61.8%) -> SL to first step (22538.2)
        result = strategy.isTargetAchieved(order, 22562);
        assertFalse(result.sellOrder());
        assertEquals(22538.2, order.getStopLoss(), DELTA);

        // Cross final step (100%) -> SELL
        result = strategy.isTargetAchieved(order, 22601);
        assertTrue(result.sellOrder());
        assertEquals(OrderSellReason.TARGET_HIT, result.reason());
    }

    // ---- Test 3: Conservative ratios (0.50/0.75/1.0) ----

    @Test
    void conservativeRatios_widerSteps_revisesStopLossCorrectly() {
        // entry=22500, target=22600 -> distance=100
        // steps at 50%=22550, 75%=22575, 100%=22600
        Task task = mockTaskWithRatios(List.of(0.50, 0.75, 1.0));
        ActiveIndexOrder order = createCallOrder(task, 22500, 22600, 22475);

        // Below 50% - no action
        OrderSellDetailModel result = strategy.isTargetAchieved(order, 22549);
        assertFalse(result.sellOrder());

        // Cross 50% step -> SL to entry (22500)
        result = strategy.isTargetAchieved(order, 22551);
        assertFalse(result.sellOrder());
        assertEquals(22500, order.getStopLoss(), DELTA);

        // Cross 75% step -> SL to 50% step (22550)
        result = strategy.isTargetAchieved(order, 22576);
        assertFalse(result.sellOrder());
        assertEquals(22550, order.getStopLoss(), DELTA);

        // Cross final (100%) -> SELL
        result = strategy.isTargetAchieved(order, 22601);
        assertTrue(result.sellOrder());
        assertEquals(OrderSellReason.TARGET_HIT, result.reason());
    }

    // ---- Test 4: Two-step ratios (0.50/1.0) ----

    @Test
    void twoStepRatios_onlyOneIntermediateStep_revisesOnceAndSells() {
        // entry=22500, target=22600 -> distance=100
        // steps at 50%=22550, 100%=22600
        Task task = mockTaskWithRatios(List.of(0.50, 1.0));
        ActiveIndexOrder order = createCallOrder(task, 22500, 22600, 22475);

        // Cross 50% step -> SL to entry
        OrderSellDetailModel result = strategy.isTargetAchieved(order, 22551);
        assertFalse(result.sellOrder());
        assertEquals(22500, order.getStopLoss(), DELTA);

        // Cross final step -> SELL
        result = strategy.isTargetAchieved(order, 22601);
        assertTrue(result.sellOrder());
        assertEquals(OrderSellReason.TARGET_HIT, result.reason());
    }

    // ---- Test 5: Auto-appending 1.0 when last ratio is not 1.0 ----

    @Test
    void ratiosWithout1Point0_autoAppends_sellsAtFullTarget() {
        // configured: [0.382, 0.618] -> resolved: [0.382, 0.618, 1.0]
        // entry=22500, target=22600 -> steps at 22538.2, 22561.8, 22600
        Task task = mockTaskWithRatios(List.of(0.382, 0.618));
        ActiveIndexOrder order = createCallOrder(task, 22500, 22600, 22475);

        // Cross first step
        OrderSellDetailModel result = strategy.isTargetAchieved(order, 22539);
        assertFalse(result.sellOrder());
        assertEquals(22500, order.getStopLoss(), DELTA);

        // Cross second step -> SL to first step
        result = strategy.isTargetAchieved(order, 22562);
        assertFalse(result.sellOrder());
        assertEquals(22538.2, order.getStopLoss(), DELTA);

        // Cross auto-appended final step (1.0) -> SELL
        result = strategy.isTargetAchieved(order, 22601);
        assertTrue(result.sellOrder());
        assertEquals(OrderSellReason.TARGET_HIT, result.reason());
    }

    // ---- Test 6: PE (put) order with custom ratios ----

    @Test
    void putOrder_customRatios_stepsBelow_revisesStopLossDownward() {
        // PE: entry=22600, target=22500 -> distance=100 (entry - target)
        // ratios: [0.50, 1.0] -> steps at 22550, 22500
        Task task = mockTaskWithRatios(List.of(0.50, 1.0));
        ActiveIndexOrder order = createPutOrder(task, 22600, 22500, 22625);

        // LTP above first step - no action
        OrderSellDetailModel result = strategy.isTargetAchieved(order, 22560);
        assertFalse(result.sellOrder());

        // Cross first step (50%) at 22549 -> SL to entry (22600)
        result = strategy.isTargetAchieved(order, 22549);
        assertFalse(result.sellOrder());
        assertEquals(22600, order.getStopLoss(), DELTA);

        // Cross final step at 22499 -> SELL at target
        result = strategy.isTargetAchieved(order, 22499);
        assertTrue(result.sellOrder());
        assertEquals(25, result.quantity());
        assertEquals(OrderSellReason.TARGET_HIT, result.reason());
    }

    // ---- Additional edge case: stop-loss hit after SL revision ----

    @Test
    void stopLossHit_afterRevision_returnsStopLossHit() {
        Task task = mockTaskWithRatios(List.of(0.50, 1.0));
        ActiveIndexOrder order = createCallOrder(task, 22500, 22600, 22475);

        // Cross 50% step -> SL revised to 22500
        strategy.isTargetAchieved(order, 22551);
        assertEquals(22500, order.getStopLoss(), DELTA);

        // Price reverses, SL hit at revised level
        OrderSellDetailModel result = strategy.isStopLossHit(order, 22499);
        assertTrue(result.sellOrder());
        assertEquals(25, result.quantity());
        assertEquals(OrderSellReason.STOP_LOSS_HIT, result.reason());
    }

    // ---- Edge case: unsorted ratios should be sorted ----

    @Test
    void unsortedRatios_areSortedBeforeStepGeneration() {
        // Configured out of order: [0.75, 0.25, 0.50, 1.0]
        // Should sort to: [0.25, 0.50, 0.75, 1.0]
        // entry=22500, target=22600 -> steps at 22525, 22550, 22575, 22600
        Task task = mockTaskWithRatios(List.of(0.75, 0.25, 0.50, 1.0));
        ActiveIndexOrder order = createCallOrder(task, 22500, 22600, 22475);

        // Cross first step (25%) -> SL to entry
        OrderSellDetailModel result = strategy.isTargetAchieved(order, 22526);
        assertFalse(result.sellOrder());
        assertEquals(22500, order.getStopLoss(), DELTA);

        // Cross second step (50%) -> SL to 22525
        result = strategy.isTargetAchieved(order, 22551);
        assertFalse(result.sellOrder());
        assertEquals(22525, order.getStopLoss(), DELTA);

        // Cross third step (75%) -> SL to 22550
        result = strategy.isTargetAchieved(order, 22576);
        assertFalse(result.sellOrder());
        assertEquals(22550, order.getStopLoss(), DELTA);

        // Final step -> SELL
        result = strategy.isTargetAchieved(order, 22601);
        assertTrue(result.sellOrder());
        assertEquals(OrderSellReason.TARGET_HIT, result.reason());
    }
}
