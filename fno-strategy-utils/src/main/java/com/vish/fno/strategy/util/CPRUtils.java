package com.vish.fno.strategy.util;

import com.vish.fno.model.Candle;
import com.vish.fno.util.Utils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/*
 * Pivot width forecast
 * After a trending day (uptrend), CPR will be wide (TC - BC) ->  a sideways day
 * After a sideways day, CPR will be narrow, which will lead to a trending or a float distribution day (potential range breakout and rallies)
 * For higher forecast success CPR has to be extremely wide or narrow
* */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CPRUtils {

    public static Map<String, Float> getFloorPivots(Candle previousDayCandle) {
        Map<String, Float> floorPivots = new HashMap<>();

        float c = (float) previousDayCandle.close();
        float h = (float) previousDayCandle.high();
        float l = (float) previousDayCandle.low();

        float pivot = (h + l + c) / 3;
        float bc = (h + l) / 2;
        float tc = pivot + (pivot - bc);

        float r1 = 2 * pivot - l;
        float r2 = pivot + (h - l);
        float r3 = r1 + (h - l);
        float r4 = r2 + (h - l);

        float s1 = 2 * pivot - h;
        float s2 = pivot - (h - l);
        float s3 = s1 - (h - l);
        float s4 = s2 - (h - l);

        roundAndPut(floorPivots, "pivotPoint", pivot);
        roundAndPut(floorPivots, "topCentralPivot", tc);
        roundAndPut(floorPivots, "bottomCentralPivot", bc);
        roundAndPut(floorPivots, "r1", r1);
        roundAndPut(floorPivots, "r2", r2);
        roundAndPut(floorPivots, "r3", r3);
        roundAndPut(floorPivots, "r4", r4);
        roundAndPut(floorPivots, "s1", s1);
        roundAndPut(floorPivots, "s2", s2);
        roundAndPut(floorPivots, "s3", s3);
        roundAndPut(floorPivots, "s4", s4);

        return floorPivots;
    }

    private static void roundAndPut(Map<String, Float> floorPivots, String key, float value) {
        floorPivots.put(key, ((float) Utils.round(value)));
    }

}
