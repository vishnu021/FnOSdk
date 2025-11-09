package com.vish.fno.strategy.orderflow;

import com.vish.fno.model.Candle;
import com.vish.fno.model.order.OrderSellDetailModel;
import com.vish.fno.model.order.OrderSellReason;
import com.vish.fno.model.order.activeorder.ActiveIndexOrder;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.activeorder.OptionBasedActiveOrder;
import com.vish.fno.util.chart.HeikinAshi;
import com.vish.fno.util.helper.DataCache;
import com.vish.fno.util.orderflow.TargetAndStopLossStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

// CPD-OFF
@Slf4j
@RequiredArgsConstructor
public class PartialRevisingStopLoss implements TargetAndStopLossStrategy {
    private static final int TIMEFRAME = 1;
    private final DataCache dataCache;

    @Override
    public OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp) {
        if(order.isTargetAchieved(ltp)) {
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
        if(order instanceof ActiveIndexOrder) {
            if(((ActiveIndexOrder) order).isCallOrder()) {
                final String index = order.getIndex();
                final List<Candle> candles = dataCache.updateAndGetMinuteData(index);
                final List<Candle> heikinAshiCandles = HeikinAshi.getIntradayCompleteCandle(candles, TIMEFRAME);
                final double newStopLoss = heikinAshiCandles.get(heikinAshiCandles.size() - 1).low();
                if (newStopLoss > order.getStopLoss()) {
                    log.info("Revising stopLoss to: {}, ltp: {} for order: {} ", newStopLoss, ltp, order);
                    order.setStopLoss(newStopLoss);
                }

            } else {
                final String index = order.getIndex();
                final List<Candle> candles = dataCache.updateAndGetMinuteData(index);
                final List<Candle> heikinAshiCandles =  HeikinAshi.getIntradayCompleteCandle(candles, TIMEFRAME);
                final double newStopLoss = heikinAshiCandles.get(heikinAshiCandles.size() - 1).high();
                if(newStopLoss < order.getStopLoss()) {
                    log.info("Revising stopLoss to: {}, ltp: {} for order: {} ", newStopLoss, ltp, order);
                    order.setStopLoss(newStopLoss);
                }
            }
        } else if(order instanceof OptionBasedActiveOrder) {
            final String index = order.getIndex();
            final List<Candle> candles = dataCache.updateAndGetMinuteData(index);
            final List<Candle> heikinAshiCandles = HeikinAshi.getIntradayCompleteCandle(candles, TIMEFRAME);
            final double newStopLoss = heikinAshiCandles.get(heikinAshiCandles.size() - 1).low();
            if (newStopLoss > order.getStopLoss()) {
                log.info("Revising stopLoss to: {}, ltp: {} for order: {} ", newStopLoss, ltp, order);
                order.setStopLoss(newStopLoss);
            }
        } else {
            log.error("Invalid order type : {}", order);
        }
    }

    @Override
    public OrderSellDetailModel isStopLossHit(ActiveOrder order, double ltp) {
        if(order.isStopLossHit(ltp)) {
            int quantitiesRemaining = order.getBuyQuantity() - order.getSoldQuantity();
            log.info("StopLoss hit for order : {} ltp: {}, selling remaining {} orders", order, ltp, quantitiesRemaining);
            return new OrderSellDetailModel(true, quantitiesRemaining, OrderSellReason.STOP_LOSS_HIT, order);
        }
        return new OrderSellDetailModel(false);
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
// CPD-ON