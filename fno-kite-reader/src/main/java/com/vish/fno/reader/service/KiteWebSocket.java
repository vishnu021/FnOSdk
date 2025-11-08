package com.vish.fno.reader.service;

import com.vish.fno.reader.exception.InitialisationException;
import com.vish.fno.util.JsonUtils;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.ticker.KiteTicker;
import com.zerodhatech.ticker.OnOrderUpdate;
import com.zerodhatech.ticker.OnTicks;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@SuppressWarnings({"PMD.RedundantFieldInitializer", "PMD.LooseCoupling", "PMD.AvoidCatchingGenericException"})
public class KiteWebSocket {
    private KiteTicker tickerProvider;
    private final InstrumentCache instrumentCache;
    @Getter
    private final boolean connectToWebSocket;
    private boolean isConnected;
    private final ArrayList<Long> webSocketTokensToSubscribe;
    @Setter
    private OnTicks onTickerArrivalListener;
    @Setter
    private OnOrderUpdate onOrderUpdateListener;

    public KiteWebSocket(boolean connectToWebSocket, InstrumentCache instrumentCache) {
        this.connectToWebSocket = connectToWebSocket;
        this.instrumentCache = instrumentCache;
        this.webSocketTokensToSubscribe = new ArrayList<>();
        this.webSocketTokensToSubscribe.add(256265L);
        this.webSocketTokensToSubscribe.add(260105L);
        this.onOrderUpdateListener = order -> log.info("Order update complete : {}", JsonUtils.getFormattedObject(order));
    }

    public void initialize(KiteConnect kiteSdk) {
        if(connectToWebSocket) {
            log.info("Initialising websocket...");
            this.tickerProvider = new KiteTicker(kiteSdk.getAccessToken(), kiteSdk.getApiKey());
            addWebSocketListeners(onTickerArrivalListener, onOrderUpdateListener);
            tickerProvider.connect();
            isConnected = tickerProvider.isConnectionOpen();
            log.info("isConnected : {}", isConnected);

            /* set mode is used to set mode in which you need tick for list of tokens.
             * Ticker allows three modes, modeFull, modeQuote, modeLTP.
             * For getting only last traded price, use modeLTP
             * For getting last traded price, last traded quantity, average price, volume traded today, total sell quantity and total buy quantity, open, high, low, close, change, use modeQuote
             * For getting all data with depth, use modeFull*/
            tickerProvider.setMode(webSocketTokensToSubscribe, KiteTicker.modeLTP);
        }
    }

    private void addWebSocketListeners(OnTicks onTickerArrivalListener, OnOrderUpdate onOrderUpdateListener) {
        tickerProvider.setOnConnectedListener(() -> {
            /* Subscribe ticks for token.
             * By default, all tokens are subscribed for modeQuote.
             * */
            log.info("Subscribing to following tokens: {}", webSocketTokensToSubscribe);
            tickerProvider.subscribe(webSocketTokensToSubscribe);
            tickerProvider.setMode(webSocketTokensToSubscribe, KiteTicker.modeFull);
        });

        tickerProvider.setOnDisconnectedListener(() -> log.info("disconnected"));

        /* Set listener to get order updates.*/
        tickerProvider.setOnOrderUpdateListener(onOrderUpdateListener);
        tickerProvider.setOnTickerArrivalListener(onTickerArrivalListener);

        tickerProvider.setTryReconnection(true);
        try {
            tickerProvider.setMaximumRetries(10);
            tickerProvider.setMaximumRetryInterval(30);
        } catch (KiteException e) {
            log.error("Exception while setting retries", e);
            throw new InitialisationException("Error initializing kite web socket", e);
        }
    }

    public void addListener(OnTicks onTickerArrivalListener) {
        tickerProvider.setOnTickerArrivalListener(onTickerArrivalListener);
    }

    public void unsubscribe(ArrayList<Long> tokens) {
        log.info("Unsubscribing : {}", tokens);
        tickerProvider.unsubscribe(tokens);
    }

    public void appendWebSocketSymbolsList(List<String> symbols, boolean addFutures) {
        if(!connectToWebSocket) {
            return;
        }

        // Adding futures of the symbols as well
        ArrayList<String> allSymbols = new ArrayList<>();
        if(addFutures) {
            for (String symbol : symbols) {
                Optional<String> futureTradingSymbol = OptionPriceUtils.getNextExpiryFutureSymbol(symbol, instrumentCache.getInstruments());
                futureTradingSymbol.ifPresent(allSymbols::add);
            }
        }

        allSymbols.addAll(symbols);
        List<Long> webSocketsToAdd = allSymbols
                .stream()
                .map(instrumentCache::getInstrument)
                .filter(t -> !webSocketTokensToSubscribe.contains(t))
                .collect(Collectors.toCollection(ArrayList::new));

        if(!webSocketsToAdd.isEmpty()) {
            log.info("Appending symbols: {} to websocket token list", allSymbols);
            webSocketTokensToSubscribe.addAll(webSocketsToAdd);
            this.subscribe(webSocketTokensToSubscribe);
        }
    }

    private void subscribe(ArrayList<Long> tokens) {
        if(isConnected) {
            /* Subscribe ticks for token.
             * By default, all tokens are subscribed for modeQuote.
             * */
            log.info("Subscribing : {}", tokens);
            tickerProvider.subscribe(tokens);
            tickerProvider.setMode(tokens, KiteTicker.modeFull);
        } else {
            log.info("Appending tokens to toSubscribe list : {}", tokens);
            webSocketTokensToSubscribe.addAll(tokens);
        }
    }

    @PreDestroy
    public void disconnect() {
        log.info("Disconnecting...");
        tickerProvider.disconnect();
    }
}
