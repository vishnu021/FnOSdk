package com.vish.fno.phase.wyckoff;

import com.vish.fno.model.Candle;
import com.vish.fno.model.wyckoff.IWyckoffPhaseIdentifier;
import com.vish.fno.model.wyckoff.WyckoffPhase;

import java.util.ArrayList;
import java.util.List;

/**
 * Derivatives Futures Open Interest (OI) based Wyckoff phase identifier.
 * Uses futures price and OI changes to identify real market positioning.
 * 
 * Key patterns:
 * - Price↑ + OI↑ = Long buildup (Accumulation/Markup)
 * - Price↑ + OI↓ = Short covering (Late Markup)
 * - Price↓ + OI↑ = Short buildup (Distribution/Markdown)
 * - Price↓ + OI↓ = Long unwinding (Late Markdown)
 * 
 * Reliability: 4.5/5.0 (Highest reliability for indices)
 * Best for: Position confirmation, fewer false breakouts
 * Note: Requires futures data feed with OI information
 */

public class DerivativesFuturesOIWyckoffPhaseIdentifier implements IWyckoffPhaseIdentifier {
    
    private static final int OI_LOOKBACK_PERIOD = 20; // 5-20 bars as per CSV
    private static final double SIGNIFICANT_OI_CHANGE = 0.05; // 5% OI change is significant
    private static final double SIGNIFICANT_PRICE_CHANGE = 0.005; // 0.5% price change
    private static final double OI_SPIKE_THRESHOLD = 0.10; // 10% OI spike for Phase C
    private static final int TREND_CONFIRMATION_BARS = 3; // Bars needed for trend confirmation
    
    // OI analysis state
    private final List<OIDataPoint> oiHistory = new ArrayList<>();
    
    @Override
    public WyckoffPhase identifyPhase(List<Candle> data, int currentIndex) {
        if (data == null || data.isEmpty() || currentIndex < 0 || currentIndex >= data.size()) {
            return WyckoffPhase.UNKNOWN;
        }
        
        // Build OI history
        buildOIHistory(data, currentIndex);
        
        if (oiHistory.size() < 3) {
            return WyckoffPhase.UNKNOWN;
        }
        
        // Analyze OI patterns
        return analyzeOIPatterns(data, currentIndex);
    }
    
    private void buildOIHistory(List<Candle> data, int currentIndex) {
        oiHistory.clear();
        
        int startIdx = Math.max(0, currentIndex - OI_LOOKBACK_PERIOD);
        
        for (int i = startIdx; i <= currentIndex; i++) {
            Candle candle = data.get(i);
            
            OIDataPoint point = new OIDataPoint();
            point.price = candle.close();
            point.openInterest = candle.oi(); // Assuming OI is available in Candle
            point.volume = candle.volume();
            point.timestamp = candle.time();
            point.high = candle.high();
            point.low = candle.low();
            
            // Calculate changes
            if (i > startIdx) {
                OIDataPoint prev = oiHistory.get(oiHistory.size() - 1);
                point.priceChange = (point.price - prev.price) / prev.price;
                
                if (prev.openInterest > 0) {
                    point.oiChange = (point.openInterest - prev.openInterest) / (double)prev.openInterest;
                } else {
                    point.oiChange = 0;
                }
                
                // Determine market position
                point.marketPosition = categorizeMarketPosition(point.priceChange, point.oiChange);
            }
            
            oiHistory.add(point);
        }
    }
    
    private MarketPosition categorizeMarketPosition(double priceChange, double oiChange) {
        boolean priceUp = priceChange > SIGNIFICANT_PRICE_CHANGE;
        boolean priceDown = priceChange < -SIGNIFICANT_PRICE_CHANGE;
        boolean oiUp = oiChange > SIGNIFICANT_OI_CHANGE;
        boolean oiDown = oiChange < -SIGNIFICANT_OI_CHANGE;
        
        if (priceUp && oiUp) {
            return MarketPosition.LONG_BUILDUP; // Fresh longs entering
        } else if (priceUp && oiDown) {
            return MarketPosition.SHORT_COVERING; // Shorts exiting
        } else if (priceDown && oiUp) {
            return MarketPosition.SHORT_BUILDUP; // Fresh shorts entering
        } else if (priceDown && oiDown) {
            return MarketPosition.LONG_UNWINDING; // Longs exiting
        } else {
            return MarketPosition.NEUTRAL;
        }
    }
    
    private WyckoffPhase analyzeOIPatterns(List<Candle> data, int currentIndex) {
        if (oiHistory.size() < 5) {
            return WyckoffPhase.CONSOLIDATION;
        }
        
        Candle currentCandle = data.get(currentIndex);
        OIDataPoint currentOI = oiHistory.get(oiHistory.size() - 1);
        
        // Analyze recent OI patterns
        List<MarketPosition> recentPositions = getRecentPositions(5);
        
        // Count position types
        int longBuildupCount = 0;
        int shortCoveringCount = 0;
        int shortBuildupCount = 0;
        int longUnwindingCount = 0;
        
        for (MarketPosition pos : recentPositions) {
            switch (pos) {
                case LONG_BUILDUP: longBuildupCount++; break;
                case SHORT_COVERING: shortCoveringCount++; break;
                case SHORT_BUILDUP: shortBuildupCount++; break;
                case LONG_UNWINDING: longUnwindingCount++; break;
                default: break;
            }
        }
        
        // Check for OI spikes (Phase C indicators)
        boolean hasOISpike = detectOISpike();
        boolean hasFailedBreakout = detectFailedBreakoutWithOI(currentIndex);
        
        // Analyze trend alignment
        TrendAlignment trendAlignment = analyzeTrendAlignment();
        
        // Phase determination based on OI patterns
        
        // Strong long buildup with price trend = MARKUP
        if (longBuildupCount >= 3 && trendAlignment == TrendAlignment.BULLISH_ALIGNED) {
            return WyckoffPhase.MARKUP;
        }
        
        // Strong short buildup with price trend = MARKDOWN
        if (shortBuildupCount >= 3 && trendAlignment == TrendAlignment.BEARISH_ALIGNED) {
            return WyckoffPhase.MARKDOWN;
        }
        
        // Short covering dominance = Late markup or distribution ending
        if (shortCoveringCount >= 3) {
            if (currentOI.priceChange > 0) {
                return WyckoffPhase.ACCUMULATION_PHASE_D; // Sign of Strength
            } else {
                return WyckoffPhase.DISTRIBUTION_PHASE_D; // Exhaustion
            }
        }
        
        // Long unwinding dominance = Late markdown or accumulation starting
        if (longUnwindingCount >= 3) {
            if (currentOI.priceChange < 0) {
                return WyckoffPhase.DISTRIBUTION_PHASE_D; // Sign of Weakness
            } else {
                return WyckoffPhase.ACCUMULATION_PHASE_A; // Stopping action
            }
        }
        
        // OI spike on failed breakout = Spring or Upthrust (Phase C)
        if (hasOISpike && hasFailedBreakout) {
            if (currentCandle.close() < getAveragePrice()) {
                return WyckoffPhase.ACCUMULATION_PHASE_C; // Spring with OI spike
            } else {
                return WyckoffPhase.DISTRIBUTION_PHASE_C; // Upthrust with OI spike
            }
        }
        
        // Flat OI with sideways price = Consolidation (Phase B)
        if (isFlatOI() && isSidewaysPrice()) {
            // Determine accumulation or distribution based on prior trend
            if (wasInDowntrend()) {
                return WyckoffPhase.ACCUMULATION_PHASE_B;
            } else if (wasInUptrend()) {
                return WyckoffPhase.DISTRIBUTION_PHASE_B;
            }
            return WyckoffPhase.CONSOLIDATION;
        }
        
        // Divergence patterns
        OIDivergence divergence = detectOIDivergence();
        
        if (divergence == OIDivergence.BULLISH) {
            // Price down but OI down (weak selling) = Potential accumulation
            return WyckoffPhase.ACCUMULATION_PHASE_A;
        } else if (divergence == OIDivergence.BEARISH) {
            // Price up but OI down (weak buying) = Potential distribution
            return WyckoffPhase.DISTRIBUTION_PHASE_A;
        }
        
        // Mixed signals with trend continuation
        if (trendAlignment == TrendAlignment.BULLISH_ALIGNED) {
            return WyckoffPhase.REACCUMULATION;
        } else if (trendAlignment == TrendAlignment.BEARISH_ALIGNED) {
            return WyckoffPhase.REDISTRIBUTION;
        }
        
        return WyckoffPhase.CONSOLIDATION;
    }
    
    private List<MarketPosition> getRecentPositions(int count) {
        List<MarketPosition> recent = new ArrayList<>();
        int start = Math.max(0, oiHistory.size() - count);
        
        for (int i = start; i < oiHistory.size(); i++) {
            recent.add(oiHistory.get(i).marketPosition);
        }
        
        return recent;
    }
    
    private boolean detectOISpike() {
        if (oiHistory.size() < 2) {
            return false;
        }

        OIDataPoint current = oiHistory.get(oiHistory.size() - 1);

        return Math.abs(current.oiChange) > OI_SPIKE_THRESHOLD;
    }
    
    private boolean detectFailedBreakoutWithOI(int currentIndex) {
        if (currentIndex < 5 || oiHistory.size() < 5) {
            return false;
        }
        
        // Look for price breakout with OI spike, then reversal
        double highestHigh = Double.MIN_VALUE;
        double lowestLow = Double.MAX_VALUE;
        
        // Find range
        for (int i = oiHistory.size() - 10; i < oiHistory.size() - 3; i++) {
            if (i >= 0) {
                highestHigh = Math.max(highestHigh, oiHistory.get(i).high);
                lowestLow = Math.min(lowestLow, oiHistory.get(i).low);
            }
        }
        
        // Check recent bars for failed breakout
        for (int i = oiHistory.size() - 3; i < oiHistory.size(); i++) {
            OIDataPoint point = oiHistory.get(i);
            
            // Breakout with OI spike
            if ((point.high > highestHigh * 1.002 || point.low < lowestLow * 0.998) &&
                Math.abs(point.oiChange) > SIGNIFICANT_OI_CHANGE) {
                
                // Check if price reversed
                OIDataPoint current = oiHistory.get(oiHistory.size() - 1);
                if (point.high > highestHigh && current.price < highestHigh) {
                    return true; // Failed upside breakout
                }
                if (point.low < lowestLow && current.price > lowestLow) {
                    return true; // Failed downside breakout
                }
            }
        }
        
        return false;
    }
    
    private TrendAlignment analyzeTrendAlignment() {
        if (oiHistory.size() < TREND_CONFIRMATION_BARS) {
            return TrendAlignment.NEUTRAL;
        }
        
        int bullishAligned = 0;
        int bearishAligned = 0;
        
        for (int i = oiHistory.size() - TREND_CONFIRMATION_BARS; i < oiHistory.size(); i++) {
            OIDataPoint point = oiHistory.get(i);
            
            // Price and OI moving in same direction = aligned trend
            if (point.priceChange > 0 && point.oiChange > 0) {
                bullishAligned++; // Long buildup aligned
            } else if (point.priceChange < 0 && point.oiChange > 0) {
                bearishAligned++; // Short buildup aligned
            }
        }
        
        if (bullishAligned >= TREND_CONFIRMATION_BARS - 1) {
            return TrendAlignment.BULLISH_ALIGNED;
        } else if (bearishAligned >= TREND_CONFIRMATION_BARS - 1) {
            return TrendAlignment.BEARISH_ALIGNED;
        }
        
        return TrendAlignment.NEUTRAL;
    }
    
    private boolean isFlatOI() {
        if (oiHistory.size() < 5) {
            return false;
        }
        
        double totalOIChange = 0;
        for (int i = oiHistory.size() - 5; i < oiHistory.size(); i++) {
            totalOIChange += Math.abs(oiHistory.get(i).oiChange);
        }
        
        return totalOIChange / 5 < 0.02; // Average OI change less than 2%
    }
    
    private boolean isSidewaysPrice() {
        if (oiHistory.size() < 5) {
            return false;
        }
        
        double maxPrice = Double.MIN_VALUE;
        double minPrice = Double.MAX_VALUE;
        
        for (int i = oiHistory.size() - 5; i < oiHistory.size(); i++) {
            maxPrice = Math.max(maxPrice, oiHistory.get(i).price);
            minPrice = Math.min(minPrice, oiHistory.get(i).price);
        }
        
        double range = (maxPrice - minPrice) / minPrice;
        return range < 0.02; // Less than 2% range
    }
    
    private double getAveragePrice() {
        double sum = 0;
        int count = Math.min(10, oiHistory.size());
        
        for (int i = oiHistory.size() - count; i < oiHistory.size(); i++) {
            sum += oiHistory.get(i).price;
        }
        
        return sum / count;
    }
    
    private boolean wasInUptrend() {
        if (oiHistory.size() < 10) {
            return false;
        }
        
        double oldPrice = oiHistory.get(oiHistory.size() - 10).price;
        double recentPrice = oiHistory.get(oiHistory.size() - 3).price;
        
        return (recentPrice - oldPrice) / oldPrice > 0.01;
    }
    
    private boolean wasInDowntrend() {
        if (oiHistory.size() < 10) {
            return false;
        }
        
        double oldPrice = oiHistory.get(oiHistory.size() - 10).price;
        double recentPrice = oiHistory.get(oiHistory.size() - 3).price;
        
        return (recentPrice - oldPrice) / oldPrice < -0.01;
    }
    
    private OIDivergence detectOIDivergence() {
        if (oiHistory.size() < 5) {
            return OIDivergence.NONE;
        }
        
        // Calculate average price and OI changes
        double avgPriceChange = 0;
        double avgOIChange = 0;
        
        for (int i = oiHistory.size() - 5; i < oiHistory.size(); i++) {
            avgPriceChange += oiHistory.get(i).priceChange;
            avgOIChange += oiHistory.get(i).oiChange;
        }
        
        avgPriceChange /= 5;
        avgOIChange /= 5;
        
        // Bullish divergence: Price down but OI also down (weak selling)
        if (avgPriceChange < -0.002 && avgOIChange < -0.02) {
            return OIDivergence.BULLISH;
        }
        
        // Bearish divergence: Price up but OI down (weak buying)
        if (avgPriceChange > 0.002 && avgOIChange < -0.02) {
            return OIDivergence.BEARISH;
        }
        
        return OIDivergence.NONE;
    }
    
    @Override
    public String getIdentifierType() {
        return "DerivativesFuturesOI";
    }
    
    @Override
    public String getDescription() {
        return "Derivatives Futures Open Interest based phase identification. " +
               "Uses real market positioning data to confirm phases with high reliability. " +
               "Excellent for indices where traditional volume is unreliable. " +
               "Reliability: 4.5/5.0";
    }
    
    @Override
    public double getPhaseConfidence(List<Candle> data, int currentIndex) {
        if (oiHistory.isEmpty()) {
            return 0.0;
        }
        
        double confidence = 0.5;
        
        // Strong OI trend = higher confidence
        TrendAlignment alignment = analyzeTrendAlignment();
        if (alignment != TrendAlignment.NEUTRAL) {
            confidence += 0.25;
        }
        
        // OI spike = higher confidence
        if (detectOISpike()) {
            confidence += 0.15;
        }
        
        // Clear divergence = higher confidence
        if (detectOIDivergence() != OIDivergence.NONE) {
            confidence += 0.1;
        }
        
        return Math.min(1.0, confidence);
    }
    
    @Override
    public int getMinimumDataPoints() {
        return 5; // Need at least 5 bars for OI analysis
    }
    
    @Override
    public void reset() {
        oiHistory.clear();
    }
    
    // Inner classes and enums
    private enum MarketPosition {
        LONG_BUILDUP,    // Price↑ OI↑
        SHORT_COVERING,  // Price↑ OI↓
        SHORT_BUILDUP,   // Price↓ OI↑
        LONG_UNWINDING,  // Price↓ OI↓
        NEUTRAL
    }
    
    private enum TrendAlignment {
        BULLISH_ALIGNED,
        BEARISH_ALIGNED,
        NEUTRAL
    }
    
    private enum OIDivergence {
        BULLISH,
        BEARISH,
        NONE
    }
    
    private static final class OIDataPoint {
        double price;
        long openInterest;
        long volume;
        double high;
        double low;
        String timestamp;
        double priceChange;
        double oiChange;
        MarketPosition marketPosition = MarketPosition.NEUTRAL;
    }
}