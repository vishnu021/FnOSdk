package com.vish.fno.reader.shoonya.core;

import com.vish.fno.reader.shoonya.model.ShoonyaOpenOrder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoonyaOrderExecutorTest {

    @Test
    void shouldReturnFailedOrderWhenSessionNotInitialised() {
        ShoonyaSession session = new ShoonyaSession(
            "user", "pass", "vendor", "secret", "TOTP", "imei", true
        );
        ShoonyaInstrumentCache cache = new ShoonyaInstrumentCache(List.of(), session.getHttpClient());
        ShoonyaOrderExecutor executor = new ShoonyaOrderExecutor(session, cache);

        Optional<ShoonyaOpenOrder> result = executor.buyOrder("NIFTY28APR26C22000", 65, "test", true);
        assertTrue(result.isPresent());
        assertFalse(result.get().isOrderPlaced());
        assertEquals("Session not initialized", result.get().errorMessage());
    }

    @Test
    void shouldReturnTestOrderWhenPlaceOrderDisabled() {
        ShoonyaSession session = new ShoonyaSession(
            "user", "pass", "vendor", "secret", "TOTP", "imei", false
        );
        ShoonyaInstrumentCache cache = new ShoonyaInstrumentCache(List.of(), session.getHttpClient());
        ShoonyaOrderExecutor executor = new ShoonyaOrderExecutor(session, cache);

        Optional<ShoonyaOpenOrder> result = executor.buyOrder("NIFTY28APR26C22000", 65, "test", false);
        assertTrue(result.isPresent());
        assertTrue(result.get().isOrderPlaced());
    }
}
