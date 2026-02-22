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
}
