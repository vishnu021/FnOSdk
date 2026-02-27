package com.vish.fno.strategy.orderflow;

import com.vish.fno.model.Candle;
import com.vish.fno.model.order.OrderSellDetailModel;
import com.vish.fno.model.order.OrderSellReason;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.util.candle.HeikinAshi;
import com.vish.fno.util.candle.store.CandleStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class PartialRevisingStopLoss extends AbstractTargetAndStopLossStrategy {
    private static final int TIMEFRAME = 1;
    private final CandleStore candleStore;

    @Override
    public OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp) {
        if(checkTargetAchieved(order, ltp)) {
            int totalLots = getTotalLots(order.getBuyQuantity(), order.getLotSize());

            // if low quantity was bought initially
            if(totalLots < 2) {
                log.info("Target achieved for order: {} ltp: {}", order, ltp);
                return new OrderSellDetailModel(true, order.getBuyQuantity(), OrderSellReason.TARGET_HIT, order);
            }

            // if high quantity was bought, check if some quantity was not sold
            if(totalLots > 2 && order.getSoldQuantity() == 0) {
                // selling some lots
                int lotsToSell = getLotsToSell(totalLots);
                log.info("Target achieved for order : {} ltp: {}, selling : {} out of total : {}",
                        order, ltp, (lotsToSell * order.getLotSize()), order.getBuyQuantity());
                reviseStopLoss(order, ltp);
                return new OrderSellDetailModel(true, lotsToSell * order.getLotSize(), OrderSellReason.TARGET_HIT, order);
            }

            if(order.getSoldQuantity() > 0) {
                // some orders were already sold, now stopLoss will be revised till its hit
                reviseStopLoss(order, ltp);
                return new OrderSellDetailModel(false);
            }
            log.info("Call target achieved for order : {} ltp: {}",  order, ltp);
            return new OrderSellDetailModel(true, order.getBuyQuantity(), OrderSellReason.TARGET_HIT, order);
        }
        return new OrderSellDetailModel(false);
    }

    // TODO: getting revised on every tick
    private void reviseStopLoss(ActiveOrder order, double ltp) {
        boolean isCallOrder = order.isCallOrder();
        String index = order.getIndex();
        List<Candle> candles = candleStore.updateAndGetMinuteData(index);
        List<Candle> heikinAshiCandles = HeikinAshi.getIntradayCompleteCandle(candles, TIMEFRAME);
        Candle lastCandle = heikinAshiCandles.get(heikinAshiCandles.size() - 1);

        double newStopLoss = isCallOrder ? lastCandle.low() : lastCandle.high();
        boolean shouldUpdate = isCallOrder
                ? newStopLoss > order.getStopLoss()
                : newStopLoss < order.getStopLoss();

        if (shouldUpdate) {
            log.info("Revising stopLoss to: {}, ltp: {} for order: {}", newStopLoss, ltp, order);
            order.setStopLoss(newStopLoss);
        }
    }

    private int getLotsToSell(int totalLots) {
        return switch (totalLots) {
            case 1 -> 1;
            case 2, 3 -> 2;
            case 4, 5 -> 3;
            case 6, 7 -> 4;
            default -> (totalLots * 2) / 3;
        };
    }

    private int getTotalLots(int quantity, int lotSize) {
        if(lotSize == 0) {
            return 1;
        }
        return quantity / lotSize;
    }
}
