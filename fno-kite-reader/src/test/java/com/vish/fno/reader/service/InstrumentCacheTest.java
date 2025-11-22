package com.vish.fno.reader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vish.fno.reader.util.InstrumentFileUtils;
import com.zerodhatech.models.Instrument;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Slf4j
class InstrumentCacheTest {
    private static final String INSTRUMENT_CACHE_FILE = "/src/test/java/resources/instrument_cache/instruments_2024-07-04.json";

    @Mock private KiteService kiteService;

    private InstrumentCache underTest;
    private final ObjectMapper mapper = new ObjectMapper();


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        List<String> nifty100Symbols = List.of("NIFTY 50", "NIFTY BANK", "HDFCBANK", "BANKNIFTY", "NIFTY");
        underTest = new InstrumentCache(nifty100Symbols, kiteService);
        List<Instrument> instruments = mockInstrumentCache();
        when(kiteService.getAllInstruments()).thenReturn(instruments);
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
            boolean isExpiryDayForOption = underTest.isExpiryDayForOption(optionSymbol, date);
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
            String optionSymbol = "NIFTY24JUL25000PE";

            Calendar calendar = Calendar.getInstance();
            calendar.set(2024, Calendar.JULY, 25, 0, 0, 0);
            Date date = calendar.getTime();
            // Act
            boolean isExpiryDayForOption = underTest.isExpiryDayForOption(optionSymbol, date);
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
            String optionSymbol = "NIFTY24JUL25000PE";

            Calendar calendar = Calendar.getInstance();
            calendar.set(2024, Calendar.JULY, 27, 0, 0, 0);

            Date date = calendar.getTime();
            // Act
            boolean isExpiryDayForOption = underTest.isExpiryDayForOption(optionSymbol, date);
            // Assert
            assertFalse(isExpiryDayForOption);
        }
    }

    /**
     * Thread-safety test: Concurrent initialization via double-checked locking
     * Verifies that only one thread initializes the cache even with concurrent access
     */
    @Test
    void testConcurrentInitialization() throws InterruptedException, ExecutionException {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);

            List<String> nifty100Symbols = List.of("NIFTY 50", "NIFTY BANK", "HDFCBANK");
            InstrumentCache cache = new InstrumentCache(nifty100Symbols, kiteService);

            int numThreads = 20;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            List<Future<List<Instrument>>> futures = new ArrayList<>();
            CountDownLatch startLatch = new CountDownLatch(1);

            // Act - multiple threads call getInstruments() simultaneously (first time)
            for (int i = 0; i < numThreads; i++) {
                Future<List<Instrument>> future = executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready
                        return cache.getInstruments();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                });
                futures.add(future);
            }

            // Start all threads simultaneously
            startLatch.countDown();

            // Collect results
            List<List<Instrument>> results = new ArrayList<>();
            for (Future<List<Instrument>> future : futures) {
                results.add(future.get());
            }

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            // Assert - all threads should get the same list reference (initialized once)
            // Verify kiteService.getAllInstruments() was called exactly once
            verify(kiteService, times(1)).getAllInstruments();

            // All results should be non-null and have same size
            for (List<Instrument> result : results) {
                assertNotNull(result);
                assertEquals(results.get(0).size(), result.size());
            }
        }
    }

    /**
     * Thread-safety test: Concurrent reads after initialization
     * Verifies fast-path (non-locking) access works correctly
     */
    @Test
    void testConcurrentReadsAfterInitialization() throws InterruptedException, ExecutionException {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);

            List<String> nifty100Symbols = List.of("NIFTY 50", "NIFTY BANK", "HDFCBANK");
            InstrumentCache cache = new InstrumentCache(nifty100Symbols, kiteService);

            // Initialize cache first (single-threaded)
            List<Instrument> initialResult = cache.getInstruments();
            assertNotNull(initialResult);

            // Act - concurrent reads from already-initialized cache
            int numThreads = 50;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            List<Future<List<Instrument>>> futures = new ArrayList<>();

            for (int i = 0; i < numThreads; i++) {
                Future<List<Instrument>> future = executor.submit(cache::getInstruments);
                futures.add(future);
            }

            // Collect results
            List<List<Instrument>> results = new ArrayList<>();
            for (Future<List<Instrument>> future : futures) {
                results.add(future.get());
            }

            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            // Assert - all reads return consistent data
            for (List<Instrument> result : results) {
                assertNotNull(result);
                assertEquals(initialResult.size(), result.size());
            }

            // Verify no additional calls to kiteService (should use cached data)
            verify(kiteService, times(1)).getAllInstruments();
        }
    }

    /**
     * Thread-safety test: Concurrent calls to various cache methods
     * Verifies thread-safety of all public methods
     */
    @Test
    void testConcurrentMixedOperations() throws InterruptedException, ExecutionException {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);

            List<String> nifty100Symbols = List.of("NIFTY 50", "NIFTY BANK", "HDFCBANK");
            InstrumentCache cache = new InstrumentCache(nifty100Symbols, kiteService);

            int numThreads = 30;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            List<Future<?>> futures = new ArrayList<>();
            CountDownLatch startLatch = new CountDownLatch(1);

            // Act - threads perform different operations concurrently
            for (int i = 0; i < numThreads; i++) {
                final int threadId = i;
                Future<?> future = executor.submit(() -> {
                    try {
                        startLatch.await();
                        // Mix of different operations
                        cache.getInstruments();
                        cache.getAllSymbols();
                        cache.getExpiryDates();
                        cache.getFilteredSymbols();

                        if (threadId % 3 == 0) {
                            cache.getInstrument("HDFCBANK");
                        } else if (threadId % 3 == 1) {
                            cache.getSymbol(256265L); // Example token
                        } else {
                            cache.getInstrumentForSymbol("NIFTY24JUL25000PE");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                futures.add(future);
            }

            // Start all threads
            startLatch.countDown();

            // Wait for all threads
            for (Future<?> future : futures) {
                future.get();
            }

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            // Assert - cache should be initialized exactly once
            verify(kiteService, times(1)).getAllInstruments();
        }
    }

    /**
     * Thread-safety test: Defensive copy verification
     * Ensures returned lists cannot modify internal state
     */
    @Test
    void testDefensiveCopyInConcurrentEnvironment() throws InterruptedException, ExecutionException {
        try(MockedStatic<InstrumentFileUtils> mockedStatic = Mockito.mockStatic(InstrumentFileUtils.class)) {
            // Arrange
            mockedStatic.when(() -> InstrumentFileUtils.saveInstrumentCache(any())).thenAnswer(invocationOnMock -> null);
            mockedStatic.when(() -> InstrumentFileUtils.saveFilteredInstrumentCache(any())).thenAnswer(invocationOnMock -> null);

            List<String> nifty100Symbols = List.of("NIFTY 50", "NIFTY BANK", "HDFCBANK");
            InstrumentCache cache = new InstrumentCache(nifty100Symbols, kiteService);

            // Initialize
            cache.getInstruments();

            int numThreads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            List<Future<Boolean>> futures = new ArrayList<>();

            // Act - threads try to modify returned lists
            for (int i = 0; i < numThreads; i++) {
                Future<Boolean> future = executor.submit(() -> {
                    try {
                        List<Instrument> instruments = cache.getInstruments();
                        // Try to modify - should throw UnsupportedOperationException
                        instruments.clear();
                        return false; // Should not reach here
                    } catch (UnsupportedOperationException e) {
                        return true; // Expected exception
                    }
                });
                futures.add(future);
            }

            // Collect results
            for (Future<Boolean> future : futures) {
                assertTrue(future.get(), "Defensive copy failed - list was modifiable");
            }

            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            // Assert - cache should still have all data
            List<Instrument> instruments = cache.getInstruments();
            assertFalse(instruments.isEmpty());
        }
    }
}