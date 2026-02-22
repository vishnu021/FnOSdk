package com.vish.fno.reader.util;

import com.zerodhatech.models.Instrument;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.vish.fno.util.FnoConstants.BFO;
import static com.vish.fno.util.FnoConstants.CE;
import static com.vish.fno.util.FnoConstants.FUT;
import static com.vish.fno.util.FnoConstants.INDEX_TO_DERIVATIVE;
import static com.vish.fno.util.FnoConstants.NFO;
import static com.vish.fno.util.FnoConstants.PE;

@Slf4j
@SuppressWarnings("PMD.TooManyStaticImports")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OptionPriceUtils {

    public static Optional<String> getNextExpiryFutureSymbol(String symbol, List<Instrument> instruments) {
        String symbolPrefix = getOptionPrefix(symbol);

        Optional<List<Instrument>> earliestExpiryInstrument = getEarliestExpiryInstrument(instruments, symbolPrefix, FUT);

        if (earliestExpiryInstrument.isPresent()) {
            List<Instrument> nextExpiryFuture = earliestExpiryInstrument.get();
            return Optional.of(nextExpiryFuture.get(0).getTradingsymbol());
        }

        log.warn("Cannot find FUTURE symbol for {}", symbol);
        return Optional.empty();
    }

    public static String getITMStock(String indexSymbol, double price, boolean isCall, List<Instrument> instruments) {
        // ITM call = last strike below price; ITM put = first strike above price
        String itmSymbol = findStrike(indexSymbol, price, isCall, isCall, instruments);
        if (itmSymbol == null || itmSymbol.isBlank()) {
            log.error("Unable to find itmSymbol for index: {}, price: {}, call: {}", indexSymbol, price, isCall);
        }
        return itmSymbol;
    }

    public static String getOTMStock(String indexSymbol, double price, boolean isCall, List<Instrument> instruments) {
        // OTM call = first strike above price; OTM put = last strike below price
        String otmSymbol = findStrike(indexSymbol, price, isCall, !isCall, instruments);
        if (otmSymbol == null || otmSymbol.isBlank()) {
            log.error("Unable to find otmSymbol for index: {}, price: {}, call: {}", indexSymbol, price, isCall);
        }
        return otmSymbol;
    }

    private static String findStrike(String indexSymbol, double price, boolean isCall, boolean selectLastBelow,
                                     List<Instrument> instruments) {
        String symbolsName = getOptionPrefix(indexSymbol);
        String instrumentType = isCall ? CE : PE;
        return getEarliestExpiryInstrument(instruments, symbolsName, instrumentType)
                .map(expiryInstruments -> resolveStrike(expiryInstruments, price, selectLastBelow))
                .orElse("");
    }

    private static String resolveStrike(List<Instrument> instruments, double price, boolean selectLastBelow) {
        NavigableMap<Long, String> strikeToSymbolMap = instruments.stream()
                .collect(Collectors.toMap(
                        instrument -> {
                            try {
                                return Long.parseLong(instrument.getStrike());
                            } catch (NumberFormatException e) {
                                log.error("NumberFormatException while parsing the strike price");
                                return null;
                            }
                        },
                        Instrument::getTradingsymbol,
                        (existing, replacement) -> existing,
                        TreeMap::new
                ));

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

    private static Optional<List<Instrument>> getEarliestExpiryInstrument(List<Instrument> instruments, String symbolsName, String instrumentType) {
        final Map<Date, List<Instrument>> indexSymbolsInstruments = instruments.stream()
                .filter(i -> i.getName().toUpperCase(Locale.ENGLISH).equalsIgnoreCase(symbolsName))
                .filter(instrument -> instrument.exchange.equals(NFO) || instrument.exchange.equals(BFO))
                .filter(i -> i.getInstrument_type().equals(instrumentType))
                .collect(Collectors.groupingBy(Instrument::getExpiry));

        return indexSymbolsInstruments.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /**
     * Get ALL option symbols for a given index (both CALL and PUT, all strikes, nearest expiry)
     *
     * @param indexSymbol The index symbol (e.g., "NIFTY 50")
     * @param instruments List of all instruments
     * @return List of all option trading symbols for the index
     */
    public static List<String> getAllOptionSymbols(String indexSymbol, List<Instrument> instruments) {
        String symbolsName = getOptionPrefix(indexSymbol);

        Optional<List<Instrument>> callOptions = getEarliestExpiryInstrument(instruments, symbolsName, CE);
        Optional<List<Instrument>> putOptions = getEarliestExpiryInstrument(instruments, symbolsName, PE);

        List<String> allOptionSymbols = new ArrayList<>();

        callOptions.ifPresent(calls ->
            calls.forEach(instrument -> allOptionSymbols.add(instrument.getTradingsymbol()))
        );

        putOptions.ifPresent(puts ->
            puts.forEach(instrument -> allOptionSymbols.add(instrument.getTradingsymbol()))
        );

        log.info("Found {} option symbols for {}: {} CALLs, {} PUTs",
                 allOptionSymbols.size(), indexSymbol,
                 callOptions.map(List::size).orElse(0),
                 putOptions.map(List::size).orElse(0));

        return allOptionSymbols;
    }

    /**
     * Get the derivative trading symbol prefix for an index.
     * Uses the centralized INDEX_TO_DERIVATIVE mapping from Constants.
     *
     * @param indexSymbol the index name (e.g., "NIFTY 50", "NIFTY BANK")
     * @return the derivative symbol (e.g., "NIFTY", "BANKNIFTY"), or the original symbol if no mapping exists
     */
    private static String getOptionPrefix(String indexSymbol) {
        return INDEX_TO_DERIVATIVE.getOrDefault(indexSymbol, indexSymbol);
    }
}
