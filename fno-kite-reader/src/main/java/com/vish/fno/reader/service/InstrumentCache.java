package com.vish.fno.reader.service;

import com.vish.fno.reader.util.InstrumentFileUtils;
import com.vish.fno.util.TimeUtils;
import com.zerodhatech.models.Instrument;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;


/*Only focusing on the 100 stocks of nifty 100 and indices*/
@Slf4j
@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes", "PMD.AvoidSynchronizedAtMethodLevel"})
class InstrumentCache {
    private final static String NSE = "NSE";
    private final static String NFO = "NFO";
    private final static String BFO = "BFO";
    private final static String BSE = "BSE";

    private final KiteService kiteService;
    private final List<String> nifty100Symbols;
    private List<Instrument> filteredInstruments;
    private Map<String, Long> symbolMap;
    private Map<Long, String> instrumentMap;

    public InstrumentCache(List<String> nifty100Symbols, KiteService kiteService) {
        this.nifty100Symbols = nifty100Symbols;
        this.kiteService = kiteService;
    }

    public synchronized List<Instrument> getInstruments() {
        if (filteredInstruments != null) {
            return filteredInstruments;
        }

        List<Instrument> allInstruments = kiteService.getAllInstruments();
        InstrumentFileUtils.saveInstrumentCache(allInstruments);

        filteredInstruments = allInstruments.stream()
                .filter(i -> i.getName() != null)
                .filter(i -> i.getExchange().contentEquals(NSE)
                        || (i.getExchange().contentEquals(NFO) && i.expiry != null)
                        || i.getExchange().contentEquals(BSE)
                        || (i.getExchange().contentEquals(BFO) && i.expiry != null))
                .filter(i ->  nifty100Symbols.contains(i.getTradingsymbol())
                                || nifty100Symbols.contains(i.getName()))
                .toList();

        symbolMap = filteredInstruments.stream()
                .collect(Collectors.toMap(Instrument::getTradingsymbol,
                        Instrument::getInstrument_token, (token, symbol) -> token, TreeMap::new));

        InstrumentFileUtils.saveFilteredInstrumentCache(symbolMap);
        log.info("Filtered instrument count : {}", symbolMap.size());
        log.info("Filtered instrument expiry dates: {}", getExpiryDates());
        instrumentMap = symbolMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
        return filteredInstruments;
    }

    public List<Map<String, String>> getAllInstruments() {
        List<Map<String, String>> allInstrumentData = new ArrayList<>();
        getInstruments().stream()
                .sorted(Comparator.comparing(Instrument::getName))
                .forEach( i -> allInstrumentData.add(
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
                .map(TimeUtils::getStringDate).collect(Collectors.toSet());
    }

    public Long getInstrument(String script) {
        getInstruments();
        if(script == null) {
            return null;
        }

        return this.symbolMap.get(script.toUpperCase(Locale.ENGLISH));
    }

    public String getSymbol(long instrument) {
        getInstruments();
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
                .collect(Collectors.toMap(Instrument::getTradingsymbol, Instrument::getName, (k1, k2) ->  k1, LinkedHashMap::new ));
    }

    public List<Instrument> getInstrumentForSymbol(String symbol) {
        return getInstruments().stream()
                .filter(i -> i.getTradingsymbol() != null)
                .filter(i -> i.getTradingsymbol().equals(symbol))
                .toList();
    }

    public boolean isExpiryDayForOption(String optionSymbol, Date currentDate) {
        List<Instrument> optionSymbolInstrument = getInstruments().stream().filter(i -> i.getTradingsymbol().equals(optionSymbol)).toList();

        if(optionSymbolInstrument.size() == 1) {
            Date expiryDay = optionSymbolInstrument.get(0).getExpiry();
            return isSameDay(currentDate, expiryDay);
        }
        log.error("Cannot find option : {} in the instrument cache. {}", optionSymbol, optionSymbolInstrument);
        return false;
    }

    private boolean isSameDay(Date date1, Date date2) {
        LocalDate localDate1 = date1.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate localDate2 = date2.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return localDate1.equals(localDate2);
    }
}
