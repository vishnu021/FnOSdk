package com.vish.fno.strategy.priceaction;

import com.vish.fno.util.time.TimeUtils;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChartPoint implements Comparable<ChartPoint>{
    private String time;
    private double value;

    public ChartPoint(Point point) {
        this.time = TimeUtils.getTimeByIndex((int) point.getX());
        this.value = point.getY();
    }

    public ChartPoint(Point point, int timeframe) {
        this.time = TimeUtils.getTimeByIndex((int) point.getX() * timeframe);
        this.value = point.getY();
    }

    @Override
    public int compareTo(ChartPoint that) {
        return (this.time.compareTo(that.time));
    }
}
