package com.vish.fno.technical.greeks;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/*
* Delta is a measure of sensitivity of value of an option to its stock price.
* */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Delta extends OptionGreek{

    public static double calculateDelta(double stockPrice, double strikePrice, double timeToExpiryInYears, double riskFreeRate, double volatility, boolean isCall) {
        double d1 = calculateD1(stockPrice, strikePrice, timeToExpiryInYears, riskFreeRate, volatility);

        if (isCall) {
            return cumulativeProbability(d1);
        } else {
            return cumulativeProbability(d1) - 1;
        }
    }
}

