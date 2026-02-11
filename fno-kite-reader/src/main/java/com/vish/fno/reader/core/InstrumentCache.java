package com.vish.fno.reader.core;

import com.vish.fno.reader.util.InstrumentFileUtils;
import com.vish.fno.util.TimeUtils;
import com.zerodhatech.models.Instrument;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
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

    private final KiteService kiteService;
    private final List<String> nifty100Symbols;

    private final Object initLock;
    // Volatile for safe publication in double-checked locking
    private volatile List<Instrument> filteredInstruments;
    private volatile Map<String, Long> symbolMap;
    private volatile Map<Long, String> instrumentMap;
    private volatile Map<String, String> exchangeMap;

    public InstrumentCache(List<String> nifty100Symbols, KiteService kiteService) {
        this.nifty100Symbols = nifty100Symbols;
        this.kiteService = kiteService;
        this.initLock = new Object();
    }

    /**
     * Gets filtered instruments with thread-safe lazy initialization.
     * Uses double-checked locking to minimize synchronization overhead.
     *
     * @return unmodifiable list of instruments
     */
    public List<Instrument> getInstruments() {
        // First check (no locking) - fast path for already initialized
        if (filteredInstruments != null) {
            return Collections.unmodifiableList(filteredInstruments);
        }

        // Synchronize for initialization
        synchronized (initLock) {
            // Second check (with locking) - ensure only one thread initializes
            if (filteredInstruments != null) {
                return Collections.unmodifiableList(filteredInstruments);
            }

            // Perform initialization outside of method-level synchronization
            initializeInstruments();
            return Collections.unmodifiableList(filteredInstruments);
        }
    }

    /**
     * Initializes instrument cache by fetching from Kite API and filtering.
     * Should only be called from synchronized block in getInstruments().
     */
    private void initializeInstruments() {
        // Fetch all instruments (network I/O)
        List<Instrument> allInstruments = kiteService.getAllInstruments();
        InstrumentFileUtils.saveInstrumentCache(allInstruments);

        // Filter instruments
        List<Instrument> filtered = filterInstruments(allInstruments);

        // Build symbol map
        Map<String, Long> symbols = buildSymbolMap(filtered);
        // Build instrument map (reverse of symbol map)
        Map<Long, String> instruments = buildInstrumentMap(symbols);
        // Build exchange map (symbol → exchange)
        Map<String, String> exchanges = buildExchangeMap(filtered);

        InstrumentFileUtils.saveFilteredInstrumentCache(symbols);
        log.info("Filtered instrument count: {}", symbols.size());

        logExpiryDates(filtered);

        // Assign to volatile fields (ensures visibility to other threads)
        this.symbolMap = symbols;
        this.instrumentMap = instruments;
        this.exchangeMap = exchanges;
        this.filteredInstruments = filtered;  // Assign last for happens-before guarantee
    }

    public List<Map<String, String>> getAllInstruments() {
        List<Map<String, String>> allInstrumentData = new ArrayList<>();
        getInstruments().stream()
                .sorted(Comparator.comparing(Instrument::getName))
                .forEach(i -> allInstrumentData.add(
                        Map.of(
                                "exchange", i.getExchange(),
                                "symbol", i.getTradingsymbol(),
                                "expiry", TimeUtils.getStringDate(i.getExpiry()))));
        return allInstrumentData;
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

        return Optional.ofNullable(this.symbolMap.get(symbol.toUpperCase(Locale.ENGLISH)));
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
        if (symbol == null || exchangeMap == null) {
            return NFO;
        }
        String exchange = exchangeMap.get(symbol.toUpperCase(Locale.ENGLISH));
        if (exchange == null) {
            log.warn("Exchange not found for symbol: {}, defaulting to NFO", symbol);
            return NFO;
        }
        return exchange;
    }

    public String getSymbol(long instrument) {
        getInstruments();  // Ensure initialized
        return this.instrumentMap.get(instrument);
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
        return instrumentMap != null ? instrumentMap.size() : 0;
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
     * This is useful for indices where we want the lot size but only have the index name.
     * Futures and options for the same underlying have the same lot size.
     *
     * @param indexName the index name (e.g., "NIFTY 50", "NIFTY BANK", "SENSEX")
     * @return Optional containing lot size from the future contract, or empty if not found
     */
    public Optional<Integer> getLotSizeFromFuture(String indexName) {
        getInstruments();  // Ensure initialized
        if (indexName == null) {
            return Optional.empty();
        }

        String derivativeName = INDEX_TO_DERIVATIVE.getOrDefault(indexName, indexName);

        // Find any future (FUT) instrument for this index
        // Futures have the same lot size regardless of expiry
        return filteredInstruments.stream()
                .filter(i -> FUT.equals(i.getInstrument_type()))
                .filter(i -> derivativeName.equals(i.getName()))
                .findFirst()
                .map(Instrument::getLot_size);
    }

    /**
     * Get lot sizes for all indices with future contracts.
     * Returns a map with index name as key and lot size as value.
     *
     * @return map of index name to lot size
     */
    public Map<String, Integer> getAllFutureLotSizeInfo() {
        getInstruments();

        Map<String, String> reverseMap = INDEX_TO_DERIVATIVE.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getValue,
                        Map.Entry::getKey
                ));

        return filteredInstruments.stream()
                .filter(i -> FUT.equals(i.getInstrument_type()))
                .filter(i -> i.getName() != null)
                .collect(Collectors.toMap(
                        i -> reverseMap.getOrDefault(i.getName(), i.getName()),
                        Instrument::getLot_size,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));
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

    private Map<String, Long> buildSymbolMap(List<Instrument> filtered) {
        return filtered.stream()
                .collect(Collectors.toMap(
                        Instrument::getTradingsymbol,
                        Instrument::getInstrument_token,
                        (token, symbol) -> token,
                        TreeMap::new));
    }

    private Map<Long, String> buildInstrumentMap(Map<String, Long> symbols) {
        return symbols.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    }

    private Map<String, String> buildExchangeMap(List<Instrument> filtered) {
        return filtered.stream()
                .collect(Collectors.toMap(
                        i -> i.getTradingsymbol().toUpperCase(Locale.ENGLISH),
                        Instrument::getExchange,
                        (existing, replacement) -> existing,
                        TreeMap::new));
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
