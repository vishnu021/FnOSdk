package com.vish.fno.reader.service;

import com.vish.fno.reader.util.InstrumentFileUtils;
import com.vish.fno.util.TimeUtils;
import com.zerodhatech.models.Instrument;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Cache for Kite instruments, focusing on Nifty 100 stocks and indices.
 * Thread-safe lazy initialization using double-checked locking pattern.
 */
@Slf4j
@SuppressWarnings("PMD.AvoidThrowingRawExceptionTypes")
class InstrumentCache {
    private static final String NSE = "NSE";
    private static final String NFO = "NFO";
    private static final String BFO = "BFO";
    private static final String BSE = "BSE";

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
}
