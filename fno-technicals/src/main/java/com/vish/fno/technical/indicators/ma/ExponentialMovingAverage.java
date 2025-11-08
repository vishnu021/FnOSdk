package com.vish.fno.technical.indicators.ma;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor
public class ExponentialMovingAverage extends MovingAverage {
    public ExponentialMovingAverage(int duration) {
        super(duration);
    }

    protected double getMultiplier() {
        return ((double) 2) / (duration + 1);
    }

    protected double maValue(double lastMa, double closePrice, double multiplier) {
        return closePrice * multiplier + lastMa * (1 - multiplier);
    }
}
