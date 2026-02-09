package com.vish.fno.reader.core;

import com.vish.fno.reader.exception.InitialisationException;
import com.vish.fno.reader.util.OptionPriceUtils;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@SuppressWarnings({"PMD.RedundantFieldInitializer", "PMD.LooseCoupling", "PMD.AvoidCatchingGenericException"})
public class KiteWebSocket {

    private KiteTicker tickerProvider;
    private final InstrumentCache instrumentCache;
    @Getter
    private final boolean connectToWebSocket;
    private volatile boolean isConnected;
    private final CopyOnWriteArrayList<Long> tokensToSubscribe;
    private final CopyOnWriteArrayList<Long> subscribedTokens;
    @Setter
    private OnTicks onTickerArrivalListener;
    @Setter
    private OnOrderUpdate onOrderUpdateListener;

    public KiteWebSocket(boolean connectToWebSocket, InstrumentCache instrumentCache) {
        this.connectToWebSocket = connectToWebSocket;
        this.instrumentCache = instrumentCache;
        this.tokensToSubscribe = new CopyOnWriteArrayList<>();
        this.subscribedTokens = new CopyOnWriteArrayList<>();
        this.tokensToSubscribe.add(256265L);
        this.tokensToSubscribe.add(260105L);
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
            tickerProvider.setMode(new ArrayList<>(tokensToSubscribe), KiteTicker.modeLTP);
        }
    }

    private void addWebSocketListeners(OnTicks onTickerArrivalListener, OnOrderUpdate onOrderUpdateListener) {
        tickerProvider.setOnConnectedListener(() -> {
            /* Subscribe ticks for token.
             * By default, all tokens are subscribed for modeQuote.
             * */
            log.info("Subscribing to following {} tokens: {}", tokensToSubscribe.size(), tokensToSubscribe);
            tickerProvider.subscribe(new ArrayList<>(tokensToSubscribe));
            tickerProvider.setMode(new ArrayList<>(tokensToSubscribe), KiteTicker.modeFull);

            // Move tokens from queue to subscribed list AFTER subscribing
            subscribedTokens.addAll(tokensToSubscribe);
            tokensToSubscribe.clear();
            log.info("Subscription complete. {} tokens now subscribed", subscribedTokens.size());
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

    public void unsubscribe(List<Long> tokens) {
        log.info("Unsubscribing : {}", tokens);
        tickerProvider.unsubscribe(new ArrayList<>(tokens));
    }

    /**
     * Returns the list of currently subscribed WebSocket tokens
     * @return Immutable copy of subscribed tokens list
     */
    public List<Long> getSubscribedTokens() {
        return new ArrayList<>(subscribedTokens);
    }

    /**
     * Returns the count of currently subscribed WebSocket tokens
     * @return Number of subscribed tokens
     */
    public int getSubscribedTokensCount() {
        return subscribedTokens.size();
    }

    /**
     * Checks if a symbol is already subscribed to WebSocket
     * @param symbol The trading symbol to check
     * @return true if symbol is subscribed, false otherwise
     */
    public boolean isSymbolSubscribed(String symbol) {
        return instrumentCache.getInstrument(symbol)
                .map(subscribedTokens::contains)
                .orElse(false);
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

        // Separate into new tokens and already existing tokens (filter out empty Optionals from unknown symbols)
        List<Long> allTokens = allSymbols
                .stream()
                .map(instrumentCache::getInstrument)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        List<Long> alreadySubscribed = allTokens
                .stream()
                .filter(subscribedTokens::contains)
                .toList();

        List<Long> alreadyInQueue = allTokens
                .stream()
                .filter(t -> !subscribedTokens.contains(t) && tokensToSubscribe.contains(t))
                .toList();

        List<Long> newTokensToAdd = allTokens
                .stream()
                .filter(t -> !subscribedTokens.contains(t) && !tokensToSubscribe.contains(t))
                .collect(Collectors.toCollection(ArrayList::new));

        // Log tokens that were skipped
        if(!alreadySubscribed.isEmpty()) {
            log.info("Skipping {} tokens - already subscribed: {}", alreadySubscribed.size(), alreadySubscribed);
        }
        if(!alreadyInQueue.isEmpty()) {
            log.info("Skipping {} tokens - already in queue: {}", alreadyInQueue.size(), alreadyInQueue);
        }

        if(newTokensToAdd.isEmpty()) {
            log.info("No new tokens to add - all {} tokens were already subscribed or queued: {}", allTokens.size(), allTokens);
        } else {
            if(isConnected) {
                // WebSocket is connected - subscribe immediately then add to subscribedTokens
                log.info("WebSocket connected - subscribing to {} new tokens immediately: {}", newTokensToAdd.size(), newTokensToAdd);
                tickerProvider.subscribe(new ArrayList<>(newTokensToAdd));
                tickerProvider.setMode(new ArrayList<>(newTokensToAdd), KiteTicker.modeFull);

                // Add to subscribedTokens AFTER subscribing
                subscribedTokens.addAll(newTokensToAdd);
                log.info("Subscription complete. Total subscribed tokens: {}", subscribedTokens.size());
            } else {
                // WebSocket not connected - add to queue
                log.info("WebSocket not connected - adding {} tokens to queue (will subscribe on connect): {}", newTokensToAdd.size(), newTokensToAdd);
                tokensToSubscribe.addAll(newTokensToAdd);
                log.info("Total tokens in queue: {}", tokensToSubscribe.size());
            }
        }
    }

    @PreDestroy
    public void disconnect() {
        log.info("Disconnecting...");
        tickerProvider.disconnect();
    }
}
