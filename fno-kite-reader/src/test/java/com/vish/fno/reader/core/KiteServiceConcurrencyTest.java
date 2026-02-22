package com.vish.fno.reader.core;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.Position;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;

@Slf4j
@ExtendWith(MockitoExtension.class)
class KiteServiceConcurrencyTest {

    private static final int THREAD_COUNT = 10;

    @Mock
    private KiteConnect mockKiteSdk;

    private KiteService kiteService;

    @BeforeEach
    void setUp() throws Throwable {
        kiteService = new KiteService("secret", "apiKey", "userId", List.of(), false, false);
        // Inject mock KiteConnect into KiteSession via reflection
        Field sessionField = KiteService.class.getDeclaredField("session");
        sessionField.setAccessible(true);
        KiteSession session = (KiteSession) sessionField.get(kiteService);

        Field kiteSdkField = KiteSession.class.getDeclaredField("kiteSdk");
        kiteSdkField.setAccessible(true);
        kiteSdkField.set(session, mockKiteSdk);

        // Mark as initialised
        Field initialisedField = KiteSession.class.getDeclaredField("initialised");
        initialisedField.setAccessible(true);
        initialisedField.set(session, true);
    }

    @Test
    void testGetOrdersSerializedAccess() throws Throwable {
        AtomicInteger concurrentCount = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);

        doAnswer(invocation -> {
            int current = concurrentCount.incrementAndGet();
            maxConcurrent.updateAndGet(max -> Math.max(max, current));
            Thread.sleep(50);
            concurrentCount.decrementAndGet();
            return new ArrayList<Order>();
        }).when(mockKiteSdk).getOrders();

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<List<Order>>> futures = new ArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return kiteService.getOrders();
            }));
        }

        startLatch.countDown();

        for (Future<List<Order>> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(1, maxConcurrent.get(), "Only one thread should access the API at a time");
    }

    @Test
    void testMixedAPIConcurrentCalls() throws Throwable {
        int mixedThreadCount = 20;
        AtomicInteger concurrentCount = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);

        doAnswer(invocation -> {
            int current = concurrentCount.incrementAndGet();
            maxConcurrent.updateAndGet(max -> Math.max(max, current));
            Thread.sleep(30);
            concurrentCount.decrementAndGet();
            return new ArrayList<Order>();
        }).when(mockKiteSdk).getOrders();

        Map<String, List<Position>> positionsMap = new HashMap<>();
        positionsMap.put("net", new ArrayList<>());
        positionsMap.put("day", new ArrayList<>());

        doAnswer(invocation -> {
            int current = concurrentCount.incrementAndGet();
            maxConcurrent.updateAndGet(max -> Math.max(max, current));
            Thread.sleep(30);
            concurrentCount.decrementAndGet();
            return positionsMap;
        }).when(mockKiteSdk).getPositions();

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(mixedThreadCount);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < mixedThreadCount; i++) {
            if (i % 2 == 0) {
                futures.add(executor.submit(() -> {
                    startLatch.await();
                    kiteService.getOrders();
                    return null;
                }));
            } else {
                futures.add(executor.submit(() -> {
                    startLatch.await();
                    kiteService.getPositions();
                    return null;
                }));
            }
        }

        startLatch.countDown();

        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(1, maxConcurrent.get(), "Mixed API calls should still be serialized");
    }

    @Test
    void testLockTimeoutReturnsNull() throws Throwable {
        // Set a very short timeout (1 second) to keep the test fast
        ApiRateLimiter.lockTimeoutSeconds = 1;

        // Acquire the lock externally so KiteService cannot acquire it
        Field sessionField = KiteService.class.getDeclaredField("session");
        sessionField.setAccessible(true);
        KiteSession session = (KiteSession) sessionField.get(kiteService);

        Field rateLimiterField = KiteSession.class.getDeclaredField("apiRateLimiter");
        rateLimiterField.setAccessible(true);
        ApiRateLimiter rateLimiter = (ApiRateLimiter) rateLimiterField.get(session);

        Field lockField = ApiRateLimiter.class.getDeclaredField("apiLock");
        lockField.setAccessible(true);
        ReentrantLock lock = (ReentrantLock) lockField.get(rateLimiter);

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
        assertTrue(lockHeld.await(5, TimeUnit.SECONDS), "Lock holder thread should have started");

        AtomicReference<List<Order>> result = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> result.set(kiteService.getOrders()));
        future.get(10, TimeUnit.SECONDS);

        assertNull(result.get(), "Should return null when lock acquisition times out");

        testDone.countDown();
        holder.join(5000);
        executor.shutdown();

        // Restore original timeout
        ApiRateLimiter.lockTimeoutSeconds = 12;
    }
}
