package com.vish.fno.reader.service;

import com.zerodhatech.models.Instrument;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.vish.fno.util.Constants.*;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OptionPriceUtils {
    private static final String BFO = "BFO";
    private static final String CE = "CE";
    private static final String FUT = "FUT";
    private static final String NFO = "NFO";
    private static final String PE = "PE";

    public static Optional<String> getNextExpiryFutureSymbol(String symbol, List<Instrument> instruments) {
        String symbolPrefix = getOptionPrefix(symbol);

        Optional<List<Instrument>> earliestExpiryInstrument = getEarliestExpiryInstrument(instruments, symbolPrefix, FUT);

        if(earliestExpiryInstrument.isPresent()) {
            List<Instrument> nextExpiryFuture = earliestExpiryInstrument.get();
            return Optional.of(nextExpiryFuture.get(0).getTradingsymbol());
        }

        log.warn("Cannot find FUTURE symbol for {}", symbol);
        return Optional.empty();
    }

    // CPD-OFF
    public static String getITMStock(String indexSymbol, double price, boolean isCall, List<Instrument> instruments) {
        String symbolsName = getOptionPrefix(indexSymbol);

        String instrumentType = isCall ? CE : PE;

        Optional<List<Instrument>> earliestExpiryInstrument = getEarliestExpiryInstrument(instruments, symbolsName, instrumentType);

        AtomicReference<String> itmSymbol = new AtomicReference<>("");
        earliestExpiryInstrument.ifPresent(expiryInstruments -> {

            Map<Long, String> strikeToSymbolMap = expiryInstruments.stream()
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

            if(isCall) {
                for(long strikePrice: strikeToSymbolMap.keySet()) {
                    if(strikePrice > price) {
                        break;
                    }
                    itmSymbol.set(strikeToSymbolMap.get(strikePrice));
                }
            } else {
                for(long strikePrice: strikeToSymbolMap.keySet()) {
                    if(strikePrice > price) {
                        itmSymbol.set(strikeToSymbolMap.get(strikePrice));
                        break;
                    }
                 }
            }
        });

        String itmSymbolValue = itmSymbol.get();

        if (itmSymbolValue == null || itmSymbolValue.isBlank()) {
            log.error("Unable to find itmSymbol for index: {}, price: {}, call: {}", indexSymbol, price, isCall);
        }
        return itmSymbolValue;
    }

    public static String getOTMStock(String indexSymbol, double price, boolean isCall, List<Instrument> instruments) {
        String symbolsName = getOptionPrefix(indexSymbol);

        String instrumentType = isCall ? CE : PE;

        Optional<List<Instrument>> earliestExpiryInstrument = getEarliestExpiryInstrument(instruments, symbolsName, instrumentType);

        AtomicReference<String> otmSymbol = new AtomicReference<>("");
        earliestExpiryInstrument.ifPresent(expiryInstruments -> {

            Map<Long, String> strikeToSymbolMap = expiryInstruments.stream()
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

            if(isCall) {
                for(long strikePrice: strikeToSymbolMap.keySet()) {
                    if(strikePrice > price) {
                        otmSymbol.set(strikeToSymbolMap.get(strikePrice));
                        break;
                    }
                }
            } else {
                for(long strikePrice: strikeToSymbolMap.keySet()) {
                    if(strikePrice > price) {
                        break;
                    }
                    otmSymbol.set(strikeToSymbolMap.get(strikePrice));
                }
            }
        });

        String otmSymbolValue = otmSymbol.get();

        if (otmSymbolValue == null || otmSymbolValue.isBlank()) {
            log.error("Unable to find otmSymbol for index: {}, price: {}, call: {}", indexSymbol, price, isCall);
        }
        return otmSymbolValue;
    }
    // CPD-ON

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

        // Get all CE options (nearest expiry)
        Optional<List<Instrument>> callOptions = getEarliestExpiryInstrument(instruments, symbolsName, CE);

        // Get all PE options (nearest expiry)
        Optional<List<Instrument>> putOptions = getEarliestExpiryInstrument(instruments, symbolsName, PE);

        List<String> allOptionSymbols = new ArrayList<>();

        // Add all CALL symbols
        callOptions.ifPresent(calls ->
            calls.forEach(instrument -> allOptionSymbols.add(instrument.getTradingsymbol()))
        );

        // Add all PUT symbols
        putOptions.ifPresent(puts ->
            puts.forEach(instrument -> allOptionSymbols.add(instrument.getTradingsymbol()))
        );

        log.info("Found {} option symbols for {}: {} CALLs, {} PUTs",
                 allOptionSymbols.size(), indexSymbol,
                 callOptions.map(List::size).orElse(0),
                 putOptions.map(List::size).orElse(0));

        return allOptionSymbols;
    }

    private static String getOptionPrefix(String indexSymbol) {
        return switch (indexSymbol) {
            case NIFTY_BANK -> "BANKNIFTY";
            case NIFTY_50 -> "NIFTY";
            case NIFTY_FIN_SERVICE -> "FINNIFTY";
            default -> indexSymbol;
        };
    }
}
