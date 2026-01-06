package com.vish.fno.reader.service;

import com.vish.fno.reader.util.InstrumentFileUtils;
import com.vish.fno.util.TimeUtils;
import com.zerodhatech.models.Instrument;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.vish.fno.util.FnoConstants.BFO;
import static com.vish.fno.util.FnoConstants.BSE;
import static com.vish.fno.util.FnoConstants.FUT;
import static com.vish.fno.util.FnoConstants.INDEX_TO_DERIVATIVE;
import static com.vish.fno.util.FnoConstants.NFO;
import static com.vish.fno.util.FnoConstants.NSE;


/**
 * Cache for Kite instruments, focusing on Nifty 100 stocks and indices.
 * Thread-safe lazy initialization using double-checked locking pattern.
 */
@Slf4j
@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes", "PMD.TooManyStaticImports"})
class InstrumentCache {

    private final KiteService kiteService;
    private final List<String> nifty100Symbols;
    private final Object initLock = new Object();

    // Volatile for safe publication in double-checked locking
    private volatile List<Instrument> filteredInstruments;
    private volatile Map<String, Long> symbolMap;
    private volatile Map<Long, String> instrumentMap;

    public InstrumentCache(List<String> nifty100Symbols, KiteService kiteService) {
        this.nifty100Symbols = nifty100Symbols;
        this.kiteService = kiteService;
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
        List<Instrument> filtered = allInstruments.stream()
                .filter(i -> i.getName() != null)
                .filter(i -> i.getExchange().contentEquals(NSE)
                        || (i.getExchange().contentEquals(NFO) && i.expiry != null)
                        || i.getExchange().contentEquals(BSE)
                        || (i.getExchange().contentEquals(BFO) && i.expiry != null))
                .filter(i -> nifty100Symbols.contains(i.getTradingsymbol())
                        || nifty100Symbols.contains(i.getName()))
                .toList();

        // Build symbol map
        Map<String, Long> symbols = filtered.stream()
                .collect(Collectors.toMap(
                        Instrument::getTradingsymbol,
                        Instrument::getInstrument_token,
                        (token, symbol) -> token,
                        TreeMap::new));

        InstrumentFileUtils.saveFilteredInstrumentCache(symbols);
        log.info("Filtered instrument count: {}", symbols.size());

        // Build instrument map (reverse of symbol map)
        Map<Long, String> instruments = symbols.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        // Log expiry dates (before assignment to avoid race condition)
        Set<String> expiryDates = filtered.stream()
                .map(Instrument::getExpiry)
                .filter(Objects::nonNull)
                .map(TimeUtils::getStringDate)
                .collect(Collectors.toSet());
        log.info("Filtered instrument expiry dates: {}", expiryDates);

        // Assign to volatile fields (ensures visibility to other threads)
        this.symbolMap = symbols;
        this.instrumentMap = instruments;
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

    public Long getInstrument(String script) {
        getInstruments();  // Ensure initialized
        if (script == null) {
            return null;
        }

        return this.symbolMap.get(script.toUpperCase(Locale.ENGLISH));
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

    private boolean isSameDay(Date date1, Date date2) {
        LocalDate localDate1 = date1.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate localDate2 = date2.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return localDate1.equals(localDate2);
    }

    /**
     * Get lot size for an index by searching for its future contract.
     * This is useful for indices where we want the lot size but only have the index name.
     * Futures and options for the same underlying have the same lot size.
     *
     * @param indexName the index name (e.g., "NIFTY 50", "NIFTY BANK", "SENSEX")
     * @return lot size from the future contract, or null if not found
     */
    public Integer getLotSizeFromFuture(String indexName) {
        getInstruments();  // Ensure initialized
        if (indexName == null) {
            return null;
        }

        String derivativeName = INDEX_TO_DERIVATIVE.getOrDefault(indexName, indexName);

        // Find any future (FUT) instrument for this index
        // Futures have the same lot size regardless of expiry
        return filteredInstruments.stream()
                .filter(i -> FUT.equals(i.getInstrument_type()))
                .filter(i -> derivativeName.equals(i.getName()))
                .findFirst()
                .map(Instrument::getLot_size)
                .orElse(null);
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
}
