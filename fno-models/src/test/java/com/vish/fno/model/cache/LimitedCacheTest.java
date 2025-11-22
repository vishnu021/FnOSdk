package com.vish.fno.model.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class LimitedCacheTest {

    private LimitedCache<String, Integer> cache;
    private static final int MAX_ENTRIES = 5;

    @BeforeEach
    void setUp() {
        cache = new LimitedCache<>(MAX_ENTRIES);
    }

    @Test
    void testPutAndGet() {
        // Arrange & Act
        cache.put("key1", 100);
        cache.put("key1", 200);
        cache.put("key1", 300);

        // Assert
        List<Integer> values = cache.get("key1");
        assertEquals(3, values.size());
        assertEquals(100, values.get(0));
        assertEquals(200, values.get(1));
        assertEquals(300, values.get(2));
    }

    @Test
    void testMaxEntriesLimit() {
        // Arrange & Act - add more than max entries
        for (int i = 0; i < 10; i++) {
            cache.put("key1", i);
        }

        // Assert - only last 5 entries should remain
        List<Integer> values = cache.get("key1");
        assertEquals(MAX_ENTRIES, values.size());
        assertEquals(5, values.get(0)); // First element should be 5 (0-4 removed)
        assertEquals(9, values.get(4)); // Last element should be 9
    }

    @Test
    void testGetNonExistentKey() {
        // Act
        List<Integer> values = cache.get("nonexistent");

        // Assert
        assertNotNull(values);
        assertTrue(values.isEmpty());
    }

    @Test
    void testKeySet() {
        // Arrange
        cache.put("key1", 1);
        cache.put("key2", 2);
        cache.put("key3", 3);

        // Act & Assert
        assertEquals(3, cache.keySet().size());
        assertTrue(cache.keySet().contains("key1"));
        assertTrue(cache.keySet().contains("key2"));
        assertTrue(cache.keySet().contains("key3"));
    }

    @Test
    void testSize() {
        // Arrange
        cache.put("key1", 1);
        cache.put("key1", 2);
        cache.put("key1", 3);

        // Act & Assert
        assertEquals(3, cache.size("key1"));
        assertEquals(0, cache.size("nonexistent"));
    }

    @Test
    void testDefensiveCopy() {
        // Arrange
        cache.put("key1", 100);
        cache.put("key1", 200);

        // Act - get the list and try to modify it
        List<Integer> values = cache.get("key1");
        values.add(300); // Should not affect internal cache

        // Assert - original cache should be unchanged
        List<Integer> originalValues = cache.get("key1");
        assertEquals(2, originalValues.size());
        assertFalse(originalValues.contains(300));
    }

    @Test
    void testKeySetIsUnmodifiable() {
        // Arrange
        cache.put("key1", 1);

        // Act & Assert
        assertThrows(UnsupportedOperationException.class, () -> {
            cache.keySet().add("key2");
        });
    }

    /**
     * Thread-safety test: Concurrent puts to same key
     * Verifies that ReentrantReadWriteLock prevents race conditions
     */
    @Test
    void testConcurrentPutsToSameKey() throws InterruptedException, ExecutionException {
        // Arrange
        int numThreads = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<?>> futures = new ArrayList<>();

        // Act - multiple threads putting to same key concurrently
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            Future<?> future = executor.submit(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    cache.put("sharedKey", threadId * 1000 + i);
                }
            });
            futures.add(future);
        }

        // Wait for all threads to complete
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Assert - should have exactly MAX_ENTRIES due to FIFO eviction
        List<Integer> values = cache.get("sharedKey");
        assertEquals(MAX_ENTRIES, values.size());
        // Verify list is consistent (no corruption)
        assertNotNull(values);
    }

    /**
     * Thread-safety test: Concurrent puts to different keys
     * Verifies that ConcurrentHashMap allows parallel updates to different keys
     */
    @Test
    void testConcurrentPutsToDifferentKeys() throws InterruptedException, ExecutionException {
        // Arrange
        int numThreads = 10;
        int entriesPerKey = 3;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<?>> futures = new ArrayList<>();

        // Act - each thread writes to its own key
        for (int t = 0; t < numThreads; t++) {
            final String key = "key" + t;
            Future<?> future = executor.submit(() -> {
                for (int i = 0; i < entriesPerKey; i++) {
                    cache.put(key, i);
                }
            });
            futures.add(future);
        }

        // Wait for all threads to complete
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Assert - all keys should have correct number of entries
        assertEquals(numThreads, cache.keySet().size());
        for (int t = 0; t < numThreads; t++) {
            String key = "key" + t;
            assertEquals(entriesPerKey, cache.size(key));
        }
    }

    /**
     * Thread-safety test: Concurrent reads and writes
     * Verifies that read-write lock allows concurrent reads while maintaining consistency
     */
    @Test
    void testConcurrentReadsAndWrites() throws InterruptedException, ExecutionException {
        // Arrange
        cache.put("key1", 1);
        cache.put("key1", 2);
        cache.put("key1", 3);

        int numReaders = 5;
        int numWriters = 3;
        ExecutorService executor = Executors.newFixedThreadPool(numReaders + numWriters);
        List<Future<?>> futures = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        // Act - readers and writers start simultaneously
        // Readers
        for (int i = 0; i < numReaders; i++) {
            Future<?> future = executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 100; j++) {
                        List<Integer> values = cache.get("key1");
                        assertNotNull(values);
                        assertTrue(values.size() >= 3); // At least initial 3 values
                        assertTrue(values.size() <= MAX_ENTRIES);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            futures.add(future);
        }

        // Writers
        for (int i = 0; i < numWriters; i++) {
            final int writerId = i;
            Future<?> future = executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 50; j++) {
                        cache.put("key1", writerId * 100 + j);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            futures.add(future);
        }

        // Start all threads
        startLatch.countDown();

        // Wait for all threads to complete
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Assert - cache should be in consistent state
        List<Integer> finalValues = cache.get("key1");
        assertEquals(MAX_ENTRIES, finalValues.size());
    }

    /**
     * Thread-safety test: Stress test with mixed operations
     * Verifies overall thread-safety under heavy concurrent load
     */
    @Test
    void testStressTestMixedOperations() throws InterruptedException, ExecutionException {
        // Arrange
        int numThreads = 20;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<?>> futures = new ArrayList<>();

        // Act - mixed put, get, size, keySet operations
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            Future<?> future = executor.submit(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    String key = "key" + (threadId % 5); // 5 different keys
                    cache.put(key, threadId * 1000 + i);
                    cache.get(key);
                    cache.size(key);
                    cache.keySet();
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

        // Assert - verify no corruption
        assertTrue(cache.keySet().size() <= 5);
        for (String key : cache.keySet()) {
            assertTrue(cache.size(key) <= MAX_ENTRIES);
            List<Integer> values = cache.get(key);
            assertNotNull(values);
            assertEquals(cache.size(key), values.size());
        }
    }
}
