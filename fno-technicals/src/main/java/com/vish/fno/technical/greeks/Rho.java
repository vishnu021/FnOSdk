package com.vish.fno.technical.greeks;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/*
* Rho measures the sensitivity of an option's price to changes in the interest rate (or risk-free rate)
* */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Rho extends OptionGreek {

    public static double calculateRho(
            double stockPrice,
            double strikePrice,
            double timeToExpiryInYears,
            double riskFreeRate,
            double volatility,
            boolean isCall) {

        double d1 = calculateD1(stockPrice, strikePrice, timeToExpiryInYears, riskFreeRate, volatility);
        double d2 = d1 - volatility * Math.sqrt(timeToExpiryInYears);

        if (isCall) {
            return strikePrice * timeToExpiryInYears * Math.exp(-riskFreeRate * timeToExpiryInYears) * cumulativeProbability(d2);
        } else {
            return -strikePrice * timeToExpiryInYears * Math.exp(-riskFreeRate * timeToExpiryInYears) * cumulativeProbability(-d2);
        }
    }
}
