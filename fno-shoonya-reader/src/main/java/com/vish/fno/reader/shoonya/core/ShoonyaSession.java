package com.vish.fno.reader.shoonya.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.vish.fno.reader.shoonya.exception.ShoonyaApiException;
import com.vish.fno.reader.shoonya.util.ShoonyaHttpClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Slf4j
class ShoonyaSession {

    private static final long LOCK_WAIT_LOG_THRESHOLD_MS = 100;
    static volatile long lockTimeoutSeconds = 12;

    private final String userId;
    private final String passwordHash;
    private final String vendorCode;
    private final String apiSecret;
    private final String totpSecret;
    private final String imei;
    @Getter
    private final boolean placeOrders;
    @Getter
    private final ShoonyaHttpClient httpClient;

    private final ReentrantLock apiLock = new ReentrantLock(true);
    @Getter
    private volatile String sessionToken;
    @Getter
    private volatile boolean initialised;

    ShoonyaSession(String userId, String password, String vendorCode,
                   String apiSecret, String totpSecret, String imei,
                   boolean placeOrders) {
        this.userId = userId;
        this.passwordHash = sha256Hex(password);
        this.vendorCode = vendorCode;
        this.apiSecret = apiSecret;
        this.totpSecret = totpSecret;
        this.imei = imei;
        this.placeOrders = placeOrders;
        this.httpClient = new ShoonyaHttpClient();
    }

    void authenticate() {
        executeWithLockVoid(() -> {
            try {
                String appKey = sha256Hex(userId + "|" + apiSecret);
                String totp = generateTOTP(totpSecret);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("source", "API");
                payload.put("apkversion", "1.0.0");
                payload.put("uid", userId);
                payload.put("pwd", passwordHash);
                payload.put("factor2", totp);
                payload.put("vc", vendorCode);
                payload.put("appkey", appKey);
                payload.put("imei", imei);

                JsonNode response = httpClient.post("QuickAuth", payload);
                sessionToken = response.get("susertoken").asText();

                log.info("Shoonya authentication successful for user: {}", userId);
                if (response.has("exarr")) {
                    log.info("Enabled exchanges: {}", response.get("exarr"));
                }

                initialised = true;
            } catch (ShoonyaApiException e) {
                log.error("Shoonya authentication failed: {}", e.getMessage());
            } catch (IOException e) {
                log.error("Error during Shoonya authentication", e);
            }
        }, "authenticate");
    }

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

    <T> T executeWithLockSafe(Supplier<T> action, String operationName, T fallback) {
        long waitStart = System.nanoTime();
        if (!acquireLock(operationName)) {
            return fallback;
        }
        try {
            logWaitTime(waitStart, operationName);
            return action.get();
        } catch (ShoonyaApiException e) {
            log.error("{} failed: {}", operationName, e.getMessage());
            return fallback;
        } finally {
            apiLock.unlock();
        }
    }

    String getUserId() {
        return userId;
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

    private void logWaitTime(long waitStart, String operationName) {
        long waitMs = (System.nanoTime() - waitStart) / 1_000_000;
        if (waitMs > LOCK_WAIT_LOG_THRESHOLD_MS) {
            log.info("API lock acquired for {} after {}ms wait", operationName, waitMs);
        }
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    static String generateTOTP(String base32Secret) {
        long timeStep = System.currentTimeMillis() / 1000 / 30;
        byte[] key = base32Decode(base32Secret);
        byte[] timeBytes = ByteBuffer.allocate(8).putLong(timeStep).array();

        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hmac = mac.doFinal(timeBytes);

            int offset = hmac[hmac.length - 1] & 0x0F;
            int code = ((hmac[offset] & 0x7F) << 24)
                    | ((hmac[offset + 1] & 0xFF) << 16)
                    | ((hmac[offset + 2] & 0xFF) << 8)
                    | (hmac[offset + 3] & 0xFF);

            int otp = code % 1_000_000;
            return String.format("%06d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("TOTP generation failed", e);
        }
    }

    private static byte[] base32Decode(String encoded) {
        String base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        String upper = encoded.toUpperCase().replaceAll("[=\\s]", "");
        int bitBuffer = 0;
        int bitsInBuffer = 0;
        byte[] output = new byte[upper.length() * 5 / 8];
        int outputIndex = 0;

        for (char c : upper.toCharArray()) {
            int val = base32Chars.indexOf(c);
            if (val < 0) {
                continue;
            }
            bitBuffer = (bitBuffer << 5) | val;
            bitsInBuffer += 5;
            if (bitsInBuffer >= 8) {
                bitsInBuffer -= 8;
                output[outputIndex++] = (byte) ((bitBuffer >> bitsInBuffer) & 0xFF);
            }
        }
        byte[] result = new byte[outputIndex];
        System.arraycopy(output, 0, result, 0, outputIndex);
        return result;
    }
}
