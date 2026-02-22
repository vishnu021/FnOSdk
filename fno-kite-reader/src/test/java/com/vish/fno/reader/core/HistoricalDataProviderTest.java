package com.vish.fno.reader.core;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalDataProviderTest {

    private static final String SYMBOL = "NIFTY";
    private static final String INTERVAL = "minute";
    private static final long INSTRUMENT_TOKEN = 256L;

    @Mock
    private KiteSession session;
    @Mock
    private InstrumentCache instrumentCache;
    @Mock
    private KiteConnect mockKiteSdk;

    private HistoricalDataProvider provider;
    private Date fromDate;
    private Date toDate;

    @BeforeEach
    void setUp() {
        provider = new HistoricalDataProvider(session, instrumentCache);
        fromDate = new Date();
        toDate = new Date();
    }

    // -----------------------------------------------------------------------
    // Helper: make session.executeWithLockChecked actually invoke the supplier
    // -----------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    private void mockExecuteWithLockChecked() throws IOException, KiteException {
        when(session.executeWithLockChecked(any(ApiRateLimiter.CheckedSupplier.class), anyString()))
                .thenAnswer(invocation -> {
                    ApiRateLimiter.CheckedSupplier<?> supplier = invocation.getArgument(0);
                    return supplier.get();
                });
    }

    // =======================================================================
    // getHistoricalData tests
    // =======================================================================

    @Test
    void testGetHistoricalData_success() throws IOException, KiteException {
        // Arrange
        when(instrumentCache.getInstrument(SYMBOL)).thenReturn(Optional.of(INSTRUMENT_TOKEN));
        when(session.isInitialised()).thenReturn(true);
        mockExecuteWithLockChecked();
        when(session.getKiteSdk()).thenReturn(mockKiteSdk);

        HistoricalData expectedData = new HistoricalData();
        when(mockKiteSdk.getHistoricalData(
                eq(fromDate), eq(toDate), eq(String.valueOf(INSTRUMENT_TOKEN)),
                eq(INTERVAL), eq(false), eq(true)))
                .thenReturn(expectedData);

        // Act
        Optional<HistoricalData> result = provider.getHistoricalData(fromDate, toDate, SYMBOL, INTERVAL, false);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(expectedData, result.get());
        verify(mockKiteSdk).getHistoricalData(
                eq(fromDate), eq(toDate), eq(String.valueOf(INSTRUMENT_TOKEN)),
                eq(INTERVAL), eq(false), eq(true));
    }

    @Test
    void testGetHistoricalData_symbolNotFound() {
        // Arrange
        when(instrumentCache.getInstrument(SYMBOL)).thenReturn(Optional.empty());

        // Act
        Optional<HistoricalData> result = provider.getHistoricalData(fromDate, toDate, SYMBOL, INTERVAL, false);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testGetHistoricalData_notInitialised() {
        // Arrange
        when(instrumentCache.getInstrument(SYMBOL)).thenReturn(Optional.of(INSTRUMENT_TOKEN));
        when(session.isInitialised()).thenReturn(false);

        // Act
        Optional<HistoricalData> result = provider.getHistoricalData(fromDate, toDate, SYMBOL, INTERVAL, false);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testGetHistoricalData_kiteException() throws IOException, KiteException {
        // Arrange
        when(instrumentCache.getInstrument(SYMBOL)).thenReturn(Optional.of(INSTRUMENT_TOKEN));
        when(session.isInitialised()).thenReturn(true);

        when(session.executeWithLockChecked(any(ApiRateLimiter.CheckedSupplier.class), anyString()))
                .thenThrow(new KiteException("Rate limit exceeded", 429));

        // Act
        Optional<HistoricalData> result = provider.getHistoricalData(fromDate, toDate, SYMBOL, INTERVAL, false);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testGetHistoricalData_ioException() throws IOException, KiteException {
        // Arrange
        when(instrumentCache.getInstrument(SYMBOL)).thenReturn(Optional.of(INSTRUMENT_TOKEN));
        when(session.isInitialised()).thenReturn(true);

        when(session.executeWithLockChecked(any(ApiRateLimiter.CheckedSupplier.class), anyString()))
                .thenThrow(new IOException("Connection refused"));

        // Act
        Optional<HistoricalData> result = provider.getHistoricalData(fromDate, toDate, SYMBOL, INTERVAL, false);

        // Assert
        assertFalse(result.isPresent());
    }

    // =======================================================================
    // getEntireDayHistoricalData tests
    // =======================================================================

    @Test
    void testGetEntireDayHistoricalData_delegatesToGetHistoricalData() throws IOException, KiteException {
        // Arrange
        when(instrumentCache.getInstrument(SYMBOL)).thenReturn(Optional.of(INSTRUMENT_TOKEN));
        when(session.isInitialised()).thenReturn(true);
        mockExecuteWithLockChecked();
        when(session.getKiteSdk()).thenReturn(mockKiteSdk);

        HistoricalData expectedData = new HistoricalData();
        when(mockKiteSdk.getHistoricalData(
                eq(fromDate), eq(toDate), eq(String.valueOf(INSTRUMENT_TOKEN)),
                eq(INTERVAL), eq(false), eq(true)))
                .thenReturn(expectedData);

        // Act
        Optional<HistoricalData> result = provider.getEntireDayHistoricalData(fromDate, toDate, SYMBOL, INTERVAL);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(expectedData, result.get());
        // Verify it was called with continuous=false
        verify(mockKiteSdk).getHistoricalData(
                eq(fromDate), eq(toDate), eq(String.valueOf(INSTRUMENT_TOKEN)),
                eq(INTERVAL), eq(false), eq(true));
    }

    // =======================================================================
    // Continuous contract resolution tests
    // =======================================================================

    @Test
    void testGetHistoricalData_continuousMode_directTokenFound() throws IOException, KiteException {
        // Arrange - exact symbol exists in cache, continuous=true, uses direct token
        String futuresSymbol = "NIFTY26FEBFUT";
        long futuresToken = 12345L;
        when(instrumentCache.getInstrument(futuresSymbol)).thenReturn(Optional.of(futuresToken));
        when(session.isInitialised()).thenReturn(true);
        mockExecuteWithLockChecked();
        when(session.getKiteSdk()).thenReturn(mockKiteSdk);

        HistoricalData expectedData = new HistoricalData();
        when(mockKiteSdk.getHistoricalData(
                eq(fromDate), eq(toDate), eq(String.valueOf(futuresToken)),
                eq(INTERVAL), eq(true), eq(true)))
                .thenReturn(expectedData);

        // Act
        Optional<HistoricalData> result = provider.getHistoricalData(
                fromDate, toDate, futuresSymbol, INTERVAL, true);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(expectedData, result.get());
        verify(mockKiteSdk).getHistoricalData(
                eq(fromDate), eq(toDate), eq(String.valueOf(futuresToken)),
                eq(INTERVAL), eq(true), eq(true));
    }

    @Test
    void testGetHistoricalData_continuousMode_resolveCurrentContract() throws IOException, KiteException {
        // Arrange - expired symbol not in cache, but a current contract is found via iteration
        String expiredSymbol = "NIFTY24AUGFUT";
        long resolvedToken = 99999L;

        // The expired symbol is not in the cache
        when(instrumentCache.getInstrument(expiredSymbol)).thenReturn(Optional.empty());

        // Build the current year/month contract that will be found during iteration
        int currentYear = LocalDate.now().getYear() % 100;
        String currentYearCode = String.format("%02d", currentYear);
        // The iteration goes year by year, month by month (JAN first).
        // We make the first candidate for the current year found:
        String currentContract = "NIFTY" + currentYearCode + "JANFUT";
        when(instrumentCache.getInstrument(currentContract)).thenReturn(Optional.of(resolvedToken));

        when(session.isInitialised()).thenReturn(true);
        mockExecuteWithLockChecked();
        when(session.getKiteSdk()).thenReturn(mockKiteSdk);

        HistoricalData expectedData = new HistoricalData();
        when(mockKiteSdk.getHistoricalData(
                eq(fromDate), eq(toDate), eq(String.valueOf(resolvedToken)),
                eq(INTERVAL), eq(true), eq(true)))
                .thenReturn(expectedData);

        // Act
        Optional<HistoricalData> result = provider.getHistoricalData(
                fromDate, toDate, expiredSymbol, INTERVAL, true);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(expectedData, result.get());
    }

    @Test
    void testGetHistoricalData_continuousMode_noContractFound() {
        // Arrange - expired futures symbol, no current contract found in any year/month combo
        String expiredSymbol = "NIFTY24AUGFUT";
        // Return empty for all instrument lookups
        when(instrumentCache.getInstrument(anyString())).thenReturn(Optional.empty());

        // Act
        Optional<HistoricalData> result = provider.getHistoricalData(
                fromDate, toDate, expiredSymbol, INTERVAL, true);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testGetHistoricalData_continuousMode_nonFuturesSymbol() {
        // Arrange - symbol without FUT and not matching the futures pattern, continuous=true
        String nonFuturesSymbol = "RELIANCE";
        when(instrumentCache.getInstrument(nonFuturesSymbol)).thenReturn(Optional.empty());

        // Act
        Optional<HistoricalData> result = provider.getHistoricalData(
                fromDate, toDate, nonFuturesSymbol, INTERVAL, true);

        // Assert - isFuturesSymbol returns false, so no contract resolution attempted
        assertFalse(result.isPresent());
    }

    // =======================================================================
    // extractBaseName (tested indirectly through continuous resolution)
    // =======================================================================

    @Test
    void testGetHistoricalData_continuousMode_invalidBaseNamePattern() {
        // Arrange - symbol that contains FUT but doesn't match BASE_NAME_PATTERN
        // BASE_NAME_PATTERN requires: ^([A-Z]+)\d{2}[A-Z]{3}FUT$
        // "123INVALIDFUT" starts with digits, so group(1) won't match [A-Z]+
        String invalidSymbol = "123INVALIDFUT";
        when(instrumentCache.getInstrument(invalidSymbol)).thenReturn(Optional.empty());

        // Act
        Optional<HistoricalData> result = provider.getHistoricalData(
                fromDate, toDate, invalidSymbol, INTERVAL, true);

        // Assert - extractBaseName returns empty, so no contract resolution
        assertFalse(result.isPresent());
    }

    @Test
    void testGetHistoricalData_continuousMode_nullSymbol() {
        // Arrange - null symbol passed
        when(instrumentCache.getInstrument(null)).thenReturn(Optional.empty());

        // Act
        Optional<HistoricalData> result = provider.getHistoricalData(
                fromDate, toDate, null, INTERVAL, true);

        // Assert - isFuturesSymbol handles null safely, returns empty
        assertFalse(result.isPresent());
    }
}
