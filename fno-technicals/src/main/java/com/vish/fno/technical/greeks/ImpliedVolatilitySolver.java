package com.vish.fno.technical.greeks;

import java.util.OptionalDouble;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Newton-Raphson implied volatility solver using Black-Scholes model.
 *
 * <p>Given a market price, solves for the volatility (sigma) that makes the
 * Black-Scholes theoretical price equal to the market price within a tolerance.
 *
 * <p>Convergence guards:
 * <ul>
 *   <li>Volatility bounded to [0.01, 5.0] (1% to 500%)</li>
 *   <li>Maximum 30 iterations</li>
 *   <li>Vega floor to prevent division by near-zero</li>
 *   <li>Returns empty if market price is below intrinsic value</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ImpliedVolatilitySolver {

    private static final int MAX_ITERATIONS = 30;
    private static final double PRICE_TOLERANCE = 0.01;
    private static final double MIN_VOL = 0.01;
    private static final double MAX_VOL = 5.0;
    private static final double MIN_VEGA = 0.01;
    private static final double INITIAL_GUESS = 0.20;

    /**
     * Solves for implied volatility using Newton-Raphson iteration.
     *
     * @param strike       option strike price
     * @param spot         current underlying spot price
     * @param timeToExpiry time to expiry in years (e.g., 7.0/365.0 for 7 days)
     * @param riskFreeRate annual risk-free rate as decimal (e.g., 0.065 for 6.5%)
     * @param marketPrice  current market price of the option
     * @param isCall       true for call option, false for put option
     * @return the implied volatility as decimal (e.g., 0.15 for 15%), or empty if solver fails
     */
    public static OptionalDouble solve(double strike, double spot, double timeToExpiry,
                                        double riskFreeRate, double marketPrice, boolean isCall) {
        if (marketPrice <= 0 || timeToExpiry <= 0) {
            return OptionalDouble.empty();
        }

        double intrinsic = isCall ? Math.max(0, spot - strike) : Math.max(0, strike - spot);
        if (marketPrice < intrinsic) {
            return OptionalDouble.of(MIN_VOL);
        }

        double sigma = INITIAL_GUESS;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double bsPrice = BlackScholes.calculateOptionPrice(strike, spot, timeToExpiry,
                    riskFreeRate, sigma, isCall);
            double diff = bsPrice - marketPrice;

            if (Math.abs(diff) < PRICE_TOLERANCE) {
                return OptionalDouble.of(sigma);
            }

            double vega = Vega.calculateVega(spot, strike, timeToExpiry, riskFreeRate, sigma);

            if (Math.abs(vega) < MIN_VEGA) {
                return OptionalDouble.empty();
            }

            sigma = sigma - diff / vega;

            if (sigma < MIN_VOL) {
                sigma = MIN_VOL;
            }
            if (sigma > MAX_VOL) {
                return OptionalDouble.empty();
            }
        }

        return OptionalDouble.empty();
    }
}
