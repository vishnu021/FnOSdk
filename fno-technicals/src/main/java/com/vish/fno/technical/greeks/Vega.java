package com.vish.fno.technical.greeks;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/*
* Vega measures the sensitivity of an option's price to changes in the volatility of the underlying asset
* */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Vega extends OptionGreek {

    public static double calculateVega(
            double stockPrice,
            double strikePrice,
            double timeToExpiryInYears,
            double riskFreeRate,
            double volatility) {

        double d1 = calculateD1(stockPrice, strikePrice, timeToExpiryInYears, riskFreeRate, volatility);
        double pdfD1 = getDensity(d1); // N'(d1)

        return stockPrice * pdfD1 * Math.sqrt(timeToExpiryInYears);
    }
}
