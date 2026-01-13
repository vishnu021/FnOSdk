package com.vish.fno.reader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vish.fno.reader.util.InstrumentFileUtils;
import com.zerodhatech.models.Instrument;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Slf4j
class InstrumentCacheTest {
    private static final String INSTRUMENT_CACHE_FILE = "/src/test/java/resources/instrument_cache/instruments_2025-12-31.json";
    private static final List<String> NIFTY_100_SYMBOLS = List.of("NIFTY", "BANKNIFTY", "HDFCBANK", "RELIANCE", "SBIN", "SENSEX", "BANKEX");

    @Mock
    private KiteService kiteService;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private InstrumentCache createInstrumentCache() {
        List<Instrument> instruments = mockInstrumentCache();
        when(kiteService.getAllInstruments()).thenReturn(instruments);
        return new InstrumentCache(NIFTY_100_SYMBOLS, kiteService);
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
            Integer lotSize = instrumentCache.getLotSizeFromFuture(indexName);

            // Assert
            assertNotNull(lotSize, "Lot size should not be null for NIFTY futures");
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
            Integer lotSize = instrumentCache.getLotSizeFromFuture(indexName);

            // Assert
            assertNull(lotSize, "Lot size should be null for invalid index name");
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
            Integer lotSize = instrumentCache.getLotSizeFromFuture(null);

            // Assert
            assertNull(lotSize, "Lot size should be null for null index name");
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
            List<Future<Integer>> futures = new ArrayList<>();

            // Act - Multiple threads accessing lot size methods concurrently
            for (int i = 0; i < threadCount; i++) {
                Future<Integer> future = executor.submit(() -> {
                    try {
                        latch.countDown();
                        latch.await(); // Wait for all threads to be ready
                        return instrumentCache.getLotSizeFromFuture("NIFTY");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                });
                futures.add(future);
            }

            // Assert - All threads should get the same lot size
            Set<Integer> uniqueLotSizes = new HashSet<>();
            for (Future<Integer> future : futures) {
                Integer lotSize = future.get();
                assertNotNull(lotSize, "Lot size should not be null");
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
}
