package com.vish.fno.reader.service;

import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;

import java.io.IOException;
import java.util.Date;

import static com.vish.fno.util.Utils.getTopNLines;

@Slf4j
@AllArgsConstructor
@SuppressWarnings("PMD")
class HistoricalDataService {
    private final KiteService kiteService;
    private final InstrumentCache instrumentCache;

    HistoricalData getEntireDayHistoricalData(Date fromDate, Date toDate, String symbol, String interval) {
        return getHistoricalData(fromDate, toDate, symbol, interval, false);
    }

    HistoricalData getHistoricalData(Date from, Date to, String symbol, String interval, boolean continuous) {
        String instrument = getInstrumentToken(symbol, continuous);
        if (instrument == null) {
            return null;
        }

        if(!kiteService.isInitialised()) {
            log.warn("Kite service is not initialised yet");
            return null;
        }

        try {
            log.debug("Collecting data for {} from: {}, to: {}, interval: {}, continuous: {}", instrument, from, to, interval, continuous);
            return kiteService.getKiteSdk().getHistoricalData(from, to, instrument, interval, continuous, true);
        } catch (JSONException | IOException | KiteException e) {
            log.error("Error while requesting historical data (from: {}, to: {}, symbol: {}, continuous: {}), errorMessage: {}\n{}",
                    from, to, instrument, continuous, e.getMessage(), getTopNLines(e, 3));
        }
        return null;
    }

    private String getInstrumentToken(String symbol) {
        return getInstrumentToken(symbol, false);
    }

    /**
     * Retrieves instrument token for the given symbol with continuous mode support.
     *
     * <p>For continuous mode with futures contracts:
     * <ul>
     * <li>If the exact symbol exists, use it directly</li>
     * <li>If the symbol doesn't exist (expired contract) and continuous=true,
     *     try to find the current active contract for the same underlying</li>
     * </ul>
     *
     * @param symbol The trading symbol (e.g., NIFTY25AUGFUT)
     * @param continuous Whether continuous mode is enabled
     * @return Instrument token as string, null if not found
     */
    private String getInstrumentToken(String symbol, boolean continuous) {
        String instrument = String.valueOf(instrumentCache.getInstrument(symbol));

        if(instrument != null && !instrument.equalsIgnoreCase("null")) {
            return instrument;
        }

        // If exact symbol not found and continuous mode is enabled, try to find current contract
        if (continuous && isFuturesSymbol(symbol)) {
            String currentContract = findCurrentFuturesContract(symbol);
            if (currentContract != null) {
                instrument = String.valueOf(instrumentCache.getInstrument(currentContract));
                if(instrument != null && !instrument.equalsIgnoreCase("null")) {
                    log.info("Using current contract {} (token: {}) for expired symbol {} with continuous mode",
                            currentContract, instrument, symbol);
                    return instrument;
                }
            }
        }

        log.warn("No instrument available for symbol {} (continuous: {})", symbol, continuous);
        return null;
    }

    /**
     * Checks if the symbol is a futures contract based on naming pattern.
     */
    private boolean isFuturesSymbol(String symbol) {
        return symbol != null && (symbol.contains("FUT") || symbol.matches(".*\\d{2}(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC).*"));
    }

    /**
     * Attempts to find the current active futures contract for the same underlying.
     *
     * <p>Example: NIFTY25AUGFUT (expired) → NIFTY25SEPFUT (current)
     */
    private String findCurrentFuturesContract(String expiredSymbol) {
        if (expiredSymbol == null) return null;

        // Extract the base name (e.g., "NIFTY" from "NIFTY25AUGFUT")
        String baseName = extractBaseName(expiredSymbol);
        if (baseName == null) return null;

        // Try common current month patterns
        String[] months = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN",
                          "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
        String[] years = {"24", "25", "26"}; // Current and near-term years

        for (String year : years) {
            for (String month : months) {
                String candidateSymbol = baseName + year + month + "FUT";
                if (instrumentCache.getInstrument(candidateSymbol) != null) {
                    log.debug("Found potential current contract: {}", candidateSymbol);
                    return candidateSymbol;
                }
            }
        }

        log.warn("Could not find current contract for expired symbol: {}", expiredSymbol);
        return null;
    }

    /**
     * Extracts the base name from a futures symbol.
     *
     * <p>Examples:
     * <ul>
     * <li>NIFTY25AUGFUT → NIFTY</li>
     * <li>BANKNIFTY25SEPFUT → BANKNIFTY</li>
     * </ul>
     */
    private String extractBaseName(String symbol) {
        if (symbol == null) return null;

        // Pattern for symbols like NIFTY25AUGFUT, BANKNIFTY25SEPFUT
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^([A-Z]+)\\d{2}[A-Z]{3}FUT$");
        java.util.regex.Matcher matcher = pattern.matcher(symbol);

        if (matcher.matches()) {
            return matcher.group(1);
        }

        log.warn("Could not extract base name from symbol: {}", symbol);
        return null;
    }
}
