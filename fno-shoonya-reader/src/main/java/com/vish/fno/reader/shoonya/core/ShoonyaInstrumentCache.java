package com.vish.fno.reader.shoonya.core;

import com.vish.fno.model.Exchange;
import com.vish.fno.model.InstrumentType;
import com.vish.fno.reader.shoonya.model.ShoonyaInstrument;
import com.vish.fno.reader.shoonya.util.ShoonyaHttpClient;
import com.vish.fno.reader.shoonya.util.ShoonyaInstrumentFileUtils;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.vish.fno.util.FnoConstants.INDEX_TO_DERIVATIVE;

@Slf4j
class ShoonyaInstrumentCache {

    private static final List<String> EXCHANGES_TO_DOWNLOAD = List.of("NFO", "NSE", "BFO", "BSE");

    private final ShoonyaHttpClient httpClient;
    private final Set<String> nifty100Symbols;
    private final ReentrantLock initLock = new ReentrantLock();
    private volatile CacheData cache;

    record SymbolInfo(long token, String exchange) {}

    private record CacheData(
        List<ShoonyaInstrument> filteredInstruments,
        Map<String, SymbolInfo> symbolInfoMap,
        Map<Long, String> tokenToSymbolMap
    ) {}

    ShoonyaInstrumentCache(List<String> nifty100Symbols, ShoonyaHttpClient httpClient) {
        this.nifty100Symbols = new HashSet<>(nifty100Symbols);
        this.httpClient = httpClient;
    }

    private void ensureInitialized() {
        if (cache != null) {
            return;
        }
        initLock.lock();
        try {
            if (cache == null) {
                initializeInstruments();
            }
        } finally {
            initLock.unlock();
        }
    }

    List<ShoonyaInstrument> getInstruments() {
        ensureInitialized();
        return Collections.unmodifiableList(cache.filteredInstruments());
    }

    Optional<Long> getInstrument(String symbol) {
        if (symbol == null) {
            return Optional.empty();
        }
        ensureInitialized();
        SymbolInfo info = cache.symbolInfoMap().get(symbol.toUpperCase(Locale.ENGLISH));
        return info != null ? Optional.of(info.token()) : Optional.empty();
    }

    String getExchangeForSymbol(String symbol) {
        if (symbol == null) {
            return Exchange.NFO.getCode();
        }
        ensureInitialized();
        SymbolInfo info = cache.symbolInfoMap().get(symbol.toUpperCase(Locale.ENGLISH));
        if (info == null) {
            log.warn("Exchange not found for symbol: {}, defaulting to NFO", symbol);
            return Exchange.NFO.getCode();
        }
        return info.exchange();
    }

    String getSymbol(long token) {
        ensureInitialized();
        return cache.tokenToSymbolMap().get(token);
    }

    Optional<Integer> getLotSizeFromFuture(String indexName) {
        if (indexName == null) {
            return Optional.empty();
        }
        String derivativeName = INDEX_TO_DERIVATIVE.getOrDefault(indexName, indexName);
        return getInstruments().stream()
                .filter(i -> isFutureType(i.instrumentType()))
                .filter(i -> derivativeName.equalsIgnoreCase(i.symbol()))
                .findFirst()
                .map(ShoonyaInstrument::lotSize);
    }

    Map<String, Integer> getAllFutureLotSizeInfo() {
        List<ShoonyaInstrument> instruments = getInstruments();
        Map<String, String> reverseMap = INDEX_TO_DERIVATIVE.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        return instruments.stream()
                .filter(i -> isFutureType(i.instrumentType()))
                .filter(i -> i.symbol() != null)
                .collect(Collectors.toMap(
                        i -> reverseMap.getOrDefault(i.symbol(), i.symbol()),
                        ShoonyaInstrument::lotSize,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));
    }

    boolean isExpiryDayForOption(String optionSymbol, Date currentDate) {
        List<ShoonyaInstrument> matching = getInstruments().stream()
                .filter(i -> i.tradingSymbol().equals(optionSymbol))
                .toList();

        if (matching.size() == 1 && matching.get(0).expiry() != null) {
            return isSameDayByExpiryString(matching.get(0).expiry(), currentDate);
        }
        log.error("Cannot find option: {} in Shoonya instrument cache. Found: {}", optionSymbol, matching.size());
        return false;
    }

    boolean isExpiryDayForIndex(String indexName, Date currentDate) {
        if (indexName == null || currentDate == null) {
            return false;
        }
        String derivativeName = INDEX_TO_DERIVATIVE.getOrDefault(indexName, indexName);
        return getInstruments().stream()
                .filter(i -> derivativeName.equalsIgnoreCase(i.symbol()))
                .filter(i -> InstrumentType.CE.getCode().equals(i.optionType())
                        || InstrumentType.PE.getCode().equals(i.optionType()))
                .anyMatch(i -> i.expiry() != null && isSameDayByExpiryString(i.expiry(), currentDate));
    }

    List<String> getAllOptionSymbols(String indexSymbol) {
        String symbolsName = INDEX_TO_DERIVATIVE.getOrDefault(indexSymbol, indexSymbol);
        List<ShoonyaInstrument> instruments = getInstruments();

        return instruments.stream()
                .filter(i -> symbolsName.equalsIgnoreCase(i.symbol()))
                .filter(i -> Exchange.NFO.matches(i.exchange()) || Exchange.BFO.matches(i.exchange()))
                .filter(i -> InstrumentType.CE.getCode().equals(i.optionType())
                        || InstrumentType.PE.getCode().equals(i.optionType()))
                .map(ShoonyaInstrument::tradingSymbol)
                .toList();
    }

    int getInstrumentMapSize() {
        CacheData data = cache;
        return data != null ? data.tokenToSymbolMap().size() : 0;
    }

    private void initializeInstruments() {
        List<ShoonyaInstrument> allInstruments = ShoonyaInstrumentFileUtils.downloadAndParseInstruments(
                httpClient, EXCHANGES_TO_DOWNLOAD);

        if (allInstruments.isEmpty()) {
            throw new IllegalStateException("Shoonya instrument cache initialization failed — no instruments loaded");
        }

        List<ShoonyaInstrument> filtered = filterInstruments(allInstruments);
        Map<String, SymbolInfo> symbolInfoMap = buildSymbolInfoMap(filtered);
        Map<Long, String> tokenToSymbolMap = buildTokenToSymbolMap(symbolInfoMap);

        log.info("Shoonya instrument cache: {} total, {} filtered", allInstruments.size(), filtered.size());

        this.cache = new CacheData(filtered, symbolInfoMap, tokenToSymbolMap);
    }

    private List<ShoonyaInstrument> filterInstruments(List<ShoonyaInstrument> allInstruments) {
        return allInstruments.stream()
                .filter(i -> i.symbol() != null)
                .filter(this::isRelevantExchange)
                .filter(this::isInTrackingList)
                .toList();
    }

    private boolean isInTrackingList(ShoonyaInstrument i) {
        return nifty100Symbols.contains(i.tradingSymbol())
                || nifty100Symbols.contains(i.symbol());
    }

    private boolean isRelevantExchange(ShoonyaInstrument i) {
        return Exchange.NSE.matches(i.exchange())
                || (Exchange.NFO.matches(i.exchange()) && i.expiry() != null)
                || Exchange.BSE.matches(i.exchange())
                || (Exchange.BFO.matches(i.exchange()) && i.expiry() != null);
    }

    private Map<String, SymbolInfo> buildSymbolInfoMap(List<ShoonyaInstrument> filtered) {
        return filtered.stream()
                .collect(Collectors.toMap(
                        i -> i.tradingSymbol().toUpperCase(Locale.ENGLISH),
                        i -> new SymbolInfo(i.token(), i.exchange()),
                        (existing, replacement) -> existing,
                        TreeMap::new));
    }

    private Map<Long, String> buildTokenToSymbolMap(Map<String, SymbolInfo> symbolInfoMap) {
        return symbolInfoMap.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getValue().token(), Map.Entry::getKey));
    }

    private static boolean isFutureType(String instrumentType) {
        return "FUTIDX".equals(instrumentType) || "FUTSTK".equals(instrumentType);
    }

    private static boolean isSameDayByExpiryString(String expiryStr, Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH);
        String dateStr = sdf.format(date).toUpperCase(Locale.ENGLISH);
        return expiryStr.toUpperCase(Locale.ENGLISH).equals(dateStr);
    }
}
