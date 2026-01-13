package com.vish.fno.phase.wyckoff;

import com.vish.fno.model.Candle;
import com.vish.fno.model.wyckoff.IWyckoffPhaseIdentifier;
import com.vish.fno.model.wyckoff.WyckoffPhase;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Composite Wyckoff phase identifier that combines multiple identification strategies.
 * Returns the phase with the highest confidence score from all identifiers.
 */
public class CompositeWyckoffPhaseIdentifier implements IWyckoffPhaseIdentifier {
    
    private final IWyckoffPhaseIdentifier[] identifiers;
    private final Map<WyckoffPhase, Double> phaseConfidenceCache = new HashMap<>();
    
    public CompositeWyckoffPhaseIdentifier(IWyckoffPhaseIdentifier... identifiers) {
        if (identifiers == null || identifiers.length == 0) {
            throw new IllegalArgumentException("At least one identifier must be provided");
        }
        this.identifiers = identifiers.clone();
    }
    
    @Override
    public WyckoffPhase identifyPhase(List<Candle> data, int currentIndex) {
        if (data == null || data.isEmpty() || currentIndex < 0 || currentIndex >= data.size()) {
            return WyckoffPhase.UNKNOWN;
        }
        
        // Clear cache for new identification
        phaseConfidenceCache.clear();
        
        // Collect phases and their confidence scores from all identifiers
        Map<WyckoffPhase, Double> phaseScores = new HashMap<>();
        Map<WyckoffPhase, Integer> phaseVotes = new HashMap<>();
        
        for (IWyckoffPhaseIdentifier identifier : identifiers) {
            WyckoffPhase phase = identifier.identifyPhase(data, currentIndex);
            double confidence = identifier.getPhaseConfidence(data, currentIndex);
            
            // Accumulate confidence scores
            phaseScores.merge(phase, confidence, Double::sum);
            phaseVotes.merge(phase, 1, Integer::sum);
        }
        
        // Calculate weighted average confidence for each phase
        Map<WyckoffPhase, Double> weightedScores = new HashMap<>();
        for (Map.Entry<WyckoffPhase, Double> entry : phaseScores.entrySet()) {
            WyckoffPhase phase = entry.getKey();
            double totalConfidence = entry.getValue();
            int votes = phaseVotes.get(phase);
            double weightedScore = totalConfidence / votes;
            weightedScores.put(phase, weightedScore);
        }
        
        // Store in cache for getPhaseConfidence method
        phaseConfidenceCache.putAll(weightedScores);
        
        // Return the phase with highest weighted confidence
        return weightedScores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(WyckoffPhase.UNKNOWN);
    }
    
    @Override
    public String getIdentifierType() {
        return "Composite";
    }
    
    @Override
    public String getDescription() {
        StringBuilder desc = new StringBuilder("Composite identifier combining: ");
        for (int i = 0; i < identifiers.length; i++) {
            if (i > 0) {
                desc.append(", ");
            }
            desc.append(identifiers[i].getIdentifierType());
        }
        return desc.toString();
    }
    
    @Override
    public double getPhaseConfidence(List<Candle> data, int currentIndex) {
        // If we have cached confidence from recent identification, use it
        if (!phaseConfidenceCache.isEmpty()) {
            WyckoffPhase phase = identifyPhase(data, currentIndex);
            return phaseConfidenceCache.getOrDefault(phase, 0.0);
        }
        
        // Otherwise, calculate fresh
        WyckoffPhase phase = identifyPhase(data, currentIndex);
        return phaseConfidenceCache.getOrDefault(phase, 0.0);
    }
    
    @Override
    public int getMinimumDataPoints() {
        // Return the maximum requirement from all identifiers
        return Arrays.stream(identifiers)
            .mapToInt(IWyckoffPhaseIdentifier::getMinimumDataPoints)
            .max()
            .orElse(5);
    }
    
    @Override
    public boolean supportsRealTimeAnalysis() {
        // Support real-time only if all identifiers support it
        return Arrays.stream(identifiers)
            .allMatch(IWyckoffPhaseIdentifier::supportsRealTimeAnalysis);
    }
    
    @Override
    public void reset() {
        // Reset all identifiers
        for (IWyckoffPhaseIdentifier identifier : identifiers) {
            identifier.reset();
        }
        phaseConfidenceCache.clear();
    }
    
    /**
     * Get detailed analysis from all identifiers
     */
    public Map<String, WyckoffPhase> getDetailedAnalysis(List<Candle> data, int currentIndex) {
        Map<String, WyckoffPhase> analysis = new LinkedHashMap<>();
        for (IWyckoffPhaseIdentifier identifier : identifiers) {
            WyckoffPhase phase = identifier.identifyPhase(data, currentIndex);
            analysis.put(identifier.getIdentifierType(), phase);
        }
        return analysis;
    }
    
    /**
     * Get confidence scores from all identifiers
     */
    public Map<String, Double> getConfidenceScores(List<Candle> data, int currentIndex) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (IWyckoffPhaseIdentifier identifier : identifiers) {
            double confidence = identifier.getPhaseConfidence(data, currentIndex);
            scores.put(identifier.getIdentifierType(), confidence);
        }
        return scores;
    }
}