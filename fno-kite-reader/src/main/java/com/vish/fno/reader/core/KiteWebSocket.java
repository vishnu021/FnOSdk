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
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.function.Function;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.vish.fno.util.FnoConstants.BANKEX_TOKEN;
import static com.vish.fno.util.FnoConstants.NIFTY_50_TOKEN;
import static com.vish.fno.util.FnoConstants.NIFTY_BANK_TOKEN;
import static com.vish.fno.util.FnoConstants.SENSEX_TOKEN;

@Slf4j
@SuppressWarnings({"PMD.RedundantFieldInitializer", "PMD.AvoidCatchingGenericException", "PMD.LooseCoupling"})
public class KiteWebSocket {
    private static final int MAX_RECONNECTION_RETRIES = 10;
    private static final int MAX_RETRY_INTERVAL_SECONDS = 30;

    private volatile KiteTicker tickerProvider;
    private final InstrumentCache instrumentCache;
    @Getter
    private final boolean connectToWebSocket;
    private volatile boolean isConnected;
    // True from rebuild() until the rebuilt ticker's first onConnected; makes the WS_RESUBSCRIBE log honest.
    private volatile boolean rebuildPending;
    // ReentrantLock instead of synchronized to avoid pinning virtual threads to carrier threads.
    // synchronized pins because intrinsic monitors are tied to the OS thread's stack frame;
    // ReentrantLock uses LockSupport.park() which the JVM recognizes as a virtual thread yield point.
    private final ReentrantLock tokenLock = new ReentrantLock();
    // Serialises rebuild(); separate from tokenLock so onConnected never contends with it.
    private final ReentrantLock rebuildLock = new ReentrantLock();
    private final List<Long> tokensToSubscribe;
    private final List<Long> subscribedTokens;
    @Setter
    private volatile OnTicks onTickerArrivalListener;
    @Setter
    private volatile OnOrderUpdate onOrderUpdateListener;
    // Seam for tests: how a KiteTicker is built from the SDK session.
    private Function<KiteConnect, KiteTicker> tickerFactory = kc -> new KiteTicker(kc.getAccessToken(), kc.getApiKey());

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

    public void initialize(KiteConnect kiteSdk) {
        if (!connectToWebSocket) {
            return;
        }
        // Blocking lock: an initialize racing a rebuild must wait, else both swap tickerProvider and orphan one.
        // rebuild() does not call initialize(); it calls retireCurrentTicker()/startTicker() directly under the same lock.
        rebuildLock.lock();
        try {
            if (tickerProvider != null) {
                log.info("WS_REINITIALIZE: tearing down existing ticker");
                retireCurrentTicker();
            }
            rebuildPending = false;
            startTicker(kiteSdk);
        } finally {
            rebuildLock.unlock();
        }
    }

    private void startTicker(KiteConnect kiteSdk) {
        log.info("Initialising websocket...");
        this.tickerProvider = tickerFactory.apply(kiteSdk);
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

    @SuppressWarnings("PMD.CompareObjectsWithEquals") // identity check is intended: detect callbacks from a superseded ticker
    private void addWebSocketListeners(OnTicks onTickerArrivalListener, OnOrderUpdate onOrderUpdateListener) {
        final KiteTicker ticker = tickerProvider;
        tickerProvider.setOnConnectedListener(() -> {
            tokenLock.lock();
            try {
                if (ticker != tickerProvider) {
                    log.info("WS_RESUBSCRIBE: stale-ticker onConnected ignored");
                    return;
                }
                boolean reconnect = !subscribedTokens.isEmpty();
                boolean rebuilt = rebuildPending;
                rebuildPending = false;
                sendAllKnownTokens(reconnect, rebuilt);
                isConnected = true;
            } finally {
                tokenLock.unlock();
            }
        });

        tickerProvider.setOnDisconnectedListener(() -> {
            if (ticker != tickerProvider) {
                log.info("WS_RESUBSCRIBE: stale-ticker onDisconnected ignored");
                return;
            }
            if (ticker.isConnectionOpen()) {
                log.info("WS_DISCONNECT_IGNORED: current socket still open");
                return;
            }
            isConnected = false;
            log.info("disconnected");
        });

        tickerProvider.setOnOrderUpdateListener(order -> {
            if (ticker != tickerProvider) {
                log.debug("WS_STALE_TICKER: order update from replaced ticker dropped");
                return;
            }
            if (onOrderUpdateListener != null) {
                onOrderUpdateListener.onOrderUpdate(order);
            }
        });
        tickerProvider.setOnTickerArrivalListener(ticks -> {
            if (ticker != tickerProvider) {
                log.debug("WS_STALE_TICKER: ticks from replaced ticker dropped");
                return;
            }
            if (onTickerArrivalListener != null) {
                onTickerArrivalListener.onTicks(ticks);
            }
        });

        tickerProvider.setTryReconnection(true);
        try {
            tickerProvider.setMaximumRetries(MAX_RECONNECTION_RETRIES);
            tickerProvider.setMaximumRetryInterval(MAX_RETRY_INTERVAL_SECONDS);
        } catch (KiteException e) {
            log.error("Exception while setting retries", e);
            throw new InitialisationException("Error initializing kite web socket", e);
        }
    }

    /**
     * Subscribes the de-duplicated union of already-subscribed and queued tokens on the current
     * ticker, sets full mode, and records the union as subscribed. Caller must hold {@code tokenLock}.
     * The Kite SDK's own re-subscribe wrapper is not relied on (D-23-01).
     */
    private void sendAllKnownTokens(boolean reconnect, boolean rebuilt) {
        LinkedHashSet<Long> union = new LinkedHashSet<>(subscribedTokens);
        union.addAll(tokensToSubscribe);
        ArrayList<Long> all = new ArrayList<>(union);
        tickerProvider.subscribe(new ArrayList<>(all));
        tickerProvider.setMode(new ArrayList<>(all), KiteTicker.modeFull);
        subscribedTokens.clear();
        subscribedTokens.addAll(all);
        tokensToSubscribe.clear();
        log.info(resubscribeMessage(all.size(), reconnect, rebuilt));
    }

    /** Builds the WS_RESUBSCRIBE line; the boot grep prefix is unchanged, rebuild=true is appended only after a rebuild. */
    static String resubscribeMessage(int tokenCount, boolean reconnect, boolean rebuilt) {
        String base = "WS_RESUBSCRIBE: " + tokenCount + " tokens (reconnect=" + reconnect + ")";
        return rebuilt ? base + " rebuild=true" : base;
    }

    /** Re-sends every known token on the current socket, if connected. No-op when the websocket is disabled. */
    public void forceResubscribe() {
        if (!connectToWebSocket) {
            return;
        }
        tokenLock.lock();
        try {
            if (tickerProvider == null || !isConnected) {
                log.warn("WS_RESUBSCRIBE: skipped, websocket not connected");
                return;
            }
            sendAllKnownTokens(true, false);
        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * Tears down the current ticker and builds a fresh one. All known tokens are queued so the new
     * ticker's onConnected subscribes them. Stored listeners are re-attached by {@link #initialize}.
     */
    public void rebuild(KiteConnect kiteSdk) {
        if (!connectToWebSocket) {
            return;
        }
        if (!rebuildLock.tryLock()) {
            log.info("WS_RESUBSCRIBE: rebuild already in flight, skipping");
            return;
        }
        try {
            retireCurrentTicker();
            rebuildPending = true;
            startTicker(kiteSdk);
        } finally {
            rebuildLock.unlock();
        }
    }

    /**
     * Queues every known token for the next ticker, marks the socket disconnected and stops the
     * current ticker (auto-reconnect off, then disconnect) so no parallel live feed survives.
     */
    private void retireCurrentTicker() {
        KiteTicker old;
        tokenLock.lock();
        try {
            LinkedHashSet<Long> union = new LinkedHashSet<>(subscribedTokens);
            union.addAll(tokensToSubscribe);
            subscribedTokens.clear();
            tokensToSubscribe.clear();
            tokensToSubscribe.addAll(union);
            isConnected = false;
            old = tickerProvider;
        } finally {
            tokenLock.unlock();
        }
        if (old != null) {
            try {
                old.setTryReconnection(false);
                old.disconnect();
            } catch (RuntimeException e) {
                log.warn("WS_RESUBSCRIBE: stopping old ticker failed, continuing", e);
            }
        }
    }

    void setTickerFactory(Function<KiteConnect, KiteTicker> tickerFactory) {
        this.tickerFactory = tickerFactory;
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
