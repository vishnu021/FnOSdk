package com.vish.fno.reader.service;

import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
class ApiRateLimiterTest {

    private ApiRateLimiter rateLimiter;
    private long originalTimeout;

    @BeforeEach
    void setUp() {
        rateLimiter = new ApiRateLimiter();
        originalTimeout = ApiRateLimiter.lockTimeoutSeconds;
    }

    @AfterEach
    void tearDown() {
        ApiRateLimiter.lockTimeoutSeconds = originalTimeout;
    }

    // --- executeWithLock basic behavior ---

    @Test
    void testExecuteWithLockReturnsSupplierResult() {
        String result = rateLimiter.executeWithLock(() -> "hello", "test");
        assertEquals("hello", result);
    }

    @Test
    void testExecuteWithLockReturnsNullFromSupplier() {
        String result = rateLimiter.executeWithLock(() -> null, "test");
        assertNull(result);
    }

    @Test
    void testExecuteWithLockVoidExecutesAction() {
        AtomicBoolean executed = new AtomicBoolean(false);
        rateLimiter.executeWithLockVoid(() -> executed.set(true), "test");
        assertTrue(executed.get());
    }

    // --- executeWithLockChecked basic behavior ---

    @Test
    void testExecuteWithLockCheckedReturnsResult() throws IOException, KiteException {
        Integer result = rateLimiter.executeWithLockChecked(() -> 42, "test");
        assertEquals(42, result);
    }

    @Test
    void testExecuteWithLockCheckedPropagatesIOException() {
        assertThrows(IOException.class, () ->
                rateLimiter.executeWithLockChecked(() -> {
                    throw new IOException("test IO error");
                }, "test"));
    }

    @Test
    void testExecuteWithLockCheckedPropagatesKiteException() {
        assertThrows(KiteException.class, () ->
                rateLimiter.executeWithLockChecked(() -> {
                    throw new KiteException("test kite error", 403);
                }, "test"));
    }

    // --- Serialization / mutual exclusion ---

    @Test
    void testConcurrentCallsAreSerialized() throws Exception {
        int threadCount = 10;
        AtomicInteger concurrentCount = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            int idx = i;
            futures.add(executor.submit(() -> {
                startLatch.await();
                return rateLimiter.executeWithLock(() -> {
                    int current = concurrentCount.incrementAndGet();
                    maxConcurrent.updateAndGet(max -> Math.max(max, current));
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    concurrentCount.decrementAndGet();
                    return "result-" + idx;
                }, "thread-" + idx);
            }));
        }

        startLatch.countDown();
        for (Future<String> f : futures) {
            assertNotNull(f.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(1, maxConcurrent.get(), "Only one thread should hold the lock at a time");
    }

    @Test
    void testMixedLockAndVoidAreSerialized() throws Exception {
        int threadCount = 10;
        AtomicInteger concurrentCount = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            if (i % 2 == 0) {
                futures.add(executor.submit(() -> {
                    startLatch.await();
                    rateLimiter.executeWithLock(() -> {
                        int c = concurrentCount.incrementAndGet();
                        maxConcurrent.updateAndGet(max -> Math.max(max, c));
                        try { Thread.sleep(15); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        concurrentCount.decrementAndGet();
                        return "ok";
                    }, "supplier");
                    return null;
                }));
            } else {
                futures.add(executor.submit(() -> {
                    startLatch.await();
                    rateLimiter.executeWithLockVoid(() -> {
                        int c = concurrentCount.incrementAndGet();
                        maxConcurrent.updateAndGet(max -> Math.max(max, c));
                        try { Thread.sleep(15); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        concurrentCount.decrementAndGet();
                    }, "runnable");
                    return null;
                }));
            }
        }

        startLatch.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(1, maxConcurrent.get(), "executeWithLock and executeWithLockVoid should share the same lock");
    }

    // --- Timeout behavior ---

    @Test
    void testExecuteWithLockReturnsNullOnTimeout() throws Exception {
        ApiRateLimiter.lockTimeoutSeconds = 1;
        ReentrantLock lock = getInternalLock();

        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch testDone = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                lockHeld.countDown();
                testDone.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });
        holder.start();
        assertTrue(lockHeld.await(5, TimeUnit.SECONDS));

        AtomicReference<String> result = new AtomicReference<>("sentinel");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> result.set(rateLimiter.executeWithLock(() -> "should-not-run", "timeout-test")));
        future.get(10, TimeUnit.SECONDS);

        assertNull(result.get(), "Should return null on lock timeout");

        testDone.countDown();
        holder.join(5000);
        executor.shutdown();
    }

    @Test
    void testExecuteWithLockCheckedThrowsIOExceptionOnTimeout() throws Exception {
        ApiRateLimiter.lockTimeoutSeconds = 1;
        ReentrantLock lock = getInternalLock();

        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch testDone = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                lockHeld.countDown();
                testDone.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });
        holder.start();
        assertTrue(lockHeld.await(5, TimeUnit.SECONDS));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> {
            assertThrows(IOException.class, () ->
                    rateLimiter.executeWithLockChecked(() -> "should-not-run", "checked-timeout"));
        });
        future.get(10, TimeUnit.SECONDS);

        testDone.countDown();
        holder.join(5000);
        executor.shutdown();
    }

    @Test
    void testExecuteWithLockVoidSilentlyReturnsOnTimeout() throws Exception {
        ApiRateLimiter.lockTimeoutSeconds = 1;
        ReentrantLock lock = getInternalLock();

        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch testDone = new CountDownLatch(1);
        AtomicBoolean actionRan = new AtomicBoolean(false);

        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                lockHeld.countDown();
                testDone.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });
        holder.start();
        assertTrue(lockHeld.await(5, TimeUnit.SECONDS));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() ->
                rateLimiter.executeWithLockVoid(() -> actionRan.set(true), "void-timeout"));
        future.get(10, TimeUnit.SECONDS);

        assertTrue(!actionRan.get(), "Action should not run when lock times out");

        testDone.countDown();
        holder.join(5000);
        executor.shutdown();
    }

    // --- Interrupt handling ---

    @Test
    void testExecuteWithLockReturnsNullOnInterrupt() throws Exception {
        ReentrantLock lock = getInternalLock();

        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch testDone = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                lockHeld.countDown();
                testDone.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });
        holder.start();
        assertTrue(lockHeld.await(5, TimeUnit.SECONDS));

        AtomicReference<String> result = new AtomicReference<>("sentinel");
        Thread worker = new Thread(() -> result.set(rateLimiter.executeWithLock(() -> "nope", "interrupt-test")));
        worker.start();
        Thread.sleep(50); // let worker start waiting on lock
        worker.interrupt();
        worker.join(5000);

        assertNull(result.get(), "Should return null when interrupted");

        testDone.countDown();
        holder.join(5000);
    }

    @Test
    void testExecuteWithLockCheckedThrowsIOExceptionOnInterrupt() throws Exception {
        ReentrantLock lock = getInternalLock();

        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch testDone = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                lockHeld.countDown();
                testDone.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });
        holder.start();
        assertTrue(lockHeld.await(5, TimeUnit.SECONDS));

        AtomicReference<Throwable> caught = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                rateLimiter.executeWithLockChecked(() -> "nope", "checked-interrupt");
            } catch (Throwable t) {
                caught.set(t);
            }
        });
        worker.start();
        Thread.sleep(50);
        worker.interrupt();
        worker.join(5000);

        assertNotNull(caught.get(), "Should throw on interrupt");
        assertTrue(caught.get() instanceof IOException, "Should throw IOException wrapping InterruptedException");

        testDone.countDown();
        holder.join(5000);
    }

    // --- Exception propagation from supplier ---

    @Test
    void testExecuteWithLockPropagatesRuntimeException() {
        assertThrows(RuntimeException.class, () ->
                rateLimiter.executeWithLock(() -> {
                    throw new RuntimeException("boom");
                }, "exception-test"));
    }

    @Test
    void testExecuteWithLockVoidPropagatesRuntimeException() {
        assertThrows(RuntimeException.class, () ->
                rateLimiter.executeWithLockVoid(() -> {
                    throw new RuntimeException("boom");
                }, "void-exception-test"));
    }

    @Test
    void testLockReleasedAfterSupplierException() {
        try {
            rateLimiter.executeWithLock(() -> {
                throw new RuntimeException("fail");
            }, "release-test");
        } catch (RuntimeException ignored) {
            // expected
        }

        // Lock should be released — next call should succeed
        String result = rateLimiter.executeWithLock(() -> "recovered", "after-exception");
        assertEquals("recovered", result);
    }

    @Test
    void testLockReleasedAfterCheckedSupplierException() throws IOException, KiteException {
        try {
            rateLimiter.executeWithLockChecked(() -> {
                throw new IOException("fail");
            }, "release-test");
        } catch (Exception ignored) {
            // expected
        }

        // Lock should be released
        String result = rateLimiter.executeWithLock(() -> "recovered", "after-checked-exception");
        assertEquals("recovered", result);
    }

    // --- Fair ordering ---

    @Test
    void testFairOrderingFIFO() throws Exception {
        List<Integer> executionOrder = new ArrayList<>();
        ReentrantLock lock = getInternalLock();

        // Hold the lock so threads queue up in order
        lock.lock();

        int threadCount = 5;
        CountDownLatch allQueued = new CountDownLatch(threadCount);
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            int idx = i;
            Thread t = new Thread(() -> {
                allQueued.countDown();
                rateLimiter.executeWithLockVoid(() -> {
                    synchronized (executionOrder) {
                        executionOrder.add(idx);
                    }
                }, "fair-" + idx);
            });
            t.start();
            threads.add(t);
            // Small delay to ensure threads queue in order
            Thread.sleep(30);
        }

        assertTrue(allQueued.await(5, TimeUnit.SECONDS), "All threads should be queued");
        // Give a bit more time for all threads to actually be waiting on the lock
        Thread.sleep(50);

        // Release the lock — fair ordering should process in FIFO
        lock.unlock();

        for (Thread t : threads) {
            t.join(10000);
        }

        assertEquals(threadCount, executionOrder.size(), "All threads should have executed");
        // With fair lock + staggered starts, execution should be in order
        for (int i = 0; i < threadCount; i++) {
            assertEquals(i, executionOrder.get(i), "Thread " + i + " should execute in FIFO order");
        }
    }

    // --- Helper ---

    private ReentrantLock getInternalLock() {
        try {
            Field lockField = ApiRateLimiter.class.getDeclaredField("apiLock");
            lockField.setAccessible(true);
            return (ReentrantLock) lockField.get(rateLimiter);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access apiLock", e);
        }
    }
}
