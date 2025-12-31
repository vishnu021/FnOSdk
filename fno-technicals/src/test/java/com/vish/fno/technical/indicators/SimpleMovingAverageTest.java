package com.vish.fno.technical.indicators;

import com.vish.fno.model.Candle;
import com.vish.fno.technical.indicators.ma.SimpleMovingAverage;
import com.vish.fno.util.CandleUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.List;

import static com.vish.fno.util.CandleUtils.getSmaData;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class SimpleMovingAverageTest {
    private static final int DURATION = 14;

    private static final String resourcePath = "." + File.separator + "src" + File.separator + "test" + File.separator + "resources" + File.separator + "RELIANCE_2023_03_17" + File.separator;
    private static final String currentDayFile = "RELIANCE_2023-03-17.json";
    private static final String prevDayFile = "RELIANCE_2023-03-16.json";

    private SimpleMovingAverage underTest;

    @BeforeEach
    public void before() {
        MockitoAnnotations.openMocks(this);
        underTest = new SimpleMovingAverage(DURATION);
    }

    @Test
    public void testCalculateSMAWithLastSMA() {
        //Given
        List<Candle> candles = CandleUtils.getCandleData(resourcePath + currentDayFile);
        List<Candle> prevDayCandles = CandleUtils.getPrevDayCandleData(resourcePath + prevDayFile);
        List<Double> expectedEMA14 = getSmaData(resourcePath + "sma14withPrevSMA.txt");
        // When
        List<Double> actualSMA = underTest.calculate(candles, prevDayCandles);
        // Then
        for (int i = 0; i < actualSMA.size(); i++) {
            assertEquals(actualSMA.get(i), expectedEMA14.get(i), 0.001);
        }
    }

    @Test
    public void testCalculateSMA() {
        //Given
        List<Candle> candles = CandleUtils.getCandleData(resourcePath + currentDayFile);
        List<Double> expectedSMA14 = getSmaData(resourcePath + "sma14.txt");
        // When
        List<Double> sma14 = underTest.calculate(candles);
        // Then
        for (int i = 0; i < sma14.size(); i++) {
            assertEquals(sma14.get(i), expectedSMA14.get(i), 0.001);
        }
    }
}