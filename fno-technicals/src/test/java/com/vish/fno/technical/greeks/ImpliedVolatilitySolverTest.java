package com.vish.fno.technical.greeks;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpliedVolatilitySolverTest {

    private static final double DELTA = 0.01;

    // ═══════════════════════════════════════════════════════════════════════════════
    // Valid convergence cases
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void solve_shouldConvergeForATMCallOption() {
        // ATM NIFTY call: strike=25000, spot=25000, 7 days to expiry, 15% IV
        double strike = 25000;
        double spot = 25000;
        double timeToExpiry = 7.0 / 365.0;
        double riskFreeRate = 0.065;
        boolean isCall = true;

        // First compute a BS price at known vol, then solve back
        double knownVol = 0.15;
        double bsPrice = BlackScholes
                .calculateOptionPrice(strike, spot, timeToExpiry, riskFreeRate, knownVol, isCall);

        OptionalDouble result = ImpliedVolatilitySolver.solve(strike, spot, timeToExpiry, riskFreeRate, bsPrice, isCall);

        assertTrue(result.isPresent(), "Should converge for ATM call");
        assertEquals(knownVol, result.getAsDouble(), DELTA);
    }

    @Test
    void solve_shouldConvergeForATMPutOption() {
        double strike = 25000;
        double spot = 25000;
        double timeToExpiry = 7.0 / 365.0;
        double riskFreeRate = 0.065;
        boolean isCall = false;

        double knownVol = 0.20;
        double bsPrice = BlackScholes
                .calculateOptionPrice(strike, spot, timeToExpiry, riskFreeRate, knownVol, isCall);

        OptionalDouble result = ImpliedVolatilitySolver.solve(strike, spot, timeToExpiry, riskFreeRate, bsPrice, isCall);

        assertTrue(result.isPresent(), "Should converge for ATM put");
        assertEquals(knownVol, result.getAsDouble(), DELTA);
    }

    @Test
    void solve_shouldConvergeForOTMCall() {
        double strike = 26000;
        double spot = 25000;
        double timeToExpiry = 14.0 / 365.0;
        double riskFreeRate = 0.065;

        double knownVol = 0.25;
        double bsPrice = BlackScholes
                .calculateOptionPrice(strike, spot, timeToExpiry, riskFreeRate, knownVol, true);

        OptionalDouble result = ImpliedVolatilitySolver.solve(strike, spot, timeToExpiry, riskFreeRate, bsPrice, true);

        assertTrue(result.isPresent());
        assertEquals(knownVol, result.getAsDouble(), DELTA);
    }

    @Test
    void solve_shouldConvergeForHighVolatility() {
        double strike = 25000;
        double spot = 25000;
        double timeToExpiry = 30.0 / 365.0;
        double riskFreeRate = 0.065;

        double knownVol = 0.50;
        double bsPrice = BlackScholes
                .calculateOptionPrice(strike, spot, timeToExpiry, riskFreeRate, knownVol, true);

        OptionalDouble result = ImpliedVolatilitySolver.solve(strike, spot, timeToExpiry, riskFreeRate, bsPrice, true);

        assertTrue(result.isPresent());
        assertEquals(knownVol, result.getAsDouble(), DELTA);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Edge cases — should return empty or MIN_VOL
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void solve_shouldReturnEmptyForZeroMarketPrice() {
        OptionalDouble result = ImpliedVolatilitySolver.solve(25000, 25000, 7.0 / 365.0, 0.065, 0, true);
        assertFalse(result.isPresent());
    }

    @Test
    void solve_shouldReturnEmptyForNegativeMarketPrice() {
        OptionalDouble result = ImpliedVolatilitySolver.solve(25000, 25000, 7.0 / 365.0, 0.065, -10, true);
        assertFalse(result.isPresent());
    }

    @Test
    void solve_shouldReturnEmptyForZeroTimeToExpiry() {
        OptionalDouble result = ImpliedVolatilitySolver.solve(25000, 25000, 0, 0.065, 100, true);
        assertFalse(result.isPresent());
    }

    @Test
    void solve_shouldReturnEmptyForNegativeTimeToExpiry() {
        OptionalDouble result = ImpliedVolatilitySolver.solve(25000, 25000, -1.0 / 365.0, 0.065, 100, true);
        assertFalse(result.isPresent());
    }

    @Test
    void solve_shouldReturnMinVolForPriceBelowIntrinsicCall() {
        // Deep ITM call: intrinsic = 25000 - 24000 = 1000, market price = 500
        OptionalDouble result = ImpliedVolatilitySolver.solve(24000, 25000, 7.0 / 365.0, 0.065, 500, true);
        assertTrue(result.isPresent());
        assertEquals(0.01, result.getAsDouble(), DELTA);
    }

    @Test
    void solve_shouldReturnMinVolForPriceBelowIntrinsicPut() {
        // Deep ITM put: intrinsic = 26000 - 25000 = 1000, market price = 500
        OptionalDouble result = ImpliedVolatilitySolver.solve(26000, 25000, 7.0 / 365.0, 0.065, 500, false);
        assertTrue(result.isPresent());
        assertEquals(0.01, result.getAsDouble(), DELTA);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Consistency tests
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void solve_shouldReturnHigherIVForHigherMarketPrice() {
        double strike = 25000;
        double spot = 25000;
        double timeToExpiry = 7.0 / 365.0;
        double riskFreeRate = 0.065;

        double lowPrice = BlackScholes
                .calculateOptionPrice(strike, spot, timeToExpiry, riskFreeRate, 0.10, true);
        double highPrice = BlackScholes
                .calculateOptionPrice(strike, spot, timeToExpiry, riskFreeRate, 0.30, true);

        OptionalDouble lowIV = ImpliedVolatilitySolver.solve(strike, spot, timeToExpiry, riskFreeRate, lowPrice, true);
        OptionalDouble highIV = ImpliedVolatilitySolver.solve(strike, spot, timeToExpiry, riskFreeRate, highPrice, true);

        assertTrue(lowIV.isPresent());
        assertTrue(highIV.isPresent());
        assertTrue(highIV.getAsDouble() > lowIV.getAsDouble(),
                "Higher market price should imply higher IV");
    }
}
