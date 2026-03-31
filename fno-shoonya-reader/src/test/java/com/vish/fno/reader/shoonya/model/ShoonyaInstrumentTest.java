package com.vish.fno.reader.shoonya.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShoonyaInstrumentTest {

    @Test
    void shouldCreateInstrumentFromNFOFields() {
        ShoonyaInstrument instrument = new ShoonyaInstrument(
            "NFO", 78900, 65, "NIFTY", "NIFTY28APR26P17200",
            "28-APR-2026", "OPTIDX", "PE", 17200.0, 0.05
        );

        assertEquals("NFO", instrument.exchange());
        assertEquals(78900L, instrument.token());
        assertEquals(65, instrument.lotSize());
        assertEquals("NIFTY", instrument.symbol());
        assertEquals("NIFTY28APR26P17200", instrument.tradingSymbol());
        assertEquals("28-APR-2026", instrument.expiry());
        assertEquals("OPTIDX", instrument.instrumentType());
        assertEquals("PE", instrument.optionType());
        assertEquals(17200.0, instrument.strikePrice());
        assertEquals(0.05, instrument.tickSize());
    }

    @Test
    void shouldCreateFutureInstrument() {
        ShoonyaInstrument future = new ShoonyaInstrument(
            "NFO", 66691, 65, "NIFTY", "NIFTY28APR26F",
            "28-APR-2026", "FUTIDX", "XX", -0.01, 0.1
        );

        assertEquals("FUTIDX", future.instrumentType());
        assertEquals("XX", future.optionType());
        assertEquals(-0.01, future.strikePrice());
    }
}
