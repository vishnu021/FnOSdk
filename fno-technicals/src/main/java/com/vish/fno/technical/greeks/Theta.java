package com.vish.fno.technical.greeks;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/*
 * Theta measures change in option price with respect to Time till maturity
 * */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Theta extends OptionGreek {

    public static double calculateTheta(double stockPrice, double strikePrice, double timeToExpiryInYears, double riskFreeRate, double volatility, boolean isCall) {
        double d1 = calculateD1(stockPrice, strikePrice, timeToExpiryInYears, riskFreeRate, volatility);
        double d2 = d1 - volatility * Math.sqrt(timeToExpiryInYears);
        double pdfD1 = getDensity(d1); // N'(d1)

        double commonTerm = -stockPrice * pdfD1 * volatility / (2 * Math.sqrt(timeToExpiryInYears));

        if (isCall) {
            return commonTerm - riskFreeRate * strikePrice * Math.exp(-riskFreeRate * timeToExpiryInYears) * cumulativeProbability(d2);
        } else {
            return commonTerm + riskFreeRate * strikePrice * Math.exp(-riskFreeRate * timeToExpiryInYears) * cumulativeProbability(-d2);
        }
    }
}
