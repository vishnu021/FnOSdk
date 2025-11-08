package com.vish.fno.technical.greeks;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.*;

class BlackScholesTest {


    @Test
    public void testOptionPriceNearExpiry() {
        //Given
        LocalDateTime expiryTime = LocalDateTime.of(2023, Month.AUGUST, 31, 15, 30);
        LocalDateTime currentTime = LocalDateTime.of(2023, Month.AUGUST, 31, 10, 5);

        double strikePrice = 19500;
        double stockPrice = 19382.85; // spotPrice
        double expectedCallOptionPrice = 117.89906113570032;
        double expectedPutOptionPrice = 1.4398836215035544;
        double timeToExpiryInYears = BlackScholes.getTimeToExpiryInMinutes(currentTime, expiryTime) / (365 * 24 * 60);
        double riskFreeRate = 6.25 * 0.01;
        double ceVolatility = 2.9 * 0.01;
        double peVolatility = 14.71 * 0.01;

        //When
        double callOptionPrice = BlackScholes.calculateOptionPrice(stockPrice, strikePrice, timeToExpiryInYears, riskFreeRate, ceVolatility, true);
        double putOptionPrice = BlackScholes.calculateOptionPrice(stockPrice, strikePrice, timeToExpiryInYears, riskFreeRate, peVolatility, false);

        //Then
        assertEquals(callOptionPrice, expectedCallOptionPrice);
        assertEquals(putOptionPrice, expectedPutOptionPrice);
    }

    @Test
    public void testOptionPriceExpiry() {
        //Given
        LocalDateTime expiryTime = LocalDateTime.of(2023, Month.SEPTEMBER, 7, 15, 30);
        LocalDateTime currentTime = LocalDateTime.of(2023, Month.SEPTEMBER, 1, 15, 30);

        double strikePrice = 19450;
        double stockPrice = 19435.30; // spotPrice
        double expectedCallOptionPrice = 91.689480684101;
        double expectedPutOptionPrice = 72.80124469644943;
        double timeToExpiryInYears = BlackScholes.getTimeToExpiryInMinutes(currentTime, expiryTime) / (365 * 24 * 60);
        double riskFreeRate = 6.25 * 0.01;
        double ceVolatility = 7.35 * 0.01;
        double peVolatility = 8.96 * 0.01;

        //When
        double callOptionPrice = BlackScholes.calculateOptionPrice(stockPrice, strikePrice, timeToExpiryInYears, riskFreeRate, ceVolatility, true);
        double putOptionPrice = BlackScholes.calculateOptionPrice(stockPrice, strikePrice, timeToExpiryInYears, riskFreeRate, peVolatility, false);

        //Then
        assertEquals(callOptionPrice, expectedCallOptionPrice);
        assertEquals(putOptionPrice, expectedPutOptionPrice);
    }
}