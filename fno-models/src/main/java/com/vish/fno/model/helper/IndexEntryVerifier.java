package com.vish.fno.model.helper;

import com.vish.fno.model.Task;
import com.vish.fno.model.Ticker;
import com.vish.fno.model.order.orderrequest.IndexOrderRequest;
import com.vish.fno.model.order.orderrequest.OrderRequest;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

// CPD-OFF
@Slf4j
@NoArgsConstructor
public class IndexEntryVerifier implements EntryVerifier {

    // TODO: dont stop sell order by price (but the condition is only for buyPrice :thinking)
    public boolean isPlaceOrder(OrderRequest orderRequest, double ltp, boolean isExpiryDayForOption, double availableCash) {
        
        if(orderRequest instanceof IndexOrderRequest order) {

            // check if the strategy is enabled for expiry day
            Task task = order.getTask();
            // TODO: add test case
            if (!task.isExpiryDayOrders() && isExpiryDayForOption) {
                log.info("Expiry day orders is not enabled for task: {} ", order.getTask());
                return false;
            }

            if (!order.getTask().isEnabled()) {
                log.info("The following task({}) has not been enabled, not placing order: {}", order.getTask(), order);
                return false;
            }

            if(hasMoveAlreadyHappened(ltp, orderRequest)) {
                return false;
            }

            if (availableCash > ltp * order.getQuantity()) {
                log.info("Placing order as the symbol is : {} or amount is: {}. Available amount: {}",
                        order.getIndex(), (ltp * order.getQuantity()), availableCash);
                return true;
            } else {
                log.info("Not placing order as the symbol is : {} or amount is: {}. Available amount: {}",
                        order.getIndex(), (ltp * order.getQuantity()), availableCash);
            }
        } else {
            log.error("IndexEntry Verifier got an instance of non ActiveIndexOrder, activeOrder : {}", orderRequest);
        }
        return false;
    }

    @Override
    public Optional<OrderRequest> verifyBuyThreshold(Ticker tick, OrderRequest order) {
        if(order instanceof IndexOrderRequest orderRequest) {
            if (orderRequest.isCallOrder()) {
                if (tick.getLastTradedPrice() > orderRequest.getBuyThreshold()) {
                    log.info("tick ltp: {} is greater than buy threshold {}, placing order request({}) : {}",
                            tick.getLastTradedPrice(), orderRequest.getBuyThreshold(), orderRequest.getOptionSymbol(), orderRequest);
                    return Optional.of(orderRequest);
                }
            } else {
                if (tick.getLastTradedPrice() < orderRequest.getBuyThreshold()) {
                    log.info("tick ltp: {} is lesser than buy threshold {}, placing order request({}) : {}",
                            tick.getLastTradedPrice(), orderRequest.getBuyThreshold(), orderRequest.getOptionSymbol(), orderRequest);
                    return Optional.of(orderRequest);
                }
            }
        } else {
            log.warn("Invalid order request type : {}", order);
        }
        return Optional.empty();
    }

    // TODO: increase the remaining target
    public boolean hasMoveAlreadyHappened(double ltp, OrderRequest order) {
        if(order instanceof IndexOrderRequest orderRequest) {
            if (ltp < 0.1) {
                log.info("ltp price not set, not placing order for {}. ltp: {}, order: {}", orderRequest.getIndex(), ltp, orderRequest);
                return true;
            }

            final double buyAt = orderRequest.getBuyThreshold();
            final double target = orderRequest.getTarget();

            if (orderRequest.isCallOrder()) {
                final double priceAlreadyCrossed = ltp - buyAt;
                final double targetRemaining = target - ltp;
                // to ensure risk:reward is still 1:1, for 1:1.5 initial target
                if (targetRemaining > (priceAlreadyCrossed * 5)) {
                    return false;
                }
            } else {
                final double priceAlreadyCrossed = buyAt - ltp;
                final double targetRemaining = ltp - target;
                if (targetRemaining > (priceAlreadyCrossed * 5)) {
                    return false;
                }
            }
            log.info("Price movement has already happened for {}, tag: {}, ltp : {}, order buy: {}, target: {}",
                    orderRequest.getIndex(), orderRequest.getTag(), ltp, orderRequest.getBuyThreshold(), orderRequest.getTarget());

        } else {
            log.error("OrderRequest is of non supported type : {}", order);
        }
        return true;
    }
}
// CPD-ON