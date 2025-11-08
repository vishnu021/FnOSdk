package com.vish.fno.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Candle {
    private String time;
    private double open;
    private double high;
    private double low;
    private double close;
    @ToString.Exclude
    private Long volume;
    @ToString.Exclude
    private Long oi;
}
