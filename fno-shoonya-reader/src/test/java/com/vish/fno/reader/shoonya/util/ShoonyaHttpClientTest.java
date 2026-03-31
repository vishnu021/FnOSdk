package com.vish.fno.reader.shoonya.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShoonyaHttpClientTest {

    @Test
    void shouldEncodeSimpleSymbol() {
        assertEquals("NIFTY28APR26C17200", ShoonyaHttpClient.encodeSymbol("NIFTY28APR26C17200"));
    }

    @Test
    void shouldEncodeSymbolWithAmpersand() {
        assertEquals("M%26M-EQ", ShoonyaHttpClient.encodeSymbol("M&M-EQ"));
    }

    @Test
    void shouldEncodeSymbolWithSpaces() {
        assertEquals("NIFTY+INDEX", ShoonyaHttpClient.encodeSymbol("NIFTY INDEX"));
    }
}
