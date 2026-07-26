package com.vish.fno.reader.core;

import com.vish.fno.model.order.StrikePolicy;
import com.vish.fno.reader.model.InstrumentSummary;
import com.vish.fno.reader.model.KiteOpenOrder;
import com.vish.fno.reader.util.OptionPriceUtils;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Instrument;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.OrderResponse;
import com.zerodhatech.models.Position;
import com.zerodhatech.ticker.OnOrderUpdate;
import com.zerodhatech.ticker.OnTicks;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.vish.fno.util.FnoConstants.BANKEX;
import static com.vish.fno.util.FnoConstants.MINUTE;
import static com.vish.fno.util.FnoConstants.NIFTY_50;
import static com.vish.fno.util.FnoConstants.NIFTY_BANK;
import static com.vish.fno.util.FnoConstants.SENSEX;
import static com.vish.fno.util.time.TimeUtils.getClosingTime;
import static com.vish.fno.util.time.TimeUtils.getOpeningTime;

@Slf4j
@SuppressWarnings("PMD.TooManyStaticImports")
public class KiteService {

    /** Shared PMD suppression key — the literal appears on several deliberately-broad catches. */
    private static final String SUPPRESS_GENERIC_CATCH = "PMD.AvoidCatchingGenericException";
    private static final List<String> defaultIndices = List.of(NIFTY_50, NIFTY_BANK, BANKEX, SENSEX);

    private final KiteSession session;
    private final KiteOrderExecutor orderExecutor;
    private final HistoricalDataProvider dataProvider;
    private final InstrumentCache instrumentCache;
    private final KiteWebSocket kiteWebSocket;
    private boolean itmOptionsAppended;

    public KiteService(String apiSecret,
                       String apiKey,
                       String userId,
                       List<String> nifty100Symbols,
                       boolean placeOrders,
                       boolean connectToWebSocket) {
        this.session = new KiteSession(apiKey, userId, apiSecret, placeOrders);
        this.instrumentCache = new InstrumentCache(nifty100Symbols, session);
        this.dataProvider = new HistoricalDataProvider(session, instrumentCache);
        this.orderExecutor = new KiteOrderExecutor(session, instrumentCache);
        this.kiteWebSocket = new KiteWebSocket(connectToWebSocket, instrumentCache);
    }

    public void authenticate(String requestToken) {
        session.authenticate(requestToken, () -> {
            for (String index : defaultIndices) {
                appendAllOptionsForIndex(index);
            }
            kiteWebSocket.initialize(session.getKiteSdk());
        });
    }

    public boolean isInitialised() {
        return session.isInitialised();
    }

    public Optional<HistoricalData> getEntireDayHistoricalData(Date fromDate, Date toDate, String symbol, String interval) {
        return dataProvider.getHistoricalData(fromDate, toDate, symbol, interval);
    }

    /**
     * Retrieves historical market data with optional continuous contract support.
     *
     * <p>This method calls the Kite Connect API to fetch historical candlestick data.
     * The continuous parameter enables access to expired futures/options contracts data
     * by stitching together data from multiple contract expiries.
     *
     * @param from Start date for historical data
     * @param to End date for historical data
     * @param symbol Trading symbol (instrument token will be resolved internally)
     * @param interval Data interval (minute, day, etc.)
     * @param continuous Enable continuous contract mode for futures/options
     * @return HistoricalData object containing candlestick data, empty if unavailable
     */
    public Optional<HistoricalData> getHistoricalData(Date from, Date to, String symbol, String interval, boolean continuous) {
        return dataProvider.getHistoricalData(from, to, symbol, interval, continuous);
    }

    public String getITMStock(String indexSymbol, double price, boolean isCall) {
        return OptionPriceUtils.getITMStock(indexSymbol, price, isCall, instrumentCache.getInstruments());
    }

    public String getOTMStock(String indexSymbol, double price, boolean isCall) {
        return OptionPriceUtils.getOTMStock(indexSymbol, price, isCall, instrumentCache.getInstruments());
    }

    /**
     * Resolve option symbol based on the given strike policy.
     * For production, maps policy to ITM/OTM/ATM resolution.
     * BacktestKiteService overrides this with DynamicStrikeResolver.
     */
    /**
     * Resolve the option symbol for an entry.
     *
     * <p><b>ADR 0062 — SHADOW MODE.</b> The {@code switch} below does not implement
     * {@link StrikePolicy}'s documented contract: it maps all five values onto two floor/ceiling
     * helpers that take no offset, so {@code ATM} resolves one strike in-the-money and
     * {@code ITM_2} / {@code OTM_2} resolve identically to their {@code _1} counterparts. Measured
     * 2026-07-24: prod strike moneyness sat in (0, 1.03] strike-intervals for every order
     * regardless of policy, against the backtest's [−0.49, +1.52]; the two rules disagree on 51.5%
     * of orders.
     *
     * <p>Correcting it changes the strike of <b>every production option order</b>, so this method
     * computes the corrected symbol, logs it when it differs, and <b>still returns the current
     * one</b>. Soak the {@code STRIKE_POLICY_SHADOW} lines for at least five sessions, then delete
     * the legacy branch and return {@code corrected}.
     */
    public String getOptionStock(String indexSymbol, double price, boolean isCall, StrikePolicy policy) {
        String legacy = switch (policy) {
            case ITM_1, ITM_2 -> getITMStock(indexSymbol, price, isCall);
            case OTM_1, OTM_2 -> getOTMStock(indexSymbol, price, isCall);
            case ATM -> getITMStock(indexSymbol, price, isCall);
        };
        logStrikePolicyShadow(indexSymbol, price, isCall, policy, legacy);
        return legacy;
    }

    /**
     * Never allowed to affect the returned symbol — a fault in the shadow path must not disturb
     * order placement, so the whole computation is guarded.
     */
    @SuppressWarnings(SUPPRESS_GENERIC_CATCH) // deliberate: see the javadoc above —
    // this is a diagnostics-only shadow path and ANY fault in it, including an unchecked one from
    // the instrument cache, must be swallowed rather than propagate into order placement.
    private void logStrikePolicyShadow(String indexSymbol, double price, boolean isCall,
                                       StrikePolicy policy, String legacy) {
        try {
            String corrected = OptionPriceUtils.getStrikeByPolicy(
                    indexSymbol, price, isCall, policy, instrumentCache.getInstruments());
            if (corrected != null && !corrected.isBlank() && !corrected.equals(legacy)) {
                log.info("STRIKE_POLICY_SHADOW: index={} price={} leg={} policy={} legacy={} corrected={} (ADR 0062 — legacy still used)",
                        indexSymbol, price, isCall ? "CE" : "PE", policy, legacy, corrected);
            }
        } catch (RuntimeException e) {
            log.warn("STRIKE_POLICY_SHADOW failed for index={} policy={}: {}", indexSymbol, policy, e.getMessage());
        }
    }

    public void setOnTickerArrivalListener(OnTicks onTickerArrivalListener) {
        if (onTickerArrivalListener == null) {
            return;
        }
        this.kiteWebSocket.setOnTickerArrivalListener(onTickerArrivalListener);
    }

    public void setOnOrderUpdateListener(OnOrderUpdate onOrderUpdateListener) {
        if (onOrderUpdateListener == null) {
            return;
        }
        this.kiteWebSocket.setOnOrderUpdateListener(onOrderUpdateListener);
    }

    public Optional<Long> getInstrument(String symbol) {
        return instrumentCache.getInstrument(symbol);
    }

    public String getSymbol(long token) {
        return instrumentCache.getSymbol(token);
    }

    public void appendWebSocketSymbolsList(List<String> symbols, boolean addFutures) {
        kiteWebSocket.appendWebSocketSymbolsList(symbols, addFutures);
    }

    public List<InstrumentSummary> getFilteredInstruments() {
        return instrumentCache.getAllInstruments();
    }

    public boolean isExpiryDayForOption(String optionSymbol, Date date) {
        return instrumentCache.isExpiryDayForOption(optionSymbol, date);
    }

    public boolean isExpiryDayForIndex(String indexName, Date date) {
        return instrumentCache.isExpiryDayForIndex(indexName, date);
    }

    public OrderResponse placeOptionOrder(OrderParams orderParams) {
        return orderExecutor.placeOptionOrder(orderParams);
    }

    public Optional<KiteOpenOrder> buyOrder(String symbol, int orderSize, String tag, boolean isPlaceOrder) {
        return orderExecutor.buyOrder(symbol, orderSize, tag, isPlaceOrder);
    }

    public Optional<KiteOpenOrder> sellOrder(String symbol, int orderSize, String tag, boolean isPlaceOrder) {
//        TODO: log at end of session
//        if (log.isDebugEnabled()) {
//            orderExecutor.logExistingOrdersAndPositions(symbol, tag);
//        }
        return orderExecutor.sellOrder(symbol, orderSize, tag, isPlaceOrder);
    }

    public Order cancelOrder(String orderId, String variety) {
        return orderExecutor.cancelOrder(orderId, variety);
    }

    public List<Order> getOrders() {
        return orderExecutor.getOrders();
    }

    public Map<String, List<Position>> getPositions() {
        return orderExecutor.getPositions();
    }

    @SuppressWarnings(SUPPRESS_GENERIC_CATCH)
    public void appendIndexITMOptions() {
        if (kiteWebSocket.isConnectToWebSocket() && !itmOptionsAppended) {
            try {
                List<String> indicesITMOptionSymbols = getDefaultOptionSymbols();
                appendWebSocketSymbolsList(indicesITMOptionSymbols, false);
            } catch (Exception e) {
                log.error("Failed to get the ITM option symbols, appending : {}", itmOptionsAppended, e);
                return;
            }
        }
        itmOptionsAppended = true;
    }

    @SuppressWarnings(SUPPRESS_GENERIC_CATCH)
    public void appendAllOptionsForIndex(String indexSymbol) {
        if (kiteWebSocket.isConnectToWebSocket()) {
            try {
                List<String> optionSymbols = OptionPriceUtils.getAllOptionSymbols(
                    indexSymbol,
                    instrumentCache.getInstruments()
                );
                log.info("Adding {} {} option symbols to WebSocket subscription list", optionSymbols.size(), indexSymbol);
                kiteWebSocket.appendWebSocketSymbolsList(optionSymbols, false);
            } catch (Exception e) {
                log.error("Failed to get {} option symbols", indexSymbol, e);
            }
        }
    }

    public List<Long> getSubscribedWebSocketTokens() {
        return kiteWebSocket.getSubscribedTokens();
    }

    public int getSubscribedWebSocketTokensCount() {
        return kiteWebSocket.getSubscribedTokensCount();
    }

    public boolean isSymbolSubscribed(String symbol) {
        return kiteWebSocket.isSymbolSubscribed(symbol);
    }

    @SuppressWarnings(SUPPRESS_GENERIC_CATCH)
    public List<String> getAllOptionSymbols(String indexSymbol) {
        try {
            return OptionPriceUtils.getAllOptionSymbols(indexSymbol, instrumentCache.getInstruments());
        } catch (Exception e) {
            log.error("Failed to get option symbols for {}: {}", indexSymbol, e.getMessage());
            return List.of();
        }
    }

    public List<Instrument> getInstruments() {
        return instrumentCache.getInstruments();
    }

    public int getInstrumentCacheSize() {
        return instrumentCache.getInstrumentMapSize();
    }

    public Optional<Integer> getLotSizeFromFuture(String indexName) {
        return instrumentCache.getLotSizeFromFuture(indexName);
    }

    public Map<String, Integer> getAllFutureLotSizeInfo() {
        return instrumentCache.getAllFutureLotSizeInfo();
    }

    private List<String> getDefaultOptionSymbols() {
        List<String> indexOptionSymbols = new ArrayList<>();
        for (String index : defaultIndices) {
            identifyStrikePriceAndAppend(indexOptionSymbols, index);
        }
        return indexOptionSymbols;
    }

    private void identifyStrikePriceAndAppend(List<String> indexOptionSymbols, String index) {
        getEntireDayHistoricalData(getOpeningTime(), getClosingTime(), index, MINUTE)
                .ifPresent(niftyData -> appendOptionSymbols(niftyData, indexOptionSymbols, index));
    }

    private void appendOptionSymbols(HistoricalData data, List<String> indicesOptionSymbols, String index) {
        if (data.dataArrayList.isEmpty()) {
            log.warn("no data received, is the market open ?");
            return;
        }
        double openPrice = data.dataArrayList.get(0).open;
        indicesOptionSymbols.add(getITMStock(index, openPrice, true));
        indicesOptionSymbols.add(getITMStock(index, openPrice, false));
        indicesOptionSymbols.add(getOTMStock(index, openPrice, true));
        indicesOptionSymbols.add(getOTMStock(index, openPrice, false));
    }
}
