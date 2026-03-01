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
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.vish.fno.util.FnoConstants.BANKEX_TOKEN;
import static com.vish.fno.util.FnoConstants.NIFTY_50_TOKEN;
import static com.vish.fno.util.FnoConstants.NIFTY_BANK_TOKEN;
import static com.vish.fno.util.FnoConstants.SENSEX_TOKEN;

@Slf4j
@SuppressWarnings({"PMD.RedundantFieldInitializer", "PMD.AvoidCatchingGenericException"})
public class KiteWebSocket {
    private static final int MAX_RECONNECTION_RETRIES = 10;
    private static final int MAX_RETRY_INTERVAL_SECONDS = 30;

    private volatile KiteTicker tickerProvider;
    private final InstrumentCache instrumentCache;
    @Getter
    private final boolean connectToWebSocket;
    private volatile boolean isConnected;
    // ReentrantLock instead of synchronized to avoid pinning virtual threads to carrier threads.
    // synchronized pins because intrinsic monitors are tied to the OS thread's stack frame;
    // ReentrantLock uses LockSupport.park() which the JVM recognizes as a virtual thread yield point.
    private final ReentrantLock tokenLock = new ReentrantLock();
    private final List<Long> tokensToSubscribe;
    private final List<Long> subscribedTokens;
    @Setter
    private volatile OnTicks onTickerArrivalListener;
    @Setter
    private volatile OnOrderUpdate onOrderUpdateListener;

    @SuppressWarnings("PMD.LooseCoupling")
    public KiteWebSocket(boolean connectToWebSocket, InstrumentCache instrumentCache) {
        this.connectToWebSocket = connectToWebSocket;
        this.instrumentCache = instrumentCache;
        this.subscribedTokens = new ArrayList<>();
        this.tokensToSubscribe = new ArrayList<>();
        this.tokensToSubscribe.add(NIFTY_50_TOKEN);
        this.tokensToSubscribe.add(NIFTY_BANK_TOKEN);
        this.tokensToSubscribe.add(SENSEX_TOKEN);
        this.tokensToSubscribe.add(BANKEX_TOKEN);
        this.onOrderUpdateListener = order -> log.info("Order update complete : {}", JsonUtils.getFormattedObject(order));
    }

    @SuppressWarnings("PMD.LooseCoupling")
    public void initialize(KiteConnect kiteSdk) {
        if (connectToWebSocket) {
            log.info("Initialising websocket...");
            this.tickerProvider = new KiteTicker(kiteSdk.getAccessToken(), kiteSdk.getApiKey());
            addWebSocketListeners(onTickerArrivalListener, onOrderUpdateListener);
            tickerProvider.connect();
            log.info("WebSocket connect() called, waiting for onConnected callback");

            tokenLock.lock();
            try {
                tickerProvider.setMode(new ArrayList<>(tokensToSubscribe), KiteTicker.modeLTP);
            } finally {
                tokenLock.unlock();
            }
        }
    }

    private void addWebSocketListeners(OnTicks onTickerArrivalListener, OnOrderUpdate onOrderUpdateListener) {
        tickerProvider.setOnConnectedListener(() -> {
            tokenLock.lock();
            try {
                log.info("Subscribing to following {} tokens: {}", tokensToSubscribe.size(), tokensToSubscribe);
                tickerProvider.subscribe(new ArrayList<>(tokensToSubscribe));
                tickerProvider.setMode(new ArrayList<>(tokensToSubscribe), KiteTicker.modeFull);

                subscribedTokens.addAll(tokensToSubscribe);
                tokensToSubscribe.clear();
                isConnected = true;
                log.info("Subscription complete. {} tokens now subscribed", subscribedTokens.size());
            } finally {
                tokenLock.unlock();
            }
        });

        tickerProvider.setOnDisconnectedListener(() -> {
            isConnected = false;
            log.info("disconnected");
        });

        tickerProvider.setOnOrderUpdateListener(onOrderUpdateListener);
        tickerProvider.setOnTickerArrivalListener(onTickerArrivalListener);

        tickerProvider.setTryReconnection(true);
        try {
            tickerProvider.setMaximumRetries(MAX_RECONNECTION_RETRIES);
            tickerProvider.setMaximumRetryInterval(MAX_RETRY_INTERVAL_SECONDS);
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

    public List<Long> getSubscribedTokens() {
        tokenLock.lock();
        try {
            return new ArrayList<>(subscribedTokens);
        } finally {
            tokenLock.unlock();
        }
    }

    public int getSubscribedTokensCount() {
        tokenLock.lock();
        try {
            return subscribedTokens.size();
        } finally {
            tokenLock.unlock();
        }
    }

    public boolean isSymbolSubscribed(String symbol) {
        return instrumentCache.getInstrument(symbol)
                .map(token -> {
                    tokenLock.lock();
                    try {
                        return subscribedTokens.contains(token);
                    } finally {
                        tokenLock.unlock();
                    }
                })
                .orElse(false);
    }

    @SuppressWarnings("PMD.LooseCoupling")
    public void appendWebSocketSymbolsList(List<String> symbols, boolean addFutures) {
        if (!connectToWebSocket) {
            return;
        }

        ArrayList<String> allSymbols = new ArrayList<>();
        if (addFutures) {
            for (String symbol : symbols) {
                Optional<String> futureTradingSymbol = OptionPriceUtils.getNextExpiryFutureSymbol(symbol, instrumentCache.getInstruments());
                futureTradingSymbol.ifPresent(allSymbols::add);
            }
        }

        allSymbols.addAll(symbols);

        List<Long> allTokens = allSymbols
                .stream()
                .map(instrumentCache::getInstrument)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        tokenLock.lock();
        try {
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

            if (!alreadySubscribed.isEmpty()) {
                log.info("Skipping {} tokens - already subscribed: {}", alreadySubscribed.size(), alreadySubscribed);
            }
            if (!alreadyInQueue.isEmpty()) {
                log.info("Skipping {} tokens - already in queue: {}", alreadyInQueue.size(), alreadyInQueue);
            }

            if (newTokensToAdd.isEmpty()) {
                log.info("No new tokens to add - all {} tokens were already subscribed or queued: {}", allTokens.size(), allTokens);
            } else {
                if (isConnected) {
                    log.info("WebSocket connected - subscribing to {} new tokens immediately: {}", newTokensToAdd.size(), newTokensToAdd);
                    tickerProvider.subscribe(new ArrayList<>(newTokensToAdd));
                    tickerProvider.setMode(new ArrayList<>(newTokensToAdd), KiteTicker.modeFull);

                    subscribedTokens.addAll(newTokensToAdd);
                    log.info("Subscription complete. Total subscribed tokens: {}", subscribedTokens.size());
                } else {
                    log.info("WebSocket not connected - adding {} tokens to queue (will subscribe on connect): {}", newTokensToAdd.size(), newTokensToAdd);
                    tokensToSubscribe.addAll(newTokensToAdd);
                    log.info("Total tokens in queue: {}", tokensToSubscribe.size());
                }
            }
        } finally {
            tokenLock.unlock();
        }
    }

    @PreDestroy
    public void disconnect() {
        log.info("Disconnecting...");
        tickerProvider.disconnect();
    }
}
