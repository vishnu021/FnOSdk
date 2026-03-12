package com.vish.fno.phase.wyckoff;

import com.vish.fno.model.Candle;
import com.vish.fno.model.wyckoff.IWyckoffPhaseIdentifier;
import com.vish.fno.model.wyckoff.WyckoffPhase;
import com.vish.fno.strategy.HATrendUtils;
import com.vish.fno.strategy.Point2D;
import com.vish.fno.strategy.PointType;
import com.vish.fno.util.Trend;
import com.vish.fno.util.candle.CandleUtils;
import com.vish.fno.util.candle.HeikinAshi;

import java.util.List;

/**
 * Heikin-Ashi based Wyckoff phase identifier.
 * Uses Heikin-Ashi candles to smooth price action and identify trends,
 * making Wyckoff phase detection more reliable by filtering out market noise.
 * 
 * Key advantages:
 * - Smoother trend identification
 * - Better detection of trend reversals
 * - Reduced false signals from market noise
 * - Clearer identification of consolidation zones
 */

public class HeikinAshiWyckoffPhaseIdentifier implements IWyckoffPhaseIdentifier {
    
    private static final int MIN_CANDLES_FOR_ANALYSIS = 10;
    private static final double VOLUME_SPIKE_THRESHOLD = 1.5;
    
    @Override
    public WyckoffPhase identifyPhase(List<Candle> data, int currentIndex) {
        if (data == null || data.isEmpty() || currentIndex < 0 || currentIndex >= data.size()) {
            return WyckoffPhase.UNKNOWN;
        }
        
        // Get subset of data up to current index
        List<Candle> dataSubset = data.subList(0, currentIndex + 1);
        
        if (dataSubset.size() < MIN_CANDLES_FOR_ANALYSIS) {
            return identifyWithLimitedData(dataSubset);
        }
        
        // Convert to Heikin-Ashi candles for smoother analysis
        List<Candle> haCandles = HeikinAshi.getCandles(dataSubset);
        
        // Get trend analysis from Heikin-Ashi
        Trend currentTrend = HATrendUtils.getTrend(haCandles);
        List<Trend> trendHistory = HATrendUtils.getTrendList(haCandles);
        
        // Get maxima and minima points for support/resistance
        List<Point2D> pivotPoints = HATrendUtils.getMaximaMinima(haCandles);
        
        // Analyze the phase based on Heikin-Ashi patterns
        return analyzeWyckoffPhase(haCandles, currentTrend, trendHistory, pivotPoints, dataSubset);
    }
    
    private WyckoffPhase analyzeWyckoffPhase(
            List<Candle> haCandles,
            Trend currentTrend,
            List<Trend> trendHistory,
            List<Point2D> pivotPoints,
            List<Candle> originalCandles) {
        
        // Analyze trend transitions for Wyckoff phases
        TrendTransition transition = analyzeTrendTransition(trendHistory);
        
        // Check volume patterns (use original candles for accurate volume)
        VolumePattern volumePattern = analyzeVolumePattern(originalCandles);
        
        // Analyze Heikin-Ashi specific patterns
        HAPattern haPattern = analyzeHAPattern(haCandles);
        
        // Determine phase based on combined analysis
        
        // Strong uptrend with no upper wicks = MARKUP
        if (currentTrend == Trend.UPTREND && haPattern.hasStrongBullishCandles) {
            if (haPattern.consecutiveBullishCount > 3) {
                return WyckoffPhase.MARKUP;
            }
            // Early uptrend after accumulation
            if (transition.fromConsolidationToUptrend) {
                return WyckoffPhase.ACCUMULATION_PHASE_D; // Sign of Strength
            }
        }
        
        // Strong downtrend with no lower wicks = MARKDOWN
        if (currentTrend == Trend.DOWNTREND && haPattern.hasStrongBearishCandles) {
            if (haPattern.consecutiveBearishCount > 3) {
                return WyckoffPhase.MARKDOWN;
            }
            // Early downtrend after distribution
            if (transition.fromConsolidationToDowntrend) {
                return WyckoffPhase.DISTRIBUTION_PHASE_D; // Sign of Weakness
            }
        }
        
        // Consolidation patterns
        if (currentTrend == Trend.CONSOLIDATION || currentTrend == Trend.INDECISIVE) {
            return analyzeConsolidationPhase(
                haCandles, pivotPoints, transition, volumePattern, haPattern
            );
        }
        
        // Trend reversal patterns
        if (transition.hasRecentReversal && transition.reversalDirection == Trend.UPTREND) {
            // Check for spring pattern (false breakdown)
            if (detectSpringPattern(haCandles, pivotPoints)) {
                return WyckoffPhase.ACCUMULATION_PHASE_C;
            }
            return WyckoffPhase.ACCUMULATION_PHASE_B;
        }
        if (transition.hasRecentReversal && transition.reversalDirection == Trend.DOWNTREND) {
            // Check for upthrust pattern (false breakout)
            if (detectUpthrustPattern(haCandles, pivotPoints)) {
                return WyckoffPhase.DISTRIBUTION_PHASE_C;
            }
            return WyckoffPhase.DISTRIBUTION_PHASE_B;
        }
        
        // Default phase based on current trend
        switch (currentTrend) {
            case UPTREND:
                return haPattern.weakening ? WyckoffPhase.DISTRIBUTION_PHASE_A : WyckoffPhase.MARKUP;
            case DOWNTREND:
                return haPattern.weakening ? WyckoffPhase.ACCUMULATION_PHASE_A : WyckoffPhase.MARKDOWN;
            case CONSOLIDATION:
                return WyckoffPhase.CONSOLIDATION;
            default:
                return WyckoffPhase.UNKNOWN;
        }
    }
    
    private WyckoffPhase analyzeConsolidationPhase(
            List<Candle> haCandles,
            List<Point2D> pivotPoints,
            TrendTransition transition,
            VolumePattern volumePattern,
            HAPattern haPattern) {
        
        // Determine if accumulation or distribution based on prior trend
        boolean wasInUptrend = transition.priorTrend == Trend.UPTREND;
        boolean wasInDowntrend = transition.priorTrend == Trend.DOWNTREND;
        
        // Look for accumulation patterns
        if (wasInDowntrend || haPattern.hasBottomingPattern) {
            if (volumePattern.hasHighVolumeAtLows) {
                return WyckoffPhase.ACCUMULATION_PHASE_A; // Stopping action
            }
            if (haPattern.hasDojisAtBottom) {
                return WyckoffPhase.ACCUMULATION_PHASE_B; // Building cause
            }
            if (detectSpringPattern(haCandles, pivotPoints)) {
                return WyckoffPhase.ACCUMULATION_PHASE_C; // Spring
            }
        }
        
        // Look for distribution patterns
        if (wasInUptrend || haPattern.hasToppingPattern) {
            if (volumePattern.hasHighVolumeAtHighs) {
                return WyckoffPhase.DISTRIBUTION_PHASE_A; // Stopping action
            }
            if (haPattern.hasDojisAtTop) {
                return WyckoffPhase.DISTRIBUTION_PHASE_B; // Building cause
            }
            if (detectUpthrustPattern(haCandles, pivotPoints)) {
                return WyckoffPhase.DISTRIBUTION_PHASE_C; // Upthrust
            }
        }
        
        // Re-accumulation or redistribution
        if (wasInUptrend && haPattern.bullishBias > 0.6) {
            return WyckoffPhase.REACCUMULATION;
        }
        if (wasInDowntrend && haPattern.bullishBias < 0.4) {
            return WyckoffPhase.REDISTRIBUTION;
        }
        
        return WyckoffPhase.CONSOLIDATION;
    }
    
    private TrendTransition analyzeTrendTransition(List<Trend> trendHistory) {
        TrendTransition transition = new TrendTransition();
        
        if (trendHistory.size() < 5) {
            return transition;
        }
        
        int size = trendHistory.size();
        Trend currentTrend = trendHistory.get(size - 1);
        
        // Look for recent trend changes
        for (int i = size - 2; i >= Math.max(0, size - 10); i--) {
            Trend previousTrend = trendHistory.get(i);
            
            if (previousTrend != currentTrend && previousTrend != Trend.INDECISIVE) {
                transition.hasRecentReversal = true;
                transition.reversalDirection = currentTrend;
                transition.priorTrend = previousTrend;
                
                // Check specific transitions
                if (previousTrend == Trend.CONSOLIDATION && currentTrend == Trend.UPTREND) {
                    transition.fromConsolidationToUptrend = true;
                } else if (previousTrend == Trend.CONSOLIDATION && currentTrend == Trend.DOWNTREND) {
                    transition.fromConsolidationToDowntrend = true;
                }
                
                break;
            }
        }
        
        return transition;
    }
    
    private VolumePattern analyzeVolumePattern(List<Candle> candles) {
        VolumePattern pattern = new VolumePattern();
        
        if (candles.size() < 10) {
            return pattern;
        }
        
        int size = candles.size();
        double avgVolume = candles.stream()
            .mapToDouble(Candle::volume)
            .average()
            .orElse(0);
        
        // Check recent volume patterns
        for (int i = size - 5; i < size; i++) {
            Candle candle = candles.get(i);
            double volumeRatio = candle.volume() / avgVolume;
            
            if (volumeRatio > VOLUME_SPIKE_THRESHOLD) {
                if (candle.close() < candle.open()) {
                    pattern.hasHighVolumeAtLows = true;
                } else {
                    pattern.hasHighVolumeAtHighs = true;
                }
            }
        }
        
        return pattern;
    }
    
    private HAPattern analyzeHAPattern(List<Candle> haCandles) {
        HAPattern pattern = new HAPattern();
        
        if (haCandles.size() < 5) {
            return pattern;
        }
        
        int bullishCount = 0;
        int dojiCount = 0;
        int consecutiveBullish = 0;
        int consecutiveBearish = 0;
        
        for (int i = haCandles.size() - 5; i < haCandles.size(); i++) {
            Candle candle = haCandles.get(i);
            
            // Check for doji (small body relative to range)
            double bodySize = Math.abs(candle.close() - candle.open());
            double range = candle.high() - candle.low();
            boolean isDoji = range > 0 && (bodySize / range) < 0.1;
            
            if (isDoji) {
                dojiCount++;
            } else if (CandleUtils.isBullish(candle)) {
                bullishCount++;
                consecutiveBullish++;
                consecutiveBearish = 0;
                
                // Strong bullish: no lower wick
                if (candle.low() == candle.open()) {
                    pattern.hasStrongBullishCandles = true;
                }
            } else if (CandleUtils.isBearish(candle)) {
                consecutiveBearish++;
                consecutiveBullish = 0;
                
                // Strong bearish: no upper wick
                if (candle.high() == candle.open()) {
                    pattern.hasStrongBearishCandles = true;
                }
            }
        }
        
        pattern.consecutiveBullishCount = consecutiveBullish;
        pattern.consecutiveBearishCount = consecutiveBearish;
        pattern.bullishBias = bullishCount / 5.0;
        
        // Check for topping/bottoming patterns
        Candle lastCandle = haCandles.get(haCandles.size() - 1);
        double highestHigh = haCandles.stream()
            .mapToDouble(Candle::high)
            .max()
            .orElse(0);
        double lowestLow = haCandles.stream()
            .mapToDouble(Candle::low)
            .min()
            .orElse(0);
        
        // Dojis at extremes indicate potential reversal
        if (dojiCount > 2) {
            if (Math.abs(lastCandle.high() - highestHigh) / highestHigh < 0.002) {
                pattern.hasDojisAtTop = true;
                pattern.hasToppingPattern = true;
            }
            if (Math.abs(lastCandle.low() - lowestLow) / lowestLow < 0.002) {
                pattern.hasDojisAtBottom = true;
                pattern.hasBottomingPattern = true;
            }
        }
        
        // Weakening trend: decreasing candle bodies
        if (haCandles.size() > 3) {
            Candle recentCandle = haCandles.get(haCandles.size() - 1);
            double recentBodySize = Math.abs(recentCandle.close() - recentCandle.open());
            double previousBodySize = Math.abs(
                haCandles.get(haCandles.size() - 3).close() - 
                haCandles.get(haCandles.size() - 3).open()
            );
            pattern.weakening = recentBodySize < previousBodySize * 0.5;
        }
        
        return pattern;
    }
    
    private boolean detectSpringPattern(List<Candle> haCandles, List<Point2D> pivotPoints) {
        if (haCandles.size() < 3 || pivotPoints.isEmpty()) {
            return false;
        }
        
        // Find recent support level from pivot points
        double support = pivotPoints.stream()
            .filter(p -> p.getType() == PointType.MINIMA)
            .mapToDouble(Point2D::getY)
            .min()
            .orElse(0);
        
        // Check for false breakdown and recovery
        for (int i = haCandles.size() - 3; i < haCandles.size() - 1; i++) {
            Candle candle = haCandles.get(i);
            Candle nextCandle = haCandles.get(i + 1);
            
            // False breakdown: low below support
            // Recovery: close back above support with bullish candle
            if (candle.low() < support * 0.998 && 
                nextCandle.close() > support &&
                CandleUtils.isBullish(nextCandle)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean detectUpthrustPattern(List<Candle> haCandles, List<Point2D> pivotPoints) {
        if (haCandles.size() < 3 || pivotPoints.isEmpty()) {
            return false;
        }
        
        // Find recent resistance level from pivot points
        double resistance = pivotPoints.stream()
            .filter(p -> p.getType() == PointType.MAXIMA)
            .mapToDouble(Point2D::getY)
            .max()
            .orElse(0);
        
        // Check for false breakout and rejection
        for (int i = haCandles.size() - 3; i < haCandles.size() - 1; i++) {
            Candle candle = haCandles.get(i);
            Candle nextCandle = haCandles.get(i + 1);
            
            // False breakout: high above resistance
            // Rejection: close back below resistance with bearish candle
            if (candle.high() > resistance * 1.002 && 
                nextCandle.close() < resistance &&
                CandleUtils.isBearish(nextCandle)) {
                return true;
            }
        }
        
        return false;
    }
    
    private WyckoffPhase identifyWithLimitedData(List<Candle> data) {
        if (data.size() < 2) {
            return WyckoffPhase.UNKNOWN;
        }
        
        // Convert limited data to Heikin-Ashi
        List<Candle> haCandles = HeikinAshi.getCandles(data);
        Candle lastHA = haCandles.get(haCandles.size() - 1);
        
        // Simple trend determination based on Heikin-Ashi
        if (CandleUtils.isBullish(lastHA) && lastHA.low() == lastHA.open()) {
            // Strong bullish HA candle
            return WyckoffPhase.MARKUP;
        } else if (CandleUtils.isBearish(lastHA) && lastHA.high() == lastHA.open()) {
            // Strong bearish HA candle
            return WyckoffPhase.MARKDOWN;
        }
        
        return WyckoffPhase.CONSOLIDATION;
    }
    
    @Override
    public String getIdentifierType() {
        return "Heikin-Ashi";
    }
    
    @Override
    public String getDescription() {
        return "Heikin-Ashi based Wyckoff phase identification using smoothed candlesticks " +
               "for clearer trend detection and noise reduction. Excellent for identifying " +
               "trend continuations and reversals with reduced false signals.";
    }
    
    @Override
    public double getPhaseConfidence(List<Candle> data, int currentIndex) {
        if (data == null || currentIndex < 0 || currentIndex >= data.size()) {
            return 0.0;
        }
        
        List<Candle> dataSubset = data.subList(0, currentIndex + 1);
        
        if (dataSubset.size() < MIN_CANDLES_FOR_ANALYSIS) {
            return 0.3; // Low confidence with limited data
        }
        
        List<Candle> haCandles = HeikinAshi.getCandles(dataSubset);
        HAPattern pattern = analyzeHAPattern(haCandles);
        
        double confidence = 0.5; // Base confidence
        
        // Strong consecutive candles increase confidence
        if (pattern.consecutiveBullishCount > 3 || pattern.consecutiveBearishCount > 3) {
            confidence += 0.2;
        }
        
        // Clear patterns increase confidence
        if (pattern.hasStrongBullishCandles || pattern.hasStrongBearishCandles) {
            confidence += 0.15;
        }
        
        // Reversal patterns with dojis increase confidence
        if (pattern.hasDojisAtTop || pattern.hasDojisAtBottom) {
            confidence += 0.1;
        }
        
        // More data increases confidence
        double dataConfidence = Math.min(1.0, dataSubset.size() / 50.0);
        
        return Math.min(1.0, confidence * dataConfidence);
    }
    
    @Override
    public int getMinimumDataPoints() {
        return MIN_CANDLES_FOR_ANALYSIS;
    }
    
    // Helper classes for pattern analysis

    private static final class TrendTransition {
        boolean hasRecentReversal;
        Trend reversalDirection = Trend.INDECISIVE;
        Trend priorTrend = Trend.INDECISIVE;
        boolean fromConsolidationToUptrend;
        boolean fromConsolidationToDowntrend;
    }

    private static final class VolumePattern {
        boolean hasHighVolumeAtHighs;
        boolean hasHighVolumeAtLows;
    }

    private static final class HAPattern {
        boolean hasStrongBullishCandles;
        boolean hasStrongBearishCandles;
        int consecutiveBullishCount;
        int consecutiveBearishCount;
        double bullishBias = 0.5;
        boolean hasDojisAtTop;
        boolean hasDojisAtBottom;
        boolean hasToppingPattern;
        boolean hasBottomingPattern;
        boolean weakening;
    }
}