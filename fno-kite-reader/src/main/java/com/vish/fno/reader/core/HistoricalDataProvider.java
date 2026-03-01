package com.vish.fno.reader.core;

import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;

import java.io.IOException;
import java.util.Date;
import java.util.Optional;

import static com.vish.fno.util.PriceUtils.getTopNLines;

@Slf4j
@RequiredArgsConstructor
class HistoricalDataProvider {
    private static final int ERROR_STACK_TRACE_LINES = 3;

    private final KiteSession session;
    private final InstrumentCache instrumentCache;

    Optional<HistoricalData> getHistoricalData(Date fromDate, Date toDate, String symbol, String interval) {
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

        if (continuous) {
            Optional<String> resolved = instrumentCache.resolveNearestFutureToken(symbol).map(String::valueOf);
            if (resolved.isPresent()) {
                return resolved;
            }
        }

        log.warn("No instrument available for symbol {} (continuous: {})", symbol, continuous);
        return Optional.empty();
    }
}
