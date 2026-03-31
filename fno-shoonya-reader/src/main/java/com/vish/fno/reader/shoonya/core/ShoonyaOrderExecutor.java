package com.vish.fno.reader.shoonya.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vish.fno.reader.shoonya.exception.ShoonyaApiException;
import com.vish.fno.reader.shoonya.model.ShoonyaOpenOrder;
import com.vish.fno.reader.shoonya.model.ShoonyaOrder;
import com.vish.fno.reader.shoonya.util.ShoonyaHttpClient;
import com.vish.fno.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
class ShoonyaOrderExecutor {

    private static final int MAX_TAG_LENGTH = 20;
    private static final ObjectMapper MAPPER = JsonUtils.createObjectMapper();

    private final ShoonyaSession session;
    private final ShoonyaInstrumentCache instrumentCache;

    Optional<ShoonyaOpenOrder> buyOrder(String symbol, int orderSize, String tag, boolean isPlaceOrder) {
        log.info("Creating Shoonya buy order: qty={}, symbol={}, isPlaceOrder={}", orderSize, symbol, isPlaceOrder);
        return placeOrder(symbol, orderSize, tag, "B", isPlaceOrder);
    }

    Optional<ShoonyaOpenOrder> sellOrder(String symbol, int orderSize, String tag, boolean isPlaceOrder) {
        log.info("Creating Shoonya sell order: qty={}, symbol={}, tag={}, isPlaceOrder={}", orderSize, symbol, tag, isPlaceOrder);
        return placeOrder(symbol, orderSize, tag, "S", isPlaceOrder);
    }

    List<ShoonyaOrder> getOrders() {
        return session.executeWithLockSafe(() -> {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("uid", session.getUserId());
                payload.put("ordersource", "API");

                String rawResponse = session.getHttpClient().postAuthenticatedRaw(
                        "OrderBook", payload, session.getSessionToken());

                JsonNode arrayNode = MAPPER.readTree(rawResponse);
                if (!arrayNode.isArray()) {
                    log.warn("OrderBook returned non-array response, likely an error");
                    return List.<ShoonyaOrder>of();
                }

                List<ShoonyaOrder> orders = new ArrayList<>();
                for (JsonNode node : arrayNode) {
                    orders.add(new ShoonyaOrder(
                            getTextOrDefault(node, "norenordno", ""),
                            getTextOrDefault(node, "exch", ""),
                            getTextOrDefault(node, "tsym", ""),
                            getTextOrDefault(node, "status", ""),
                            getTextOrDefault(node, "trantype", ""),
                            getIntOrDefault(node, "qty", 0),
                            getIntOrDefault(node, "fillshares", 0),
                            getDoubleOrDefault(node, "avgprc", 0.0),
                            getTextOrDefault(node, "remarks", "")
                    ));
                }
                return orders;
            } catch (IOException e) {
                log.error("Failed to fetch order book", e);
                return List.<ShoonyaOrder>of();
            }
        }, "getOrders", List.of());
    }

    private Optional<ShoonyaOpenOrder> placeOrder(String symbol, int orderSize, String tag,
                                                   String transactionType, boolean isPlaceOrder) {
        if (!isPlaceOrder) {
            log.warn("Not placing order as it is not enabled currently");
            return Optional.of(new ShoonyaOpenOrder(null, true, null));
        }

        if (!session.isPlaceOrders()) {
            log.warn("Not placing order as it is turned off by configuration");
            return Optional.of(new ShoonyaOpenOrder(null, true, null));
        }

        if (!session.isInitialised()) {
            log.warn("Not placing order as Shoonya session is not initialized");
            return Optional.of(new ShoonyaOpenOrder(null, false, "Session not initialized"));
        }

        return session.executeWithLock(() -> {
            try {
                String exchange = instrumentCache.getExchangeForSymbol(symbol);
                String truncatedTag = tag.length() > MAX_TAG_LENGTH ? tag.substring(0, MAX_TAG_LENGTH) : tag;

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("ordersource", "API");
                payload.put("uid", session.getUserId());
                payload.put("actid", session.getUserId());
                payload.put("trantype", transactionType);
                payload.put("prd", "I");
                payload.put("exch", exchange);
                payload.put("tsym", ShoonyaHttpClient.encodeSymbol(symbol));
                payload.put("qty", String.valueOf(orderSize));
                payload.put("prctyp", "MKT");
                payload.put("prc", "0");
                payload.put("ret", "DAY");
                payload.put("remarks", truncatedTag);

                JsonNode response = session.getHttpClient().postAuthenticated(
                        "PlaceOrder", payload, session.getSessionToken());

                String orderId = response.get("norenordno").asText();
                log.info("Shoonya order placed successfully: orderId={}, symbol={}, qty={}",
                        orderId, symbol, orderSize);
                return Optional.of(new ShoonyaOpenOrder(orderId, true, null));

            } catch (ShoonyaApiException e) {
                log.error("Shoonya API error placing order for {}: {}", symbol, e.getMessage());
                return Optional.of(new ShoonyaOpenOrder(null, false, e.getMessage()));
            } catch (IOException e) {
                log.error("Error placing Shoonya order for {}", symbol, e);
                return Optional.of(new ShoonyaOpenOrder(null, false, e.getMessage()));
            }
        }, "placeOrder");
    }

    private static String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        return node.has(field) ? node.get(field).asText() : defaultValue;
    }

    private static int getIntOrDefault(JsonNode node, String field, int defaultValue) {
        return node.has(field) ? node.get(field).asInt(defaultValue) : defaultValue;
    }

    private static double getDoubleOrDefault(JsonNode node, String field, double defaultValue) {
        return node.has(field) ? node.get(field).asDouble(defaultValue) : defaultValue;
    }
}
