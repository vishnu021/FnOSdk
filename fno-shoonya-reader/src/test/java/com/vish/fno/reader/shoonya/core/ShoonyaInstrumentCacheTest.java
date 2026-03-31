package com.vish.fno.reader.shoonya.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShoonyaInstrumentCacheTest {

    @Test
    void symbolInfoRecordShouldStoreTokenAndExchange() {
        ShoonyaInstrumentCache.SymbolInfo info = new ShoonyaInstrumentCache.SymbolInfo(78900L, "NFO");
        assertEquals(78900L, info.token());
        assertEquals("NFO", info.exchange());
    }
}
