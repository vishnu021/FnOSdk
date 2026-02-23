package com.vish.fno.reader.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vish.fno.reader.util.InstrumentFileUtils;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Instrument;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
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

        // Act — continuous=false, so resolveNearestFutureToken is not called
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
    // Continuous contract resolution tests (mock-based)
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
        // Arrange - expired symbol not in cache, resolved via InstrumentCache
        String expiredSymbol = "NIFTY24AUGFUT";
        long resolvedToken = 99999L;

        when(instrumentCache.getInstrument(expiredSymbol)).thenReturn(Optional.empty());
        when(instrumentCache.resolveNearestFutureToken(expiredSymbol)).thenReturn(Optional.of(resolvedToken));

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
        // Arrange - expired futures symbol, no contract found via InstrumentCache
        String expiredSymbol = "NIFTY24AUGFUT";
        when(instrumentCache.getInstrument(expiredSymbol)).thenReturn(Optional.empty());
        when(instrumentCache.resolveNearestFutureToken(expiredSymbol)).thenReturn(Optional.empty());

        // Act
        Optional<HistoricalData> result = provider.getHistoricalData(
                fromDate, toDate, expiredSymbol, INTERVAL, true);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testGetHistoricalData_continuousMode_nonFuturesSymbol() {
        // Arrange - symbol without FUT, continuous=true → resolveNearestFutureToken returns empty
        String nonFuturesSymbol = "RELIANCE";
        when(instrumentCache.getInstrument(nonFuturesSymbol)).thenReturn(Optional.empty());
        when(instrumentCache.resolveNearestFutureToken(nonFuturesSymbol)).thenReturn(Optional.empty());

        // Act
        Optional<HistoricalData> result = provider.getHistoricalData(
                fromDate, toDate, nonFuturesSymbol, INTERVAL, true);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testGetHistoricalData_continuousMode_invalidSymbolFormat() {
        // Arrange - symbol that contains FUT but with invalid format
        String invalidSymbol = "123INVALIDFUT";
        when(instrumentCache.getInstrument(invalidSymbol)).thenReturn(Optional.empty());
        when(instrumentCache.resolveNearestFutureToken(invalidSymbol)).thenReturn(Optional.empty());

        // Act
        Optional<HistoricalData> result = provider.getHistoricalData(
                fromDate, toDate, invalidSymbol, INTERVAL, true);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testGetHistoricalData_continuousMode_nullSymbol() {
        // Arrange - null symbol
        when(instrumentCache.getInstrument(null)).thenReturn(Optional.empty());
        when(instrumentCache.resolveNearestFutureToken(null)).thenReturn(Optional.empty());

        // Act
        Optional<HistoricalData> result = provider.getHistoricalData(
                fromDate, toDate, null, INTERVAL, true);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testGetHistoricalData_nonContinuousMode_expiredSymbolNotResolved() {
        // Arrange - expired symbol, continuous=false → no resolution attempted
        String expiredSymbol = "NIFTY24AUGFUT";
        when(instrumentCache.getInstrument(expiredSymbol)).thenReturn(Optional.empty());

        // Act
        Optional<HistoricalData> result = provider.getHistoricalData(
                fromDate, toDate, expiredSymbol, INTERVAL, false);

        // Assert
        assertFalse(result.isPresent());
    }

    // =======================================================================
    // Tests with real instrument data from instruments_2025-12-31.json
    // =======================================================================

    private static final String INSTRUMENT_CACHE_FILE = "/src/test/java/resources/instrument_cache/instruments_2025-12-31.json";
    private static final List<String> NIFTY_100_SYMBOLS = List.of(
            "NIFTY", "BANKNIFTY", "HDFCBANK", "RELIANCE", "SBIN", "SENSEX", "BANKEX");

    @SneakyThrows
    @SuppressWarnings("unchecked")
    private HistoricalDataProvider createProviderWithRealCache() {
        ObjectMapper objectMapper = new ObjectMapper();
        File file = new File(System.getProperty("user.dir") + INSTRUMENT_CACHE_FILE);
        List<Instrument> instruments = objectMapper.readValue(file,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Instrument.class));

        when(session.executeWithLock(any(Supplier.class), anyString()))
                .thenReturn(instruments);

        InstrumentCache realCache = new InstrumentCache(NIFTY_100_SYMBOLS, session);
        return new HistoricalDataProvider(session, realCache);
    }

    private void setupSessionForFullFlow() throws IOException, KiteException {
        when(session.isInitialised()).thenReturn(true);
        mockExecuteWithLockChecked();
        when(session.getKiteSdk()).thenReturn(mockKiteSdk);
        when(mockKiteSdk.getHistoricalData(any(Date.class), any(Date.class), anyString(),
                anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(new HistoricalData());
    }

    // --- getInstrumentToken: direct token resolution with real tokens ---

    @Test
    void testDirectTokenResolution_niftyFutureContracts() throws IOException, KiteException {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);

            HistoricalDataProvider realProvider = createProviderWithRealCache();
            setupSessionForFullFlow();

            realProvider.getHistoricalData(fromDate, toDate, "NIFTY26JANFUT", INTERVAL, false);
            realProvider.getHistoricalData(fromDate, toDate, "NIFTY26FEBFUT", INTERVAL, false);
            realProvider.getHistoricalData(fromDate, toDate, "NIFTY26MARFUT", INTERVAL, false);

            ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockKiteSdk, times(3)).getHistoricalData(
                    any(Date.class), any(Date.class), tokenCaptor.capture(),
                    anyString(), anyBoolean(), anyBoolean());

            List<String> tokens = tokenCaptor.getAllValues();
            assertEquals("12602626", tokens.get(0), "NIFTY26JANFUT token");
            assertEquals("15150594", tokens.get(1), "NIFTY26FEBFUT token");
            assertEquals("13238786", tokens.get(2), "NIFTY26MARFUT token");
        }
    }

    @Test
    void testDirectTokenResolution_niftyOptionContract() throws IOException, KiteException {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);

            HistoricalDataProvider realProvider = createProviderWithRealCache();
            setupSessionForFullFlow();

            Optional<HistoricalData> result = realProvider.getHistoricalData(
                    fromDate, toDate, "NIFTY2610624950CE", INTERVAL, false);

            assertTrue(result.isPresent());
            verify(mockKiteSdk).getHistoricalData(
                    eq(fromDate), eq(toDate), eq("10340610"), eq(INTERVAL), eq(false), eq(true));
        }
    }

    @Test
    void testDirectTokenResolution_bankniftyFutureContracts() throws IOException, KiteException {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);

            HistoricalDataProvider realProvider = createProviderWithRealCache();
            setupSessionForFullFlow();

            realProvider.getHistoricalData(fromDate, toDate, "BANKNIFTY26JANFUT", INTERVAL, false);
            realProvider.getHistoricalData(fromDate, toDate, "BANKNIFTY26FEBFUT", INTERVAL, false);
            realProvider.getHistoricalData(fromDate, toDate, "BANKNIFTY26MARFUT", INTERVAL, false);

            ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockKiteSdk, times(3)).getHistoricalData(
                    any(Date.class), any(Date.class), tokenCaptor.capture(),
                    anyString(), anyBoolean(), anyBoolean());

            List<String> tokens = tokenCaptor.getAllValues();
            assertEquals("12601346", tokens.get(0), "BANKNIFTY26JANFUT token");
            assertEquals("15148802", tokens.get(1), "BANKNIFTY26FEBFUT token");
            assertEquals("13235458", tokens.get(2), "BANKNIFTY26MARFUT token");
        }
    }

    @Test
    void testDirectTokenResolution_bfoFutures() throws IOException, KiteException {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);

            HistoricalDataProvider realProvider = createProviderWithRealCache();
            setupSessionForFullFlow();

            realProvider.getHistoricalData(fromDate, toDate, "SENSEX26JANFUT", INTERVAL, false);
            realProvider.getHistoricalData(fromDate, toDate, "BANKEX26JANFUT", INTERVAL, false);

            ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockKiteSdk, times(2)).getHistoricalData(
                    any(Date.class), any(Date.class), tokenCaptor.capture(),
                    anyString(), anyBoolean(), anyBoolean());

            List<String> tokens = tokenCaptor.getAllValues();
            assertEquals("292786437", tokens.get(0), "SENSEX26JANFUT token (BFO)");
            assertEquals("293244165", tokens.get(1), "BANKEX26JANFUT token (BFO)");
        }
    }

    @Test
    void testDirectTokenResolution_stockFutures() throws IOException, KiteException {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);

            HistoricalDataProvider realProvider = createProviderWithRealCache();
            setupSessionForFullFlow();

            realProvider.getHistoricalData(fromDate, toDate, "HDFCBANK26JANFUT", INTERVAL, false);
            realProvider.getHistoricalData(fromDate, toDate, "RELIANCE26JANFUT", INTERVAL, false);
            realProvider.getHistoricalData(fromDate, toDate, "SBIN26JANFUT", INTERVAL, false);

            ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockKiteSdk, times(3)).getHistoricalData(
                    any(Date.class), any(Date.class), tokenCaptor.capture(),
                    anyString(), anyBoolean(), anyBoolean());

            List<String> tokens = tokenCaptor.getAllValues();
            assertEquals("12652034", tokens.get(0), "HDFCBANK26JANFUT token");
            assertEquals("12798210", tokens.get(1), "RELIANCE26JANFUT token");
            assertEquals("12801282", tokens.get(2), "SBIN26JANFUT token");
        }
    }

    // --- Continuous mode resolution with real instrument cache ---

    @Test
    void testContinuousMode_expiredNiftyResolvesToJanContract() throws IOException, KiteException {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);

            HistoricalDataProvider realProvider = createProviderWithRealCache();
            setupSessionForFullFlow();

            // "NIFTY24AUGFUT" → base name "NIFTY" → nearest expiry FUT → NIFTY26JANFUT
            Optional<HistoricalData> result = realProvider.getHistoricalData(
                    fromDate, toDate, "NIFTY24AUGFUT", INTERVAL, true);

            assertTrue(result.isPresent(), "Expired NIFTY future should resolve to current contract");
            verify(mockKiteSdk).getHistoricalData(
                    eq(fromDate), eq(toDate), eq("12602626"), eq(INTERVAL), eq(true), eq(true));
        }
    }

    @Test
    void testContinuousMode_expiredBankniftyResolvesToJanContract() throws IOException, KiteException {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);

            HistoricalDataProvider realProvider = createProviderWithRealCache();
            setupSessionForFullFlow();

            // "BANKNIFTY24AUGFUT" → base name "BANKNIFTY" → BANKNIFTY26JANFUT
            Optional<HistoricalData> result = realProvider.getHistoricalData(
                    fromDate, toDate, "BANKNIFTY24AUGFUT", INTERVAL, true);

            assertTrue(result.isPresent());
            verify(mockKiteSdk).getHistoricalData(
                    eq(fromDate), eq(toDate), eq("12601346"), eq(INTERVAL), eq(true), eq(true));
        }
    }

    @Test
    void testContinuousMode_expiredSensexResolvesToJanContract() throws IOException, KiteException {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);

            HistoricalDataProvider realProvider = createProviderWithRealCache();
            setupSessionForFullFlow();

            // "SENSEX24AUGFUT" → base name "SENSEX" → SENSEX26JANFUT (BFO)
            Optional<HistoricalData> result = realProvider.getHistoricalData(
                    fromDate, toDate, "SENSEX24AUGFUT", INTERVAL, true);

            assertTrue(result.isPresent());
            verify(mockKiteSdk).getHistoricalData(
                    eq(fromDate), eq(toDate), eq("292786437"), eq(INTERVAL), eq(true), eq(true));
        }
    }

    @Test
    void testContinuousMode_expiredStockFutureResolvesToJanContract() throws IOException, KiteException {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);

            HistoricalDataProvider realProvider = createProviderWithRealCache();
            setupSessionForFullFlow();

            realProvider.getHistoricalData(fromDate, toDate, "RELIANCE25MARFUT", INTERVAL, true);
            realProvider.getHistoricalData(fromDate, toDate, "HDFCBANK24DECFUT", INTERVAL, true);
            realProvider.getHistoricalData(fromDate, toDate, "SBIN25JUNFUT", INTERVAL, true);

            ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockKiteSdk, times(3)).getHistoricalData(
                    any(Date.class), any(Date.class), tokenCaptor.capture(),
                    anyString(), anyBoolean(), anyBoolean());

            List<String> tokens = tokenCaptor.getAllValues();
            assertEquals("12798210", tokens.get(0), "RELIANCE25MARFUT → RELIANCE26JANFUT");
            assertEquals("12652034", tokens.get(1), "HDFCBANK24DECFUT → HDFCBANK26JANFUT");
            assertEquals("12801282", tokens.get(2), "SBIN25JUNFUT → SBIN26JANFUT");
        }
    }

    // --- Edge cases with real instrument cache ---

    @Test
    void testContinuousMode_nonFuturesSymbolNotInCacheReturnsEmpty() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);

            HistoricalDataProvider realProvider = createProviderWithRealCache();

            // "INVALIDXYZ" doesn't end with FUT → resolveNearestFutureToken returns empty
            Optional<HistoricalData> result = realProvider.getHistoricalData(
                    fromDate, toDate, "INVALIDXYZ", INTERVAL, true);

            assertFalse(result.isPresent(), "Non-futures symbol not in cache should return empty");
        }
    }

    @Test
    void testContinuousMode_invalidBaseNameWithRealCache() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);

            HistoricalDataProvider realProvider = createProviderWithRealCache();

            // "123ABCFUT" ends with FUT but no instrument name starts with "123ABC"
            Optional<HistoricalData> result = realProvider.getHistoricalData(
                    fromDate, toDate, "123ABCFUT", INTERVAL, true);

            assertFalse(result.isPresent(), "Invalid base name should not resolve");
        }
    }

    @Test
    void testContinuousMode_shorthandFutSymbolResolvesToNearestContract() throws IOException, KiteException {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);

            HistoricalDataProvider realProvider = createProviderWithRealCache();
            setupSessionForFullFlow();

            // "NIFTYFUT" ends with FUT + "NIFTY" matches as base name → resolves to nearest NIFTY FUT
            Optional<HistoricalData> result = realProvider.getHistoricalData(
                    fromDate, toDate, "NIFTYFUT", INTERVAL, true);

            assertTrue(result.isPresent(), "NIFTYFUT should resolve to nearest NIFTY future contract");
            verify(mockKiteSdk).getHistoricalData(
                    eq(fromDate), eq(toDate), eq("12602626"), eq(INTERVAL), eq(true), eq(true));
        }
    }

    // --- Month preference and year-independent resolution ---

    @Test
    void testContinuousMode_resolvesToEarliestExpiry() throws IOException, KiteException {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);

            HistoricalDataProvider realProvider = createProviderWithRealCache();
            setupSessionForFullFlow();

            // All 3 NIFTY futures exist (JAN, FEB, MAR). Earliest expiry (JAN) should be picked.
            Optional<HistoricalData> result = realProvider.getHistoricalData(
                    fromDate, toDate, "NIFTY23OCTFUT", INTERVAL, true);

            assertTrue(result.isPresent());
            // Verify JAN token (12602626), NOT FEB (15150594) or MAR (13238786)
            verify(mockKiteSdk).getHistoricalData(
                    eq(fromDate), eq(toDate), eq("12602626"), eq(INTERVAL), eq(true), eq(true));
        }
    }

    @Test
    void testContinuousMode_differentExpiredYearsResolveToSameContract() throws IOException, KiteException {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);

            HistoricalDataProvider realProvider = createProviderWithRealCache();
            setupSessionForFullFlow();

            // All expired BANKNIFTY symbols should resolve to the same nearest contract
            realProvider.getHistoricalData(fromDate, toDate, "BANKNIFTY23JANFUT", INTERVAL, true);
            realProvider.getHistoricalData(fromDate, toDate, "BANKNIFTY24AUGFUT", INTERVAL, true);
            realProvider.getHistoricalData(fromDate, toDate, "BANKNIFTY25DECFUT", INTERVAL, true);

            ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockKiteSdk, times(3)).getHistoricalData(
                    any(Date.class), any(Date.class), tokenCaptor.capture(),
                    anyString(), anyBoolean(), anyBoolean());

            // All should resolve to BANKNIFTY26JANFUT (token 12601346)
            List<String> tokens = tokenCaptor.getAllValues();
            assertEquals("12601346", tokens.get(0), "BANKNIFTY23JANFUT → BANKNIFTY26JANFUT");
            assertEquals("12601346", tokens.get(1), "BANKNIFTY24AUGFUT → BANKNIFTY26JANFUT");
            assertEquals("12601346", tokens.get(2), "BANKNIFTY25DECFUT → BANKNIFTY26JANFUT");
        }
    }

    @Test
    void testExpiredFuturesWithoutContinuousModeReturnsEmpty() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);

            HistoricalDataProvider realProvider = createProviderWithRealCache();

            // Expired symbol not in cache, continuous=false → no resolution attempted
            Optional<HistoricalData> result = realProvider.getHistoricalData(
                    fromDate, toDate, "NIFTY24AUGFUT", INTERVAL, false);

            assertFalse(result.isPresent(), "Expired futures without continuous mode should return empty");
        }
    }
}
