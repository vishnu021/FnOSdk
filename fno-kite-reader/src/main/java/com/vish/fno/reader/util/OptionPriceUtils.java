package com.vish.fno.reader.util;

import com.vish.fno.model.Exchange;
import com.vish.fno.model.InstrumentType;
import com.vish.fno.model.order.StrikePolicy;
import com.zerodhatech.models.Instrument;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.vish.fno.util.FnoConstants.INDEX_TO_DERIVATIVE;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OptionPriceUtils {

    public static Optional<String> getNextExpiryFutureSymbol(String symbol, List<Instrument> instruments) {
        String symbolPrefix = getOptionPrefix(symbol);

        Optional<List<Instrument>> earliestExpiryInstrument = getEarliestExpiryInstrument(instruments, symbolPrefix, InstrumentType.FUT.getCode());

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

    /**
     * The single entry point production uses to pick an option strike, gated on ADR 0062.
     *
     * <p>{@code correctionEnabled = true} applies {@link #getStrikeByPolicy}, i.e. the contract
     * {@link StrikePolicy} documents. {@code false} reproduces the legacy rule that has been in
     * production since inception: all five policy values collapse onto {@link #getITMStock} /
     * {@link #getOTMStock}, which are floor/ceiling selections against spot and take no offset.
     *
     * <p>The flag exists because correcting this changes the strike of <b>every production option
     * order</b>. Measured 2026-09-05 over 78 matched prod/backtest pairs, the two rules disagree on
     * 49% of orders <b>even when both see an identical spot to the paisa</b> — the divergence is
     * entirely this rule, not a price or depth difference. Moneyness was separately measured to be
     * P&amp;L-neutral (r = -0.009 over 1,693 orders), so this is a correctness change, not a
     * performance one.
     *
     * <p>Turning the flag off must reproduce the historical strike exactly — otherwise the rollback
     * is not a rollback. {@code StrikePolicyEntryWiringTest} pins both sides.
     */
    public static String getStrikeForEntry(String indexSymbol, double price, boolean isCall,
                                           StrikePolicy policy, List<Instrument> instruments,
                                           boolean correctionEnabled) {
        if (correctionEnabled) {
            return getStrikeByPolicy(indexSymbol, price, isCall, policy, instruments);
        }
        return switch (policy) {
            case ITM_1, ITM_2, ATM -> getITMStock(indexSymbol, price, isCall, instruments);
            case OTM_1, OTM_2 -> getOTMStock(indexSymbol, price, isCall, instruments);
        };
    }

    /**
     * Resolve an option symbol by {@link StrikePolicy}, implementing the enum's own documented
     * contract: {@code ATM = round(price / strikeInterval)}, then step {@code offset} intervals
     * toward the money ({@code -1} for calls, {@code +1} for puts).
     *
     * <p>ADR 0062. This did not previously exist: {@code KiteService.getOptionStock} mapped all
     * five policy values onto {@link #getITMStock} / {@link #getOTMStock}, which are floor/ceiling
     * selections relative to spot and take no offset. The effect in production was that
     * {@code ATM} resolved one strike in-the-money, and {@code ITM_2} / {@code OTM_2} resolved
     * identically to their _1 counterparts — silently, with no log line. Measured 2026-07-24, prod
     * strike moneyness sat in (0, 1.03] strike-intervals for every order regardless of policy.
     *
     * <p>The strike interval is inferred from the live instrument ladder rather than configured, so
     * it stays correct per index and across any exchange re-gridding.
     *
     * @return the option symbol, or {@code ""} when the index has no listed options
     */
    public static String getStrikeByPolicy(String indexSymbol, double price, boolean isCall,
                                           StrikePolicy policy, List<Instrument> instruments) {
        String symbolsName = getOptionPrefix(indexSymbol);
        String instrumentType = isCall ? InstrumentType.CE.getCode() : InstrumentType.PE.getCode();
        return getEarliestExpiryInstrument(instruments, symbolsName, instrumentType)
                .map(expiryInstruments -> resolveByPolicy(expiryInstruments, price, isCall, policy))
                .orElse("");
    }

    private static String resolveByPolicy(List<Instrument> expiryInstruments, double price,
                                          boolean isCall, StrikePolicy policy) {
        NavigableMap<Long, String> strikes = strikeMap(expiryInstruments);
        if (strikes.isEmpty()) {
            return "";
        }
        long interval = inferStrikeInterval(strikes);
        if (interval <= 0) {
            log.error("Could not infer strike interval from {} strikes — falling back to nearest", strikes.size());
            return nearestTo(strikes, Math.round(price));
        }
        long atm = Math.round(price / (double) interval) * interval;
        long target = atm + (long) (isCall ? -1 : 1) * policy.getOffset() * interval;

        String exact = strikes.get(target);
        if (exact != null) {
            return exact;
        }
        log.warn("Strike {} not listed (policy={}, price={}, interval={}) — using nearest available",
                target, policy, price, interval);
        return nearestTo(strikes, target);
    }

    /**
     * Median gap between consecutive listed strikes. The median rather than the minimum, so an
     * occasional missing strike or a stray off-grid listing — both of which occur in live
     * instrument dumps — cannot skew the inferred grid.
     */
    private static long inferStrikeInterval(NavigableMap<Long, String> strikes) {
        List<Long> gaps = new ArrayList<>();
        Long previous = null;
        for (Long strike : strikes.keySet()) {
            if (previous != null && strike - previous > 0) {
                gaps.add(strike - previous);
            }
            previous = strike;
        }
        if (gaps.isEmpty()) {
            return 0L;
        }
        gaps.sort(Long::compare);
        return gaps.get(gaps.size() / 2);
    }

    private static String nearestTo(NavigableMap<Long, String> strikes, long target) {
        Long floor = strikes.floorKey(target);
        Long ceiling = strikes.ceilingKey(target);
        if (floor == null && ceiling == null) {
            return "";
        }
        if (floor == null) {
            return strikes.get(ceiling);
        }
        if (ceiling == null) {
            return strikes.get(floor);
        }
        return strikes.get(target - floor <= ceiling - target ? floor : ceiling);
    }

    private static String findStrike(String indexSymbol, double price, boolean isCall, boolean selectLastBelow,
                                     List<Instrument> instruments) {
        String symbolsName = getOptionPrefix(indexSymbol);
        String instrumentType = isCall ? InstrumentType.CE.getCode() : InstrumentType.PE.getCode();
        return getEarliestExpiryInstrument(instruments, symbolsName, instrumentType)
                .map(expiryInstruments -> resolveStrike(expiryInstruments, price, selectLastBelow))
                .orElse("");
    }

    /** Strike → tradingsymbol for one expiry, ordered ascending. */
    private static NavigableMap<Long, String> strikeMap(List<Instrument> instruments) {
        return instruments.stream()
                .collect(Collectors.toMap(
                        instrument -> {
                            try {
                                return Math.round(Double.parseDouble(instrument.getStrike()));
                            } catch (NumberFormatException e) {
                                log.error("Failed to parse strike price: '{}'", instrument.getStrike());
                                return null;
                            }
                        },
                        Instrument::getTradingsymbol,
                        (existing, replacement) -> existing,
                        TreeMap::new
                ));
    }

    private static String resolveStrike(List<Instrument> instruments, double price, boolean selectLastBelow) {
        NavigableMap<Long, String> strikeToSymbolMap = strikeMap(instruments);

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
                // Receiver flipped so a null name filters out instead of throwing. Live instrument
                // dumps do contain entries with a null name; this path only avoided NPEing because
                // every production caller happened to pre-filter through InstrumentCache.
                .filter(i -> symbolsName.equalsIgnoreCase(i.getName()))
                .filter(instrument -> Exchange.NFO.matches(instrument.exchange) || Exchange.BFO.matches(instrument.exchange))
                .filter(i -> instrumentType.equals(i.getInstrument_type()))
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

        Optional<List<Instrument>> callOptions = getEarliestExpiryInstrument(instruments, symbolsName, InstrumentType.CE.getCode());
        Optional<List<Instrument>> putOptions = getEarliestExpiryInstrument(instruments, symbolsName, InstrumentType.PE.getCode());

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
