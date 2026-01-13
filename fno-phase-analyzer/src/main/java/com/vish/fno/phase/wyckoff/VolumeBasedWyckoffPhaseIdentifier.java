package com.vish.fno.phase.wyckoff;

import com.vish.fno.model.Candle;
import com.vish.fno.model.wyckoff.IWyckoffPhaseIdentifier;
import com.vish.fno.model.wyckoff.WyckoffPhase;

import java.util.List;

/**
 * Volume-based Wyckoff phase identifier.
 * Focuses primarily on volume patterns and price-volume relationships
 * to identify accumulation, distribution, and trending phases.
 */

public class VolumeBasedWyckoffPhaseIdentifier implements IWyckoffPhaseIdentifier {
    
    private static final int VOLUME_LOOKBACK = 10;
    private static final double HIGH_VOLUME_THRESHOLD = 1.5;
    private static final double LOW_VOLUME_THRESHOLD = 0.7;
    private static final double PRICE_CHANGE_THRESHOLD = 0.005;
    
    @Override
    public WyckoffPhase identifyPhase(List<Candle> data, int currentIndex) {
        if (data == null || data.isEmpty() || currentIndex < 0 || currentIndex >= data.size()) {
            return WyckoffPhase.UNKNOWN;
        }
        
        if (currentIndex < VOLUME_LOOKBACK) {
            return analyzeWithLimitedData(data, currentIndex);
        }
        
        // Calculate volume and price metrics
        VolumeMetrics metrics = calculateVolumeMetrics(data, currentIndex);
        
        // Identify phase based on volume patterns
        return identifyPhaseFromVolumePatterns(metrics, data, currentIndex);
    }
    
    private VolumeMetrics calculateVolumeMetrics(List<Candle> data, int currentIndex) {
        int startIdx = Math.max(0, currentIndex - VOLUME_LOOKBACK);
        
        // Calculate average volume
        double avgVolume = 0;
        for (int i = startIdx; i <= currentIndex; i++) {
            avgVolume += data.get(i).volume();
        }
        avgVolume /= (currentIndex - startIdx + 1);
        
        Candle current = data.get(currentIndex);
        double volumeRatio = current.volume() / avgVolume;
        
        // Calculate price change
        double priceChange = 0;
        if (startIdx > 0) {
            double startPrice = data.get(startIdx).close();
            double currentPrice = current.close();
            priceChange = (currentPrice - startPrice) / startPrice;
        }
        
        // Analyze volume trend
        double volumeTrend = calculateVolumeTrend(data, startIdx, currentIndex);
        
        // Check for volume spikes
        boolean hasVolumeSpike = volumeRatio > HIGH_VOLUME_THRESHOLD;
        boolean hasLowVolume = volumeRatio < LOW_VOLUME_THRESHOLD;
        
        // Analyze buying vs selling volume
        double buyingPressure = calculateBuyingPressure(data, startIdx, currentIndex);
        
        return new VolumeMetrics(
            avgVolume, volumeRatio, priceChange, volumeTrend,
            hasVolumeSpike, hasLowVolume, buyingPressure
        );
    }
    
    private double calculateVolumeTrend(List<Candle> data, int startIdx, int endIdx) {
        if (endIdx - startIdx < 2) {
            return 0;
        }
        
        double firstHalfVolume = 0;
        double secondHalfVolume = 0;
        int midPoint = (startIdx + endIdx) / 2;
        
        for (int i = startIdx; i <= midPoint; i++) {
            firstHalfVolume += data.get(i).volume();
        }
        
        for (int i = midPoint + 1; i <= endIdx; i++) {
            secondHalfVolume += data.get(i).volume();
        }
        
        if (firstHalfVolume == 0) {
            return 0;
        }
        return (secondHalfVolume - firstHalfVolume) / firstHalfVolume;
    }
    
    private double calculateBuyingPressure(List<Candle> data, int startIdx, int endIdx) {
        double buyVolume = 0;
        double sellVolume = 0;
        
        for (int i = startIdx; i <= endIdx; i++) {
            Candle candle = data.get(i);
            if (candle.close() > candle.open()) {
                // Bullish candle - consider as buying volume
                buyVolume += candle.volume();
            } else if (candle.close() < candle.open()) {
                // Bearish candle - consider as selling volume
                sellVolume += candle.volume();
            } else {
                // Neutral - split volume
                buyVolume += candle.volume() / 2;
                sellVolume += candle.volume() / 2;
            }
        }
        
        double totalVolume = buyVolume + sellVolume;
        if (totalVolume == 0) {
            return 0.5;
        }
        
        return buyVolume / totalVolume;
    }
    
    private WyckoffPhase identifyPhaseFromVolumePatterns(VolumeMetrics metrics,
                                                         List<Candle> data,
                                                         int currentIndex) {
        // High volume with price advance = Markup or Sign of Strength
        if (metrics.hasVolumeSpike && metrics.priceChange > PRICE_CHANGE_THRESHOLD) {
            if (metrics.buyingPressure > 0.65) {
                return WyckoffPhase.MARKUP;
            } else {
                return WyckoffPhase.ACCUMULATION_PHASE_D; // Sign of Strength
            }
        }
        
        // High volume with price decline = Markdown or Sign of Weakness
        if (metrics.hasVolumeSpike && metrics.priceChange < -PRICE_CHANGE_THRESHOLD) {
            if (metrics.buyingPressure < 0.35) {
                return WyckoffPhase.MARKDOWN;
            } else {
                return WyckoffPhase.DISTRIBUTION_PHASE_D; // Sign of Weakness
            }
        }
        
        // High volume with little price change = Accumulation or Distribution
        if (metrics.hasVolumeSpike && Math.abs(metrics.priceChange) < PRICE_CHANGE_THRESHOLD) {
            if (wasInDowntrend(data, currentIndex)) {
                // High volume after downtrend = potential accumulation
                return metrics.buyingPressure > 0.5 ? 
                    WyckoffPhase.ACCUMULATION_PHASE_A : 
                    WyckoffPhase.ACCUMULATION_PHASE_B;
            } else if (wasInUptrend(data, currentIndex)) {
                // High volume after uptrend = potential distribution
                return metrics.buyingPressure < 0.5 ? 
                    WyckoffPhase.DISTRIBUTION_PHASE_A : 
                    WyckoffPhase.DISTRIBUTION_PHASE_B;
            }
        }
        
        // Low volume with price advance = Potential test or markup continuation
        if (metrics.hasLowVolume && metrics.priceChange > 0) {
            return WyckoffPhase.ACCUMULATION_PHASE_C; // Test phase
        }
        
        // Low volume with price decline = Potential test or markdown continuation
        if (metrics.hasLowVolume && metrics.priceChange < 0) {
            return WyckoffPhase.DISTRIBUTION_PHASE_C; // Test phase
        }
        
        // Increasing volume trend with sideways price = Building cause
        if (metrics.volumeTrend > 0.2 && Math.abs(metrics.priceChange) < PRICE_CHANGE_THRESHOLD) {
            return metrics.buyingPressure > 0.5 ? 
                WyckoffPhase.ACCUMULATION_PHASE_B : 
                WyckoffPhase.DISTRIBUTION_PHASE_B;
        }
        
        // Default based on price trend and buying pressure
        if (metrics.priceChange > PRICE_CHANGE_THRESHOLD / 2) {
            return WyckoffPhase.MARKUP;
        } else if (metrics.priceChange < -PRICE_CHANGE_THRESHOLD / 2) {
            return WyckoffPhase.MARKDOWN;
        } else {
            return WyckoffPhase.CONSOLIDATION;
        }
    }
    
    private boolean wasInUptrend(List<Candle> data, int currentIndex) {
        if (currentIndex < 10) {
            return false;
        }
        
        int lookback = Math.min(20, currentIndex);
        double startPrice = data.get(currentIndex - lookback).close();
        double endPrice = data.get(currentIndex - 1).close();
        
        return (endPrice - startPrice) / startPrice > 0.01;
    }
    
    private boolean wasInDowntrend(List<Candle> data, int currentIndex) {
        if (currentIndex < 10) {
            return false;
        }
        
        int lookback = Math.min(20, currentIndex);
        double startPrice = data.get(currentIndex - lookback).close();
        double endPrice = data.get(currentIndex - 1).close();
        
        return (endPrice - startPrice) / startPrice < -0.01;
    }
    
    private WyckoffPhase analyzeWithLimitedData(List<Candle> data, int currentIndex) {
        if (currentIndex == 0) {
            return WyckoffPhase.UNKNOWN;
        }
        
        Candle current = data.get(currentIndex);
        Candle previous = data.get(currentIndex - 1);
        
        double priceChange = (current.close() - previous.close()) / previous.close();
        double volumeChange = (current.volume() - previous.volume()) / (double)previous.volume();
        
        // Simple volume-price analysis
        if (volumeChange > 0.5 && priceChange > 0.002) {
            return WyckoffPhase.MARKUP;
        } else if (volumeChange > 0.5 && priceChange < -0.002) {
            return WyckoffPhase.MARKDOWN;
        } else if (volumeChange > 0.5 && Math.abs(priceChange) < 0.002) {
            // High volume, little price movement
            return priceChange > 0 ? 
                WyckoffPhase.ACCUMULATION_PHASE_B : 
                WyckoffPhase.DISTRIBUTION_PHASE_B;
        }
        
        return WyckoffPhase.CONSOLIDATION;
    }
    
    @Override
    public String getIdentifierType() {
        return "Volume-Based";
    }
    
    @Override
    public String getDescription() {
        return "Volume-focused Wyckoff phase identification using volume patterns, " +
               "price-volume relationships, and buying/selling pressure analysis";
    }
    
    @Override
    public double getPhaseConfidence(List<Candle> data, int currentIndex) {
        if (data == null || currentIndex < 0 || currentIndex >= data.size()) {
            return 0.0;
        }
        
        if (currentIndex < VOLUME_LOOKBACK) {
            return 0.3; // Low confidence with limited data
        }
        
        VolumeMetrics metrics = calculateVolumeMetrics(data, currentIndex);
        
        double confidence = 0.5; // Base confidence
        
        // High volume events increase confidence
        if (metrics.hasVolumeSpike) {
            confidence += 0.2;
        }
        
        // Clear buying/selling pressure increases confidence
        if (metrics.buyingPressure > 0.7 || metrics.buyingPressure < 0.3) {
            confidence += 0.15;
        }
        
        // Strong volume trend increases confidence
        if (Math.abs(metrics.volumeTrend) > 0.3) {
            confidence += 0.15;
        }
        
        return Math.min(1.0, confidence);
    }
    
    @Override
    public int getMinimumDataPoints() {
        return VOLUME_LOOKBACK;
    }
    
    // Inner class for volume metrics
    private static class VolumeMetrics {
        final double avgVolume;
        final double volumeRatio;
        final double priceChange;
        final double volumeTrend;
        final boolean hasVolumeSpike;
        final boolean hasLowVolume;
        final double buyingPressure;
        
        VolumeMetrics(double avgVolume, double volumeRatio, double priceChange,
                     double volumeTrend, boolean hasVolumeSpike, boolean hasLowVolume,
                     double buyingPressure) {
            this.avgVolume = avgVolume;
            this.volumeRatio = volumeRatio;
            this.priceChange = priceChange;
            this.volumeTrend = volumeTrend;
            this.hasVolumeSpike = hasVolumeSpike;
            this.hasLowVolume = hasLowVolume;
            this.buyingPressure = buyingPressure;
        }
    }
}