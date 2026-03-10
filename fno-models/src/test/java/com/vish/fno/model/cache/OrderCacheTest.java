package com.vish.fno.model.cache;

import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.orderrequest.OrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class OrderCacheTest {

    private OrderCache orderCache;
    private static final double INITIAL_CASH = 100000.0;

    @Mock
    private OrderRequest mockOrderRequest;

    @Mock
    private ActiveOrder mockActiveOrder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orderCache = new OrderCache(INITIAL_CASH);
    }

    @Test
    void testInitialCash() {
        // Assert
        assertEquals(INITIAL_CASH, orderCache.getAvailableCash());
    }

    @Test
    void testDeductCash() {
        // Arrange
        double deductAmount = 10000.0;

        // Act
        orderCache.deductCash(deductAmount);

        // Assert
        assertEquals(INITIAL_CASH - deductAmount, orderCache.getAvailableCash());
    }

    @Test
    void testAddCash() {
        // Arrange
        double addAmount = 5000.0;

        // Act
        orderCache.addCash(addAmount);

        // Assert
        assertEquals(INITIAL_CASH + addAmount, orderCache.getAvailableCash());
    }

    @Test
    void testDeductAndAddCash() {
        // Arrange
        double deductAmount = 20000.0;
        double addAmount = 15000.0;

        // Act
        orderCache.deductCash(deductAmount);
        orderCache.addCash(addAmount);

        // Assert
        assertEquals(INITIAL_CASH - deductAmount + addAmount, orderCache.getAvailableCash());
    }

    @Test
    void testAddOrderRequest() {
        // Arrange
        when(mockOrderRequest.getTag()).thenReturn("TAG1");
        when(mockOrderRequest.getIndex()).thenReturn("NIFTY");

        // Act
        orderCache.addOrderRequest(mockOrderRequest);

        // Assert - should not throw
        assertDoesNotThrow(() -> orderCache.logOpenOrders());
    }

    @Test
    void testAppendActiveOrder() {
        // Arrange
        when(mockActiveOrder.getOrderRequest()).thenReturn(mockOrderRequest);
        when(mockOrderRequest.getTag()).thenReturn("TAG1");
        when(mockOrderRequest.getIndex()).thenReturn("NIFTY");

        // Act
        orderCache.appendActiveOrder(mockActiveOrder);

        // Assert
        assertEquals(1, orderCache.getActiveOrders().size());
        assertTrue(orderCache.getActiveOrders().contains(mockActiveOrder));
    }

    @Test
    void testRemoveActiveOrder() {
        // Arrange
        when(mockActiveOrder.getOrderRequest()).thenReturn(mockOrderRequest);
        when(mockOrderRequest.getTag()).thenReturn("TAG1");
        when(mockOrderRequest.getIndex()).thenReturn("NIFTY");
        orderCache.appendActiveOrder(mockActiveOrder);

        // Act
        orderCache.removeActiveOrder(mockActiveOrder);

        // Assert
        assertTrue(orderCache.getActiveOrders().isEmpty());
    }

    /**
     * Thread-safety test: Concurrent deductions
     * Verifies that synchronized deductCash prevents race conditions
     */
    @Test
    void testConcurrentDeductions() throws InterruptedException, ExecutionException {
        // Arrange
        int numThreads = 10;
        double deductionPerThread = 1000.0;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<?>> futures = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        // Act - multiple threads deducting cash simultaneously
        for (int i = 0; i < numThreads; i++) {
            Future<?> future = executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    orderCache.deductCash(deductionPerThread);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            futures.add(future);
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Assert - total deduction should be exactly numThreads * deductionPerThread
        double expectedCash = INITIAL_CASH - (numThreads * deductionPerThread);
        assertEquals(expectedCash, orderCache.getAvailableCash(), 0.001);
    }

    /**
     * Thread-safety test: Concurrent additions
     * Verifies that synchronized addCash prevents race conditions
     */
    @Test
    void testConcurrentAdditions() throws InterruptedException, ExecutionException {
        // Arrange
        int numThreads = 10;
        double additionPerThread = 500.0;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<?>> futures = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        // Act - multiple threads adding cash simultaneously
        for (int i = 0; i < numThreads; i++) {
            Future<?> future = executor.submit(() -> {
                try {
                    startLatch.await();
                    orderCache.addCash(additionPerThread);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            futures.add(future);
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Assert - total addition should be exactly numThreads * additionPerThread
        double expectedCash = INITIAL_CASH + (numThreads * additionPerThread);
        assertEquals(expectedCash, orderCache.getAvailableCash(), 0.001);
    }

    /**
     * Thread-safety test: Concurrent deductions and additions
     * Verifies that synchronized methods work correctly when mixed
     */
    @Test
    void testConcurrentDeductionsAndAdditions() throws InterruptedException, ExecutionException {
        // Arrange
        int numDeductThreads = 10;
        int numAddThreads = 10;
        double amount = 1000.0;
        ExecutorService executor = Executors.newFixedThreadPool(numDeductThreads + numAddThreads);
        List<Future<?>> futures = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        // Act - deduct threads
        for (int i = 0; i < numDeductThreads; i++) {
            Future<?> future = executor.submit(() -> {
                try {
                    startLatch.await();
                    orderCache.deductCash(amount);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            futures.add(future);
        }

        // Add threads
        for (int i = 0; i < numAddThreads; i++) {
            Future<?> future = executor.submit(() -> {
                try {
                    startLatch.await();
                    orderCache.addCash(amount);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            futures.add(future);
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Assert - cash should be unchanged (equal deductions and additions)
        assertEquals(INITIAL_CASH, orderCache.getAvailableCash(), 0.001);
    }

    /**
     * Thread-safety test: High-frequency trading simulation
     * Simulates rapid buy/sell cycles with cash movements
     */
    @Test
    void testHighFrequencyTradingSimulation() throws InterruptedException, ExecutionException {
        // Arrange
        int numTradingThreads = 20;
        int tradesPerThread = 100;
        double tradeAmount = 100.0;
        ExecutorService executor = Executors.newFixedThreadPool(numTradingThreads);
        List<Future<?>> futures = new ArrayList<>();

        // Act - simulate rapid buy/sell cycles
        for (int i = 0; i < numTradingThreads; i++) {
            Future<?> future = executor.submit(() -> {
                for (int j = 0; j < tradesPerThread; j++) {
                    // Simulate buy (deduct cash)
                    orderCache.deductCash(tradeAmount);
                    // Simulate sell (add cash back with profit/loss)
                    orderCache.addCash(tradeAmount);
                }
            });
            futures.add(future);
        }

        // Wait for all threads to complete
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Assert - cash should return to initial value (buy then sell same amount)
        assertEquals(INITIAL_CASH, orderCache.getAvailableCash(), 0.001);
    }

    /**
     * Thread-safety test: Stress test with concurrent reads and writes
     * Verifies volatile keyword ensures visibility across threads
     */
    @Test
    void testConcurrentReadsAndWrites() throws InterruptedException, ExecutionException {
        // Arrange
        int numReaders = 10;
        int numWriters = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numReaders + numWriters);
        List<Future<Boolean>> readerFutures = new ArrayList<>();
        List<Future<?>> writerFutures = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        // Readers - continuously read available cash
        for (int i = 0; i < numReaders; i++) {
            Future<Boolean> future = executor.submit(() -> {
                try {
                    startLatch.await();
                    double previousCash = orderCache.getAvailableCash();
                    for (int j = 0; j < 1000; j++) {
                        double currentCash = orderCache.getAvailableCash();
                        // Cash should always be consistent (no torn reads due to volatile)
                        if (currentCash < 0 || currentCash > INITIAL_CASH * 2) {
                            return false; // Invalid state detected
                        }
                        previousCash = currentCash;
                    }
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            });
            readerFutures.add(future);
        }

        // Writers - perform cash operations
        for (int i = 0; i < numWriters; i++) {
            Future<?> future = executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 200; j++) {
                        orderCache.deductCash(100.0);
                        orderCache.addCash(100.0);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            writerFutures.add(future);
        }

        // Start all threads
        startLatch.countDown();

        // Wait for all threads
        for (Future<?> future : writerFutures) {
            future.get();
        }
        for (Future<Boolean> future : readerFutures) {
            assertTrue(future.get(), "Reader detected invalid state");
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Assert - final cash should be correct
        assertEquals(INITIAL_CASH, orderCache.getAvailableCash(), 0.001);
    }

    /**
     * Thread-safety test: Atomicity of cash operations
     * Verifies no lost updates occur
     */
    @Test
    void testAtomicityOfCashOperations() throws InterruptedException, ExecutionException {
        // Arrange - start with zero cash for easier verification
        OrderCache testCache = new OrderCache(0.0);
        int numThreads = 100;
        double incrementPerThread = 1.0;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<?>> futures = new ArrayList<>();

        // Act - each thread increments cash exactly once
        for (int i = 0; i < numThreads; i++) {
            Future<?> future = executor.submit(() -> {
                testCache.addCash(incrementPerThread);
            });
            futures.add(future);
        }

        // Wait for all threads
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Assert - total should be exactly numThreads * incrementPerThread
        // If synchronization was broken, we'd see lost updates
        assertEquals(numThreads * incrementPerThread, testCache.getAvailableCash(), 0.001);
    }
}
