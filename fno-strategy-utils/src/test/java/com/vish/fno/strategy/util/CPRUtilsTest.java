package com.vish.fno.strategy.util;

import com.vish.fno.model.Candle;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class CPRUtilsTest {

    @Test
    public void testFloorPoints() {
        //Given
        Candle previousDayCandle = new Candle("2023-12-20", 21543.50, 21593.00, 21087.35, 21150.15, 0L, 0L);

        //when
        Map<String, Float> pivotPoints = CPRUtils.getFloorPivots(previousDayCandle);

        List<Map.Entry<String, Float>> list = new ArrayList<>(pivotPoints.entrySet());
        list.sort(Map.Entry.comparingByValue());
        LinkedHashMap<String, Float> arrangedPivots = new LinkedHashMap<>();

        for (Map.Entry<String, Float> entry : list) {
            arrangedPivots.put(entry.getKey(), entry.getValue());
        }

        arrangedPivots.keySet().forEach(p -> log.info("{} -> {}", p, arrangedPivots.get(p)));

        assertEquals(pivotPoints.get("s4"), 20265.55f);
        assertEquals(pivotPoints.get("s3"), 20455.00f);
        assertEquals(pivotPoints.get("s2"), 20771.2f);
        assertEquals(pivotPoints.get("s1"), 20960.65f);
        assertEquals(pivotPoints.get("topCentralPivot"), 21213.5f);
        assertEquals(pivotPoints.get("pivotPoint"), 21276.85f);
        assertEquals(pivotPoints.get("bottomCentralPivot"), 21340.2f);
        assertEquals(pivotPoints.get("r1"), 21466.3f);
        assertEquals(pivotPoints.get("r2"), 21782.5f);
        assertEquals(pivotPoints.get("r3"), 21971.95f);
        assertEquals(pivotPoints.get("r4"), 22288.15f);
        log.info("pivot width : {}", (pivotPoints.get("topCentralPivot") - pivotPoints.get("bottomCentralPivot")));
    }

    @Test
    public void testFloorPoints2() {
        //Given
        Candle previousDayCandle = new Candle("2023-12-21", 21022.95, 21288.35, 20976.8, 21255.05, 0L, 0L);

        //when
        Map<String, Float> pivotPoints = CPRUtils.getFloorPivots(previousDayCandle);

        List<Map.Entry<String, Float>> list = new ArrayList<>(pivotPoints.entrySet());
        list.sort(Map.Entry.comparingByValue());
        LinkedHashMap<String, Float> arrangedPivots = new LinkedHashMap<>();

        for (Map.Entry<String, Float> entry : list) {
            arrangedPivots.put(entry.getKey(), entry.getValue());
        }

        arrangedPivots.keySet().forEach(p -> log.info("{} -> {}", p, arrangedPivots.get(p)));
        log.info("pivot width : {}", (pivotPoints.get("topCentralPivot") - pivotPoints.get("bottomCentralPivot")));
    }

    @Test
    public void testFloorPoints3() {
        //Given
        Candle previousDayCandle = new Candle("2024-07-01", 52351.15, 52656.15, 52166.05, 52574.75, 0L, 0L);

        //when
        Map<String, Float> pivotPoints = CPRUtils.getFloorPivots(previousDayCandle);

        List<Map.Entry<String, Float>> list = new ArrayList<>(pivotPoints.entrySet());
        list.sort(Map.Entry.comparingByValue());
        LinkedHashMap<String, Float> arrangedPivots = new LinkedHashMap<>();

        for (Map.Entry<String, Float> entry : list) {
            arrangedPivots.put(entry.getKey(), entry.getValue());
        }

        arrangedPivots.keySet().forEach(p -> log.info("{} -> {}", p, arrangedPivots.get(p)));
        log.info("pivot width : {}", (pivotPoints.get("topCentralPivot") - pivotPoints.get("bottomCentralPivot")));
    }

}