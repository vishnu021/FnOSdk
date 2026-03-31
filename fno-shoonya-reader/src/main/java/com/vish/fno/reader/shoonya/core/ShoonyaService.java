package com.vish.fno.reader.shoonya.core;

import com.vish.fno.model.order.StrikePolicy;
import com.vish.fno.reader.shoonya.model.ShoonyaInstrument;
import com.vish.fno.reader.shoonya.model.ShoonyaOpenOrder;
import com.vish.fno.reader.shoonya.model.ShoonyaOrder;
import com.vish.fno.reader.shoonya.util.ShoonyaOptionPriceUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class ShoonyaService {

    private final ShoonyaSession session;
    private final ShoonyaOrderExecutor orderExecutor;
    private final ShoonyaInstrumentCache instrumentCache;

    public ShoonyaService(String userId, String password, String vendorCode,
                          String apiSecret, String totpSecret, String imei,
                          List<String> nifty100Symbols, boolean placeOrders) {
        this.session = new ShoonyaSession(userId, password, vendorCode, apiSecret,
                totpSecret, imei, placeOrders);
        this.instrumentCache = new ShoonyaInstrumentCache(nifty100Symbols, session.getHttpClient());
        this.orderExecutor = new ShoonyaOrderExecutor(session, instrumentCache);
    }

    // --- Auth ---

    public void authenticate() {
        session.authenticate();
    }

    public boolean isInitialised() {
        return session.isInitialised();
    }

    // --- Orders ---

    public Optional<ShoonyaOpenOrder> buyOrder(String symbol, int orderSize, String tag, boolean isPlaceOrder) {
        return orderExecutor.buyOrder(symbol, orderSize, tag, isPlaceOrder);
    }

    public Optional<ShoonyaOpenOrder> sellOrder(String symbol, int orderSize, String tag, boolean isPlaceOrder) {
        return orderExecutor.sellOrder(symbol, orderSize, tag, isPlaceOrder);
    }

    public List<ShoonyaOrder> getOrders() {
        return orderExecutor.getOrders();
    }

    // --- Instruments ---

    public String getITMStock(String indexSymbol, double price, boolean isCall) {
        return ShoonyaOptionPriceUtils.getITMStock(indexSymbol, price, isCall, instrumentCache.getInstruments());
    }

    public String getOTMStock(String indexSymbol, double price, boolean isCall) {
        return ShoonyaOptionPriceUtils.getOTMStock(indexSymbol, price, isCall, instrumentCache.getInstruments());
    }

    public String getOptionStock(String indexSymbol, double price, boolean isCall, StrikePolicy policy) {
        return switch (policy) {
            case ITM_1, ITM_2 -> getITMStock(indexSymbol, price, isCall);
            case OTM_1, OTM_2 -> getOTMStock(indexSymbol, price, isCall);
            case ATM -> getITMStock(indexSymbol, price, isCall);
        };
    }

    public Optional<Long> getInstrument(String symbol) {
        return instrumentCache.getInstrument(symbol);
    }

    public String getSymbol(long token) {
        return instrumentCache.getSymbol(token);
    }

    public List<String> getAllOptionSymbols(String indexSymbol) {
        return instrumentCache.getAllOptionSymbols(indexSymbol);
    }

    public Optional<Integer> getLotSizeFromFuture(String indexName) {
        return instrumentCache.getLotSizeFromFuture(indexName);
    }

    public Map<String, Integer> getAllFutureLotSizeInfo() {
        return instrumentCache.getAllFutureLotSizeInfo();
    }

    public boolean isExpiryDayForOption(String optionSymbol, Date date) {
        return instrumentCache.isExpiryDayForOption(optionSymbol, date);
    }

    public boolean isExpiryDayForIndex(String indexName, Date date) {
        return instrumentCache.isExpiryDayForIndex(indexName, date);
    }

    public List<ShoonyaInstrument> getInstruments() {
        return instrumentCache.getInstruments();
    }

    public int getInstrumentCacheSize() {
        return instrumentCache.getInstrumentMapSize();
    }
}
