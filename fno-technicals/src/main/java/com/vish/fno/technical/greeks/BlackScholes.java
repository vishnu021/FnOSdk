package com.vish.fno.technical.greeks;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class BlackScholes extends OptionGreek {

    public static double calculateOptionPrice(
            double strikePrice,
            double spotPrice,
            double timeToExpiryInYears,
            double riskFreeRate,
            double volatility,
            boolean isCall) {

        double d1 = calculateD1(spotPrice, strikePrice, timeToExpiryInYears, riskFreeRate, volatility);
        double d2 = d1 - volatility * Math.sqrt(timeToExpiryInYears);

        if (isCall) {
            return spotPrice * cumulativeProbability(d1) - strikePrice * Math.exp(-riskFreeRate * timeToExpiryInYears) * cumulativeProbability(d2);
        } else {
            return strikePrice * Math.exp(-riskFreeRate * timeToExpiryInYears) * cumulativeProbability(-d2) - spotPrice * cumulativeProbability(-d1);
        }
    }

    public static double getTimeToExpiryInMinutes(LocalDateTime now, LocalDateTime expiryTime) {
        return (double) Duration.between(now, expiryTime).toMinutes();
    }
}
