package com.vish.fno.reader.shoonya.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ShoonyaSessionTest {

    @Test
    void shouldComputeSha256Hex() {
        String hash = ShoonyaSession.sha256Hex("test");
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", hash);
    }

    @Test
    void shouldComputeAppKeyHash() {
        String appKey = ShoonyaSession.sha256Hex("FA12345|my_api_secret");
        assertNotNull(appKey);
        assertEquals(64, appKey.length());
    }

    @Test
    void shouldGenerateSixDigitTOTP() {
        String totp = ShoonyaSession.generateTOTP("JBSWY3DPEHPK3PXP");
        assertNotNull(totp);
        assertEquals(6, totp.length());
    }

    @Test
    void shouldNotBeInitialisedBeforeAuth() {
        ShoonyaSession session = new ShoonyaSession(
            "user", "pass", "vendor", "secret", "TOTP", "imei", false
        );
        assertFalse(session.isInitialised());
    }
}
