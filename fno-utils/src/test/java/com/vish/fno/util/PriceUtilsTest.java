package com.vish.fno.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.vish.fno.util.PriceUtils.round;
import static com.vish.fno.util.PriceUtils.roundTo5Paise;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceUtilsTest {

    @Test
    void roundToNearestFiveCentsTest() {
        double originalValue = 10.03;
        double actualValue = round(originalValue);
        double expectedValue = 10.05;
        assertEquals(expectedValue, actualValue);
    }

    @Test
    void roundToNearestFiveCentsTest2() {
        double originalValue = 0.01;
        double actualValue = round(originalValue);
        double expectedValue = 0.00;
        assertEquals(expectedValue, actualValue);
    }

    @Test
    void roundToNearestFiveCentsTest3() {
        double originalValue = 24157.1;
        BigDecimal actualValue = roundTo5Paise(originalValue);
        BigDecimal expectedValue = BigDecimal.valueOf(24157.1).setScale(2, RoundingMode.HALF_UP);
        assertEquals(expectedValue, actualValue);
    }
}
