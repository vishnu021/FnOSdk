package com.vish.fno.reader.core;

import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Slf4j
class ApiRateLimiter {

    private static final long LOCK_WAIT_LOG_THRESHOLD_MS = 100;

    static volatile long lockTimeoutSeconds = 12;

    private final ReentrantLock apiLock = new ReentrantLock(true);

    <T> T executeWithLock(Supplier<T> action, String operationName) {
        long waitStart = System.nanoTime();
        if (!acquireLock(operationName)) {
            return null;
        }
        try {
            logWaitTime(waitStart, operationName);
            return action.get();
        } finally {
            apiLock.unlock();
        }
    }

    void executeWithLockVoid(Runnable action, String operationName) {
        long waitStart = System.nanoTime();
        if (!acquireLock(operationName)) {
            return;
        }
        try {
            logWaitTime(waitStart, operationName);
            action.run();
        } finally {
            apiLock.unlock();
        }
    }

    <T> T executeWithLockChecked(CheckedSupplier<T> action, String operationName) throws IOException, KiteException {
        long waitStart = System.nanoTime();
        if (!acquireLockChecked(operationName)) {
            throw new IOException("API lock timeout for " + operationName);
        }
        try {
            logWaitTime(waitStart, operationName);
            return action.get();
        } finally {
            apiLock.unlock();
        }
    }

    private boolean acquireLock(String operationName) {
        try {
            if (!apiLock.tryLock(lockTimeoutSeconds, TimeUnit.SECONDS)) {
                log.error("API lock timeout after {}s for {}", lockTimeoutSeconds, operationName);
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for API lock for {}", operationName, e);
            return false;
        }
    }

    private boolean acquireLockChecked(String operationName) throws IOException {
        try {
            if (!apiLock.tryLock(lockTimeoutSeconds, TimeUnit.SECONDS)) {
                log.error("API lock timeout after {}s for {}", lockTimeoutSeconds, operationName);
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for API lock", e);
        }
    }

    private void logWaitTime(long waitStart, String operationName) {
        long waitMs = (System.nanoTime() - waitStart) / 1_000_000;
        if (waitMs > LOCK_WAIT_LOG_THRESHOLD_MS) {
            log.info("API lock acquired for {} after {}ms wait", operationName, waitMs);
        }
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws IOException, KiteException;
    }
}
