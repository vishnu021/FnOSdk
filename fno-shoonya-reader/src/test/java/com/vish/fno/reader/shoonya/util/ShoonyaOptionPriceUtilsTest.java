package com.vish.fno.reader.shoonya.util;

import com.vish.fno.reader.shoonya.model.ShoonyaInstrument;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoonyaOptionPriceUtilsTest {

    private static final List<ShoonyaInstrument> TEST_INSTRUMENTS = List.of(
        new ShoonyaInstrument("NFO", 100, 65, "NIFTY", "NIFTY28APR26C22000", "28-APR-2026", "OPTIDX", "CE", 22000, 0.05),
        new ShoonyaInstrument("NFO", 101, 65, "NIFTY", "NIFTY28APR26C22050", "28-APR-2026", "OPTIDX", "CE", 22050, 0.05),
        new ShoonyaInstrument("NFO", 102, 65, "NIFTY", "NIFTY28APR26C22100", "28-APR-2026", "OPTIDX", "CE", 22100, 0.05),
        new ShoonyaInstrument("NFO", 200, 65, "NIFTY", "NIFTY28APR26P22000", "28-APR-2026", "OPTIDX", "PE", 22000, 0.05),
        new ShoonyaInstrument("NFO", 201, 65, "NIFTY", "NIFTY28APR26P22050", "28-APR-2026", "OPTIDX", "PE", 22050, 0.05),
        new ShoonyaInstrument("NFO", 202, 65, "NIFTY", "NIFTY28APR26P22100", "28-APR-2026", "OPTIDX", "PE", 22100, 0.05)
    );

    // Expiries chosen so the chronologically NEAREST (28-DEC-2025) sorts AFTER the farther one
    // (28-APR-2026) lexicographically ("28-APR..." < "28-DEC..." because 'A' < 'D').
    // A lexicographic sort therefore picks the WRONG (April) expiry; a date-aware sort picks December.
    private static final List<ShoonyaInstrument> MULTI_EXPIRY_INSTRUMENTS = List.of(
        new ShoonyaInstrument("NFO", 300, 65, "NIFTY", "NIFTY28DEC25C22000", "28-DEC-2025", "OPTIDX", "CE", 22000, 0.05),
        new ShoonyaInstrument("NFO", 301, 65, "NIFTY", "NIFTY28DEC25C22050", "28-DEC-2025", "OPTIDX", "CE", 22050, 0.05),
        new ShoonyaInstrument("NFO", 302, 65, "NIFTY", "NIFTY28DEC25P22000", "28-DEC-2025", "OPTIDX", "PE", 22000, 0.05),
        new ShoonyaInstrument("NFO", 303, 65, "NIFTY", "NIFTY28DEC25P22050", "28-DEC-2025", "OPTIDX", "PE", 22050, 0.05),
        new ShoonyaInstrument("NFO", 400, 65, "NIFTY", "NIFTY28APR26C22000", "28-APR-2026", "OPTIDX", "CE", 22000, 0.05),
        new ShoonyaInstrument("NFO", 401, 65, "NIFTY", "NIFTY28APR26P22000", "28-APR-2026", "OPTIDX", "PE", 22000, 0.05)
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

    @Test
    void getEarliestExpiryInstruments_selectsChronologicallyNearestNotLexicographic() {
        Optional<List<ShoonyaInstrument>> earliest =
            ShoonyaOptionPriceUtils.getEarliestExpiryInstruments(MULTI_EXPIRY_INSTRUMENTS, "NIFTY", "CE");

        assertTrue(earliest.isPresent());
        // December 2025 is nearer than April 2026 despite sorting later as a string.
        assertEquals("28-DEC-2025", earliest.get().get(0).expiry());
    }

    @Test
    void findStrike_resolvesAgainstNearestExpiry() {
        // ITM call at 22030 must come from the nearest (December) expiry, not the April contracts.
        String symbol = ShoonyaOptionPriceUtils.getITMStock("NIFTY", 22030, true, MULTI_EXPIRY_INSTRUMENTS);
        assertEquals("NIFTY28DEC25C22000", symbol);
    }

    @Test
    void getAllOptionSymbols_returnsOnlyNearestExpiryContracts() {
        List<String> symbols = ShoonyaOptionPriceUtils.getAllOptionSymbols("NIFTY", MULTI_EXPIRY_INSTRUMENTS);

        // 2 CE + 2 PE for 28-DEC-2025; the 28-APR-2026 contracts must be excluded.
        assertEquals(4, symbols.size());
        assertTrue(symbols.stream().allMatch(s -> s.contains("28DEC25")));
    }

    @Test
    void parseExpiry_isCaseInsensitiveAndSortsMalformedLast() {
        assertEquals(LocalDate.of(2026, 4, 28), ShoonyaOptionPriceUtils.parseExpiry("28-APR-2026"));
        assertEquals(LocalDate.of(2026, 4, 28), ShoonyaOptionPriceUtils.parseExpiry("28-Apr-2026"));
        // Unparseable expiries sort last so a bad row is never picked as the nearest expiry.
        assertEquals(LocalDate.MAX, ShoonyaOptionPriceUtils.parseExpiry("not-a-date"));
    }
}
