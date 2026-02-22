package com.vish.fno.reader.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KiteITMResolverTest {

    @Mock
    private KiteService kiteService;

    private KiteITMResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new KiteITMResolver(kiteService);
    }

    // --- resolveITMSymbol tests ---

    @Test
    void testResolveITMSymbol_call() {
        when(kiteService.getITMStock("NIFTY 50", 24950.0, true))
                .thenReturn("NIFTY2610624950CE");

        String result = resolver.resolveITMSymbol("NIFTY 50", 24950.0, true);

        assertEquals("NIFTY2610624950CE", result);
        verify(kiteService).getITMStock("NIFTY 50", 24950.0, true);
    }

    @Test
    void testResolveITMSymbol_put() {
        when(kiteService.getITMStock("NIFTY 50", 24950.0, false))
                .thenReturn("NIFTY2610624950PE");

        String result = resolver.resolveITMSymbol("NIFTY 50", 24950.0, false);

        assertEquals("NIFTY2610624950PE", result);
        verify(kiteService).getITMStock("NIFTY 50", 24950.0, false);
    }

    // --- resolveOTMSymbol tests ---

    @Test
    void testResolveOTMSymbol_call() {
        when(kiteService.getOTMStock("NIFTY BANK", 51200.0, true))
                .thenReturn("BANKNIFTY2610651300CE");

        String result = resolver.resolveOTMSymbol("NIFTY BANK", 51200.0, true);

        assertEquals("BANKNIFTY2610651300CE", result);
        verify(kiteService).getOTMStock("NIFTY BANK", 51200.0, true);
    }

    @Test
    void testResolveOTMSymbol_put() {
        when(kiteService.getOTMStock("NIFTY BANK", 51200.0, false))
                .thenReturn("BANKNIFTY2610651100PE");

        String result = resolver.resolveOTMSymbol("NIFTY BANK", 51200.0, false);

        assertEquals("BANKNIFTY2610651100PE", result);
        verify(kiteService).getOTMStock("NIFTY BANK", 51200.0, false);
    }

    // --- prepareSymbols test ---

    @Test
    void testPrepareSymbols() {
        resolver.prepareSymbols();

        verify(kiteService).appendIndexITMOptions();
    }

    // --- Edge case tests ---

    @Test
    void testResolveITMSymbol_returnsNull() {
        when(kiteService.getITMStock("NIFTY 50", 24950.0, true))
                .thenReturn(null);

        String result = resolver.resolveITMSymbol("NIFTY 50", 24950.0, true);

        assertNull(result, "Should return null when kiteService returns null");
        verify(kiteService).getITMStock("NIFTY 50", 24950.0, true);
    }

    @Test
    void testResolveOTMSymbol_returnsEmptyString() {
        when(kiteService.getOTMStock("NIFTY 50", 24950.0, false))
                .thenReturn("");

        String result = resolver.resolveOTMSymbol("NIFTY 50", 24950.0, false);

        assertEquals("", result, "Should return empty string when kiteService returns empty string");
        verify(kiteService).getOTMStock("NIFTY 50", 24950.0, false);
    }
}
