package com.vish.fno.technical.greeks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlackScholesTest {

    @ParameterizedTest(name = "Option price calculation: strike={0}, spot={1}, isCall={6}")
    @MethodSource("optionPriceTestCases")
    void testOptionPriceCalculation(
            double strikePrice,
            double stockPrice,
            double timeToExpiryInYears,
            double riskFreeRate,
            double volatility,
            double expectedPrice,
            boolean isCall) {
        // When
        double actualPrice = BlackScholes.calculateOptionPrice(
                stockPrice, strikePrice, timeToExpiryInYears, riskFreeRate, volatility, isCall);

        // Then
        assertEquals(expectedPrice, actualPrice, 0.0001);
    }

    private static Stream<Arguments> optionPriceTestCases() {
        // Near expiry test case (Aug 31, 2023)
        LocalDateTime nearExpiryTime = LocalDateTime.of(2023, Month.AUGUST, 31, 15, 30);
        LocalDateTime nearExpiryCurrentTime = LocalDateTime.of(2023, Month.AUGUST, 31, 10, 5);
        double nearExpiryTimeToExpiry = BlackScholes.getTimeToExpiryInMinutes(nearExpiryCurrentTime, nearExpiryTime) / (365 * 24 * 60);

        // Expiry test case (Sep 7, 2023)
        LocalDateTime expiryTime = LocalDateTime.of(2023, Month.SEPTEMBER, 7, 15, 30);
        LocalDateTime expiryCurrentTime = LocalDateTime.of(2023, Month.SEPTEMBER, 1, 15, 30);
        double expiryTimeToExpiry = BlackScholes.getTimeToExpiryInMinutes(expiryCurrentTime, expiryTime) / (365 * 24 * 60);

        double riskFreeRate = 6.25 * 0.01;

        return Stream.of(
                // Near expiry - Call option
                Arguments.of(19500, 19382.85, nearExpiryTimeToExpiry, riskFreeRate, 2.9 * 0.01, 117.89906113570032, true),
                // Near expiry - Put option
                Arguments.of(19500, 19382.85, nearExpiryTimeToExpiry, riskFreeRate, 14.71 * 0.01, 1.4398836215035544, false),
                // Expiry - Call option
                Arguments.of(19450, 19435.30, expiryTimeToExpiry, riskFreeRate, 7.35 * 0.01, 91.689480684101, true),
                // Expiry - Put option
                Arguments.of(19450, 19435.30, expiryTimeToExpiry, riskFreeRate, 8.96 * 0.01, 72.80124469644943, false)
        );
    }

    @ParameterizedTest(name = "Time to expiry calculation: {0} to {1}")
    @CsvSource({
            "2023-08-31T10:05, 2023-08-31T15:30, 325",
            "2023-09-01T15:30, 2023-09-07T15:30, 8640"
    })
    void testTimeToExpiryInMinutes(String currentTimeStr, String expiryTimeStr, int expectedMinutes) {
        // Given
        LocalDateTime currentTime = LocalDateTime.parse(currentTimeStr);
        LocalDateTime expiryTime = LocalDateTime.parse(expiryTimeStr);

        // When
        double actualMinutes = BlackScholes.getTimeToExpiryInMinutes(currentTime, expiryTime);

        // Then
        assertEquals(expectedMinutes, actualMinutes, 0.001);
    }

    @Test
    void testOptionPriceNearExpiry() {
        // Given
        LocalDateTime expiryTime = LocalDateTime.of(2023, Month.AUGUST, 31, 15, 30);
        LocalDateTime currentTime = LocalDateTime.of(2023, Month.AUGUST, 31, 10, 5);

        double strikePrice = 19500;
        double stockPrice = 19382.85;
        double expectedCallOptionPrice = 117.89906113570032;
        double expectedPutOptionPrice = 1.4398836215035544;
        double timeToExpiryInYears = BlackScholes.getTimeToExpiryInMinutes(currentTime, expiryTime) / (365 * 24 * 60);
        double riskFreeRate = 6.25 * 0.01;
        double ceVolatility = 2.9 * 0.01;
        double peVolatility = 14.71 * 0.01;

        // When
        double callOptionPrice = BlackScholes.calculateOptionPrice(stockPrice, strikePrice, timeToExpiryInYears, riskFreeRate, ceVolatility, true);
        double putOptionPrice = BlackScholes.calculateOptionPrice(stockPrice, strikePrice, timeToExpiryInYears, riskFreeRate, peVolatility, false);

        // Then
        assertEquals(callOptionPrice, expectedCallOptionPrice);
        assertEquals(putOptionPrice, expectedPutOptionPrice);
    }

    @Test
    void testOptionPriceExpiry() {
        // Given
        LocalDateTime expiryTime = LocalDateTime.of(2023, Month.SEPTEMBER, 7, 15, 30);
        LocalDateTime currentTime = LocalDateTime.of(2023, Month.SEPTEMBER, 1, 15, 30);

        double strikePrice = 19450;
        double stockPrice = 19435.30;
        double expectedCallOptionPrice = 91.689480684101;
        double expectedPutOptionPrice = 72.80124469644943;
        double timeToExpiryInYears = BlackScholes.getTimeToExpiryInMinutes(currentTime, expiryTime) / (365 * 24 * 60);
        double riskFreeRate = 6.25 * 0.01;
        double ceVolatility = 7.35 * 0.01;
        double peVolatility = 8.96 * 0.01;

        // When
        double callOptionPrice = BlackScholes.calculateOptionPrice(stockPrice, strikePrice, timeToExpiryInYears, riskFreeRate, ceVolatility, true);
        double putOptionPrice = BlackScholes.calculateOptionPrice(stockPrice, strikePrice, timeToExpiryInYears, riskFreeRate, peVolatility, false);

        // Then
        assertEquals(callOptionPrice, expectedCallOptionPrice);
        assertEquals(putOptionPrice, expectedPutOptionPrice);
    }
}
