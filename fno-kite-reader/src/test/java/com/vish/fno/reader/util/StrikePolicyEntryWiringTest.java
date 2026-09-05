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

/**
 * ADR 0062 — the flag-gated entry point that decides which strike production actually trades.
 *
 * <p>{@link OptionPriceUtils#getStrikeByPolicy} (the contract) and {@link
 * OptionPriceUtils#getITMStock} / {@link OptionPriceUtils#getOTMStock} (the legacy floor/ceiling
 * helpers) both already exist and are both already correct for what they do. What has never
 * existed is a single entry point that chooses between them, which is why production has been
 * running the legacy rule since inception.
 *
 * <p>Correcting it changes the strike of <b>every production option order</b>, so the switch is
 * explicit and defaults to the legacy behaviour. These tests pin <b>both</b> sides of the flag:
 * turning it off must reproduce the historical strike exactly, or the rollback is not a rollback.
 *
 * <p>Measured 2026-09-05 across 78 matched prod/backtest pairs: the two rules disagree on 49% of
 * orders even when both see an identical spot to the paisa, and each rule reproduces its own
 * side's strike 78/78 = 100%.
 */
class StrikePolicyEntryWiringTest {

    private static final String CACHE =
            "/src/test/java/resources/instrument_cache/instruments_2025-12-31.json";
    private static final String NIFTY = "NIFTY 50";

    private static List<Instrument> instruments;

    @BeforeAll
    @SneakyThrows
    static void load() {
        ObjectMapper mapper = new ObjectMapper();
        File f = new File(System.getProperty("user.dir") + CACHE);
        instruments = mapper.readValue(
                f, mapper.getTypeFactory().constructCollectionType(List.class, Instrument.class));
    }

    private static int strikeOf(String symbol) {
        return instruments.stream()
                .filter(i -> symbol.equals(i.getTradingsymbol()))
                .findFirst()
                .map(i -> (int) Math.round(Double.parseDouble(i.getStrike())))
                .orElseThrow(() -> new AssertionError("symbol not in instrument list: " + symbol));
    }

    private static int entry(double price, boolean isCall, StrikePolicy policy, boolean corrected) {
        return strikeOf(OptionPriceUtils.getStrikeForEntry(
                NIFTY, price, isCall, policy, instruments, corrected));
    }

    /** 25,080 rounds UP to an ATM of 25,100, while the last strike below spot is 25,050. */
    private static final double PRICE_ROUNDS_UP = 25_080;

    @Test
    void correctionDisabled_atmResolvesOneStrikeInTheMoney() {
        // The defect, pinned: legacy ATM is getITMStock, i.e. the last strike BELOW spot.
        assertEquals(25_050, entry(PRICE_ROUNDS_UP, true, StrikePolicy.ATM, false));
    }

    @Test
    void correctionEnabled_atmResolvesTheNearestStrike() {
        assertEquals(25_100, entry(PRICE_ROUNDS_UP, true, StrikePolicy.ATM, true));
    }

    @Test
    void correctionDisabled_itm2CollapsesOntoItm1() {
        assertEquals(entry(PRICE_ROUNDS_UP, true, StrikePolicy.ITM_1, false),
                entry(PRICE_ROUNDS_UP, true, StrikePolicy.ITM_2, false),
                "legacy discards the offset, so ITM_2 must reproduce ITM_1");
    }

    @Test
    void correctionEnabled_itm2IsOneIntervalDeeperThanItm1() {
        int itm1 = entry(PRICE_ROUNDS_UP, true, StrikePolicy.ITM_1, true);
        int itm2 = entry(PRICE_ROUNDS_UP, true, StrikePolicy.ITM_2, true);
        assertNotEquals(itm1, itm2, "ITM_2 must not collapse onto ITM_1 once corrected");
        assertEquals(50, itm1 - itm2, "a CE steps DOWN one 50-point interval per ITM level");
    }

    @Test
    void correctionDisabled_otmPoliciesUseTheLegacyOtmHelper() {
        assertEquals(strikeOf(OptionPriceUtils.getOTMStock(NIFTY, PRICE_ROUNDS_UP, true, instruments)),
                entry(PRICE_ROUNDS_UP, true, StrikePolicy.OTM_1, false));
        assertEquals(entry(PRICE_ROUNDS_UP, true, StrikePolicy.OTM_1, false),
                entry(PRICE_ROUNDS_UP, true, StrikePolicy.OTM_2, false),
                "legacy discards the offset, so OTM_2 must reproduce OTM_1");
    }

    @Test
    void correctionDisabled_reproducesTheLegacyHelpersExactlyForEveryPolicy() {
        // The rollback guarantee: flag off == the code that has been in production all along.
        String legacyItm = OptionPriceUtils.getITMStock(NIFTY, PRICE_ROUNDS_UP, false, instruments);
        String legacyOtm = OptionPriceUtils.getOTMStock(NIFTY, PRICE_ROUNDS_UP, false, instruments);
        for (StrikePolicy p : List.of(StrikePolicy.ATM, StrikePolicy.ITM_1, StrikePolicy.ITM_2)) {
            assertEquals(legacyItm,
                    OptionPriceUtils.getStrikeForEntry(NIFTY, PRICE_ROUNDS_UP, false, p, instruments, false),
                    "policy " + p + " must map to getITMStock when correction is off");
        }
        for (StrikePolicy p : List.of(StrikePolicy.OTM_1, StrikePolicy.OTM_2)) {
            assertEquals(legacyOtm,
                    OptionPriceUtils.getStrikeForEntry(NIFTY, PRICE_ROUNDS_UP, false, p, instruments, false),
                    "policy " + p + " must map to getOTMStock when correction is off");
        }
    }

    @Test
    void correctionEnabled_matchesTheDocumentedContractForEveryPolicy() {
        for (StrikePolicy p : StrikePolicy.values()) {
            assertEquals(
                    OptionPriceUtils.getStrikeByPolicy(NIFTY, PRICE_ROUNDS_UP, true, p, instruments),
                    OptionPriceUtils.getStrikeForEntry(NIFTY, PRICE_ROUNDS_UP, true, p, instruments, true),
                    "corrected entry must be the contract resolution for " + p);
        }
    }
}
