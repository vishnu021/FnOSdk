package com.vish.fno.phase.service;

import com.vish.fno.model.Candle;
import com.vish.fno.model.Ticker;
import com.vish.fno.model.wyckoff.IWyckoffPhaseIdentifier;
import com.vish.fno.model.wyckoff.WyckoffPhase;
import com.vish.fno.phase.factory.WyckoffPhaseIdentifierFactory;
import com.vish.fno.phase.factory.WyckoffIdentifierType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class WyckoffPhaseService {
    
    private static final Logger logger = LoggerFactory.getLogger(WyckoffPhaseService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final int MINUTE_BUFFER = 60; // Keep 60 minutes of data for analysis

    private final WyckoffPhaseIdentifierFactory identifierFactory;
    private IWyckoffPhaseIdentifier phaseIdentifier;
    private WyckoffIdentifierType identifierType;

    public WyckoffPhaseService(WyckoffPhaseIdentifierFactory identifierFactory) {
        this.identifierFactory = identifierFactory;
        // Use default identifier from factory
        this.phaseIdentifier = identifierFactory.getDefault();
        this.identifierType = identifierFactory.getDefaultType();
        logger.info("WyckoffPhaseService initialized with identifier: {} - {}",
                   phaseIdentifier.getIdentifierType(), phaseIdentifier.getDescription());
    }
    
    // Cache for storing recent price data per symbol
    private final Map<String, LinkedList<Candle>> recentDataCache = new ConcurrentHashMap<>();
    
    // Cache for storing identified phases per symbol and hour
    private final Map<String, Map<Integer, WyckoffPhase>> hourlyPhaseCache = new ConcurrentHashMap<>();
    
    // Current hour tracking
    private final Map<String, Integer> currentHourTracker = new ConcurrentHashMap<>();
    
    
    /**
     * Get the current Wyckoff phase for a symbol based on recent tick data
     */
    public WyckoffPhase getCurrentPhase(String symbol, Ticker currentTick) {
        // Update data cache with current tick
        updateDataCache(symbol, currentTick);
        
        // Get current hour
        LocalDateTime tickTime = LocalDateTime.ofEpochSecond(
            currentTick.tickTimestamp().getTime() / 1000, 0, java.time.ZoneOffset.UTC
        );
        int currentHour = tickTime.getHour();
        
        // Check if we've moved to a new hour
        Integer lastHour = currentHourTracker.get(symbol);
        if (lastHour == null || lastHour != currentHour) {
            // New hour - calculate phase
            WyckoffPhase phase = calculateHourlyPhase(symbol);
            
            // Cache the phase for this hour
            hourlyPhaseCache.computeIfAbsent(symbol, k -> new HashMap<>())
                           .put(currentHour, phase);
            
            currentHourTracker.put(symbol, currentHour);
            
            logger.debug("New hour {} for {}: Phase = {}", currentHour, symbol, phase.getPhaseName());
            
            return phase;
        }
        
        // Return cached phase for current hour
        return hourlyPhaseCache.getOrDefault(symbol, new HashMap<>())
                               .getOrDefault(currentHour, WyckoffPhase.UNKNOWN);
    }
    
    /**
     * Get the Wyckoff phase based on candlestick data
     */
    public WyckoffPhase getPhaseFromCandles(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return WyckoffPhase.UNKNOWN;
        }
        
        // Identify phase using the most recent data
        return phaseIdentifier.identifyPhase(candles, candles.size() - 1);
    }
    
    /**
     * Get strategy recommendation based on Wyckoff phase
     * Enhanced with comprehensive Wyckoff-based strategy mapping
     */
    public String getStrategyForPhase(WyckoffPhase phase) {
        switch (phase) {
            // MARKUP PHASE STRATEGIES - Demand overwhelms supply, trending higher
            case MARKUP:
                // Rotate between markup strategies based on time and market conditions
                int markupRotation = (int)(System.currentTimeMillis() / 3600000) % 4;
                switch (markupRotation) {
                    case 0:
                        return "WyckoffBreakoutStrategy"; // Sign of Strength breakouts
                    case 1:
                        return "PullbackBuyingStrategy"; // Last Point of Support entries
                    case 2:
                        return "MarkupTrendFollowingStrategy"; // Trend continuation
                    default:
                        return "RelativeStrengthMomentumStrategy"; // Momentum leaders
                }
                
            // MARKDOWN PHASE STRATEGIES - Supply exceeds demand, trending lower
            case MARKDOWN:
                // Rotate between markdown strategies based on market phase
                int markdownRotation = (int)(System.currentTimeMillis() / 3600000) % 4;
                switch (markdownRotation) {
                    case 0:
                        return "BreakdownTradingStrategy"; // Sign of Weakness breakdowns
                    case 1:
                        return "ContinuationShortingStrategy"; // Bear trend continuation
                    case 2:
                        return "MomentumShortingStrategy"; // Momentum shorting
                    default:
                        return "VolatilityScalpingStrategy"; // High volatility scalping
                }
                
            // ACCUMULATION PHASE STRATEGIES - Smart money building positions
            case ACCUMULATION_PHASE_A:
                // Early accumulation - heavy selling climax, initial support
                return "VolumeSpreadAnalysisStrategy"; // VSA for absorption detection
                
            case ACCUMULATION_PHASE_B:
                // Building base - range development, multiple support tests
                int accumBRotation = (int)(System.currentTimeMillis() / 3600000) % 3;
                if (accumBRotation == 0) {
                    return "AccumulationRangeStrategy"; // Range trading
                } else if (accumBRotation == 1) {
                    return "MeanReversionStrategy"; // Statistical mean reversion
                } else {
                    return "StatisticalArbitrageStrategy"; // Price inefficiency capture
                }
                
            case ACCUMULATION_PHASE_C:
                // Testing phase - springs and shakeouts
                return "SpringTradingStrategy"; // Spring/shakeout trading
                
            case ACCUMULATION_PHASE_D:
                // Sign of strength emerging - prepare for markup
                return "WyckoffBreakoutStrategy"; // Early breakout signals
                
            // DISTRIBUTION PHASE STRATEGIES - Smart money distributing to public
            case DISTRIBUTION_PHASE_A:
                // Early distribution - buying climax, initial weakness
                return "ProtectiveExitStrategy"; // Protect long positions
                
            case DISTRIBUTION_PHASE_B:
                // Range development at highs - multiple resistance tests
                int distBRotation = (int)(System.currentTimeMillis() / 3600000) % 2;
                return distBRotation == 0 ? "DistributionRangeFadingStrategy" : "ProtectiveExitStrategy";
                
            case DISTRIBUTION_PHASE_C:
                // Testing phase - upthrusts and failures
                return "UpthrustTradingStrategy"; // UTAD and upthrust trading
                
            case DISTRIBUTION_PHASE_D:
                // Sign of weakness - prepare for markdown
                return "BreakdownTradingStrategy"; // Early breakdown signals
                
            // SPECIAL WYCKOFF PHASES
            case CONSOLIDATION:
                // Neutral phase - use range-bound strategies
                int consRotation = (int)(System.currentTimeMillis() / 3600000) % 2;
                return consRotation == 0 ? "AccumulationRangeStrategy" : "MeanReversionStrategy";
                
            case REACCUMULATION:
                // Continuation of uptrend after pause - bullish bias
                return "PullbackBuyingStrategy"; // Buy pullbacks in uptrend
                
            case REDISTRIBUTION:
                // Continuation of downtrend after pause - bearish bias
                return "ContinuationShortingStrategy"; // Short rallies in downtrend
                
            // UNKNOWN OR TRANSITIONAL PHASES
            case UNKNOWN:
            default:
                // Conservative approach when phase is unclear
                return "ConservativeUndefinedStrategy"; // Risk management focus
        }
    }

    /**
     * Get alternative strategy for phase rotation and diversification
     * Provides secondary strategy options for enhanced performance
     */
    public String getAlternativeStrategyForPhase(WyckoffPhase phase) {
        switch (phase) {
            case MARKUP:
                return "PullbackBuyingStrategy";
            case MARKDOWN:
                return "MomentumShortingStrategy";
            case ACCUMULATION_PHASE_B:
                return "StatisticalArbitrageStrategy";
            case ACCUMULATION_PHASE_C:
                return "MeanReversionStrategy";
            case DISTRIBUTION_PHASE_A:
                return "UpthrustTradingStrategy";
            case DISTRIBUTION_PHASE_B:
                return "ProtectiveExitStrategy";
            default:
                return getStrategyForPhase(phase);
        }
    }

    /**
     * Get strategy description for logging and analysis
     */
    public String getStrategyDescription(String strategyName) {
        switch (strategyName) {
            // Accumulation Strategies
            case "SpringTradingStrategy":
                return "Spring/Shakeout trading - enters on false breakdowns with quick recovery";
            case "MeanReversionStrategy":
                return "Mean reversion - buys oversold, sells overbought within accumulation range";
            case "StatisticalArbitrageStrategy":
                return "Statistical arbitrage - exploits price inefficiencies with Z-score analysis";
            case "AccumulationRangeStrategy":
                return "Range trading - buys support, sells resistance in accumulation";
            case "VolumeSpreadAnalysisStrategy":
                return "VSA - identifies absorption patterns with high volume, low spread";
            
            // Markup Strategies
            case "WyckoffBreakoutStrategy":
                return "Breakout trading - enters on Sign of Strength with volume expansion";
            case "PullbackBuyingStrategy":
                return "Pullback buying - enters at Last Point of Support with pattern confirmation";
            case "MarkupTrendFollowingStrategy":
                return "Trend following - rides momentum with moving average and pattern signals";
            case "RelativeStrengthMomentumStrategy":
                return "Momentum trading - focuses on relative strength leaders with acceleration";
            
            // Distribution Strategies
            case "UpthrustTradingStrategy":
                return "Upthrust trading - shorts false breakouts above distribution resistance";
            case "DistributionRangeFadingStrategy":
                return "Range fading - sells rallies, covers dips within distribution range";
            case "ProtectiveExitStrategy":
                return "Protective exits - manages long positions defensively during distribution";
            
            // Markdown Strategies
            case "BreakdownTradingStrategy":
                return "Breakdown trading - shorts Sign of Weakness and Last Point of Supply";
            case "ContinuationShortingStrategy":
                return "Continuation shorting - follows bearish momentum with pattern confirmation";
            case "MomentumShortingStrategy":
                return "Momentum shorting - targets relative weakness with volume confirmation";
            case "VolatilityScalpingStrategy":
                return "Volatility scalping - quick profits from high volatility markdown moves";
            
            default:
                return "Strategy description not available";
        }
    }
    
    /**
     * Update the data cache with new tick data
     */
    private void updateDataCache(String symbol, Ticker tick) {
        LinkedList<Candle> dataList = recentDataCache.computeIfAbsent(
            symbol, k -> new LinkedList<>()
        );
        
        // Convert tick to candlestick data
        String timeStr = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
            LocalDateTime.ofEpochSecond(tick.tickTimestamp().getTime() / 1000, 0, java.time.ZoneOffset.UTC)
        );

        Candle candle = new Candle(
            timeStr,
            tick.lastTradedPrice(),  // Use LTP as open
            tick.highPrice(),         // Use high price
            tick.lowPrice(),          // Use low price
            tick.lastTradedPrice(),   // Use LTP as close
            tick.volumeTradedToday(),
            (long) tick.oi()
        );
        
        dataList.add(candle);
        
        // Keep only recent data (last 60 minutes)
        while (dataList.size() > MINUTE_BUFFER) {
            dataList.removeFirst();
        }
    }
    
    /**
     * Calculate the hourly phase based on accumulated minute data
     */
    private WyckoffPhase calculateHourlyPhase(String symbol) {
        LinkedList<Candle> dataList = recentDataCache.get(symbol);
        
        if (dataList == null || dataList.isEmpty()) {
            return WyckoffPhase.UNKNOWN;
        }
        
        // Create hourly candle from minute data
        List<Candle> minuteCandles = new ArrayList<>(dataList);
        
        if (minuteCandles.size() < 5) {
            // Not enough data - use simple analysis
            return analyzeRecentTicks(minuteCandles);
        }
        
        // Aggregate into 5-minute candles for better analysis
        List<Candle> fiveMinCandles = aggregateToFiveMinutes(minuteCandles);
        
        // Identify phase
        return phaseIdentifier.identifyPhase(fiveMinCandles, fiveMinCandles.size() - 1);
    }
    
    /**
     * Simple analysis for when we have limited data
     */
    private WyckoffPhase analyzeRecentTicks(List<Candle> candles) {
        if (candles.size() < 2) {
            return WyckoffPhase.CONSOLIDATION;
        }
        
        double firstPrice = candles.get(0).close();
        double lastPrice = candles.get(candles.size() - 1).close();
        double priceChange = (lastPrice - firstPrice) / firstPrice;
        
        // Determine trend
        if (priceChange > 0.002) {
            return WyckoffPhase.MARKUP;
        } else if (priceChange < -0.002) {
            return WyckoffPhase.MARKDOWN;
        } else {
            // Check volatility
            double maxPrice = candles.stream().mapToDouble(Candle::high).max().orElse(lastPrice);
            double minPrice = candles.stream().mapToDouble(Candle::low).min().orElse(lastPrice);
            double volatility = (maxPrice - minPrice) / firstPrice;
            
            if (volatility > 0.005) {
                // High volatility in range
                return priceChange > 0 ? WyckoffPhase.ACCUMULATION_PHASE_B : WyckoffPhase.DISTRIBUTION_PHASE_B;
            }
            
            return WyckoffPhase.CONSOLIDATION;
        }
    }
    
    /**
     * Aggregate minute candles into 5-minute candles
     */
    private List<Candle> aggregateToFiveMinutes(List<Candle> minuteCandles) {
        List<Candle> fiveMinCandles = new ArrayList<>();
        
        for (int i = 0; i < minuteCandles.size(); i += 5) {
            int endIdx = Math.min(i + 5, minuteCandles.size());
            List<Candle> batch = minuteCandles.subList(i, endIdx);
            
            if (!batch.isEmpty()) {
                double open = batch.get(0).open();
                double close = batch.get(batch.size() - 1).close();
                double high = batch.stream().mapToDouble(Candle::high).max().orElse(open);
                double low = batch.stream().mapToDouble(Candle::low).min().orElse(open);
                long volume = batch.stream().mapToLong(Candle::volume).sum();
                long oi = batch.get(batch.size() - 1).oi();
                String time = batch.get(0).time();
                
                fiveMinCandles.add(new Candle(time, open, high, low, close, volume, oi));
            }
        }
        
        return fiveMinCandles;
    }
    
    /**
     * Clear cache for a symbol (useful when starting new simulation)
     */
    public void clearCache(String symbol) {
        recentDataCache.remove(symbol);
        hourlyPhaseCache.remove(symbol);
        currentHourTracker.remove(symbol);
        phaseIdentifier.reset();
    }
    
    /**
     * Switch to a different phase identifier at runtime
     *
     * @param newIdentifierType The identifier type enum to switch to
     */
    public void switchIdentifier(WyckoffIdentifierType newIdentifierType) {
        IWyckoffPhaseIdentifier newIdentifier = identifierFactory.getIdentifier(newIdentifierType);
        this.phaseIdentifier = newIdentifier;
        this.identifierType = newIdentifierType;
        // Clear caches when switching identifier
        recentDataCache.clear();
        hourlyPhaseCache.clear();
        currentHourTracker.clear();
        logger.info("Switched to identifier: {} - {}",
                   newIdentifier.getIdentifierType(), newIdentifier.getDescription());
    }

    /**
     * Switch to a different phase identifier at runtime using string key (legacy support)
     *
     * @param identifierKey The identifier key string
     * @deprecated Use {@link #switchIdentifier(WyckoffIdentifierType)} for type-safe access
     */
    @Deprecated(since = "1.1", forRemoval = false)
    public void switchIdentifier(String identifierKey) {
        WyckoffIdentifierType type = WyckoffIdentifierType.fromKey(identifierKey);
        if (type != null) {
            switchIdentifier(type);
        } else {
            logger.warn("Unknown identifier key: '{}', keeping current identifier", identifierKey);
        }
    }
    
    /**
     * Get the current identifier type being used
     *
     * @return Current identifier type enum
     */
    public WyckoffIdentifierType getCurrentIdentifierType() {
        return identifierType;
    }

    /**
     * Get the current identifier type key string (legacy support)
     *
     * @return Current identifier type key
     * @deprecated Use {@link #getCurrentIdentifierType()} for type-safe access
     */
    @Deprecated(since = "1.1", forRemoval = false)
    public String getCurrentIdentifierTypeKey() {
        return identifierType.getKey();
    }
    
    /**
     * Get phase with confidence score
     */
    public PhaseWithConfidence getPhaseWithConfidence(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return new PhaseWithConfidence(WyckoffPhase.UNKNOWN, 0.0);
        }
        
        int index = candles.size() - 1;
        WyckoffPhase phase = phaseIdentifier.identifyPhase(candles, index);
        double confidence = phaseIdentifier.getPhaseConfidence(candles, index);
        
        return new PhaseWithConfidence(phase, confidence);
    }
    
    /**
     * Inner class to hold phase and confidence together
     */
    public static class PhaseWithConfidence {
        private final WyckoffPhase phase;
        private final double confidence;
        
        public PhaseWithConfidence(WyckoffPhase phase, double confidence) {
            this.phase = phase;
            this.confidence = confidence;
        }
        
        public WyckoffPhase getPhase() {
            return phase;
        }
        
        public double getConfidence() {
            return confidence;
        }
        
        @Override
        public String toString() {
            return String.format("%s (confidence: %.2f%%)", phase.getPhaseName(), confidence * 100);
        }
    }
    
    /**
     * Get current phase statistics for logging
     */
    public String getPhaseStats(String symbol) {
        Map<Integer, WyckoffPhase> phases = hourlyPhaseCache.get(symbol);
        if (phases == null || phases.isEmpty()) {
            return "No phase data available";
        }
        
        Map<WyckoffPhase, Long> phaseCounts = phases.values().stream()
            .collect(java.util.stream.Collectors.groupingBy(
                phase -> phase,
                java.util.stream.Collectors.counting()
            ));
        
        return phaseCounts.toString();
    }
}