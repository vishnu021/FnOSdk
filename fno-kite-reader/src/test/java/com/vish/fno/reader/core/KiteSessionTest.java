package com.vish.fno.reader.core;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Margin;
import com.zerodhatech.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KiteSessionTest {

    @Mock
    private KiteConnect mockKiteSdk;

    private KiteSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = new KiteSession("apiKey", "userId", "secret", true);
    }

    private KiteSession createSessionWithMockSdk(boolean placeOrders) throws Exception {
        KiteSession kiteSession = new KiteSession("apiKey", "userId", "secret", placeOrders);
        Field kiteSdkField = KiteSession.class.getDeclaredField("kiteSdk");
        kiteSdkField.setAccessible(true);
        kiteSdkField.set(kiteSession, mockKiteSdk);
        return kiteSession;
    }

    private User createUser() {
        User user = new User();
        user.accessToken = "token123";
        user.publicToken = "pubToken456";
        return user;
    }

    private Margin createMargin() {
        Margin margin = new Margin();
        margin.available = new Margin.Available();
        margin.available.cash = "50000.0";
        margin.utilised = new Margin.Utilised();
        margin.utilised.debits = "100.0";
        return margin;
    }

    // --- Constructor tests ---

    @Test
    void testConstructor_initializesFields() {
        assertFalse(session.isInitialised(), "Session should not be initialised after construction");
        assertTrue(session.isPlaceOrders(), "placeOrders should be true");
        assertNotNull(session.getKiteSdk(), "kiteSdk should not be null");
    }

    @Test
    void testConstructor_placeOrdersFalse() {
        KiteSession noOrderSession = new KiteSession("key", "user", "secret", false);
        assertFalse(noOrderSession.isPlaceOrders(), "placeOrders should be false");
        assertFalse(noOrderSession.isInitialised(), "Session should not be initialised after construction");
        assertNotNull(noOrderSession.getKiteSdk(), "kiteSdk should not be null");
    }

    // --- Authenticate tests ---

    @Test
    void testAuthenticate_success() throws Throwable {
        KiteSession mockSession = createSessionWithMockSdk(true);
        User user = createUser();
        Margin margin = createMargin();

        when(mockKiteSdk.generateSession(anyString(), anyString())).thenReturn(user);
        when(mockKiteSdk.getMargins(anyString())).thenReturn(margin);

        AtomicBoolean hookCalled = new AtomicBoolean(false);
        mockSession.authenticate("requestToken", () -> hookCalled.set(true));

        assertTrue(mockSession.isInitialised(), "Session should be initialised after successful authenticate");
        assertTrue(hookCalled.get(), "postAuthHook should have been called");
        verify(mockKiteSdk).setAccessToken("token123");
        verify(mockKiteSdk).setPublicToken("pubToken456");
        verify(mockKiteSdk).generateSession("requestToken", "secret");
    }

    @Test
    void testAuthenticate_kiteException() throws Throwable {
        KiteSession mockSession = createSessionWithMockSdk(true);

        when(mockKiteSdk.generateSession(anyString(), anyString()))
                .thenThrow(new KiteException("auth failed", 403));

        AtomicBoolean hookCalled = new AtomicBoolean(false);
        mockSession.authenticate("requestToken", () -> hookCalled.set(true));

        assertFalse(mockSession.isInitialised(), "Session should not be initialised after KiteException");
        assertFalse(hookCalled.get(), "postAuthHook should not be called on failure");
    }

    @Test
    void testAuthenticate_ioException() throws Throwable {
        KiteSession mockSession = createSessionWithMockSdk(true);

        when(mockKiteSdk.generateSession(anyString(), anyString()))
                .thenThrow(new IOException("network error"));

        AtomicBoolean hookCalled = new AtomicBoolean(false);
        mockSession.authenticate("requestToken", () -> hookCalled.set(true));

        assertFalse(mockSession.isInitialised(), "Session should not be initialised after IOException");
        assertFalse(hookCalled.get(), "postAuthHook should not be called on failure");
    }

    // --- executeWithLock tests ---

    @Test
    void testExecuteWithLock_delegatesToRateLimiter() {
        String result = session.executeWithLock(() -> "testResult", "testOp");
        assertEquals("testResult", result, "Should return the supplier result");
    }

    @Test
    void testExecuteWithLockVoid_delegatesToRateLimiter() {
        AtomicBoolean executed = new AtomicBoolean(false);
        session.executeWithLockVoid(() -> executed.set(true), "testOp");
        assertTrue(executed.get(), "Runnable should have been executed");
    }

    @Test
    void testExecuteWithLockChecked_delegatesToRateLimiter() throws IOException, KiteException {
        Integer result = session.executeWithLockChecked(() -> 42, "testOp");
        assertEquals(42, result, "Should return the checked supplier result");
    }

    @Test
    void testExecuteWithLockChecked_propagatesIOException() {
        assertThrows(IOException.class, () ->
                session.executeWithLockChecked(() -> {
                    throw new IOException("test IO error");
                }, "testOp"));
    }

    // --- Volatile visibility test ---

    @Test
    void testInitialised_volatileVisibility() throws Throwable {
        KiteSession mockSession = createSessionWithMockSdk(true);
        User user = createUser();
        Margin margin = createMargin();

        when(mockKiteSdk.generateSession(anyString(), anyString())).thenReturn(user);
        when(mockKiteSdk.getMargins(anyString())).thenReturn(margin);

        assertFalse(mockSession.isInitialised(), "Should be false before authenticate");

        mockSession.authenticate("requestToken", () -> { });

        assertTrue(mockSession.isInitialised(),
                "Volatile field should be visible after authenticate completes");
    }
}
