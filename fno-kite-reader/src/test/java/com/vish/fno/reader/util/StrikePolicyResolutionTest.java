package com.vish.fno.reader.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vish.fno.model.order.StrikePolicy;
import com.zerodhatech.models.Instrument;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR 0062 — {@link StrikePolicy} must select the strike its own contract documents:
 * {@code targetStrike = ATM + (isCall ? -1 : +1) * offset * strikeInterval}, where
 * {@code ATM = round(price / strikeInterval)}.
 *
 * <p>Production has never implemented this. {@code KiteService.getOptionStock} collapsed all five
 * enum values onto two floor/ceiling helpers, so {@code ATM} resolved one strike in-the-money and
 * {@code ITM_2} / {@code OTM_2} were unreachable — silently, with no log line. Measured on
 * 2026-07-24, prod strike moneyness was confined to (0, 1.03] strike-intervals for every order
 * regardless of the configured policy, against the backtest's [−0.49, +1.52].
 *
 * <p>These tests pin the documented semantics against a real instrument cache (NIFTY, 50-point
 * grid, earliest expiry).
 */
class StrikePolicyResolutionTest {

    private static final String CACHE =
            "/src/test/java/resources/instrument_cache/instruments_2025-12-31.json";
    private static final String NIFTY = "NIFTY 50";
    private static final int INTERVAL = 50;

    private static List<Instrument> instruments;

    @BeforeAll
    @SneakyThrows
    static void load() {
        ObjectMapper mapper = new ObjectMapper();
        File f = new File(System.getProperty("user.dir") + CACHE);
        instruments = mapper.readValue(
                f, mapper.getTypeFactory().constructCollectionType(List.class, Instrument.class));
    }

    /**
     * Strike looked up from the instrument list rather than parsed out of the symbol. A symbol like
     * {@code NIFTY2610625050CE} concatenates the expiry code and the strike with no separator, so
     * any regex over the digit run is ambiguous.
     */
    private static int strikeOf(String symbol) {
        return instruments.stream()
                .filter(i -> symbol.equals(i.getTradingsymbol()))
                .findFirst()
                .map(i -> (int) Math.round(Double.parseDouble(i.getStrike())))
                .orElseThrow(() -> new AssertionError("symbol not in instrument list: " + symbol));
    }

    private static int resolve(double price, boolean isCall, StrikePolicy policy) {
        String sym = OptionPriceUtils.getStrikeByPolicy(NIFTY, price, isCall, policy, instruments);
        assertTrue(sym != null && !sym.isBlank(),
                "no symbol for price=" + price + " call=" + isCall + " policy=" + policy);
        return strikeOf(sym);
    }

    // 25,020 rounds DOWN to an ATM of 25,000 — the case where "nearest ITM strike" and
    // "ATM then one step in" disagree for a CE, which is the production defect.
    private static final double PRICE_ROUNDS_DOWN = 25_020;
    // 25,080 rounds UP to an ATM of 25,100.
    private static final double PRICE_ROUNDS_UP = 25_080;

    @Test
    void atmIsTheNearestStrike_notOneStrikeInTheMoney() {
        assertEquals(25_000, resolve(PRICE_ROUNDS_DOWN, true, StrikePolicy.ATM));
        assertEquals(25_100, resolve(PRICE_ROUNDS_UP, true, StrikePolicy.ATM));
        // Same strike for a put — ATM has no direction.
        assertEquals(25_000, resolve(PRICE_ROUNDS_DOWN, false, StrikePolicy.ATM));
    }

    @Test
    void itmStepsTowardTheMoneyByOneIntervalPerLevel() {
        // CE: in-the-money is BELOW spot
        assertEquals(25_000 - INTERVAL, resolve(PRICE_ROUNDS_DOWN, true, StrikePolicy.ITM_1));
        assertEquals(25_000 - 2 * INTERVAL, resolve(PRICE_ROUNDS_DOWN, true, StrikePolicy.ITM_2));
        // PE: in-the-money is ABOVE spot
        assertEquals(25_000 + INTERVAL, resolve(PRICE_ROUNDS_DOWN, false, StrikePolicy.ITM_1));
        assertEquals(25_000 + 2 * INTERVAL, resolve(PRICE_ROUNDS_DOWN, false, StrikePolicy.ITM_2));
    }

    @Test
    void otmStepsAwayFromTheMoney() {
        assertEquals(25_000 + INTERVAL, resolve(PRICE_ROUNDS_DOWN, true, StrikePolicy.OTM_1));
        assertEquals(25_000 + 2 * INTERVAL, resolve(PRICE_ROUNDS_DOWN, true, StrikePolicy.OTM_2));
        assertEquals(25_000 - INTERVAL, resolve(PRICE_ROUNDS_DOWN, false, StrikePolicy.OTM_1));
    }

    /**
     * The headline regression. In production {@code ITM_2} resolved to the same symbol as
     * {@code ITM_1}, and {@code OTM_2} to the same as {@code OTM_1} — the depth was discarded with
     * no warning, which silently voided the planned ITM_2 leakage experiment.
     */
    @Test
    void everyPolicyLevelResolvesToADistinctStrike() {
        int otm2 = resolve(PRICE_ROUNDS_DOWN, true, StrikePolicy.OTM_2);
        int otm1 = resolve(PRICE_ROUNDS_DOWN, true, StrikePolicy.OTM_1);
        int atm = resolve(PRICE_ROUNDS_DOWN, true, StrikePolicy.ATM);
        int itm1 = resolve(PRICE_ROUNDS_DOWN, true, StrikePolicy.ITM_1);
        int itm2 = resolve(PRICE_ROUNDS_DOWN, true, StrikePolicy.ITM_2);

        assertNotEquals(itm1, itm2, "ITM_2 must not collapse onto ITM_1");
        assertNotEquals(otm1, otm2, "OTM_2 must not collapse onto OTM_1");
        assertNotEquals(atm, itm1, "ATM must not resolve to the ITM_1 strike");
        // Monotonic for a CE: deeper OTM is a higher strike, deeper ITM a lower one.
        assertTrue(otm2 > otm1 && otm1 > atm && atm > itm1 && itm1 > itm2,
                "strikes must be monotonic across the policy ladder, got "
                        + otm2 + " " + otm1 + " " + atm + " " + itm1 + " " + itm2);
    }

    @Test
    void fallsBackToTheNearestAvailableStrikeWhenTheExactOneIsAbsent() {
        // Far below the grid's lowest strike (24,000): ITM_2 for a CE would be off the ladder.
        String sym = OptionPriceUtils.getStrikeByPolicy(NIFTY, 24_010, true, StrikePolicy.ITM_2, instruments);
        assertTrue(sym != null && !sym.isBlank(), "must fall back rather than return empty");
        assertTrue(strikeOf(sym) <= 24_000, "fallback should stay on the requested side where possible");
    }
}
