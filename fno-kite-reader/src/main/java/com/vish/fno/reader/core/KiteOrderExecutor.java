package com.vish.fno.reader.core;

import com.vish.fno.reader.model.KiteOpenOrder;
import com.vish.fno.util.JsonUtils;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.Position;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.vish.fno.reader.util.OrderUtils.createMarketOrderWithParameters;
import static com.vish.fno.util.FnoConstants.DAY;
import static com.vish.fno.util.FnoConstants.NET;
import static com.vish.fno.util.JsonUtils.getFormattedObject;

@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("PMD.TooManyStaticImports")
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

    Order placeOptionOrder(OrderParams orderParams) {
        return session.executeWithLock(() -> {
            Order order = null;
            try {
                log.info("placing order with params : {}", orderParams);
                order = session.getKiteSdk().placeOrder(orderParams, Constants.VARIETY_REGULAR);
                log.info("order id: {}", order.orderId);
            } catch (KiteException ke) {
                log.error("KiteException occurred while placing order, code: {}, message: {}, order: {}",
                        ke.code, ke.message, getFormattedObject(orderParams), ke);
            } catch (JSONException | IOException e) {
                log.error("Error occurred while placing order", e);
            }
            return order;
        }, "placeOptionOrder");
    }

    List<Order> getOrders() {
        return session.executeWithLock(() -> {
            try {
                return session.getKiteSdk().getOrders();
            } catch (KiteException e) {
                log.error("Failed to get orders, error code: {}, error message: {}", e.code, e.message, e);
            } catch (IOException e) {
                log.error("Failed to get orders, error: {}", e.getMessage(), e);
            }
            return List.of();
        }, "getOrders");
    }

    Map<String, List<Position>> getPositions() {
        return session.executeWithLock(() -> {
            try {
                return session.getKiteSdk().getPositions();
            } catch (KiteException e) {
                log.error("Failed to get positions, error code: {}, error message: {}", e.code, e.message, e);
            } catch (IOException e) {
                log.error("Failed to get positions, error: {}", e.getMessage(), e);
            }
            return Map.of();
        }, "getPositions");
    }

    public void logExistingOrdersAndPositions(String symbol, String tag) {
        List<String> orders = getOrders()
                .stream()
                .filter(o -> o.tradingSymbol.equals(symbol))
                .filter(o -> o.tag.equals(tag))
                .map(JsonUtils::getFormattedObject)
                .toList();

        List<String> netPositions = getPositions().get(NET)
                .stream()
                .filter(o -> o.tradingSymbol.equals(symbol))
                .map(JsonUtils::getFormattedObject)
                .toList();

        List<String> dayPositions = getPositions().get(DAY)
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
            Order order;
            try {
                String exchange = instrumentCache.getExchangeForSymbol(symbol);
                OrderParams orderParams = createMarketOrderWithParameters(symbol, orderSize, transactionType, tag, exchange);
                order = session.getKiteSdk().placeOrder(orderParams, Constants.VARIETY_REGULAR);
                log.info("order placed successfully with id: {} for symbol: {}, orderSize: {}",
                        order.orderId, symbol, orderSize);
            } catch (KiteException e) {
                log.error("KiteException occurred while placing order for symbol: {}, orderSize: {}, code: {}, message: {}",
                        symbol, orderSize, e.code, e.message);
                return Optional.of(new KiteOpenOrder(null, false, e.code, e.message));
            } catch (JSONException | IOException e) {
                log.error("Error occurred while placing order", e);
                return Optional.of(buildUnsuccessfulKiteOrder(e));
            }
            return Optional.of(new KiteOpenOrder(order, true, null, null));
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
