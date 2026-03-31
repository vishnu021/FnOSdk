package com.vish.fno.reader.shoonya.util;

import com.vish.fno.reader.shoonya.model.ShoonyaInstrument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShoonyaOptionPriceUtilsTest {

    private static final List<ShoonyaInstrument> TEST_INSTRUMENTS = List.of(
        new ShoonyaInstrument("NFO", 100, 65, "NIFTY", "NIFTY28APR26C22000", "28-APR-2026", "OPTIDX", "CE", 22000, 0.05),
        new ShoonyaInstrument("NFO", 101, 65, "NIFTY", "NIFTY28APR26C22050", "28-APR-2026", "OPTIDX", "CE", 22050, 0.05),
        new ShoonyaInstrument("NFO", 102, 65, "NIFTY", "NIFTY28APR26C22100", "28-APR-2026", "OPTIDX", "CE", 22100, 0.05),
        new ShoonyaInstrument("NFO", 200, 65, "NIFTY", "NIFTY28APR26P22000", "28-APR-2026", "OPTIDX", "PE", 22000, 0.05),
        new ShoonyaInstrument("NFO", 201, 65, "NIFTY", "NIFTY28APR26P22050", "28-APR-2026", "OPTIDX", "PE", 22050, 0.05),
        new ShoonyaInstrument("NFO", 202, 65, "NIFTY", "NIFTY28APR26P22100", "28-APR-2026", "OPTIDX", "PE", 22100, 0.05)
    );

    @Test
    void shouldFindITMCallStrike() {
        String symbol = ShoonyaOptionPriceUtils.getITMStock("NIFTY", 22030, true, TEST_INSTRUMENTS);
        assertEquals("NIFTY28APR26C22000", symbol);
    }

    @Test
    void shouldFindOTMCallStrike() {
        String symbol = ShoonyaOptionPriceUtils.getOTMStock("NIFTY", 22030, true, TEST_INSTRUMENTS);
        assertEquals("NIFTY28APR26C22050", symbol);
    }

    @Test
    void shouldFindITMPutStrike() {
        String symbol = ShoonyaOptionPriceUtils.getITMStock("NIFTY", 22030, false, TEST_INSTRUMENTS);
        assertEquals("NIFTY28APR26P22050", symbol);
    }

    @Test
    void shouldFindOTMPutStrike() {
        String symbol = ShoonyaOptionPriceUtils.getOTMStock("NIFTY", 22030, false, TEST_INSTRUMENTS);
        assertEquals("NIFTY28APR26P22000", symbol);
    }

    @Test
    void shouldGetAllOptionSymbols() {
        List<String> symbols = ShoonyaOptionPriceUtils.getAllOptionSymbols("NIFTY", TEST_INSTRUMENTS);
        assertEquals(6, symbols.size());
    }
}
