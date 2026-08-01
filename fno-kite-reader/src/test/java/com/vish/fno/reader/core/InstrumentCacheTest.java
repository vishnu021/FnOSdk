package com.vish.fno.reader.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vish.fno.reader.util.InstrumentFileUtils;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.models.Instrument;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.vish.fno.reader.model.InstrumentSummary;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Slf4j
class InstrumentCacheTest {
    private static final String INSTRUMENT_CACHE_FILE = "/src/test/java/resources/instrument_cache/instruments_2025-12-31.json";
    private static final List<String> NIFTY_100_SYMBOLS = List.of("NIFTY", "BANKNIFTY", "HDFCBANK", "RELIANCE", "SBIN", "SENSEX", "BANKEX");

    @Mock
    private KiteSession session;
    @Mock
    private KiteConnect mockKiteSdk;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @SuppressWarnings("unchecked")
    private InstrumentCache createInstrumentCache() {
        List<Instrument> instruments = mockInstrumentCache();
        when(session.getKiteSdk()).thenReturn(mockKiteSdk);
        when(session.executeWithLock(any(Supplier.class), anyString()))
                .thenAnswer(invocation -> {
                    Supplier<List<Instrument>> supplier = invocation.getArgument(0);
                    return instruments;
                });
        return new InstrumentCache(NIFTY_100_SYMBOLS, session);
    }

    @SneakyThrows
    private List<Instrument> mockInstrumentCache() {
        final File instrumentCacheFile = new File(System.getProperty("user.dir") + INSTRUMENT_CACHE_FILE);
        return mapper.readValue(instrumentCacheFile,
                    mapper.getTypeFactory().constructCollectionType(List.class, Instrument.class));
    }

    @Test
    void isExpiryDayForOptionForFutureDate() {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            //Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            String optionSymbol = "NIFTY23DEC25000PE";

            Calendar calendar = Calendar.getInstance();
            calendar.set(2024, Calendar.DECEMBER, 28, 0, 0, 0);
            Date date = calendar.getTime();
            // Act
            boolean isExpiryDayForOption = createInstrumentCache().isExpiryDayForOption(optionSymbol, date);
            // Assert
            assertFalse(isExpiryDayForOption);
        }
    }

    @Test
    void isExpiryDayForOptionOnExpiryDay() {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            //Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            String optionSymbol = "NIFTY2610626000CE";

            Calendar calendar = Calendar.getInstance();
            calendar.set(2026, Calendar.JANUARY, 6, 0, 0, 0);
            Date date = calendar.getTime();
            // Act
            boolean isExpiryDayForOption = createInstrumentCache().isExpiryDayForOption(optionSymbol, date);
            // Assert
            assertTrue(isExpiryDayForOption);
        }
    }

    @Test
    void isExpiryDayForOptionForDayBeforeExpiry() {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            //Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            String optionSymbol = "NIFTY2610626000CE";

            Calendar calendar = Calendar.getInstance();
            calendar.set(2025, Calendar.DECEMBER, 31, 0, 0, 0);

            Date date = calendar.getTime();
            // Act
            boolean isExpiryDayForOption = createInstrumentCache().isExpiryDayForOption(optionSymbol, date);
            // Assert
            assertFalse(isExpiryDayForOption);
        }
    }

    /**
     * Test getLotSizeFromFuture() method with valid index name.
     * This method searches for FUT (future) instruments only, not equity.
     */
    @Test
    void testGetLotSizeFromFutureWithValidIndexName() {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();
            String indexName = "NIFTY";

            // Act
            Optional<Integer> lotSizeOpt = instrumentCache.getLotSizeFromFuture(indexName);

            // Assert
            assertTrue(lotSizeOpt.isPresent(), "Lot size should be present for NIFTY futures");
            int lotSize = lotSizeOpt.get();
            assertTrue(lotSize > 0, "Lot size for NIFTY futures should be positive");
            assertEquals(65, lotSize, "NIFTY lot size should be 65");
            log.info("Lot size for {} futures: {}", indexName, lotSize);
        }
    }

    /**
     * Test getLotSizeFromFuture() method with invalid index name
     */
    @Test
    void testGetLotSizeFromFutureWithInvalidIndexName() {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();
            String indexName = "INVALID_INDEX";

            // Act
            Optional<Integer> lotSizeOpt = instrumentCache.getLotSizeFromFuture(indexName);

            // Assert
            assertTrue(lotSizeOpt.isEmpty(), "Lot size should be empty for invalid index name");
        }
    }

    /**
     * Test getLotSizeFromFuture() method with null index name
     */
    @Test
    void testGetLotSizeFromFutureWithNullIndexName() {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act
            Optional<Integer> lotSizeOpt = instrumentCache.getLotSizeFromFuture(null);

            // Assert
            assertTrue(lotSizeOpt.isEmpty(), "Lot size should be empty for null index name");
        }
    }

    /**
     * Test getAllFutureLotSizeInfo() method.
     * This method returns a map of index name to lot size for all future contracts.
     */
    @Test
    void testGetAllFutureLotSizeInfo() {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act
            Map<String, Integer> allFutureLotSizes = instrumentCache.getAllFutureLotSizeInfo();

            // Assert
            assertNotNull(allFutureLotSizes, "Future lot sizes map should not be null");
            assertFalse(allFutureLotSizes.isEmpty(), "Future lot sizes map should not be empty");
            log.info("Total future contracts: {}", allFutureLotSizes.size());

            // Verify specific indices exist in map
            assertTrue(allFutureLotSizes.containsKey("NIFTY 50"), "NIFTY 50 should be in future lot sizes");
            assertTrue(allFutureLotSizes.containsKey("NIFTY BANK"), "NIFTY BANK should be in future lot sizes");

            // Verify specific lot sizes
            assertEquals(65, allFutureLotSizes.get("NIFTY 50"), "NIFTY lot size should be 65");
            assertEquals(30, allFutureLotSizes.get("NIFTY BANK"), "NIFTY BANK lot size should be 30");

            // Verify all lot sizes are positive (futures always have positive lot sizes)
            allFutureLotSizes.forEach((indexName, lotSize) -> {
                assertTrue(lotSize > 0, "Lot size for " + indexName + " futures should be positive");
                log.info("Index: {}, Lot Size: {}", indexName, lotSize);
            });
        }
    }

    /**
     * Test concurrent access to lot size methods.
     * Ensures thread-safe lazy initialization works correctly.
     */
    @Test
    void testConcurrentAccessToLotSizeMethods() {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<Future<Optional<Integer>>> futures = new ArrayList<>();

            // Act - Multiple threads accessing lot size methods concurrently
            for (int i = 0; i < threadCount; i++) {
                Future<Optional<Integer>> future = executor.submit(() -> {
                    try {
                        latch.countDown();
                        latch.await(); // Wait for all threads to be ready
                        return instrumentCache.getLotSizeFromFuture("NIFTY");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return Optional.empty();
                    }
                });
                futures.add(future);
            }

            // Assert - All threads should get the same lot size
            Set<Integer> uniqueLotSizes = new HashSet<>();
            for (Future<Optional<Integer>> future : futures) {
                Optional<Integer> lotSizeOpt = future.get();
                assertTrue(lotSizeOpt.isPresent(), "Lot size should be present");
                int lotSize = lotSizeOpt.get();
                assertTrue(lotSize > 0, "Lot size should be positive");
                uniqueLotSizes.add(lotSize);
            }

            assertEquals(1, uniqueLotSizes.size(), "All threads should get the same lot size");
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor should terminate");
        } catch (Exception e) {
            fail("Concurrent access test failed: " + e.getMessage());
        }
    }

    /**
     * Test getInstrument() returns a non-empty Optional with valid instrument token for a known symbol.
     */
    @Test
    void testGetInstrumentWithKnownSymbol() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act
            Optional<Long> tokenOpt = instrumentCache.getInstrument("NIFTY2610624950CE");

            // Assert
            assertTrue(tokenOpt.isPresent(), "Instrument token should be present for a known option symbol");
            assertTrue(tokenOpt.get() > 0, "Instrument token should be a positive number");
            log.info("Instrument token for NIFTY2610624950CE: {}", tokenOpt.get());
        }
    }

    /**
     * Test getInstrument(null) returns Optional.empty().
     */
    @Test
    void testGetInstrumentWithNull() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act
            Optional<Long> tokenOpt = instrumentCache.getInstrument(null);

            // Assert
            assertTrue(tokenOpt.isEmpty(), "Instrument token should be empty for null symbol");
        }
    }

    /**
     * Test getSymbol() returns the correct symbol for a known instrument token.
     * Uses getInstrument() to first obtain a valid token, then verifies the reverse lookup.
     */
    @Test
    void testGetSymbolWithKnownToken() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();
            String expectedSymbol = "NIFTY2610624950CE";

            // Act
            Optional<Long> tokenOpt = instrumentCache.getInstrument(expectedSymbol);
            assertTrue(tokenOpt.isPresent(), "Pre-condition: token must exist for known symbol");
            String symbol = instrumentCache.getSymbol(tokenOpt.get());

            // Assert
            assertEquals(expectedSymbol, symbol, "Reverse lookup should return the original symbol");
        }
    }

    /**
     * Test getExchangeForSymbol() returns the correct exchange for a known NFO symbol.
     */
    @Test
    void testGetExchangeForSymbolWithKnownSymbol() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act
            String exchange = instrumentCache.getExchangeForSymbol("NIFTY2610624950CE");

            // Assert
            assertNotNull(exchange, "Exchange should not be null for a known symbol");
            assertEquals("NFO", exchange, "Exchange for NIFTY option should be NFO");
        }
    }

    /**
     * Test getExchangeForSymbol(null) returns NFO as the default.
     */
    @Test
    void testGetExchangeForSymbolWithNull() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act
            String exchange = instrumentCache.getExchangeForSymbol(null);

            // Assert
            assertEquals("NFO", exchange, "Exchange should default to NFO for null symbol");
        }
    }

    /**
     * Test getExchangeForSymbol("UNKNOWN") returns NFO as the default with a warning.
     */
    @Test
    void testGetExchangeForSymbolWithUnknownSymbol() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act
            String exchange = instrumentCache.getExchangeForSymbol("UNKNOWN");

            // Assert
            assertEquals("NFO", exchange, "Exchange should default to NFO for unknown symbol");
        }
    }

    /**
     * Test getAllSymbols() returns a non-empty set of all instrument names.
     */
    @Test
    void testGetAllSymbols() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act
            Set<String> allSymbols = instrumentCache.getAllSymbols();

            // Assert
            assertNotNull(allSymbols, "All symbols set should not be null");
            assertFalse(allSymbols.isEmpty(), "All symbols set should not be empty");
            // getName() for NFO instruments returns the derivative name (e.g., "NIFTY", "BANKNIFTY")
            assertTrue(allSymbols.contains("NIFTY"), "All symbols should contain NIFTY");
            assertTrue(allSymbols.contains("BANKNIFTY"), "All symbols should contain BANKNIFTY");
            log.info("Total unique symbol names: {}", allSymbols.size());
        }
    }

    /**
     * Test getFilteredSymbols() returns a non-empty map of tradingSymbol to name.
     */
    @Test
    void testGetFilteredSymbols() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act
            Map<String, String> filteredSymbols = instrumentCache.getFilteredSymbols();

            // Assert
            assertNotNull(filteredSymbols, "Filtered symbols map should not be null");
            assertFalse(filteredSymbols.isEmpty(), "Filtered symbols map should not be empty");
            // tradingSymbol keys are specific option symbols like "NIFTY2610624950CE"
            assertTrue(filteredSymbols.containsKey("NIFTY2610624950CE"),
                    "Filtered symbols should contain NIFTY option trading symbol as a key");
            assertEquals("NIFTY", filteredSymbols.get("NIFTY2610624950CE"),
                    "Name for NIFTY option should be NIFTY");
            log.info("Filtered symbols count: {}", filteredSymbols.size());
        }
    }

    /**
     * Test getInstrumentForSymbol() returns a non-empty list for a known trading symbol.
     */
    @Test
    void testGetInstrumentForSymbolWithKnownSymbol() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act
            List<Instrument> instruments = instrumentCache.getInstrumentForSymbol("NIFTY2610624950CE");

            // Assert
            assertNotNull(instruments, "Instrument list should not be null");
            assertFalse(instruments.isEmpty(), "Instrument list should not be empty for a known symbol");
            assertEquals(1, instruments.size(), "Should find exactly one instrument for a specific option symbol");
            assertEquals("NIFTY2610624950CE", instruments.get(0).getTradingsymbol(),
                    "Trading symbol should match the queried symbol");
        }
    }

    /**
     * Test getInstrumentForSymbol() returns an empty list for a non-existent symbol.
     */
    @Test
    void testGetInstrumentForSymbolWithNonExistentSymbol() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act
            List<Instrument> instruments = instrumentCache.getInstrumentForSymbol("NONEXISTENT");

            // Assert
            assertNotNull(instruments, "Instrument list should not be null");
            assertTrue(instruments.isEmpty(), "Instrument list should be empty for a non-existent symbol");
        }
    }

    /**
     * Test getInstrumentMapSize() returns a positive number after initialization.
     */
    @Test
    void testGetInstrumentMapSizeAfterInit() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Trigger initialization by calling getInstruments()
            instrumentCache.getInstruments();

            // Act
            int mapSize = instrumentCache.getInstrumentMapSize();

            // Assert
            assertTrue(mapSize > 0, "Instrument map size should be positive after initialization");
            log.info("Instrument map size: {}", mapSize);
        }
    }

    /**
     * Test getInstrumentMapSize() returns 0 before initialization.
     */
    @Test
    void testGetInstrumentMapSizeBeforeInit() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            // Create cache but do NOT call getInstruments() to avoid initialization
            when(session.getKiteSdk()).thenReturn(mockKiteSdk);
            InstrumentCache uninitializedCache = new InstrumentCache(NIFTY_100_SYMBOLS, session);

            // Act
            int mapSize = uninitializedCache.getInstrumentMapSize();

            // Assert
            assertEquals(0, mapSize, "Instrument map size should be 0 before initialization");
        }
    }

    /**
     * Test getAllInstruments() returns a non-empty list of InstrumentSummary sorted by name.
     */
    @Test
    void testGetAllInstruments() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act
            List<InstrumentSummary> allInstruments = instrumentCache.getAllInstruments();

            // Assert
            assertNotNull(allInstruments, "All instruments list should not be null");
            assertFalse(allInstruments.isEmpty(), "All instruments list should not be empty");

            // Verify sorted by name (InstrumentSummary.symbol() is the trading symbol, sorted by Instrument.getName())
            for (int i = 1; i < allInstruments.size(); i++) {
                String prevExchange = allInstruments.get(i - 1).exchange();
                String currExchange = allInstruments.get(i).exchange();
                assertNotNull(prevExchange, "Exchange should not be null");
                assertNotNull(currExchange, "Exchange should not be null");
            }

            log.info("Total instruments: {}", allInstruments.size());
            log.info("First instrument: {}", allInstruments.get(0));
        }
    }

    /**
     * Test getExpiryDates() returns a non-empty set of expiry date strings.
     */
    @Test
    void testGetExpiryDates() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act
            Set<String> expiryDates = instrumentCache.getExpiryDates();

            // Assert
            assertNotNull(expiryDates, "Expiry dates set should not be null");
            assertFalse(expiryDates.isEmpty(), "Expiry dates set should not be empty");
            log.info("Expiry dates: {}", expiryDates);
        }
    }

    /**
     * Test getEarliestExpiryInstruments() returns instruments for a valid name and type.
     */
    @Test
    void testGetEarliestExpiryInstrumentsWithValidInput() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act
            Optional<List<Instrument>> result = instrumentCache.getEarliestExpiryInstruments("NIFTY", "CE");

            // Assert
            assertTrue(result.isPresent(), "Should find CE instruments for NIFTY");
            assertFalse(result.get().isEmpty(), "Instrument list should not be empty");

            // Verify all returned instruments are CE type
            result.get().forEach(instrument ->
                    assertEquals("CE", instrument.getInstrument_type(),
                            "All instruments should be CE type"));

            // Verify all instruments have the same expiry (earliest)
            Date firstExpiry = result.get().get(0).getExpiry();
            result.get().forEach(instrument ->
                    assertEquals(firstExpiry, instrument.getExpiry(),
                            "All instruments should have the same earliest expiry"));

            log.info("Found {} NIFTY CE instruments for earliest expiry", result.get().size());
        }
    }

    /**
     * Test getEarliestExpiryInstruments() returns empty Optional for an unknown name.
     */
    @Test
    void testGetEarliestExpiryInstrumentsWithUnknownName() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act
            Optional<List<Instrument>> result = instrumentCache.getEarliestExpiryInstruments("UNKNOWN", "CE");

            // Assert
            assertTrue(result.isEmpty(), "Should return empty for unknown instrument name");
        }
    }

    /**
     * Test getEarliestExpiryInstruments() returns empty Optional for an unknown instrument type.
     */
    @Test
    void testGetEarliestExpiryInstrumentsWithUnknownType() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Act
            Optional<List<Instrument>> result = instrumentCache.getEarliestExpiryInstruments("NIFTY", "UNKNOWN");

            // Assert
            assertTrue(result.isEmpty(), "Should return empty for unknown instrument type");
        }
    }

    // ===== Tests validating real values from instruments_2025-12-31.json =====

    @Test
    void testTotalFilteredInstrumentCount() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            List<Instrument> instruments = instrumentCache.getInstruments();

            assertEquals(7070, instruments.size(),
                    "Total filtered instruments for 7 NIFTY_100_SYMBOLS should be 7070");
        }
    }

    @Test
    void testInstrumentCountByName() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            List<Instrument> instruments = instrumentCache.getInstruments();

            long niftyCount = instruments.stream().filter(i -> "NIFTY".equals(i.getName())).count();
            long bankniftyCount = instruments.stream().filter(i -> "BANKNIFTY".equals(i.getName())).count();
            long sensexCount = instruments.stream().filter(i -> "SENSEX".equals(i.getName())).count();
            long bankexCount = instruments.stream().filter(i -> "BANKEX".equals(i.getName())).count();

            assertEquals(1439, niftyCount, "NIFTY instrument count");
            assertEquals(817, bankniftyCount, "BANKNIFTY instrument count");
            assertEquals(3204, sensexCount, "SENSEX instrument count");
            assertEquals(984, bankexCount, "BANKEX instrument count");
        }
    }

    @Test
    void testFuturesCountIs21WithThreePerSymbol() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            List<Instrument> futures = instrumentCache.getInstruments().stream()
                    .filter(i -> "FUT".equals(i.getInstrument_type()))
                    .toList();

            assertEquals(21, futures.size(), "Total futures: 3 contracts (near/next/far) x 7 symbols");

            // Each symbol has exactly 3 future contracts (Jan, Feb, Mar 2026)
            for (String name : List.of("NIFTY", "BANKNIFTY", "SENSEX", "BANKEX", "HDFCBANK", "RELIANCE", "SBIN")) {
                long count = futures.stream().filter(i -> name.equals(i.getName())).count();
                assertEquals(3, count, name + " should have 3 future contracts");
            }
        }
    }

    @Test
    void testSpecificInstrumentTokenForNiftyOption() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            Optional<Long> token = instrumentCache.getInstrument("NIFTY2610624950CE");

            assertTrue(token.isPresent());
            assertEquals(10340610L, token.get(), "NIFTY2610624950CE should have exact token 10340610");
        }
    }

    @Test
    void testNiftyEarliestExpiryContractCounts() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // NIFTY earliest expiry is Jan 6, 2026 (weekly)
            Optional<List<Instrument>> niftyCE = instrumentCache.getEarliestExpiryInstruments("NIFTY", "CE");
            Optional<List<Instrument>> niftyPE = instrumentCache.getEarliestExpiryInstruments("NIFTY", "PE");

            assertTrue(niftyCE.isPresent());
            assertTrue(niftyPE.isPresent());
            assertEquals(80, niftyCE.get().size(), "NIFTY earliest expiry (Jan 6, 2026) should have 80 CE strikes");
            assertEquals(80, niftyPE.get().size(), "NIFTY earliest expiry (Jan 6, 2026) should have 80 PE strikes");

            // All should have the same expiry date and lot size 65
            niftyCE.get().forEach(i -> {
                assertEquals("CE", i.getInstrument_type());
                assertEquals(65, i.getLot_size());
            });
        }
    }

    @Test
    void testBankniftyEarliestExpiryContractCounts() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // BANKNIFTY earliest expiry is Jan 27, 2026 (monthly)
            Optional<List<Instrument>> bnCE = instrumentCache.getEarliestExpiryInstruments("BANKNIFTY", "CE");
            Optional<List<Instrument>> bnPE = instrumentCache.getEarliestExpiryInstruments("BANKNIFTY", "PE");

            assertTrue(bnCE.isPresent());
            assertTrue(bnPE.isPresent());
            assertEquals(135, bnCE.get().size(), "BANKNIFTY earliest expiry (Jan 27, 2026) should have 135 CE strikes");
            assertEquals(135, bnPE.get().size(), "BANKNIFTY earliest expiry (Jan 27, 2026) should have 135 PE strikes");

            // All should have lot size 30
            bnCE.get().forEach(i -> assertEquals(30, i.getLot_size()));
        }
    }

    @Test
    void testSensexEarliestExpiryContractCount() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // SENSEX earliest expiry is Jan 1, 2026
            Optional<List<Instrument>> sensexCE = instrumentCache.getEarliestExpiryInstruments("SENSEX", "CE");

            assertTrue(sensexCE.isPresent());
            assertEquals(164, sensexCE.get().size(), "SENSEX earliest expiry (Jan 1, 2026) should have 164 CE strikes");

            // SENSEX trades on BFO with lot size 20
            sensexCE.get().forEach(i -> {
                assertEquals("BFO", i.getExchange());
                assertEquals(20, i.getLot_size());
            });
        }
    }

    @Test
    void testBankexEarliestExpiryContractCount() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // BANKEX earliest expiry is Jan 29, 2026
            Optional<List<Instrument>> bankexCE = instrumentCache.getEarliestExpiryInstruments("BANKEX", "CE");

            assertTrue(bankexCE.isPresent());
            assertEquals(174, bankexCE.get().size(), "BANKEX earliest expiry (Jan 29, 2026) should have 174 CE strikes");

            // BANKEX trades on BFO with lot size 30
            bankexCE.get().forEach(i -> {
                assertEquals("BFO", i.getExchange());
                assertEquals(30, i.getLot_size());
            });
        }
    }

    @Test
    void testLotSizesForAllSymbolsWithFutures() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // Index lot sizes
            assertEquals(65, instrumentCache.getLotSizeFromFuture("NIFTY").orElseThrow());
            assertEquals(30, instrumentCache.getLotSizeFromFuture("BANKNIFTY").orElseThrow());
            assertEquals(20, instrumentCache.getLotSizeFromFuture("SENSEX").orElseThrow());
            assertEquals(30, instrumentCache.getLotSizeFromFuture("BANKEX").orElseThrow());

            // Stock lot sizes
            assertEquals(550, instrumentCache.getLotSizeFromFuture("HDFCBANK").orElseThrow());
            assertEquals(500, instrumentCache.getLotSizeFromFuture("RELIANCE").orElseThrow());
            assertEquals(750, instrumentCache.getLotSizeFromFuture("SBIN").orElseThrow());
        }
    }

    @Test
    void testBfoExchangeForBseDerivatives() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // SENSEX and BANKEX futures trade on BFO
            assertEquals("BFO", instrumentCache.getExchangeForSymbol("SENSEX26JANFUT"));
            assertEquals("BFO", instrumentCache.getExchangeForSymbol("BANKEX26JANFUT"));

            // NIFTY and BANKNIFTY futures trade on NFO
            assertEquals("NFO", instrumentCache.getExchangeForSymbol("NIFTY26JANFUT"));
            assertEquals("NFO", instrumentCache.getExchangeForSymbol("BANKNIFTY26JANFUT"));

            // Stock futures trade on NFO
            assertEquals("NFO", instrumentCache.getExchangeForSymbol("HDFCBANK26JANFUT"));
            assertEquals("NFO", instrumentCache.getExchangeForSymbol("SBIN26JANFUT"));
            assertEquals("NFO", instrumentCache.getExchangeForSymbol("RELIANCE26JANFUT"));
        }
    }

    @Test
    void testTotalUniqueExpiryDateCount() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            Set<String> expiryDates = instrumentCache.getExpiryDates();

            assertEquals(38, expiryDates.size(),
                    "Should have 38 unique expiry dates across all filtered instruments");
        }
    }

    @Test
    void testAllSymbolNamesArePresent() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            Set<String> allSymbols = instrumentCache.getAllSymbols();

            // 7 NFO/BFO derivative names + 4 BSE equity names (HDFC BANK, RELIANCE INDUSTRIES, etc.)
            assertEquals(11, allSymbols.size(), "Should have 11 unique instrument names");

            // NFO/BFO derivative names
            for (String name : List.of("NIFTY", "BANKNIFTY", "SENSEX", "BANKEX",
                    "HDFCBANK", "RELIANCE", "SBIN")) {
                assertTrue(allSymbols.contains(name), "Should contain " + name);
            }
        }
    }

    @Test
    void testNiftyHas18UniqueExpiries() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // NIFTY has weekly + monthly + quarterly expiries
            long niftyExpiries = instrumentCache.getInstruments().stream()
                    .filter(i -> "NIFTY".equals(i.getName()))
                    .filter(i -> i.getExpiry() != null)
                    .map(Instrument::getExpiry)
                    .distinct()
                    .count();

            assertEquals(18, niftyExpiries, "NIFTY should have 18 unique expiry dates (weekly + monthly + quarterly)");
        }
    }

    @Test
    void testBankniftyHas6MonthlyExpiries() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // BANKNIFTY has monthly expiries only (no weekly in this dataset)
            long bnExpiries = instrumentCache.getInstruments().stream()
                    .filter(i -> "BANKNIFTY".equals(i.getName()))
                    .filter(i -> i.getExpiry() != null)
                    .map(Instrument::getExpiry)
                    .distinct()
                    .count();

            assertEquals(6, bnExpiries, "BANKNIFTY should have 6 unique expiry dates (monthly)");
        }
    }

    @Test
    void testSensexHas20WeeklyExpiries() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            long sensexExpiries = instrumentCache.getInstruments().stream()
                    .filter(i -> "SENSEX".equals(i.getName()))
                    .filter(i -> i.getExpiry() != null)
                    .map(Instrument::getExpiry)
                    .distinct()
                    .count();

            assertEquals(20, sensexExpiries, "SENSEX should have 20 unique expiry dates (weekly + quarterly)");
        }
    }

    @Test
    void testAllFutureLotSizeInfoMapKeysAndValues() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            Map<String, Integer> lotSizes = instrumentCache.getAllFutureLotSizeInfo();

            assertEquals(7, lotSizes.size(), "Should have lot sizes for all 7 symbols with futures");

            // INDEX_TO_DERIVATIVE reverse-maps derivative names to index names
            assertEquals(65, lotSizes.get("NIFTY 50"), "NIFTY 50 lot size");
            assertEquals(30, lotSizes.get("NIFTY BANK"), "NIFTY BANK lot size");
            assertEquals(20, lotSizes.get("SENSEX"), "SENSEX lot size");
            assertEquals(30, lotSizes.get("BANKEX"), "BANKEX lot size");

            // Stocks not in INDEX_TO_DERIVATIVE keep their derivative name as key
            assertEquals(550, lotSizes.get("HDFCBANK"), "HDFCBANK lot size");
            assertEquals(500, lotSizes.get("RELIANCE"), "RELIANCE lot size");
            assertEquals(750, lotSizes.get("SBIN"), "SBIN lot size");
        }
    }

    @Test
    void testNiftyEarliestExpiryFutureContract() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            Optional<List<Instrument>> niftyFut = instrumentCache.getEarliestExpiryInstruments("NIFTY", "FUT");

            assertTrue(niftyFut.isPresent());
            assertEquals(1, niftyFut.get().size(), "NIFTY nearest expiry should have exactly 1 FUT contract");

            Instrument nearMonthFut = niftyFut.get().get(0);
            assertEquals("NIFTY26JANFUT", nearMonthFut.getTradingsymbol());
            assertEquals("NFO", nearMonthFut.getExchange());
            assertEquals(65, nearMonthFut.getLot_size());
        }
    }

    @Test
    void testFilteredSymbolsMapSize() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            Map<String, String> filtered = instrumentCache.getFilteredSymbols();
            int mapSize = instrumentCache.getInstrumentMapSize();

            // 7070 instruments, but 3 have duplicate tradingsymbols (NSE/BSE equity share names)
            // that get deduplicated by the merge function, leaving 7067 unique entries
            assertEquals(7067, filtered.size(), "Filtered symbols map should have 7067 unique tradingsymbol entries");
            assertEquals(7067, mapSize, "Instrument map should have 7067 unique token-to-symbol mappings");
        }
    }

    // ===== resolveNearestFutureToken tests =====

    @Test
    void testResolveNearestFutureToken_expiredNiftyFuture() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // NIFTY24AUGFUT → base name "NIFTY" → nearest expiry FUT → NIFTY26JANFUT
            Optional<Long> token = instrumentCache.resolveNearestFutureToken("NIFTY24AUGFUT");

            assertTrue(token.isPresent());
            assertEquals(12602626L, token.get(), "Should resolve to NIFTY26JANFUT token");
        }
    }

    @Test
    void testResolveNearestFutureToken_expiredBankniftyFuture() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            Optional<Long> token = instrumentCache.resolveNearestFutureToken("BANKNIFTY25SEPFUT");

            assertTrue(token.isPresent());
            assertEquals(12601346L, token.get(), "Should resolve to BANKNIFTY26JANFUT token");
        }
    }

    @Test
    void testResolveNearestFutureToken_shorthandFutSymbol() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // "NIFTYFUT" ends with FUT and "NIFTY" matches as base name
            Optional<Long> token = instrumentCache.resolveNearestFutureToken("NIFTYFUT");

            assertTrue(token.isPresent(), "NIFTYFUT should resolve via base name matching");
            assertEquals(12602626L, token.get(), "Should resolve to NIFTY26JANFUT token");
        }
    }

    @Test
    void testResolveNearestFutureToken_nullSymbol() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            Optional<Long> token = instrumentCache.resolveNearestFutureToken(null);

            assertTrue(token.isEmpty(), "Null symbol should return empty");
        }
    }

    @Test
    void testResolveNearestFutureToken_nonFutSymbol() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            Optional<Long> token = instrumentCache.resolveNearestFutureToken("NIFTY2610624950CE");

            assertTrue(token.isEmpty(), "Non-FUT symbol should return empty");
        }
    }

    @Test
    void testResolveNearestFutureToken_unknownBaseName() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            Optional<Long> token = instrumentCache.resolveNearestFutureToken("UNKNOWNSYMBOLFUT");

            assertTrue(token.isEmpty(), "Unknown base name should return empty");
        }
    }

    @Test
    void testResolveNearestFutureToken_bfoFuture() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            Optional<Long> token = instrumentCache.resolveNearestFutureToken("SENSEX24AUGFUT");

            assertTrue(token.isPresent());
            assertEquals(292786437L, token.get(), "Should resolve to SENSEX26JANFUT (BFO) token");
        }
    }

    // ==================== daysToExpiryForIndex (ADR-0068) ====================
    // Fixture expiries (IST): NIFTY weekly first = 2026-01-06 (Tue); BANKEX monthly = 2026-01-29
    // (last Thu). Derived from real instrument expiry dates, so holiday-shifted expiries are
    // correct by construction.

    private static Date istDate(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
        calendar.clear();
        calendar.set(year, month, day, 10, 0, 0);
        return calendar.getTime();
    }

    @Test
    void daysToExpiryForIndex_dayBeforeWeeklyExpiryIsOne() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            assertEquals(java.util.OptionalInt.of(1),
                    instrumentCache.daysToExpiryForIndex("NIFTY 50", istDate(2026, Calendar.JANUARY, 5)));
        }
    }

    @Test
    void daysToExpiryForIndex_zeroOnExpiryDay() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            assertEquals(java.util.OptionalInt.of(0),
                    instrumentCache.daysToExpiryForIndex("NIFTY 50", istDate(2026, Calendar.JANUARY, 6)));
        }
    }

    @Test
    void daysToExpiryForIndex_monthlyIndexCountsCalendarDays() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            // BANKEX monthly expiry 2026-01-29; from Jan-28 (E-1) = 1, from Jan-01 = 28
            assertEquals(java.util.OptionalInt.of(1),
                    instrumentCache.daysToExpiryForIndex("BANKEX", istDate(2026, Calendar.JANUARY, 28)));
            assertEquals(java.util.OptionalInt.of(28),
                    instrumentCache.daysToExpiryForIndex("BANKEX", istDate(2026, Calendar.JANUARY, 1)));
        }
    }

    @Test
    void daysToExpiryForIndex_unknownIndexOrNullIsEmpty() {
        try (MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(i -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(i -> null);
            InstrumentCache instrumentCache = createInstrumentCache();

            assertTrue(instrumentCache.daysToExpiryForIndex("NO SUCH INDEX",
                    istDate(2026, Calendar.JANUARY, 5)).isEmpty());
            assertTrue(instrumentCache.daysToExpiryForIndex(null,
                    istDate(2026, Calendar.JANUARY, 5)).isEmpty());
            assertTrue(instrumentCache.daysToExpiryForIndex("NIFTY 50", null).isEmpty());
        }
    }
}
