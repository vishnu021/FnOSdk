package com.vish.fno.reader.core;

import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.vish.fno.util.FnoConstants.FUT;
import static com.vish.fno.util.PriceUtils.getTopNLines;

@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("PMD.TooManyStaticImports")
class HistoricalDataProvider {
    private static final int ERROR_STACK_TRACE_LINES = 3;
    private static final int FUTURES_YEAR_RANGE = 3;
    private static final String YEAR_FORMAT = "%02d";
    private static final String[] MONTH_CODES = {
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
        "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
    };

    private static final Pattern FUTURES_SYMBOL_PATTERN =
            Pattern.compile(".*\\d{2}(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC).*");
    private static final Pattern BASE_NAME_PATTERN =
            Pattern.compile("^([A-Z]+)\\d{2}[A-Z]{3}" + FUT + "$");

    private final KiteSession session;
    private final InstrumentCache instrumentCache;

    Optional<HistoricalData> getEntireDayHistoricalData(Date fromDate, Date toDate, String symbol, String interval) {
        return getHistoricalData(fromDate, toDate, symbol, interval, false);
    }

    Optional<HistoricalData> getHistoricalData(Date from, Date to, String symbol, String interval, boolean continuous) {
        Optional<String> instrument = getInstrumentToken(symbol, continuous);
        if (instrument.isEmpty()) {
            return Optional.empty();
        }

        if (!session.isInitialised()) {
            log.warn("Kite service is not initialised yet");
            return Optional.empty();
        }

        try {
            String token = instrument.get();
            log.debug("Collecting data for {} from: {}, to: {}, interval: {}, continuous: {}", token, from, to, interval, continuous);
            HistoricalData data = session.executeWithLockChecked(
                    () -> session.getKiteSdk().getHistoricalData(from, to, token, interval, continuous, true),
                    "getHistoricalData");
            return Optional.of(data);
        } catch (JSONException | IOException | KiteException e) {
            log.error("Error while requesting historical data (from: {}, to: {}, symbol: {}, continuous: {}), errorMessage: {}\n{}",
                    from, to, symbol, continuous, e.getMessage(), getTopNLines(e, ERROR_STACK_TRACE_LINES));
        }
        return Optional.empty();
    }

    private Optional<String> getInstrumentToken(String symbol, boolean continuous) {
        Optional<String> directToken = instrumentCache.getInstrument(symbol).map(String::valueOf);
        if (directToken.isPresent()) {
            return directToken;
        }

        if (continuous && isFuturesSymbol(symbol)) {
            Optional<String> resolved = resolveCurrentContract(symbol);
            if (resolved.isPresent()) {
                return resolved;
            }
        }

        log.warn("No instrument available for symbol {} (continuous: {})", symbol, continuous);
        return Optional.empty();
    }

    private Optional<String> resolveCurrentContract(String symbol) {
        return findCurrentFuturesContract(symbol)
                .flatMap(currentContract -> instrumentCache.getInstrument(currentContract)
                        .map(token -> {
                            log.info("Using current contract {} (token: {}) for expired symbol {} with continuous mode",
                                    currentContract, token, symbol);
                            return String.valueOf(token);
                        }));
    }

    private boolean isFuturesSymbol(String symbol) {
        return symbol != null && (symbol.contains(FUT) || FUTURES_SYMBOL_PATTERN.matcher(symbol).matches());
    }

    private Optional<String> findCurrentFuturesContract(String expiredSymbol) {
        return extractBaseName(expiredSymbol)
                .flatMap(this::findFirstAvailableContract);
    }

    private Optional<String> findFirstAvailableContract(String baseName) {
        String[] years = generateYearCodes();
        for (String year : years) {
            for (String month : MONTH_CODES) {
                String candidateSymbol = baseName + year + month + FUT;
                if (instrumentCache.getInstrument(candidateSymbol).isPresent()) {
                    log.debug("Found potential current contract: {}", candidateSymbol);
                    return Optional.of(candidateSymbol);
                }
            }
        }
        log.warn("Could not find current contract for base symbol: {}", baseName);
        return Optional.empty();
    }

    private String[] generateYearCodes() {
        int currentYear = LocalDate.now().getYear() % 100;
        String[] yearCodes = new String[FUTURES_YEAR_RANGE];
        for (int i = 0; i < FUTURES_YEAR_RANGE; i++) {
            yearCodes[i] = String.format(YEAR_FORMAT, currentYear + i);
        }
        return yearCodes;
    }

    private Optional<String> extractBaseName(String symbol) {
        if (symbol == null) {
            return Optional.empty();
        }

        Matcher matcher = BASE_NAME_PATTERN.matcher(symbol);
        if (matcher.matches()) {
            return Optional.of(matcher.group(1));
        }

        log.warn("Could not extract base name from symbol: {}", symbol);
        return Optional.empty();
    }
}
