package com.vish.fno.phase.wyckoff;

import com.vish.fno.model.Candle;
import com.vish.fno.model.wyckoff.IWyckoffPhaseIdentifier;
import com.vish.fno.model.wyckoff.WyckoffPhase;

import java.util.ArrayList;
import java.util.List;

/**
 * Structure + Swing Logic based Wyckoff phase identifier.
 * Uses Higher Highs/Higher Lows (HH/HL) and Lower Lows/Lower Highs (LL/LH) patterns
 * combined with fractal pivots and box boundaries for phase identification.
 * 
 * Key concepts:
 * - Fractal pivots identify swing points
 * - 2-3 swing sequences confirm trend vs range
 * - Failed breakouts indicate Phase C (springs/upthrusts)
 * - Box boundaries from Donchian channels
 * 
 * Reliability: 4.0/5.0
 * Best for: Clear logic, execution triggers, adaptable
 * Weakness: Subject to wick noise on 1-min
 */

public class StructureSwingWyckoffPhaseIdentifier implements IWyckoffPhaseIdentifier {
    
    private static final int FRACTAL_LOOKBACK = 2; // 2-3 bar lookback for fractals
    private static final int DONCHIAN_PERIOD = 20; // 20-40 as per CSV
    private static final double MIN_SWING_SIZE_MULTIPLIER = 0.5; // Min swing = 0.5 × ATR
    private static final int MIN_SWINGS_FOR_TREND = 2; // Need 2-3 swings to confirm
    private static final double FAILED_BREAKOUT_THRESHOLD = 0.002; // 0.2% for false break
    
    // Swing structure tracking
    private List<SwingPoint> swingHighs = new ArrayList<>();
    private List<SwingPoint> swingLows = new ArrayList<>();
    private double boxTop = 0;
    private double boxBottom = 0;
    
    @Override
    public WyckoffPhase identifyPhase(List<Candle> data, int currentIndex) {
        if (data == null || data.isEmpty() || currentIndex < 0 || currentIndex >= data.size()) {
            return WyckoffPhase.UNKNOWN;
        }
        
        // Identify swing points using fractals
        identifySwingPoints(data, currentIndex);
        
        // Update box boundaries (Donchian channel)
        updateBoxBoundaries(data, currentIndex);
        
        // Analyze swing structure
        return analyzeSwingStructure(data, currentIndex);
    }
    
    private void identifySwingPoints(List<Candle> data, int currentIndex) {
        swingHighs.clear();
        swingLows.clear();
        
        // Calculate minimum swing size based on ATR
        double minSwingSize = calculateMinSwingSize(data, currentIndex);
        
        // Find fractal highs and lows
        for (int i = FRACTAL_LOOKBACK; i <= currentIndex - FRACTAL_LOOKBACK; i++) {
            if (isFractalHigh(data, i)) {
                SwingPoint high = new SwingPoint();
                high.price = data.get(i).high();
                high.index = i;
                high.timestamp = data.get(i).time();
                
                // Only add if it's a significant swing
                if (swingHighs.isEmpty() || 
                    Math.abs(high.price - swingHighs.get(swingHighs.size() - 1).price) >= minSwingSize) {
                    swingHighs.add(high);
                }
            }
            
            if (isFractalLow(data, i)) {
                SwingPoint low = new SwingPoint();
                low.price = data.get(i).low();
                low.index = i;
                low.timestamp = data.get(i).time();
                
                // Only add if it's a significant swing
                if (swingLows.isEmpty() || 
                    Math.abs(low.price - swingLows.get(swingLows.size() - 1).price) >= minSwingSize) {
                    swingLows.add(low);
                }
            }
        }
        
        // Keep only recent swings for analysis
        int maxSwings = 10;
        if (swingHighs.size() > maxSwings) {
            swingHighs = swingHighs.subList(swingHighs.size() - maxSwings, swingHighs.size());
        }
        if (swingLows.size() > maxSwings) {
            swingLows = swingLows.subList(swingLows.size() - maxSwings, swingLows.size());
        }
    }
    
    private boolean isFractalHigh(List<Candle> data, int index) {
        if (index < FRACTAL_LOOKBACK || index >= data.size() - FRACTAL_LOOKBACK) {
            return false;
        }
        
        double centerHigh = data.get(index).high();
        
        // Check if center bar has highest high
        for (int i = index - FRACTAL_LOOKBACK; i <= index + FRACTAL_LOOKBACK; i++) {
            if (i != index && data.get(i).high() >= centerHigh) {
                return false;
            }
        }
        
        return true;
    }
    
    private boolean isFractalLow(List<Candle> data, int index) {
        if (index < FRACTAL_LOOKBACK || index >= data.size() - FRACTAL_LOOKBACK) {
            return false;
        }
        
        double centerLow = data.get(index).low();
        
        // Check if center bar has lowest low
        for (int i = index - FRACTAL_LOOKBACK; i <= index + FRACTAL_LOOKBACK; i++) {
            if (i != index && data.get(i).low() <= centerLow) {
                return false;
            }
        }
        
        return true;
    }
    
    private double calculateMinSwingSize(List<Candle> data, int currentIndex) {
        if (currentIndex < 14) {
            // Use simple range for early periods
            double range = data.get(currentIndex).high() - data.get(currentIndex).low();
            return range * 2; // Require 2x daily range for swing
        }
        
        int startIdx = Math.max(0, currentIndex - 14);
        List<Candle> atrData = data.subList(startIdx, currentIndex + 1);
        double atr = calculateATR(atrData, 14);
        return atr * MIN_SWING_SIZE_MULTIPLIER;
    }
    
    private void updateBoxBoundaries(List<Candle> data, int currentIndex) {
        int startIdx = Math.max(0, currentIndex - DONCHIAN_PERIOD);
        
        boxTop = Double.MIN_VALUE;
        boxBottom = Double.MAX_VALUE;
        
        for (int i = startIdx; i <= currentIndex; i++) {
            boxTop = Math.max(boxTop, data.get(i).high());
            boxBottom = Math.min(boxBottom, data.get(i).low());
        }
    }
    
    private WyckoffPhase analyzeSwingStructure(List<Candle> data, int currentIndex) {
        if (swingHighs.size() < 2 || swingLows.size() < 2) {
            return WyckoffPhase.CONSOLIDATION;
        }
        
        Candle currentCandle = data.get(currentIndex);
        
        // Check for Higher Highs/Higher Lows (HH/HL) - Uptrend
        boolean hasHH = false;
        boolean hasHL = false;
        boolean hasLL = false;
        boolean hasLH = false;
        
        // Analyze recent swing patterns
        if (swingHighs.size() >= 2) {
            SwingPoint lastHigh = swingHighs.get(swingHighs.size() - 1);
            SwingPoint prevHigh = swingHighs.get(swingHighs.size() - 2);
            hasHH = lastHigh.price > prevHigh.price;
            hasLH = lastHigh.price < prevHigh.price;
        }
        
        if (swingLows.size() >= 2) {
            SwingPoint lastLow = swingLows.get(swingLows.size() - 1);
            SwingPoint prevLow = swingLows.get(swingLows.size() - 2);
            hasHL = lastLow.price > prevLow.price;
            hasLL = lastLow.price < prevLow.price;
        }
        
        // Check for failed breakouts (Phase C indicators)
        boolean hasFailedBreakoutUp = checkFailedBreakout(data, currentIndex, true);
        boolean hasFailedBreakoutDown = checkFailedBreakout(data, currentIndex, false);
        
        // Phase determination based on swing structure
        
        // Strong uptrend: Persistent HH/HL
        if (hasHH && hasHL && countConsecutiveHHHL() >= MIN_SWINGS_FOR_TREND) {
            return WyckoffPhase.MARKUP;
        }
        
        // Strong downtrend: Persistent LL/LH
        if (hasLL && hasLH && countConsecutiveLLLH() >= MIN_SWINGS_FOR_TREND) {
            return WyckoffPhase.MARKDOWN;
        }
        
        // Failed breakout patterns (Phase C)
        if (hasFailedBreakoutDown) {
            // False break below support = Spring
            return WyckoffPhase.ACCUMULATION_PHASE_C;
        }
        
        if (hasFailedBreakoutUp) {
            // False break above resistance = Upthrust
            return WyckoffPhase.DISTRIBUTION_PHASE_C;
        }
        
        // Alternating swings inside box (Phase B)
        if (isAlternatingSwingsInBox()) {
            double boxWidth = boxTop - boxBottom;
            double pricePosition = (currentCandle.close() - boxBottom) / boxWidth;
            
            // Determine accumulation or distribution based on position and prior trend
            if (pricePosition < 0.4 && wasInDowntrend(data, currentIndex)) {
                return WyckoffPhase.ACCUMULATION_PHASE_B;
            } else if (pricePosition > 0.6 && wasInUptrend(data, currentIndex)) {
                return WyckoffPhase.DISTRIBUTION_PHASE_B;
            }
            
            return WyckoffPhase.CONSOLIDATION;
        }
        
        // Early trend signs (Phase D)
        if (hasHH && hasHL) {
            if (currentCandle.close() > boxTop * 0.998) {
                return WyckoffPhase.ACCUMULATION_PHASE_D; // Sign of Strength
            }
            return WyckoffPhase.REACCUMULATION;
        }
        
        if (hasLL && hasLH) {
            if (currentCandle.close() < boxBottom * 1.002) {
                return WyckoffPhase.DISTRIBUTION_PHASE_D; // Sign of Weakness
            }
            return WyckoffPhase.REDISTRIBUTION;
        }
        
        // Swing failures at extremes (Phase E - not in original Wyckoff but useful)
        if (hasSwingFailureAtTop(currentCandle)) {
            return WyckoffPhase.DISTRIBUTION_PHASE_B;
        }
        
        if (hasSwingFailureAtBottom(currentCandle)) {
            return WyckoffPhase.ACCUMULATION_PHASE_B;
        }
        
        return WyckoffPhase.CONSOLIDATION;
    }
    
    private boolean checkFailedBreakout(List<Candle> data, int currentIndex, boolean checkUpside) {
        if (currentIndex < 5) return false;
        
        // Look for breakout and quick reversal in last 5 bars
        for (int i = currentIndex - 4; i <= currentIndex - 1; i++) {
            Candle candle = data.get(i);
            
            if (checkUpside) {
                // Check for failed upside breakout
                if (candle.high() > boxTop * (1 + FAILED_BREAKOUT_THRESHOLD)) {
                    // Now check if price came back below
                    Candle current = data.get(currentIndex);
                    if (current.close() < boxTop) {
                        return true;
                    }
                }
            } else {
                // Check for failed downside breakout
                if (candle.low() < boxBottom * (1 - FAILED_BREAKOUT_THRESHOLD)) {
                    // Now check if price came back above
                    Candle current = data.get(currentIndex);
                    if (current.close() > boxBottom) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    private int countConsecutiveHHHL() {
        int count = 0;
        
        for (int i = 1; i < Math.min(swingHighs.size(), swingLows.size()); i++) {
            if (swingHighs.get(i).price > swingHighs.get(i-1).price &&
                swingLows.get(i).price > swingLows.get(i-1).price) {
                count++;
            } else {
                break;
            }
        }
        
        return count;
    }
    
    private int countConsecutiveLLLH() {
        int count = 0;
        
        for (int i = 1; i < Math.min(swingHighs.size(), swingLows.size()); i++) {
            if (swingHighs.get(i).price < swingHighs.get(i-1).price &&
                swingLows.get(i).price < swingLows.get(i-1).price) {
                count++;
            } else {
                break;
            }
        }
        
        return count;
    }
    
    private boolean isAlternatingSwingsInBox() {
        if (swingHighs.size() < 3 || swingLows.size() < 3) {
            return false;
        }
        
        // Check if swings are contained within box
        int swingsInBox = 0;
        int totalSwings = 0;
        
        for (SwingPoint high : swingHighs) {
            totalSwings++;
            if (high.price <= boxTop * 1.01 && high.price >= boxBottom) {
                swingsInBox++;
            }
        }
        
        for (SwingPoint low : swingLows) {
            totalSwings++;
            if (low.price >= boxBottom * 0.99 && low.price <= boxTop) {
                swingsInBox++;
            }
        }
        
        // More than 70% of swings within box
        return (double)swingsInBox / totalSwings > 0.7;
    }
    
    private boolean hasSwingFailureAtTop(Candle current) {
        if (swingHighs.isEmpty()) return false;
        
        SwingPoint lastHigh = swingHighs.get(swingHighs.size() - 1);
        return current.close() < lastHigh.price * 0.995 && 
               Math.abs(current.high() - boxTop) / boxTop < 0.01;
    }
    
    private boolean hasSwingFailureAtBottom(Candle current) {
        if (swingLows.isEmpty()) return false;
        
        SwingPoint lastLow = swingLows.get(swingLows.size() - 1);
        return current.close() > lastLow.price * 1.005 && 
               Math.abs(current.low() - boxBottom) / boxBottom < 0.01;
    }
    
    private boolean wasInUptrend(List<Candle> data, int currentIndex) {
        if (currentIndex < 20) return false;
        
        double oldPrice = data.get(currentIndex - 20).close();
        double recentPrice = data.get(currentIndex - 5).close();
        
        return (recentPrice - oldPrice) / oldPrice > 0.01; // 1% up
    }
    
    private boolean wasInDowntrend(List<Candle> data, int currentIndex) {
        if (currentIndex < 20) return false;
        
        double oldPrice = data.get(currentIndex - 20).close();
        double recentPrice = data.get(currentIndex - 5).close();
        
        return (recentPrice - oldPrice) / oldPrice < -0.01; // 1% down
    }
    
    @Override
    public String getIdentifierType() {
        return "StructureSwing";
    }
    
    @Override
    public String getDescription() {
        return "Structure + Swing Logic phase identification using HH/HL and LL/LH patterns. " +
               "Uses fractal pivots for swing detection and Donchian channels for box boundaries. " +
               "Excellent for clear execution triggers and adaptable logic. " +
               "Reliability: 4.0/5.0";
    }
    
    @Override
    public double getPhaseConfidence(List<Candle> data, int currentIndex) {
        double confidence = 0.5;
        
        // More swings = higher confidence
        if (swingHighs.size() >= 5 && swingLows.size() >= 5) {
            confidence += 0.2;
        }
        
        // Clear structure = higher confidence
        if (countConsecutiveHHHL() >= 2 || countConsecutiveLLLH() >= 2) {
            confidence += 0.2;
        }
        
        // Failed breakout patterns = high confidence
        if (checkFailedBreakout(data, currentIndex, true) || 
            checkFailedBreakout(data, currentIndex, false)) {
            confidence += 0.1;
        }
        
        return Math.min(1.0, confidence);
    }
    
    @Override
    public int getMinimumDataPoints() {
        return DONCHIAN_PERIOD;
    }
    
    @Override
    public void reset() {
        swingHighs.clear();
        swingLows.clear();
        boxTop = 0;
        boxBottom = 0;
    }
    
    private double calculateATR(List<Candle> candles, int period) {
        if (candles.size() < period + 1) return 0.0;
        
        double atr = 0.0;
        for (int i = 1; i < candles.size(); i++) {
            Candle current = candles.get(i);
            Candle previous = candles.get(i - 1);
            
            double tr = Math.max(
                current.high() - current.low(),
                Math.max(
                    Math.abs(current.high() - previous.close()),
                    Math.abs(current.low() - previous.close())
                )
            );
            atr += tr;
        }
        return atr / (candles.size() - 1);
    }
    
    // Inner class for swing points
    private static class SwingPoint {
        double price;
        int index;
        String timestamp;
    }
}