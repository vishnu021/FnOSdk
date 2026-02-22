package com.vish.fno.reader.core;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Margin;
import com.zerodhatech.models.User;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.function.Supplier;

import static com.vish.fno.util.FnoConstants.EQUITY;

@Slf4j
class KiteSession {

    private final ApiRateLimiter apiRateLimiter;
    @Getter
    private final KiteConnect kiteSdk;
    private final String apiSecret;
    @Getter
    private final boolean placeOrders;
    @Getter
    private volatile boolean initialised;

    KiteSession(String apiKey, String userId, String apiSecret, boolean placeOrders) {
        this.apiRateLimiter = new ApiRateLimiter();
        this.apiSecret = apiSecret;
        this.placeOrders = placeOrders;
        this.kiteSdk = createKiteSdk(apiKey, userId);
    }

    void authenticate(String requestToken, Runnable postAuthHook) {
        apiRateLimiter.executeWithLockVoid(() -> {
            try {
                User user = kiteSdk.generateSession(requestToken, apiSecret);
                kiteSdk.setAccessToken(user.accessToken);
                kiteSdk.setPublicToken(user.publicToken);
                kiteSdk.setSessionExpiryHook(() -> log.info("kite session expired"));

                postAuthHook.run();

                Margin margins = kiteSdk.getMargins(EQUITY);
                log.info("available_cash={}", margins.available.cash);
                log.info("utilised_debits={}", margins.utilised.debits);
                initialised = true;
            } catch (KiteException | IOException e) {
                log.error("Error while Initialising KiteService", e);
            }
        }, "authenticate");
    }

    <T> T executeWithLock(Supplier<T> action, String operationName) {
        return apiRateLimiter.executeWithLock(action, operationName);
    }

    void executeWithLockVoid(Runnable action, String operationName) {
        apiRateLimiter.executeWithLockVoid(action, operationName);
    }

    <T> T executeWithLockChecked(ApiRateLimiter.CheckedSupplier<T> action, String operationName)
            throws IOException, KiteException {
        return apiRateLimiter.executeWithLockChecked(action, operationName);
    }

    private KiteConnect createKiteSdk(String apiKey, String userId) {
        KiteConnect kiteConnect = new KiteConnect(apiKey, true);
        kiteConnect.setUserId(userId);
        kiteConnect.setSessionExpiryHook(() -> log.info("session expired"));
        return kiteConnect;
    }
}
