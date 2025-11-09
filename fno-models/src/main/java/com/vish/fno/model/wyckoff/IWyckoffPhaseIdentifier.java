package com.vish.fno.model.wyckoff;

import com.vish.fno.model.Candle;

import java.util.List;

/**
 * Interface for Wyckoff phase identification strategies.
 * Allows for multiple implementations of phase identification algorithms.
 */
public interface IWyckoffPhaseIdentifier {
    
    /**
     * Identify the Wyckoff phase based on candlestick data
     * 
     * @param data List of candlestick data
     * @param currentIndex The index in the data list to analyze
     * @return The identified Wyckoff phase
     */
    WyckoffPhase identifyPhase(List<Candle> data, int currentIndex);
    
    /**
     * Get the name/type of this identifier implementation
     * 
     * @return Implementation name (e.g., "Classical", "Volume-Based", "ML-Based")
     */
    String getIdentifierType();
    
    /**
     * Get a description of this identifier's approach
     * 
     * @return Description of the identification methodology
     */
    String getDescription();
    
    /**
     * Calculate confidence score for the identified phase
     * Returns a value between 0.0 and 1.0 indicating confidence in the phase identification
     * 
     * @param data List of candlestick data
     * @param currentIndex The index in the data list to analyze
     * @return Confidence score (0.0 to 1.0)
     */
    default double getPhaseConfidence(List<Candle> data, int currentIndex) {
        // Default implementation returns moderate confidence
        return 0.5;
    }
    
    /**
     * Get the minimum data points required for accurate identification
     * 
     * @return Minimum number of data points needed
     */
    default int getMinimumDataPoints() {
        return 5; // Default minimum
    }
    
    /**
     * Check if the identifier supports real-time analysis
     * 
     * @return true if real-time analysis is supported
     */
    default boolean supportsRealTimeAnalysis() {
        return true;
    }
    
    /**
     * Reset any internal state or caches
     * Useful when switching symbols or starting new analysis
     */
    default void reset() {
        // Default implementation does nothing
    }
}