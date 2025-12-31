package com.vish.fno.reader.service;

import com.vish.fno.reader.model.KiteOpenOrder;
import com.vish.fno.util.JsonUtils;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.*;
import com.zerodhatech.ticker.OnOrderUpdate;
import com.zerodhatech.ticker.OnTicks;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;

import java.io.IOException;
import java.util.*;

import static com.vish.fno.reader.util.OrderUtils.createMarketOrderWithParameters;
import static com.vish.fno.util.Constants.*;
import static com.vish.fno.util.JsonUtils.getFormattedObject;
import static com.vish.fno.util.TimeUtils.getClosingTime;
import static com.vish.fno.util.TimeUtils.getOpeningTime;

@Slf4j
@SuppressWarnings({"PMD.LooseCoupling", "PMD.TooManyStaticImports"})
public class KiteService {
    private final static List<String> defaultIndices = List.of(NIFTY_50, NIFTY_BANK, BANKEX, SENSEX);
    private static final String EQUITY = "equity";
    private static final String NET = "net";
    private static final String DAY = "day";

    @Getter(AccessLevel.PACKAGE)
    private final KiteConnect kiteSdk;
    private final String apiSecret;
    private final boolean placeOrders;
    private final InstrumentCache instrumentCache;
    private final HistoricalDataService historicalDataService;
    private final KiteWebSocket kiteWebSocket;
    @Getter
    private boolean initialised;
    private boolean itmOptionsAppended;

    public KiteService(String apiSecret,
                       String apiKey,
                       String userId,
                       List<String> nifty100Symbols,
                       boolean placeOrders,
                       boolean connectToWebSocket) {
        this.kiteSdk = kiteSdk(apiKey, userId);
        this.apiSecret = apiSecret;
        this.placeOrders = placeOrders;
        this.instrumentCache = new InstrumentCache(nifty100Symbols, this);
        this.historicalDataService = new HistoricalDataService(this, this.instrumentCache);
        this.kiteWebSocket = new KiteWebSocket(connectToWebSocket, instrumentCache);
    }

    public void authenticate(String requestToken) {
        try {
            User user = kiteSdk.generateSession(requestToken, apiSecret);
            kiteSdk.setAccessToken(user.accessToken);
            kiteSdk.setPublicToken(user.publicToken);
            addSessionExpiryHook();

            // Add all option symbols for NIFTY 50 and NIFTY BANK BEFORE WebSocket initialization
            // The OnConnectedListener will subscribe to them automatically when WebSocket connects
            appendAllOptionsForIndex(NIFTY_50);
            appendAllOptionsForIndex(NIFTY_BANK);

            kiteWebSocket.initialize(kiteSdk);
            Margin margins = kiteSdk.getMargins(EQUITY);
            log.info("available_cash={}", margins.available.cash);
            log.info("utilised_debits={}", margins.utilised.debits);
            initialised = true;
        } catch (KiteException | IOException e) {
            log.error("Error while Initialising KiteService", e);
        }
    }

    public HistoricalData getEntireDayHistoricalData(Date fromDate, Date toDate, String symbol, String interval) {
        return historicalDataService.getEntireDayHistoricalData(fromDate, toDate, symbol, interval);
    }

    /**
     * Retrieves historical market data with optional continuous contract support.
     *
     * <p>This method calls the Kite Connect API to fetch historical candlestick data.
     * The continuous parameter enables access to expired futures/options contracts data
     * by stitching together data from multiple contract expiries.
     *
     * <p><strong>Continuous Parameter Details:</strong>
     * <ul>
     * <li><strong>false</strong>: Returns data only for the active contract period specified by the symbol</li>
     * <li><strong>true</strong>: Enables continuous contract mode - allows fetching historical data for
     *     expired contracts using the current month's instrument token by simply changing the date range</li>
     * </ul>
     *
     * <p><strong>How Continuous Mode Works:</strong>
     * <ul>
     * <li>Use the current month's instrument token (e.g., NIFTYJAN24FUT)</li>
     * <li>Set continuous=true and adjust the from/to dates to access previous months' data</li>
     * <li>The API automatically maps the request to the appropriate expired contracts</li>
     * <li>Returns seamless data without gaps between contract rollovers</li>
     * </ul>
     *
     * <p><strong>Use Cases:</strong>
     * <ul>
     * <li>Long-term backtesting across multiple contract cycles</li>
     * <li>Trend analysis spanning several years of futures data</li>
     * <li>Strategy development requiring continuous price series</li>
     * </ul>
     *
     * <p><strong>API Limitations:</strong>
     * <ul>
     * <li>Continuous mode typically provides only day candle data for expired contracts</li>
     * <li>Intraday intervals may not be available for expired contracts</li>
     * <li>Data availability depends on Kite Connect's historical data retention policy</li>
     * </ul>
     *
     * @param from Start date for historical data
     * @param to End date for historical data
     * @param symbol Trading symbol (instrument token will be resolved internally)
     * @param interval Data interval (minute, day, etc.)
     * @param continuous Enable continuous contract mode for futures/options
     * @return HistoricalData object containing candlestick data, null if unavailable
     */
    public HistoricalData getHistoricalData(Date from, Date to, String symbol, String interval, boolean continuous) {
        return historicalDataService.getHistoricalData(from, to, symbol, interval, continuous);
    }

    public String getITMStock(String indexSymbol, double price, boolean isCall) {
        return OptionPriceUtils.getITMStock(indexSymbol, price, isCall, instrumentCache.getInstruments());
    }

    public String getOTMStock(String indexSymbol, double price, boolean isCall) {
        return OptionPriceUtils.getOTMStock(indexSymbol, price, isCall, instrumentCache.getInstruments());
    }

    public void setOnTickerArrivalListener(OnTicks onTickerArrivalListener) {
        if(onTickerArrivalListener == null) {
            return;
        }
        this.kiteWebSocket.setOnTickerArrivalListener(onTickerArrivalListener);
    }

    public void setOnOrderUpdateListener(OnOrderUpdate onOrderUpdateListener) {
        if(onOrderUpdateListener == null) {
            return;
        }
        this.kiteWebSocket.setOnOrderUpdateListener(onOrderUpdateListener);
    }

    public Long getInstrument(String symbol) {
        return instrumentCache.getInstrument(symbol);
    }

    public String getSymbol(long token) {
        return instrumentCache.getSymbol(token);
    }

    public void appendWebSocketSymbolsList(List<String> symbols, boolean addFutures) {
        kiteWebSocket.appendWebSocketSymbolsList(symbols, addFutures);
    }

    public List<Map<String, String>> getFilteredInstruments() {
        return instrumentCache.getAllInstruments();
    }

    List<Instrument> getAllInstruments() {
        List<Instrument> instruments = null;
        try {
            instruments = kiteSdk.getInstruments();
            log.info("Loaded instrument cache from Kite server");
        } catch (JSONException | IOException | KiteException e) {
            log.error("Failed to load instruments from Kite server", e);
        }
        return instruments;
    }

    public boolean isExpiryDayForOption(String optionSymbol, Date date) {
        return instrumentCache.isExpiryDayForOption(optionSymbol, date);
    }

    public Order placeOptionOrder(OrderParams orderParams) {
        Order order = null;
        try {
            log.info("placing order with params : {}", orderParams);
            order = kiteSdk.placeOrder(orderParams, Constants.VARIETY_REGULAR);
            log.info("order id: {}", order.orderId);
        } catch (KiteException ke) {
            log.error("KiteException occurred while placing order, code: {}, message: {}, order: {}",
                    ke.code, ke.message, getFormattedObject(orderParams), ke);
        } catch (JSONException | IOException e) {
            log.error("Error occurred while placing order", e);
        }
        return order;
    }

    public Optional<KiteOpenOrder> buyOrder(String symbol, int orderSize, String tag, boolean isPlaceOrder) {
        log.info("Creating buy order with quantity : {}, symbol : {} , isPlaceOrder: {}", orderSize, symbol, isPlaceOrder);
        return placeOrder(symbol, orderSize, tag, Constants.TRANSACTION_TYPE_BUY, isPlaceOrder);
    }

    // TODO: verify there is an existing order before placing a sell order
    public Optional<KiteOpenOrder> sellOrder(String symbol, int orderSize, String tag, boolean isPlaceOrder) {
        logExistingOrdersAndPositions(symbol, tag);
        log.info("Creating sell order with quantity: {}, symbol: {}, tag: {}, isPlaceOrder: {}", orderSize, symbol, tag, isPlaceOrder);
        return placeOrder(symbol, orderSize, tag, Constants.TRANSACTION_TYPE_SELL, isPlaceOrder);
    }

    public List<Order> getOrders() {
        try {
            return this.kiteSdk.getOrders();
        } catch (KiteException e) {
            log.error("Failed to get orders, error code: {}, error message: {}", e.code, e.message, e);
        } catch (IOException e) {
            log.error("Failed to get orders, error: {}", e.getMessage(), e);
        }
        return List.of();
    }

    public Map<String, List<Position>> getPositions() {
        try {
            return this.kiteSdk.getPositions();
        } catch (KiteException e) {
            log.error("Failed to get positions, error code: {}, error message: {}", e.code, e.message, e);
        } catch (IOException e) {
            log.error("Failed to get positions, error: {}", e.getMessage(), e);
        }
        return Map.of();
    }

    private Optional<KiteOpenOrder> placeOrder(String symbol, int orderSize, String tag, String transactionType, boolean isPlaceOrder) {
        if(!isInitialised()) {
            log.warn("Not placing order as the kite service is not initialized yet.");
            return Optional.of(buildUnsuccessfulKiteOrder());
        }

        if (!isPlaceOrder) {
            log.warn("Not placing orders as it is not enabled or allowed currently");
            return Optional.of(buildSuccessfulKiteTestOrder());
        }

        if (!placeOrders) {
            log.warn("Not placing orders as it is turned off by configuration");
            return Optional.of(buildSuccessfulKiteTestOrder());
        }
        Order order;
        try {
            OrderParams orderParams = createMarketOrderWithParameters(symbol, orderSize, transactionType, tag);
            order = kiteSdk.placeOrder(orderParams, Constants.VARIETY_REGULAR);
            log.info("order placed successfully with id: {}", order.orderId);
        } catch (KiteException e) {
            log.error("KiteException occurred while placing order, code: {}, message: {}", e.code, e.message);
            return Optional.of(new KiteOpenOrder(null, false, e.code, e.message));
        } catch (JSONException | IOException e) {
            log.error("Error occurred while placing order", e);
            return Optional.of(buildUnsuccessfulKiteOrder());
        }
        return Optional.of(new KiteOpenOrder(order, true, null, null));
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void appendIndexITMOptions() {
        if(kiteWebSocket.isConnectToWebSocket() && !itmOptionsAppended) {
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

    /**
     * Appends ALL option symbols for a given index to WebSocket subscription list
     * WARNING: This subscribes to ALL strikes (100+ symbols per index)
     *
     * @param indexSymbol The index symbol (e.g., NIFTY_50, NIFTY_BANK, NIFTY_FIN_SERVICE)
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void appendAllOptionsForIndex(String indexSymbol) {
        if(kiteWebSocket.isConnectToWebSocket()) {
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

    @NotNull
    private KiteOpenOrder buildUnsuccessfulKiteOrder() {
        return new KiteOpenOrder(null, false, null, null);
    }

    @NotNull
    private KiteOpenOrder buildSuccessfulKiteTestOrder() {
        return new KiteOpenOrder(null, true, null, null);
    }


    private KiteConnect kiteSdk(String apiKey, String userId) {
        KiteConnect kiteConnect = new KiteConnect(apiKey, true);
        kiteConnect.setUserId(userId);
        kiteConnect.setSessionExpiryHook(() -> log.info("session expired"));
        return kiteConnect;
    }

    private List<String> getDefaultOptionSymbols() {
        List<String> indexOptionSymbols = new ArrayList<>();
        for(String index: defaultIndices) {
            identifyStrikePriceAndAppend(indexOptionSymbols, index);
        }
        return indexOptionSymbols;
    }

    private void identifyStrikePriceAndAppend(List<String> indexOptionSymbols, String index) {
        HistoricalData niftyData = getEntireDayHistoricalData(getOpeningTime(), getClosingTime(), index, MINUTE);
        appendOptionSymbols(niftyData, indexOptionSymbols, index);
    }

    private void appendOptionSymbols(HistoricalData data, List<String> indicesOptionSymbols, String index) {
        if(data.dataArrayList.isEmpty()) {
            log.warn("no data received, is the market open ?");
            return;
        }
        double openPrice = data.dataArrayList.get(0).open;
        indicesOptionSymbols.add(getITMStock(index, openPrice, true));
        indicesOptionSymbols.add(getITMStock(index, openPrice, false));
        indicesOptionSymbols.add(getOTMStock(index, openPrice, true));
        indicesOptionSymbols.add(getOTMStock(index, openPrice, false));
    }

    private void addSessionExpiryHook() {
        kiteSdk.setSessionExpiryHook(() -> log.info("kite session expired"));
    }

    private void logExistingOrdersAndPositions(String symbol, String tag) {
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

        log.info("Existing orders for same symbol: {}", orders);
        log.info("Existing netPositions for same symbol: {}", netPositions);
        log.info("Existing dayPositions for same symbol: {}", dayPositions);
    }

    public List<Instrument> getInstruments() {
        return instrumentCache.getInstruments();
    }
}
