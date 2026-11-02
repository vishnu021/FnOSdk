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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.function.Function;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
    // Upper bound on one connect() (ADR 0078 Amendment 5). Healthy connects take ~15 ms; 02-11's broken edge held 49-60 s.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private volatile KiteTicker tickerProvider;
    private final InstrumentCache instrumentCache;
    @Getter
    private final boolean connectToWebSocket;
    private volatile boolean isConnected;
    // True from rebuild() until the rebuilt ticker's first onConnected; makes the WS_RESUBSCRIBE log honest.
    private volatile boolean rebuildPending;
    // The ticker whose connect() was abandoned at the bound; it stays tickerProvider until the next rebuild,
    // so its late callbacks are recognised as stale by identity with this field.
    private volatile KiteTicker abandonedTicker;
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
    // Seam for tests: how long startTicker waits for connect() before abandoning the attempt.
    private Duration connectTimeout = CONNECT_TIMEOUT;

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
        if (!connectWithinBound(tickerProvider)) {
            return;
        }
        log.info("WebSocket connect() called, waiting for onConnected callback");

        tokenLock.lock();
        try {
            tickerProvider.setMode(new ArrayList<>(tokensToSubscribe), KiteTicker.modeLTP);
        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * Runs {@code ticker.connect()} on a virtual thread and waits at most {@link #connectTimeout}. KiteTicker and its
     * nv-websocket-client socket set no connect or read timeout, and {@code disconnect()} is a no-op while CONNECTING,
     * so a handshake the server never answers blocks {@code connect()} indefinitely (02-11: 49.1 s). At the bound the
     * attempt is abandoned (see {@link #abandon}) and its thread interrupted: a blocking socket read is interruptible on
     * a virtual thread only. When the abandoned {@code connect()} returns, its thread stops the ticker. A
     * {@code RuntimeException} from a {@code connect()} that returned in time reaches the caller, as before the bound.
     * Returns {@code false} if the attempt was abandoned.
     */
    private boolean connectWithinBound(KiteTicker ticker) {
        AtomicBoolean settled = new AtomicBoolean();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        long startNanos = System.nanoTime();
        Thread connecting = Thread.ofVirtual().name("ws-connect").start(() -> {
            try {
                ticker.connect();
            } catch (RuntimeException e) {
                failure.set(e);
            } finally {
                if (!settled.compareAndSet(false, true)) {
                    Thread.interrupted(); // clear the abandon interrupt before the cleanup
                    log.info("WS_CONNECT_TIMEOUT: abandoned connect() returned ({}); stopping its ticker",
                            failure.get() == null ? "no exception" : failure.get().toString());
                    stopTicker(ticker);
                }
            }
        });
        if (joinWithinBound(connecting) || !abandon(ticker, settled, startNanos)) {
            RuntimeException thrown = failure.get();
            if (thrown != null) {
                throw thrown;
            }
            return true;
        }
        connecting.interrupt();
        return false;
    }

    private boolean joinWithinBound(Thread connecting) {
        try {
            return connecting.join(connectTimeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("WS_CONNECT_TIMEOUT: the caller was interrupted while waiting for connect()");
            return false;
        }
    }

    /**
     * Decides, under {@code tokenLock}, whether to abandon the attempt — the same lock {@link #subscribeIfCurrent} checks
     * staleness under, so either this ticker's onConnected already ran (the socket is live; {@code connect()} is only
     * unwinding: keep it) or it will see the ticker as abandoned and close it. Also keeps an attempt whose
     * {@code connect()} returned at the same instant. On abandon, auto-reconnect is turned off before the caller's
     * interrupt, else KiteTicker's {@code connect()} catch starts its own reconnect timer (another unbounded connect).
     */
    private boolean abandon(KiteTicker ticker, AtomicBoolean settled, long startNanos) {
        tokenLock.lock();
        try {
            if (isConnected || !settled.compareAndSet(false, true)) {
                return false;
            }
            abandonedTicker = ticker;
        } finally {
            tokenLock.unlock();
        }
        log.warn("WS_CONNECT_TIMEOUT: connect() did not return after {} ms; abandoning the attempt",
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
        ticker.setTryReconnection(false);
        return true;
    }

    /** True for a ticker that was replaced or whose connect was abandoned; its callbacks must not touch state. */
    @SuppressWarnings("PMD.CompareObjectsWithEquals") // identity check is intended: detect callbacks from a superseded ticker
    private boolean isStale(KiteTicker ticker) {
        return ticker != tickerProvider || ticker == abandonedTicker;
    }

    private void addWebSocketListeners(OnTicks onTickerArrivalListener, OnOrderUpdate onOrderUpdateListener) {
        final KiteTicker ticker = tickerProvider;
        tickerProvider.setOnConnectedListener(() -> {
            if (!subscribeIfCurrent(ticker)) {
                // A replaced or abandoned ticker that connected late (e.g. its own Kite reconnect): close it, so it
                // does not stay a second live socket whose ticks are silently dropped.
                stopTicker(ticker);
            }
        });

        tickerProvider.setOnDisconnectedListener(() -> {
            if (isStale(ticker)) {
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
            if (isStale(ticker)) {
                log.debug("WS_STALE_TICKER: order update from replaced ticker dropped");
                return;
            }
            if (onOrderUpdateListener != null) {
                onOrderUpdateListener.onOrderUpdate(order);
            }
        });
        tickerProvider.setOnTickerArrivalListener(ticks -> {
            if (isStale(ticker)) {
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

    /** onConnected for {@code ticker}: subscribes every known token if it is current; false (and a log line) if stale. */
    private boolean subscribeIfCurrent(KiteTicker ticker) {
        tokenLock.lock();
        try {
            if (isStale(ticker)) {
                log.info("WS_RESUBSCRIBE: stale-ticker onConnected ignored");
                return false;
            }
            boolean reconnect = !subscribedTokens.isEmpty();
            boolean rebuilt = rebuildPending;
            rebuildPending = false;
            sendAllKnownTokens(reconnect, rebuilt);
            isConnected = true;
            return true;
        } finally {
            tokenLock.unlock();
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
            stopTicker(old);
        }
    }

    /** Auto-reconnect off, then disconnect (also cancels KiteTicker's pong-check/reconnect timer). Never throws. */
    private void stopTicker(KiteTicker ticker) {
        try {
            ticker.setTryReconnection(false);
            ticker.disconnect();
        } catch (RuntimeException e) {
            log.warn("WS_RESUBSCRIBE: stopping old ticker failed, continuing", e);
        }
    }

    void setTickerFactory(Function<KiteConnect, KiteTicker> tickerFactory) {
        this.tickerFactory = tickerFactory;
    }

    void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
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
