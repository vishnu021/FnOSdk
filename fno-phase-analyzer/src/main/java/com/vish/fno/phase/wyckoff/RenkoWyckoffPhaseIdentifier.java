package com.vish.fno.phase.wyckoff;

import com.vish.fno.model.Candle;
import com.vish.fno.model.wyckoff.IWyckoffPhaseIdentifier;
import com.vish.fno.model.wyckoff.WyckoffPhase;

import java.util.ArrayList;
import java.util.List;

/**
 * Renko/Range Bars based Wyckoff phase identifier.
 * Uses fixed price movement blocks (bricks) to filter noise and identify trends.
 * 
 * Key concepts:
 * - Fixed brick size based on ATR for dynamic adjustment
 * - Sideways bricks indicate consolidation (Phase B)
 * - Uninterrupted staircases indicate trending (Phase D)
 * - Brief excursions with reversals indicate springs/upthrusts (Phase C)
 * 
 * Reliability: 3.5/5.0
 * Best for: Trend/range separation, noise filtering
 * Weakness: Parameter-sensitive, can repaint on small moves
 */

public class RenkoWyckoffPhaseIdentifier implements IWyckoffPhaseIdentifier {
    
    private static final int ATR_PERIOD = 14;
    private static final double BRICK_SIZE_MULTIPLIER = 0.75; // 0.5-1.0 × ATR as per CSV
    private static final int MIN_BRICKS_FOR_TREND = 3; // Minimum consecutive bricks for trend
    private static final int LOOKBACK_BRICKS = 10; // Number of bricks to analyze
    
    // Renko state
    private List<RenkoBrick> renkoBricks = new ArrayList<>();
    private double currentBrickSize = 0;
    
    @Override
    public WyckoffPhase identifyPhase(List<Candle> data, int currentIndex) {
        if (data == null || data.isEmpty() || currentIndex < 0 || currentIndex >= data.size()) {
            return WyckoffPhase.UNKNOWN;
        }
        
        // Calculate dynamic brick size based on ATR
        updateBrickSize(data, currentIndex);
        
        // Build Renko bricks from price data
        buildRenkoBricks(data, currentIndex);
        
        if (renkoBricks.size() < 3) {
            return WyckoffPhase.CONSOLIDATION;
        }
        
        // Analyze recent Renko patterns
        return analyzeRenkoPattern();
    }
    
    private void updateBrickSize(List<Candle> data, int currentIndex) {
        if (currentIndex < ATR_PERIOD) {
            // Use simple range for early periods
            double high = data.subList(0, currentIndex + 1).stream()
                .mapToDouble(Candle::high).max().orElse(0);
            double low = data.subList(0, currentIndex + 1).stream()
                .mapToDouble(Candle::low).min().orElse(0);
            currentBrickSize = (high - low) * 0.1; // 10% of range
        } else {
            // Calculate ATR for dynamic brick size
            int startIdx = Math.max(0, currentIndex - ATR_PERIOD);
            List<Candle> atrData = data.subList(startIdx, currentIndex + 1);
            double atr = calculateATR(atrData, ATR_PERIOD);
            currentBrickSize = atr * BRICK_SIZE_MULTIPLIER;
        }
        
        // Ensure minimum brick size
        if (currentBrickSize < 0.001) {
            currentBrickSize = 0.001; // Minimum 0.1% brick size
        }
    }
    
    private void buildRenkoBricks(List<Candle> data, int currentIndex) {
        renkoBricks.clear();
        
        if (data.isEmpty() || currentBrickSize == 0) {
            return;
        }
        
        double basePrice = data.get(0).close();
        Direction lastDirection = Direction.NONE;
        
        for (int i = 0; i <= currentIndex; i++) {
            Candle candle = data.get(i);
            double price = candle.close();
            
            // Calculate how many bricks to add
            double priceDiff = price - basePrice;
            int brickCount = (int)(Math.abs(priceDiff) / currentBrickSize);
            
            if (brickCount > 0) {
                Direction direction = priceDiff > 0 ? Direction.UP : Direction.DOWN;
                
                for (int j = 0; j < brickCount; j++) {
                    RenkoBrick brick = new RenkoBrick();
                    brick.direction = direction;
                    brick.open = basePrice;
                    
                    if (direction == Direction.UP) {
                        basePrice += currentBrickSize;
                        brick.close = basePrice;
                    } else {
                        basePrice -= currentBrickSize;
                        brick.close = basePrice;
                    }
                    
                    brick.high = Math.max(brick.open, brick.close);
                    brick.low = Math.min(brick.open, brick.close);
                    brick.timestamp = candle.time();
                    
                    // Check for reversal
                    if (lastDirection != Direction.NONE && lastDirection != direction) {
                        brick.isReversal = true;
                    }
                    
                    renkoBricks.add(brick);
                    lastDirection = direction;
                    
                    // Keep only recent bricks for analysis
                    if (renkoBricks.size() > LOOKBACK_BRICKS * 2) {
                        renkoBricks.remove(0);
                    }
                }
            }
        }
    }
    
    private WyckoffPhase analyzeRenkoPattern() {
        if (renkoBricks.size() < MIN_BRICKS_FOR_TREND) {
            return WyckoffPhase.CONSOLIDATION;
        }
        
        // Get recent bricks for analysis
        int startIdx = Math.max(0, renkoBricks.size() - LOOKBACK_BRICKS);
        List<RenkoBrick> recentBricks = renkoBricks.subList(startIdx, renkoBricks.size());
        
        // Count consecutive bricks in same direction
        int consecutiveUp = 0;
        int consecutiveDown = 0;
        int reversals = 0;
        int lateralCount = 0;
        
        Direction currentRun = Direction.NONE;
        int currentRunLength = 0;
        
        for (RenkoBrick brick : recentBricks) {
            if (brick.isReversal) {
                reversals++;
            }
            
            if (brick.direction == currentRun) {
                currentRunLength++;
            } else {
                // Direction changed
                if (currentRun == Direction.UP) {
                    consecutiveUp = Math.max(consecutiveUp, currentRunLength);
                } else if (currentRun == Direction.DOWN) {
                    consecutiveDown = Math.max(consecutiveDown, currentRunLength);
                }
                currentRun = brick.direction;
                currentRunLength = 1;
            }
        }
        
        // Update final run
        if (currentRun == Direction.UP) {
            consecutiveUp = Math.max(consecutiveUp, currentRunLength);
        } else if (currentRun == Direction.DOWN) {
            consecutiveDown = Math.max(consecutiveDown, currentRunLength);
        }
        
        // Check for lateral movement (alternating bricks)
        for (int i = 1; i < recentBricks.size(); i++) {
            if (recentBricks.get(i).direction != recentBricks.get(i-1).direction) {
                lateralCount++;
            }
        }
        
        // Phase determination based on Renko patterns
        
        // Uninterrupted staircase up = MARKUP (Phase D)
        if (consecutiveUp >= MIN_BRICKS_FOR_TREND && reversals == 0) {
            return WyckoffPhase.MARKUP;
        }
        
        // Uninterrupted staircase down = MARKDOWN (Phase D)
        if (consecutiveDown >= MIN_BRICKS_FOR_TREND && reversals == 0) {
            return WyckoffPhase.MARKDOWN;
        }
        
        // Brief excursion with reversal = Spring/Upthrust (Phase C)
        if (reversals > 0 && recentBricks.size() >= 3) {
            RenkoBrick lastBrick = recentBricks.get(recentBricks.size() - 1);
            RenkoBrick secondLast = recentBricks.get(recentBricks.size() - 2);
            
            // Check for spring pattern (down then up)
            if (secondLast.direction == Direction.DOWN && lastBrick.direction == Direction.UP) {
                if (consecutiveDown <= 2) { // Brief excursion
                    return WyckoffPhase.ACCUMULATION_PHASE_C;
                }
            }
            
            // Check for upthrust pattern (up then down)
            if (secondLast.direction == Direction.UP && lastBrick.direction == Direction.DOWN) {
                if (consecutiveUp <= 2) { // Brief excursion
                    return WyckoffPhase.DISTRIBUTION_PHASE_C;
                }
            }
        }
        
        // Lateral bricks = Consolidation (Phase B)
        double lateralRatio = (double)lateralCount / Math.max(1, recentBricks.size() - 1);
        if (lateralRatio > 0.5) { // More than 50% alternating
            // Determine if accumulation or distribution based on prior trend
            if (wasInDowntrend(recentBricks)) {
                return WyckoffPhase.ACCUMULATION_PHASE_B;
            } else if (wasInUptrend(recentBricks)) {
                return WyckoffPhase.DISTRIBUTION_PHASE_B;
            }
            return WyckoffPhase.CONSOLIDATION;
        }
        
        // Moderate trends with some reversals
        if (consecutiveUp > consecutiveDown && consecutiveUp >= 2) {
            if (reversals > 1) {
                return WyckoffPhase.REACCUMULATION; // Uptrend with pauses
            }
            return WyckoffPhase.ACCUMULATION_PHASE_D; // Early markup
        }
        
        if (consecutiveDown > consecutiveUp && consecutiveDown >= 2) {
            if (reversals > 1) {
                return WyckoffPhase.REDISTRIBUTION; // Downtrend with pauses
            }
            return WyckoffPhase.DISTRIBUTION_PHASE_D; // Early markdown
        }
        
        // Default to consolidation
        return WyckoffPhase.CONSOLIDATION;
    }
    
    private boolean wasInUptrend(List<RenkoBrick> bricks) {
        if (bricks.size() < 5) return false;
        
        // Check first half of bricks
        int midPoint = bricks.size() / 2;
        int upCount = 0;
        for (int i = 0; i < midPoint; i++) {
            if (bricks.get(i).direction == Direction.UP) {
                upCount++;
            }
        }
        return upCount > midPoint / 2;
    }
    
    private boolean wasInDowntrend(List<RenkoBrick> bricks) {
        if (bricks.size() < 5) return false;
        
        // Check first half of bricks
        int midPoint = bricks.size() / 2;
        int downCount = 0;
        for (int i = 0; i < midPoint; i++) {
            if (bricks.get(i).direction == Direction.DOWN) {
                downCount++;
            }
        }
        return downCount > midPoint / 2;
    }
    
    @Override
    public String getIdentifierType() {
        return "Renko";
    }
    
    @Override
    public String getDescription() {
        return "Renko/Range Bars phase identification using fixed price movement blocks. " +
               "Filters noise by only creating new bricks on significant price moves. " +
               "Excellent for trend/range separation but parameter-sensitive. " +
               "Reliability: 3.5/5.0";
    }
    
    @Override
    public double getPhaseConfidence(List<Candle> data, int currentIndex) {
        if (renkoBricks.isEmpty()) {
            return 0.0;
        }
        
        // Base confidence on pattern clarity
        double confidence = 0.5;
        
        // More bricks = higher confidence
        if (renkoBricks.size() >= LOOKBACK_BRICKS) {
            confidence += 0.2;
        }
        
        // Clear directional movement = higher confidence
        int lastBricks = Math.min(5, renkoBricks.size());
        boolean allSameDirection = true;
        Direction firstDir = renkoBricks.get(renkoBricks.size() - lastBricks).direction;
        
        for (int i = renkoBricks.size() - lastBricks; i < renkoBricks.size(); i++) {
            if (renkoBricks.get(i).direction != firstDir) {
                allSameDirection = false;
                break;
            }
        }
        
        if (allSameDirection) {
            confidence += 0.2;
        }
        
        return Math.min(1.0, confidence);
    }
    
    @Override
    public int getMinimumDataPoints() {
        return ATR_PERIOD; // Need ATR period for brick size calculation
    }
    
    @Override
    public void reset() {
        renkoBricks.clear();
        currentBrickSize = 0;
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
    
    // Inner classes
    private enum Direction {
        UP, DOWN, NONE
    }
    
    private static class RenkoBrick {
        double open;
        double close;
        double high;
        double low;
        Direction direction;
        boolean isReversal;
        String timestamp;
    }
}