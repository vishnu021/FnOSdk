package com.vish.fno.technical.indicators.ma;

/*
 ** SMMA (Smoothed Moving Average) and EMA (Exponential Moving Average) are both technical indicators used in financial analysis to track trends in a stock's price over a specific period of time. While they are both similar in some ways, there are a few key differences between the two:
 ** Calculation method: SMMA uses a simple moving average with additional smoothing techniques applied to it, while EMA uses a weighted moving average that places greater weight on more recent data points.
 ** Sensitivity to price changes: EMA is more sensitive to price changes and reacts more quickly to sudden shifts in the market, while SMMA is smoother and slower to respond to price changes.
 ** Accuracy: EMA is generally considered to be more accurate than SMMA because it places greater weight on recent data, which is often more indicative of future trends.
 ** Usage: SMMA is often used to track long-term trends, while EMA is more commonly used for short-term analysis.
*/

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor
public class SmoothedMovingAverage extends MovingAverage {
    public SmoothedMovingAverage(int duration) {
        super(duration);
    }

    protected double getMultiplier() {
        return ((double) 1) / (duration);
    }

    // can also be written as (lastMa * (duration - 1) + closedPrice) / duration;
    protected double maValue(double lastMa, double closedPrice, double multiplier) {
        return closedPrice * multiplier + lastMa * (1 - multiplier);
    }
}
