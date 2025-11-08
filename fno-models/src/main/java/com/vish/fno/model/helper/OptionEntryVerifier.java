package com.vish.fno.model.helper;

import com.vish.fno.model.Task;
import com.vish.fno.model.Ticker;
import com.vish.fno.model.order.orderrequest.OptionBasedOrderRequest;
import com.vish.fno.model.order.orderrequest.OrderRequest;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@NoArgsConstructor
public class OptionEntryVerifier implements EntryVerifier {

    // TODO: dont stop sell order by price (but the condition is only for buyPrice :thinking)

    @Override
    public boolean isPlaceOrder(OrderRequest orderRequest, double ltp, boolean isExpiryDayForOption, double availableCash) {
        if(orderRequest instanceof OptionBasedOrderRequest order) {
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

            if (availableCash > order.getBuyThreshold() * order.getQuantity()) {
                log.info("Placing order as the symbol is : {} or amount is: {}",
                        order.getIndex(), (order.getBuyThreshold() * order.getQuantity()));
                return true;
            } else {
                log.info("Not placing order as the symbol is : {} or amount is: {}. Available amount: {}",
                        order.getIndex(), (order.getBuyThreshold() * order.getQuantity()), availableCash);
            }

        } else {
            log.error("OptionEntryVerifier got an instance of non OptionBasedActiveOrder, activeOrder : {}", orderRequest);
        }
        return false;
    }

    @Override
    public Optional<OrderRequest> verifyBuyThreshold(Ticker tick, OrderRequest orderRequest) {
        if(orderRequest instanceof OptionBasedOrderRequest optionBasedOrderRequest) {
            if (tick.getLastTradedPrice() > optionBasedOrderRequest.getBuyThreshold()) {
                log.info("tick ltp: {} is greater than buy threshold {}, placing order request({}) : {}",
                        tick.getLastTradedPrice(), optionBasedOrderRequest.getBuyThreshold(), optionBasedOrderRequest.getIndex(), optionBasedOrderRequest);
                return Optional.of(optionBasedOrderRequest);
            }
        } else {
            log.warn("Invalid order request type : {}", orderRequest);
        }
        return Optional.empty();
    }

    // TODO: increase the remaining target
    @Override
    public boolean hasMoveAlreadyHappened(double ltp, OrderRequest order) {

        if(ltp < 0.1) {
            log.info("ltp price not set, not placing order for {}. ltp: {}, order: {}", order.getIndex(), ltp, order);
            return true;
        }

        final double buyAt = order.getBuyThreshold();
        final double target = order.getTarget();

        final double priceAlreadyCrossed = ltp - buyAt;
        final double targetRemaining = target - ltp;
        // to ensure risk:reward is still 1:1, for 1:1.5 initial target
        if(targetRemaining > (priceAlreadyCrossed * 5)) {
            return false;
        }
        log.info("Price movement has already happened for {}, tag: {}, ltp : {}, order buy: {}, target: {}",
                order.getIndex(), order.getTag(), ltp, order.getBuyThreshold(), order.getTarget());

        return true;
    }
}
