package com.vish.fno.model.wyckoff;

public record WyckoffIndicators(
    double pricePosition,
    double volumeAnalysis, 
    double trendStrength,
    double rangePosition,
    double supplyDemandBalance,
    double volatility,
    double momentum,
    double relativeStrength,
    boolean hasSpring,
    boolean hasUpthrust,
    boolean hasSignOfStrength,
    boolean hasSignOfWeakness,
    double supportLevel,
    double resistanceLevel,
    double averageVolume,
    double currentVolume,
    int daysInRange,
    double rangeWidth
) {}