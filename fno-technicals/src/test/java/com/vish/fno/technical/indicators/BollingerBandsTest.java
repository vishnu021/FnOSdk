package com.vish.fno.technical.indicators;

import com.vish.fno.model.Candle;
import com.vish.fno.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.List;

import static com.vish.fno.util.FileUtils.getBBData;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class BollingerBandsTest {

    private static final int DURATION = 20;
    private static final double MULTIPLIER = 2.0;
    private static final String resourcePath = "." + File.separator + "src" + File.separator + "test" + File.separator + "resources" + File.separator + "RELIANCE_2023_08_11" + File.separator;
    private static final String currentDayFile = "RELIANCE_2023-08-11.json";
    private static final String prevDayFile = "RELIANCE_2023-08-10.json";
    private BollingerBands underTest;

    @BeforeEach
    public void before() {
        MockitoAnnotations.openMocks(this);
        underTest = new BollingerBands(DURATION, MULTIPLIER);
    }

    @Test
    public void testCalculateBB20() {
        //Given
        List<Candle> candles = FileUtils.getCandleData(resourcePath + currentDayFile);
        List<Double> expectedBB20 = getBBData(resourcePath + "bb20_2.txt");
        // When
        List<Double> bb20 = underTest.calculate(candles);
        // Then
        for (int i = 0; i < bb20.size(); i++) {
            assertEquals(bb20.get(i), expectedBB20.get(i), 0.001);
        }
    }

    @Test
    public void testCalculateBB20withPrevData() {
        //Given
        List<Candle> candles = FileUtils.getCandleData(resourcePath + currentDayFile);
        List<Candle> prevDayCandles = FileUtils.getCandleData(resourcePath + prevDayFile);
        List<Double> expectedBB20 = getBBData(resourcePath + "bb20_2_with_prev_data.txt");
        // When
        List<Double> bb20 = underTest.calculate(candles, prevDayCandles);
        // Then
        for (int i = 0; i < bb20.size(); i++) {
            assertEquals(bb20.get(i), expectedBB20.get(i), 0.001);
        }
    }

}