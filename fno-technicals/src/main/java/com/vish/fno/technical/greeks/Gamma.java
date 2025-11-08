package com.vish.fno.technical.greeks;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/*
 * Gamma is measure of rate of change of Delta
 * */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Gamma extends OptionGreek {

    public static double calculateGamma(double stockPrice, double strikePrice, double timeToExpiryInYears, double riskFreeRate, double volatility) {
        double d1 = calculateD1(stockPrice, strikePrice, timeToExpiryInYears, riskFreeRate, volatility);
        double pdfD1 = getDensity(d1); // N'(d1)
        return pdfD1 / (stockPrice * volatility * Math.sqrt(timeToExpiryInYears));
    }
}
