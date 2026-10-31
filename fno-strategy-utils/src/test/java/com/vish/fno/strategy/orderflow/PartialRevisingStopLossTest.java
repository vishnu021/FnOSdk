package com.vish.fno.strategy.orderflow;

import com.vish.fno.model.Task;
import com.vish.fno.model.order.OrderSellDetailModel;
import com.vish.fno.model.order.OrderSellReason;
import com.vish.fno.model.order.activeorder.ActiveIndexOrder;
import com.vish.fno.model.order.orderrequest.IndexOrderRequest;
import com.vish.fno.util.candle.store.CandleStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exit labelling of {@link PartialRevisingStopLoss}. The runner left after the partial target sell
 * must not exit as STOP_LOSS_HIT: SellOrderExecutor feeds that literal to the ADR-0054
 * sameStrikeCooldown / lossStreakBreaker rails, and the trade already banked its target lots.
 */
class PartialRevisingStopLossTest {

    private static final int LOT = 25;
    private PartialRevisingStopLoss strategy;

    @BeforeEach
    void setUp() {
        CandleStore candleStore = mock(CandleStore.class);
        when(candleStore.updateAndGetMinuteData(anyString())).thenReturn(List.of());
        strategy = new PartialRevisingStopLoss(candleStore);
    }

    private ActiveIndexOrder order(boolean call, double entry, double target, double stopLoss, int lots) {
        IndexOrderRequest request = IndexOrderRequest.builder("TestStrategy", "NIFTY 50", mock(Task.class))
                .buyThreshold(entry)
                .target(target)
                .stopLoss(stopLoss)
                .callOrder(call)
                .expirationTimestamp(375)
                .build();
        return new ActiveIndexOrder(request, entry, 100, "2026-10-30 10:00:00", lots * LOT, LOT);
    }

    @Test
    void runnerStopAfterPartialTargetSell_isLabelledTargetHit() {
        ActiveIndexOrder order = order(true, 22500, 22560, 22470, 3);

        OrderSellDetailModel partial = strategy.isTargetAchieved(order, 22561);
        assertTrue(partial.sellOrder());
        assertEquals(2 * LOT, partial.quantity());
        assertEquals(OrderSellReason.TARGET_HIT, partial.reason());
        order.incrementSoldQuantity(partial.quantity(), 140.0);

        OrderSellDetailModel runner = strategy.isStopLossHit(order, 22469);
        assertTrue(runner.sellOrder());
        assertEquals(LOT, runner.quantity());
        assertEquals(OrderSellReason.TARGET_HIT, runner.reason());
    }

    @Test
    void putRunnerStopAfterPartialTargetSell_isLabelledTargetHit() {
        ActiveIndexOrder order = order(false, 22500, 22440, 22530, 3);

        OrderSellDetailModel partial = strategy.isTargetAchieved(order, 22439);
        order.incrementSoldQuantity(partial.quantity(), 140.0);

        OrderSellDetailModel runner = strategy.isStopLossHit(order, 22531);
        assertTrue(runner.sellOrder());
        assertEquals(LOT, runner.quantity());
        assertEquals(OrderSellReason.TARGET_HIT, runner.reason());
    }

    @Test
    void stopHitBeforeAnyTargetSell_staysStopLossHit() {
        ActiveIndexOrder order = order(true, 22500, 22560, 22470, 3);

        OrderSellDetailModel result = strategy.isStopLossHit(order, 22469);
        assertTrue(result.sellOrder());
        assertEquals(3 * LOT, result.quantity());
        assertEquals(OrderSellReason.STOP_LOSS_HIT, result.reason());
    }
}
