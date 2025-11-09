package com.vish.fno.phase.wyckoff;

import com.vish.fno.model.Candle;
import com.vish.fno.model.wyckoff.IWyckoffPhaseIdentifier;
import com.vish.fno.model.wyckoff.WyckoffIndicators;
import com.vish.fno.model.wyckoff.WyckoffPhase;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Classical Wyckoff phase identification implementation.
 * Uses traditional Wyckoff methodology with volume analysis, price action,
 * and market structure to identify accumulation, distribution, markup, and markdown phases.
 */

public class ClassicalWyckoffPhaseIdentifier implements IWyckoffPhaseIdentifier {
    
    private static final int LOOKBACK_PERIOD = 5;  // Very short for hourly sensitivity
    private static final int VOLUME_LOOKBACK = 3;   // Very short for hourly
    private static final double SPRING_THRESHOLD = 0.998;  // Ultra sensitive
    private static final double UPTHRUST_THRESHOLD = 1.002;  // Ultra sensitive
    private static final double VOLUME_SPIKE_THRESHOLD = 1.2;  // Lower threshold
    private static final double TREND_THRESHOLD = 0.002;  // Ultra sensitive for hourly
    private static final double STRONG_TREND_THRESHOLD = 0.005;  // For clear hourly trends
    private static final double MICRO_TREND_THRESHOLD = 0.0005;  // For micro movements
    
    @Override
    public WyckoffPhase identifyPhase(List<Candle> data, int currentIndex) {
        if (data == null || data.isEmpty()) {
            return WyckoffPhase.UNKNOWN;
        }
        
        // For early periods with limited data, use simpler analysis
        if (currentIndex < LOOKBACK_PERIOD) {
            return identifyPhaseWithLimitedData(data, currentIndex);
        }
        
        WyckoffIndicators indicators = calculateIndicators(data, currentIndex);
        return determinePhase(indicators, data, currentIndex);
    }
    
    private WyckoffIndicators calculateIndicators(List<Candle> data, int currentIndex) {
        int startIdx = Math.max(0, currentIndex - LOOKBACK_PERIOD);
        List<Candle> recentData = data.subList(startIdx, currentIndex + 1);
        
        double supportLevel = findSupport(recentData);
        double resistanceLevel = findResistance(recentData);
        double rangeWidth = resistanceLevel - supportLevel;
        
        Candle current = data.get(currentIndex);
        double pricePosition = (current.close() - supportLevel) / rangeWidth;
        
        double averageVolume = calculateAverageVolume(data, currentIndex);
        double currentVolume = current.volume();
        double volumeAnalysis = currentVolume / averageVolume;
        
        double trendStrength = calculateTrendStrength(recentData);
        double momentum = calculateMomentum(recentData);
        double volatility = calculateVolatility(recentData);
        
        boolean hasSpring = detectSpring(recentData, supportLevel);
        boolean hasUpthrust = detectUpthrust(recentData, resistanceLevel);
        boolean hasSignOfStrength = detectSignOfStrength(recentData, resistanceLevel);
        boolean hasSignOfWeakness = detectSignOfWeakness(recentData, supportLevel);
        
        double supplyDemandBalance = calculateSupplyDemandBalance(recentData);
        double relativeStrength = calculateRelativeStrength(recentData);
        
        int daysInRange = calculateDaysInRange(data, currentIndex, supportLevel, resistanceLevel);
        
        return new WyckoffIndicators(
            pricePosition,
            volumeAnalysis,
            trendStrength,
            pricePosition,
            supplyDemandBalance,
            volatility,
            momentum,
            relativeStrength,
            hasSpring,
            hasUpthrust,
            hasSignOfStrength,
            hasSignOfWeakness,
            supportLevel,
            resistanceLevel,
            averageVolume,
            currentVolume,
            daysInRange,
            rangeWidth
        );
    }
    
    private WyckoffPhase determinePhase(WyckoffIndicators indicators, List<Candle> data, int currentIndex) {
        // Clear uptrend - MARKUP phase
        if (indicators.trendStrength() > STRONG_TREND_THRESHOLD && indicators.momentum() > 0) {
            return WyckoffPhase.MARKUP;
        }
        
        // Clear downtrend - MARKDOWN phase
        if (indicators.trendStrength() < -STRONG_TREND_THRESHOLD && indicators.momentum() < 0) {
            return WyckoffPhase.MARKDOWN;
        }
        
        // Moderate uptrend with signs of strength
        if (indicators.trendStrength() > TREND_THRESHOLD && indicators.momentum() > 0) {
            if (indicators.hasSignOfStrength() || indicators.pricePosition() > 0.7) {
                return WyckoffPhase.ACCUMULATION_PHASE_D;
            }
            return WyckoffPhase.MARKUP;  // Default to markup for any uptrend
        }
        
        // Moderate downtrend with signs of weakness
        if (indicators.trendStrength() < -TREND_THRESHOLD && indicators.momentum() < 0) {
            if (indicators.hasSignOfWeakness() || indicators.pricePosition() < 0.3) {
                return WyckoffPhase.DISTRIBUTION_PHASE_D;
            }
            return WyckoffPhase.MARKDOWN;  // Default to markdown for any downtrend
        }
        
        if (indicators.hasSpring()) {
            return WyckoffPhase.ACCUMULATION_PHASE_C;
        }
        
        if (indicators.hasUpthrust()) {
            return WyckoffPhase.DISTRIBUTION_PHASE_C;
        }
        
        // Sideways movement - check for accumulation/distribution patterns
        if (Math.abs(indicators.trendStrength()) < TREND_THRESHOLD) {
            // Check for consolidation patterns
            if (indicators.volatility() < 0.01 && Math.abs(indicators.momentum()) < 0.5) {
                return WyckoffPhase.CONSOLIDATION;
            }
            
            if (indicators.daysInRange() > 3) {  // Reduced from 10 for intraday
                if (wasInDowntrend(data, currentIndex)) {
                    if (indicators.volumeAnalysis() > VOLUME_SPIKE_THRESHOLD && indicators.pricePosition() < 0.3) {
                        return WyckoffPhase.ACCUMULATION_PHASE_A;
                    }
                    return WyckoffPhase.ACCUMULATION_PHASE_B;
                } else if (wasInUptrend(data, currentIndex)) {
                    if (indicators.volumeAnalysis() > VOLUME_SPIKE_THRESHOLD && indicators.pricePosition() > 0.7) {
                        return WyckoffPhase.DISTRIBUTION_PHASE_A;
                    }
                    return WyckoffPhase.DISTRIBUTION_PHASE_B;
                }
            }
            
            // Default to consolidation for sideways movement
            return WyckoffPhase.CONSOLIDATION;
        }
        
        // Continuation patterns
        if (indicators.trendStrength() > 0 && indicators.momentum() > 0) {
            if (wasInUptrend(data, currentIndex)) {
                return WyckoffPhase.REACCUMULATION;
            }
            return WyckoffPhase.MARKUP;  // Any upward movement defaults to markup
        }
        
        if (indicators.trendStrength() < 0 && indicators.momentum() < 0) {
            if (wasInDowntrend(data, currentIndex)) {
                return WyckoffPhase.REDISTRIBUTION;
            }
            return WyckoffPhase.MARKDOWN;  // Any downward movement defaults to markdown
        }
        
        // If we can't determine, check simple price action with ultra sensitivity
        if (currentIndex > 0) {
            double priceChange = (data.get(currentIndex).close() - data.get(currentIndex - 1).close()) / data.get(currentIndex - 1).close();
            if (priceChange > MICRO_TREND_THRESHOLD) return WyckoffPhase.MARKUP;
            if (priceChange < -MICRO_TREND_THRESHOLD) return WyckoffPhase.MARKDOWN;
            return WyckoffPhase.CONSOLIDATION;
        }
        
        return WyckoffPhase.UNKNOWN;
    }
    
    private double findSupport(List<Candle> data) {
        return data.stream()
            .mapToDouble(Candle::low)
            .min()
            .orElse(0);
    }
    
    private double findResistance(List<Candle> data) {
        return data.stream()
            .mapToDouble(Candle::high)
            .max()
            .orElse(0);
    }
    
    private double calculateAverageVolume(List<Candle> data, int currentIndex) {
        int startIdx = Math.max(0, currentIndex - VOLUME_LOOKBACK);
        return data.subList(startIdx, currentIndex + 1).stream()
            .mapToDouble(Candle::volume)
            .average()
            .orElse(1);
    }
    
    private double calculateTrendStrength(List<Candle> data) {
        if (data.size() < 2) return 0;
        
        double firstPrice = data.get(0).close();
        double lastPrice = data.get(data.size() - 1).close();
        return (lastPrice - firstPrice) / firstPrice;
    }
    
    private double calculateMomentum(List<Candle> data) {
        if (data.size() < 5) return 0;
        
        int recentIndex = data.size() - 1;
        int pastIndex = data.size() - 5;
        
        double recentPrice = data.get(recentIndex).close();
        double pastPrice = data.get(pastIndex).close();
        
        return recentPrice - pastPrice;
    }
    
    private double calculateVolatility(List<Candle> data) {
        if (data.isEmpty()) return 0;
        
        double avgRange = data.stream()
            .mapToDouble(c -> c.high() - c.low())
            .average()
            .orElse(0);
        
        double avgPrice = data.stream()
            .mapToDouble(Candle::close)
            .average()
            .orElse(1);
        
        return avgRange / avgPrice;
    }
    
    private boolean detectSpring(List<Candle> data, double support) {
        if (data.size() < 3) return false;
        
        for (int i = data.size() - 3; i < data.size() - 1; i++) {
            Candle candle = data.get(i);
            Candle nextCandle = data.get(i + 1);
            
            if (candle.low() < support * SPRING_THRESHOLD && 
                nextCandle.close() > support &&
                nextCandle.volume() > candle.volume() * 1.2) {
                return true;
            }
        }
        return false;
    }
    
    private boolean detectUpthrust(List<Candle> data, double resistance) {
        if (data.size() < 3) return false;
        
        for (int i = data.size() - 3; i < data.size() - 1; i++) {
            Candle candle = data.get(i);
            Candle nextCandle = data.get(i + 1);
            
            if (candle.high() > resistance * UPTHRUST_THRESHOLD && 
                nextCandle.close() < resistance &&
                nextCandle.volume() > candle.volume() * 1.2) {
                return true;
            }
        }
        return false;
    }
    
    private boolean detectSignOfStrength(List<Candle> data, double resistance) {
        if (data.size() < 2) return false;
        
        Candle recent = data.get(data.size() - 1);
        Candle previous = data.get(data.size() - 2);
        
        return recent.close() > resistance && 
               recent.volume() > previous.volume() * VOLUME_SPIKE_THRESHOLD &&
               recent.close() > recent.open();
    }
    
    private boolean detectSignOfWeakness(List<Candle> data, double support) {
        if (data.size() < 2) return false;
        
        Candle recent = data.get(data.size() - 1);
        Candle previous = data.get(data.size() - 2);
        
        return recent.close() < support && 
               recent.volume() > previous.volume() * VOLUME_SPIKE_THRESHOLD &&
               recent.close() < recent.open();
    }
    
    private double calculateSupplyDemandBalance(List<Candle> data) {
        long buyVolume = 0;
        long sellVolume = 0;
        
        for (Candle candle : data) {
            if (candle.close() > candle.open()) {
                buyVolume += candle.volume();
            } else {
                sellVolume += candle.volume();
            }
        }
        
        double total = buyVolume + sellVolume;
        if (total == 0) return 0;
        
        return (buyVolume - sellVolume) / total;
    }
    
    private double calculateRelativeStrength(List<Candle> data) {
        if (data.size() < 2) return 0;
        
        double gains = 0;
        double losses = 0;
        
        for (int i = 1; i < data.size(); i++) {
            double change = data.get(i).close() - data.get(i - 1).close();
            if (change > 0) {
                gains += change;
            } else {
                losses -= change;
            }
        }
        
        if (losses == 0) return 100;
        double rs = gains / losses;
        return 100 - (100 / (1 + rs));
    }
    
    private int calculateDaysInRange(List<Candle> data, int currentIndex, double support, double resistance) {
        int days = 0;
        double rangeBuffer = (resistance - support) * 0.1;
        
        for (int i = currentIndex; i >= 0 && i > currentIndex - 50; i--) {
            Candle candle = data.get(i);
            if (candle.close() >= support - rangeBuffer && candle.close() <= resistance + rangeBuffer) {
                days++;
            } else {
                break;
            }
        }
        
        return days;
    }
    
    private boolean wasInUptrend(List<Candle> data, int currentIndex) {
        if (currentIndex < 3) return false;  // Ultra short for hourly
        
        int startIdx = Math.max(0, currentIndex - 3);
        int endIdx = Math.max(0, currentIndex - 1);
        
        double startPrice = data.get(startIdx).close();
        double endPrice = data.get(endIdx).close();
        
        return (endPrice - startPrice) / startPrice > 0.003;  // Very sensitive for hourly
    }
    
    private boolean wasInDowntrend(List<Candle> data, int currentIndex) {
        if (currentIndex < 3) return false;  // Ultra short for hourly
        
        int startIdx = Math.max(0, currentIndex - 3);
        int endIdx = Math.max(0, currentIndex - 1);
        
        double startPrice = data.get(startIdx).close();
        double endPrice = data.get(endIdx).close();
        
        return (endPrice - startPrice) / startPrice < -0.003;  // Very sensitive for hourly
    }
    
    private WyckoffPhase identifyPhaseWithLimitedData(List<Candle> data, int currentIndex) {
        // Simple price action analysis for limited data
        if (currentIndex == 0) {
            return WyckoffPhase.CONSOLIDATION;
        }
        
        Candle current = data.get(currentIndex);
        Candle previous = data.get(currentIndex - 1);
        
        double priceChange = (current.close() - previous.close()) / previous.close();
        double bodySize = Math.abs(current.close() - current.open()) / current.open();
        double wickSize = (current.high() - current.low()) / current.open();
        
        // Strong movement detection
        if (priceChange > 0.003) {
            return WyckoffPhase.MARKUP;
        } else if (priceChange < -0.003) {
            return WyckoffPhase.MARKDOWN;
        }
        
        // Moderate movement with body analysis
        if (priceChange > 0.001 && bodySize > 0.002) {
            return WyckoffPhase.MARKUP;
        } else if (priceChange < -0.001 && bodySize > 0.002) {
            return WyckoffPhase.MARKDOWN;
        }
        
        // Check for accumulation/distribution patterns
        if (wickSize > bodySize * 2 && current.close() > current.open()) {
            return WyckoffPhase.ACCUMULATION_PHASE_B;
        } else if (wickSize > bodySize * 2 && current.close() < current.open()) {
            return WyckoffPhase.DISTRIBUTION_PHASE_B;
        }
        
        return WyckoffPhase.CONSOLIDATION;
    }
    
    @Override
    public String getIdentifierType() {
        return "Classical";
    }
    
    @Override
    public String getDescription() {
        return "Classical Wyckoff methodology using volume analysis, price action patterns, " +
               "and market structure to identify accumulation, distribution, markup, and markdown phases. " +
               "Optimized for hourly timeframes with ultra-sensitive thresholds.";
    }
    
    @Override
    public double getPhaseConfidence(List<Candle> data, int currentIndex) {
        if (data == null || data.isEmpty() || currentIndex < 0 || currentIndex >= data.size()) {
            return 0.0;
        }
        
        // Calculate confidence based on data availability and indicator strength
        double dataConfidence = Math.min(1.0, (double)currentIndex / 20.0); // More data = higher confidence
        
        if (currentIndex < LOOKBACK_PERIOD) {
            return dataConfidence * 0.3; // Low confidence with limited data
        }
        
        WyckoffIndicators indicators = calculateIndicators(data, currentIndex);
        
        // Calculate confidence based on indicator alignment
        double confidence = 0.5; // Base confidence
        
        // Strong trend = higher confidence
        if (Math.abs(indicators.trendStrength()) > STRONG_TREND_THRESHOLD) {
            confidence += 0.2;
        }
        
        // Clear volume signals = higher confidence
        if (indicators.volumeAnalysis() > VOLUME_SPIKE_THRESHOLD) {
            confidence += 0.15;
        }
        
        // Spring or upthrust detection = higher confidence
        if (indicators.hasSpring() || indicators.hasUpthrust()) {
            confidence += 0.15;
        }
        
        return Math.min(1.0, confidence * dataConfidence);
    }
    
    @Override
    public int getMinimumDataPoints() {
        return LOOKBACK_PERIOD;
    }
    
    @Override
    public boolean supportsRealTimeAnalysis() {
        return true;
    }
}