package com.vish.fno.reader.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vish.fno.model.order.StrikePolicy;
import com.zerodhatech.models.Instrument;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * ADR 0062 — {@link KiteService#getOptionStock} must honour the strike-policy correction flag.
 *
 * <p>The resolution rules themselves are pinned by {@code StrikePolicyEntryWiringTest}; this class
 * pins the <b>wiring</b>: that the flag threaded through the constructor actually reaches the
 * strike that production trades. Without this test the flag could be stored and ignored, which is
 * exactly the failure mode ADR 0062 documents — a policy value that is accepted and silently
 * discarded.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KiteServiceStrikePolicyTest {

    private static final String CACHE =
            "/src/test/java/resources/instrument_cache/instruments_2025-12-31.json";
    private static final String NIFTY = "NIFTY 50";
    /** 25,080 rounds UP to an ATM of 25,100; the last strike below spot is 25,050. */
    private static final double PRICE_ROUNDS_UP = 25_080;

    private static List<Instrument> instruments;

    @Mock
    private InstrumentCache instrumentCache;

    @BeforeAll
    @SneakyThrows
    static void load() {
        ObjectMapper mapper = new ObjectMapper();
        File f = new File(System.getProperty("user.dir") + CACHE);
        instruments = mapper.readValue(
                f, mapper.getTypeFactory().constructCollectionType(List.class, Instrument.class));
    }

    @SneakyThrows
    private KiteService serviceWithCorrection(boolean enabled) {
        KiteService svc = new KiteService("secret", "apiKey", "userId", List.of(), false, false, enabled);
        Field f = KiteService.class.getDeclaredField("instrumentCache");
        f.setAccessible(true);
        f.set(svc, instrumentCache);
        when(instrumentCache.getInstruments()).thenReturn(instruments);
        return svc;
    }

    private static int strikeOf(String symbol) {
        return instruments.stream()
                .filter(i -> symbol.equals(i.getTradingsymbol()))
                .findFirst()
                .map(i -> (int) Math.round(Double.parseDouble(i.getStrike())))
                .orElseThrow(() -> new AssertionError("symbol not in instrument list: " + symbol));
    }

    @Test
    void correctionDisabled_tradesTheLegacyStrike() {
        String sym = serviceWithCorrection(false)
                .getOptionStock(NIFTY, PRICE_ROUNDS_UP, true, StrikePolicy.ATM);
        assertEquals(25_050, strikeOf(sym), "flag off must reproduce the historical strike");
    }

    @Test
    void correctionEnabled_tradesTheContractStrike() {
        String sym = serviceWithCorrection(true)
                .getOptionStock(NIFTY, PRICE_ROUNDS_UP, true, StrikePolicy.ATM);
        assertEquals(25_100, strikeOf(sym), "flag on must trade the strike StrikePolicy documents");
    }

    @Test
    void correctionEnabled_makesItm2Reachable() {
        KiteService svc = serviceWithCorrection(true);
        int itm1 = strikeOf(svc.getOptionStock(NIFTY, PRICE_ROUNDS_UP, true, StrikePolicy.ITM_1));
        int itm2 = strikeOf(svc.getOptionStock(NIFTY, PRICE_ROUNDS_UP, true, StrikePolicy.ITM_2));
        assertEquals(50, itm1 - itm2, "ITM_2 must be one interval deeper than ITM_1, not identical");
    }

    @Test
    void correctionDisabled_leavesItm2Unreachable() {
        KiteService svc = serviceWithCorrection(false);
        assertEquals(svc.getOptionStock(NIFTY, PRICE_ROUNDS_UP, true, StrikePolicy.ITM_1),
                svc.getOptionStock(NIFTY, PRICE_ROUNDS_UP, true, StrikePolicy.ITM_2),
                "the legacy collapse is the documented pre-fix behaviour");
    }
}
