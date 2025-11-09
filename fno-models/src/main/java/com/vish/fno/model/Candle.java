package com.vish.fno.model;

public record Candle(
    String time,
    double open,
    double high,
    double low,
    double close,
    Long volume,
    Long oi
) {
    @Override
    public String toString() {
        return "Candle[" +
                "time=" + time +
                ", open=" + open +
                ", high=" + high +
                ", low=" + low +
                ", close=" + close +
                ']';
    }
}
