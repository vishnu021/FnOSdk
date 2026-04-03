package com.vish.fno.reader.core;

import com.vish.fno.model.PositionType;
import com.vish.fno.reader.model.KiteOpenOrder;
import com.vish.fno.util.JsonUtils;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.OrderResponse;
import com.zerodhatech.models.Position;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.vish.fno.reader.util.OrderUtils.createMarketOrderWithParameters;

@Slf4j
@RequiredArgsConstructor
class KiteOrderExecutor {

    private final KiteSession session;
    private final InstrumentCache instrumentCache;

    Optional<KiteOpenOrder> buyOrder(String symbol, int orderSize, String tag, boolean isPlaceOrder) {
        log.info("Creating buy order with quantity : {}, symbol : {} , isPlaceOrder: {}", orderSize, symbol, isPlaceOrder);
        return placeOrder(symbol, orderSize, tag, Constants.TRANSACTION_TYPE_BUY, isPlaceOrder);
    }

    Optional<KiteOpenOrder> sellOrder(String symbol, int orderSize, String tag, boolean isPlaceOrder) {
        log.info("Creating sell order with quantity: {}, symbol: {}, tag: {}, isPlaceOrder: {}", orderSize, symbol, tag, isPlaceOrder);
        return placeOrder(symbol, orderSize, tag, Constants.TRANSACTION_TYPE_SELL, isPlaceOrder);
    }

    OrderResponse placeOptionOrder(OrderParams orderParams) {
        log.info("placing order with params : {}", orderParams);
        return session.executeWithLockSafe(() -> {
            OrderResponse response = session.getKiteSdk().placeOrder(orderParams, Constants.VARIETY_REGULAR);
            log.info("order id: {}", response.orderId);
            return response;
        }, "placeOptionOrder", null);
    }

    Order cancelOrder(String orderId, String variety) {
        log.info("Cancelling order: {}, variety: {}", orderId, variety);
        return session.executeWithLockSafe(() -> session.getKiteSdk().cancelOrder(orderId, variety),
                "cancelOrder", null);
    }

    List<Order> getOrders() {
        return session.executeWithLockSafe(() -> session.getKiteSdk().getOrders(), "getOrders", List.of());
    }

    Map<String, List<Position>> getPositions() {
        return session.executeWithLockSafe(() -> session.getKiteSdk().getPositions(), "getPositions", Map.of());
    }

    public void logExistingOrdersAndPositions(String symbol, String tag) {
        List<String> orders = getOrders()
                .stream()
                .filter(o -> o.tradingSymbol.equals(symbol))
                .filter(o -> o.tag.equals(tag))
                .map(JsonUtils::getFormattedObject)
                .toList();

        List<String> netPositions = getPositions().get(PositionType.NET.getCode())
                .stream()
                .filter(o -> o.tradingSymbol.equals(symbol))
                .map(JsonUtils::getFormattedObject)
                .toList();

        List<String> dayPositions = getPositions().get(PositionType.DAY.getCode())
                .stream()
                .filter(o -> o.tradingSymbol.equals(symbol))
                .map(JsonUtils::getFormattedObject)
                .toList();

        log.debug("Existing orders for same symbol: {}", orders);
        log.debug("Existing netPositions for same symbol: {}", netPositions);
        log.debug("Existing dayPositions for same symbol: {}", dayPositions);
    }

    private Optional<KiteOpenOrder> placeOrder(String symbol, int orderSize, String tag,
                                                String transactionType, boolean isPlaceOrder) {
        if (!session.isInitialised()) {
            log.warn("Not placing order as the kite service is not initialized yet.");
            return Optional.of(buildUnsuccessfulKiteOrder());
        }

        if (!isPlaceOrder) {
            log.warn("Not placing orders as it is not enabled or allowed currently");
            return Optional.of(buildSuccessfulKiteTestOrder());
        }

        if (!session.isPlaceOrders()) {
            log.warn("Not placing orders as it is turned off by configuration");
            return Optional.of(buildSuccessfulKiteTestOrder());
        }
        return session.executeWithLock(() -> {
            OrderResponse response;
            try {
                String exchange = instrumentCache.getExchangeForSymbol(symbol);
                OrderParams orderParams = createMarketOrderWithParameters(symbol, orderSize, transactionType, tag, exchange);
                response = session.getKiteSdk().placeOrder(orderParams, Constants.VARIETY_REGULAR);
                log.info("order placed successfully with id: {} for symbol: {}, orderSize: {}",
                        response.orderId, symbol, orderSize);
            } catch (KiteException e) {
                log.error("KiteException occurred while placing order for symbol: {}, orderSize: {}, code: {}, message: {}",
                        symbol, orderSize, e.code, e.message);
                return Optional.of(new KiteOpenOrder(null, false, e.code, e.message));
            } catch (JSONException | IOException e) {
                log.error("Error occurred while placing order", e);
                return Optional.of(buildUnsuccessfulKiteOrder(e));
            }
            return Optional.of(new KiteOpenOrder(response.orderId, true, null, null));
        }, "placeOrder");
    }

    private KiteOpenOrder buildUnsuccessfulKiteOrder(Exception e) {
        return new KiteOpenOrder(null, false, null, e.getMessage());
    }

    private KiteOpenOrder buildUnsuccessfulKiteOrder() {
        return new KiteOpenOrder(null, false, null, null);
    }

    private KiteOpenOrder buildSuccessfulKiteTestOrder() {
        return new KiteOpenOrder(null, true, null, null);
    }
}
