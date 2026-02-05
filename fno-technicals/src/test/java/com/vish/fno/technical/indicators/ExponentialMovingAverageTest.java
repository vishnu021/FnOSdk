package com.vish.fno.technical.indicators;

import com.vish.fno.model.Candle;
import com.vish.fno.technical.indicators.ma.ExponentialMovingAverage;
import com.vish.fno.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.List;

import static com.vish.fno.util.FileUtils.getEmaData;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class ExponentialMovingAverageTest {
    private static final int DURATION = 14;

    private static final String resourcePath = "." + File.separator + "src" + File.separator + "test" + File.separator + "resources" + File.separator + "RELIANCE_2023_03_17" + File.separator;
    private static final String currentDayFile = "RELIANCE_2023-03-17.json";
    private static final String prevDayFile = "RELIANCE_2023-03-16.json";

    private ExponentialMovingAverage underTest;

    @BeforeEach
    public void before() {
        MockitoAnnotations.openMocks(this);
        underTest = new ExponentialMovingAverage(DURATION);
    }

    @Test
    public void testCalculateEMAWithLastEma() {
        //Given
        List<Candle> candles = FileUtils.getCandleData(resourcePath + currentDayFile);
        List<Candle> prevDayCandles = FileUtils.getPrevDayCandleData(resourcePath + prevDayFile);
        List<Double> expectedEMA14 = getEmaData(resourcePath + "ema14withPrevEma.txt");
        // When
        List<Double> actualEma = underTest.calculate(candles, prevDayCandles);
        // Then
        for (int i = 0; i < actualEma.size(); i++) {
            assertEquals(actualEma.get(i), expectedEMA14.get(i), 0.001);
        }
    }

    @Test
    public void testCalculateEMA() {
        //Given
        List<Candle> candles = FileUtils.getCandleData(resourcePath + currentDayFile);
        List<Double> expectedEMA14 = getEmaData(resourcePath + "ema14.txt");
        // When
        List<Double> ema14 = underTest.calculate(candles);
        // Then
        for (int i = 0; i < ema14.size(); i++) {
            assertEquals(ema14.get(i), expectedEMA14.get(i), 0.001);
        }
    }
}