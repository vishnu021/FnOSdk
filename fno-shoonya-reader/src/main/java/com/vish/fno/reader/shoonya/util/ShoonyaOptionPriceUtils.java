package com.vish.fno.reader.shoonya.util;

import com.vish.fno.model.Exchange;
import com.vish.fno.model.InstrumentType;
import com.vish.fno.reader.shoonya.model.ShoonyaInstrument;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.vish.fno.util.FnoConstants.INDEX_TO_DERIVATIVE;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ShoonyaOptionPriceUtils {

    /** Shoonya expiry format, e.g. {@code "28-APR-2026"}. Month is parsed case-insensitively. */
    private static final DateTimeFormatter EXPIRY_FORMAT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("dd-MMM-yyyy")
            .toFormatter(Locale.ENGLISH);

    public static String getITMStock(String indexSymbol, double price, boolean isCall,
                                     List<ShoonyaInstrument> instruments) {
        String itmSymbol = findStrike(indexSymbol, price, isCall, isCall, instruments);
        if (itmSymbol == null || itmSymbol.isBlank()) {
            log.error("Unable to find ITM symbol for index: {}, price: {}, call: {}", indexSymbol, price, isCall);
        }
        return itmSymbol;
    }

    public static String getOTMStock(String indexSymbol, double price, boolean isCall,
                                     List<ShoonyaInstrument> instruments) {
        String otmSymbol = findStrike(indexSymbol, price, isCall, !isCall, instruments);
        if (otmSymbol == null || otmSymbol.isBlank()) {
            log.error("Unable to find OTM symbol for index: {}, price: {}, call: {}", indexSymbol, price, isCall);
        }
        return otmSymbol;
    }

    public static List<String> getAllOptionSymbols(String indexSymbol, List<ShoonyaInstrument> instruments) {
        String symbolsName = getOptionPrefix(indexSymbol);

        Optional<List<ShoonyaInstrument>> calls = getEarliestExpiryInstruments(
                instruments, symbolsName, InstrumentType.CE.getCode());
        Optional<List<ShoonyaInstrument>> puts = getEarliestExpiryInstruments(
                instruments, symbolsName, InstrumentType.PE.getCode());

        List<String> allOptionSymbols = new ArrayList<>();
        calls.ifPresent(c -> c.forEach(i -> allOptionSymbols.add(i.tradingSymbol())));
        puts.ifPresent(p -> p.forEach(i -> allOptionSymbols.add(i.tradingSymbol())));

        log.info("Found {} option symbols for {}: {} CALLs, {} PUTs",
                allOptionSymbols.size(), indexSymbol,
                calls.map(List::size).orElse(0),
                puts.map(List::size).orElse(0));

        return allOptionSymbols;
    }

    private static String findStrike(String indexSymbol, double price, boolean isCall,
                                     boolean selectLastBelow, List<ShoonyaInstrument> instruments) {
        String symbolsName = getOptionPrefix(indexSymbol);
        String instrumentType = isCall ? InstrumentType.CE.getCode() : InstrumentType.PE.getCode();
        return getEarliestExpiryInstruments(instruments, symbolsName, instrumentType)
                .map(expiryInstruments -> resolveStrike(expiryInstruments, price, selectLastBelow))
                .orElse("");
    }

    private static String resolveStrike(List<ShoonyaInstrument> instruments, double price,
                                        boolean selectLastBelow) {
        NavigableMap<Long, String> strikeToSymbolMap = new TreeMap<>();
        for (ShoonyaInstrument instrument : instruments) {
            long strike = Math.round(instrument.strikePrice());
            strikeToSymbolMap.putIfAbsent(strike, instrument.tradingSymbol());
        }

        if (selectLastBelow) {
            String result = "";
            for (long strikePrice : strikeToSymbolMap.keySet()) {
                if (strikePrice > price) {
                    break;
                }
                result = strikeToSymbolMap.get(strikePrice);
            }
            return result;
        } else {
            for (long strikePrice : strikeToSymbolMap.keySet()) {
                if (strikePrice > price) {
                    return strikeToSymbolMap.get(strikePrice);
                }
            }
            return "";
        }
    }

    static Optional<List<ShoonyaInstrument>> getEarliestExpiryInstruments(
            List<ShoonyaInstrument> instruments, String symbolName, String optionType) {
        Map<String, List<ShoonyaInstrument>> byExpiry = instruments.stream()
                .filter(i -> symbolName.equalsIgnoreCase(i.symbol()))
                .filter(i -> Exchange.NFO.matches(i.exchange()) || Exchange.BFO.matches(i.exchange()))
                .filter(i -> optionType.equals(i.optionType()))
                .filter(i -> i.expiry() != null)
                .collect(Collectors.groupingBy(ShoonyaInstrument::expiry));

        return byExpiry.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ShoonyaOptionPriceUtils::parseExpiry)))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /**
     * Parse a Shoonya expiry string (e.g. {@code "28-APR-2026"}, case-insensitive month) to a {@link LocalDate}.
     *
     * <p>Expiry must be ordered chronologically, not lexicographically: as strings,
     * {@code "28-APR-2026"} sorts before {@code "28-DEC-2025"} ('A' &lt; 'D'), which would wrongly pick a
     * far expiry as the nearest. Unparseable values return {@link LocalDate#MAX} so a malformed row never
     * sorts as the nearest expiry.
     */
    static LocalDate parseExpiry(String expiry) {
        try {
            return LocalDate.parse(expiry, EXPIRY_FORMAT);
        } catch (DateTimeParseException e) {
            log.warn("Unparseable Shoonya expiry '{}'; sorting it last", expiry);
            return LocalDate.MAX;
        }
    }

    private static String getOptionPrefix(String indexSymbol) {
        return INDEX_TO_DERIVATIVE.getOrDefault(indexSymbol, indexSymbol);
    }
}
