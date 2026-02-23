package com.vish.fno.reader.core;

import com.vish.fno.reader.model.InstrumentSummary;
import com.vish.fno.reader.util.InstrumentFileUtils;
import com.vish.fno.util.TimeUtils;
import com.zerodhatech.models.Instrument;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.vish.fno.util.FnoConstants.BFO;
import static com.vish.fno.util.FnoConstants.BSE;
import static com.vish.fno.util.FnoConstants.FUT;
import static com.vish.fno.util.FnoConstants.INDEX_TO_DERIVATIVE;
import static com.vish.fno.util.FnoConstants.NFO;
import static com.vish.fno.util.FnoConstants.NSE;
import static com.vish.fno.util.TimeUtils.getLocalDateFromDate;

/**
 * Cache for Kite instruments, focusing on Nifty 100 stocks and indices.
 * Thread-safe lazy initialization using double-checked locking pattern.
 */
@Slf4j
@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes", "PMD.TooManyStaticImports"})
class InstrumentCache {

    private final KiteSession session;
    private final List<String> nifty100Symbols;
    private final Object initLock;
    private volatile CacheData cache;

    /** Holds both token and exchange for a trading symbol — replaces separate symbolMap + exchangeMap. */
    record SymbolInfo(long token, String exchange) {}

    /** Immutable holder for all cached instrument data. Single volatile reference eliminates fragile field ordering. */
    private record CacheData(
        List<Instrument> filteredInstruments,
        Map<String, SymbolInfo> symbolInfoMap,     // symbol → (token, exchange)
        Map<Long, String> tokenToSymbolMap          // token → symbol (reverse lookup)
    ) {}

    public InstrumentCache(List<String> nifty100Symbols, KiteSession session) {
        this.nifty100Symbols = nifty100Symbols;
        this.session = session;
        this.initLock = new Object();
    }

    /**
     * Gets filtered instruments with thread-safe lazy initialization.
     * Uses double-checked locking to minimize synchronization overhead.
     *
     * @return unmodifiable list of instruments
     */
    public List<Instrument> getInstruments() {
        CacheData data = cache;
        if (data != null) {
            return Collections.unmodifiableList(data.filteredInstruments());
        }

        synchronized (initLock) {
            data = cache;
            if (data != null) {
                return Collections.unmodifiableList(data.filteredInstruments());
            }

            initializeInstruments();
            return Collections.unmodifiableList(cache.filteredInstruments());
        }
    }

    /**
     * Gets instruments for the earliest expiry matching the given name and instrument type.
     * Computed on-the-fly from filteredInstruments (O(n) scan — only used in tests, not on production hot paths).
     *
     * @param name the instrument name (e.g., "NIFTY", "BANKNIFTY")
     * @param instrumentType the instrument type (e.g., "CE", "PE", "FUT")
     * @return Optional containing the list of instruments for the earliest expiry, or empty
     */
    public Optional<List<Instrument>> getEarliestExpiryInstruments(String name, String instrumentType) {
        List<Instrument> instruments = getInstruments();
        String upperName = name.toUpperCase(Locale.ENGLISH);

        List<Instrument> matching = instruments.stream()
                .filter(i -> upperName.equals(i.getName()))
                .filter(i -> instrumentType.equals(i.getInstrument_type()))
                .filter(i -> NFO.equals(i.getExchange()) || BFO.equals(i.getExchange()))
                .filter(i -> i.getExpiry() != null)
                .toList();

        if (matching.isEmpty()) {
            return Optional.empty();
        }

        Date earliestExpiry = matching.stream()
                .map(Instrument::getExpiry)
                .min(Comparator.naturalOrder())
                .orElse(null);

        List<Instrument> result = matching.stream()
                .filter(i -> i.getExpiry().equals(earliestExpiry))
                .toList();

        return Optional.of(result);
    }

    /**
     * Initializes instrument cache by fetching from Kite API and filtering.
     * Should only be called from synchronized block in getInstruments().
     */
    private void initializeInstruments() {
        // Fetch all instruments (network I/O) via KiteSession
        List<Instrument> allInstruments = session.executeWithLock(() -> {
            try {
                List<Instrument> instruments = session.getKiteSdk().getInstruments();
                log.info("Loaded instrument cache from Kite server");
                return instruments;
            } catch (JSONException | IOException | com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException e) {
                log.error("Failed to load instruments from Kite server", e);
                return null;
            }
        }, "getAllInstruments");

        InstrumentFileUtils.saveInstrumentCache(allInstruments);

        List<Instrument> filtered = filterInstruments(allInstruments);
        Map<String, SymbolInfo> symbolInfoMap = buildSymbolInfoMap(filtered);
        Map<Long, String> tokenToSymbolMap = buildTokenToSymbolMap(symbolInfoMap);

        // Extract Map<String, Long> for file cache (preserves existing cache file format)
        Map<String, Long> symbolTokenMap = symbolInfoMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().token(), (a, b) -> a, TreeMap::new));

        InstrumentFileUtils.saveFilteredInstrumentCache(symbolTokenMap);
        log.info("Filtered instrument count: {}", symbolInfoMap.size());

        logExpiryDates(filtered);

        // Single atomic assignment — immutable record ensures safe publication
        this.cache = new CacheData(filtered, symbolInfoMap, tokenToSymbolMap);
    }

    public List<InstrumentSummary> getAllInstruments() {
        return getInstruments().stream()
                .sorted(Comparator.comparing(Instrument::getName))
                .map(i -> new InstrumentSummary(
                        i.getExchange(),
                        i.getTradingsymbol(),
                        TimeUtils.getStringDate(i.getExpiry())))
                .toList();
    }

    public Set<String> getExpiryDates() {
        return getInstruments().stream()
                .map(Instrument::getExpiry)
                .filter(Objects::nonNull)
                .map(TimeUtils::getStringDate)
                .collect(Collectors.toSet());
    }

    public Optional<Long> getInstrument(String symbol) {
        getInstruments();  // Ensure initialized
        if (symbol == null) {
            return Optional.empty();
        }

        SymbolInfo info = cache.symbolInfoMap().get(symbol.toUpperCase(Locale.ENGLISH));
        return info != null ? Optional.of(info.token()) : Optional.empty();
    }

    /**
     * Get the exchange (NFO or BFO) for a given trading symbol.
     * Falls back to NFO if the symbol is not found in the cache.
     *
     * @param symbol the trading symbol (e.g., "BANKEX26FEB68000CE")
     * @return the exchange string (e.g., "NFO" or "BFO")
     */
    public String getExchangeForSymbol(String symbol) {
        getInstruments();  // Ensure initialized
        if (symbol == null) {
            return NFO;
        }
        SymbolInfo info = cache.symbolInfoMap().get(symbol.toUpperCase(Locale.ENGLISH));
        if (info == null) {
            log.warn("Exchange not found for symbol: {}, defaulting to NFO", symbol);
            return NFO;
        }
        return info.exchange();
    }

    public String getSymbol(long instrument) {
        getInstruments();  // Ensure initialized
        return cache.tokenToSymbolMap().get(instrument);
    }

    public Set<String> getAllSymbols() {
        return getInstruments().stream()
                .map(Instrument::getName)
                .collect(Collectors.toSet());
    }

    /**
     * Get the size of the instrument map (token to symbol mapping).
     * Useful for diagnostics to verify instrument cache is populated.
     *
     * @return size of instrument map, 0 if not initialized
     */
    public int getInstrumentMapSize() {
        CacheData data = cache;
        return data != null ? data.tokenToSymbolMap().size() : 0;
    }

    public Map<String, String> getFilteredSymbols() {
        return getInstruments().stream()
                .sorted(Comparator.comparing(Instrument::getName))
                .collect(Collectors.toMap(
                        Instrument::getTradingsymbol,
                        Instrument::getName,
                        (k1, k2) -> k1,
                        LinkedHashMap::new));
    }

    public List<Instrument> getInstrumentForSymbol(String symbol) {
        return getInstruments().stream()
                .filter(i -> i.getTradingsymbol() != null)
                .filter(i -> i.getTradingsymbol().equals(symbol))
                .toList();
    }

    public boolean isExpiryDayForOption(String optionSymbol, Date currentDate) {
        List<Instrument> optionSymbolInstrument = getInstruments().stream()
                .filter(i -> i.getTradingsymbol().equals(optionSymbol))
                .toList();

        if (optionSymbolInstrument.size() == 1) {
            Date expiryDay = optionSymbolInstrument.get(0).getExpiry();
            return isSameDay(currentDate, expiryDay);
        }
        log.error("Cannot find option: {} in the instrument cache. Found: {}",
                optionSymbol, optionSymbolInstrument);
        return false;
    }

    /**
     * Get lot size for an index by searching for its future contract.
     *
     * @param indexName the index name (e.g., "NIFTY 50", "NIFTY BANK", "SENSEX")
     * @return Optional containing lot size from the future contract, or empty if not found
     */
    public Optional<Integer> getLotSizeFromFuture(String indexName) {
        if (indexName == null) {
            return Optional.empty();
        }

        String derivativeName = INDEX_TO_DERIVATIVE.getOrDefault(indexName, indexName);

        return getInstruments().stream()
                .filter(i -> FUT.equals(i.getInstrument_type()))
                .filter(i -> derivativeName.equals(i.getName()))
                .findFirst()
                .map(Instrument::getLot_size);
    }

    /**
     * Get lot sizes for all indices with future contracts.
     *
     * @return map of index name to lot size
     */
    public Map<String, Integer> getAllFutureLotSizeInfo() {
        List<Instrument> instruments = getInstruments();

        Map<String, String> reverseMap = INDEX_TO_DERIVATIVE.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getValue,
                        Map.Entry::getKey
                ));

        return instruments.stream()
                .filter(i -> FUT.equals(i.getInstrument_type()))
                .filter(i -> i.getName() != null)
                .collect(Collectors.toMap(
                        i -> reverseMap.getOrDefault(i.getName(), i.getName()),
                        Instrument::getLot_size,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));
    }

    /**
     * Resolves an expired futures symbol to the nearest available futures contract token.
     * Finds the base name by matching against known derivative names in the instrument cache,
     * then returns the token of the earliest-expiry FUT contract for that name.
     *
     * @param expiredFutSymbol the expired futures symbol (e.g., "NIFTY24AUGFUT")
     * @return Optional containing the instrument token of the nearest futures contract, or empty
     */
    public Optional<Long> resolveNearestFutureToken(String expiredFutSymbol) {
        if (expiredFutSymbol == null || !expiredFutSymbol.endsWith(FUT)) {
            return Optional.empty();
        }

        List<Instrument> instruments = getInstruments();

        // Find base name by matching against known FUT instrument names (longest match wins)
        Optional<String> baseName = instruments.stream()
                .filter(i -> FUT.equals(i.getInstrument_type()))
                .map(Instrument::getName)
                .filter(Objects::nonNull)
                .distinct()
                .filter(expiredFutSymbol::startsWith)
                .max(Comparator.comparingInt(String::length));

        if (baseName.isEmpty()) {
            return Optional.empty();
        }

        String resolvedBaseName = baseName.get();
        return instruments.stream()
                .filter(i -> FUT.equals(i.getInstrument_type()))
                .filter(i -> resolvedBaseName.equals(i.getName()))
                .filter(i -> i.getExpiry() != null)
                .min(Comparator.comparing(Instrument::getExpiry))
                .map(instrument -> {
                    log.info("Resolved expired symbol {} → {} (token: {})",
                            expiredFutSymbol, instrument.getTradingsymbol(), instrument.getInstrument_token());
                    return instrument.getInstrument_token();
                });
    }

    private List<Instrument> filterInstruments(List<Instrument> allInstruments) {
        return allInstruments.stream()
                .filter(i -> i.getName() != null)
                .filter(this::isNSEOrBSEFNO)
                .filter(this::isInTheTrackingList)
                .toList();
    }

    private boolean isInTheTrackingList(Instrument i) {
        return nifty100Symbols.contains(i.getTradingsymbol())
                || nifty100Symbols.contains(i.getName());
    }

    private boolean isNSEOrBSEFNO(Instrument i) {
        return i.getExchange().contentEquals(NSE)
                || (i.getExchange().contentEquals(NFO) && i.expiry != null)
                || i.getExchange().contentEquals(BSE)
                || (i.getExchange().contentEquals(BFO) && i.expiry != null);
    }

    private Map<String, SymbolInfo> buildSymbolInfoMap(List<Instrument> filtered) {
        return filtered.stream()
                .collect(Collectors.toMap(
                        i -> i.getTradingsymbol().toUpperCase(Locale.ENGLISH),
                        i -> new SymbolInfo(i.getInstrument_token(), i.getExchange()),
                        (existing, replacement) -> existing,
                        TreeMap::new));
    }

    private Map<Long, String> buildTokenToSymbolMap(Map<String, SymbolInfo> symbolInfoMap) {
        return symbolInfoMap.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getValue().token(), Map.Entry::getKey));
    }

    private void logExpiryDates(List<Instrument> filtered) {
        Set<String> expiryDates = filtered.stream()
                .map(Instrument::getExpiry)
                .filter(Objects::nonNull)
                .map(TimeUtils::getStringDate)
                .collect(Collectors.toSet());
        log.info("Filtered instrument expiry dates: {}", expiryDates);
    }

    private boolean isSameDay(Date date1, Date date2) {
        final LocalDate localDate1 = getLocalDateFromDate(date1);
        final LocalDate localDate2 = getLocalDateFromDate(date2);
        return localDate1.equals(localDate2);
    }
}
