package com.vish.fno.phase.wyckoff;

import com.vish.fno.model.wyckoff.IWyckoffPhaseIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Factory for creating and managing Wyckoff phase identifier implementations.
 * Supports dynamic selection of identifier based on configuration or runtime parameters.
 */
public class WyckoffPhaseIdentifierFactory {

    private static final Logger logger = LoggerFactory.getLogger(WyckoffPhaseIdentifierFactory.class);

    private final Map<String, IWyckoffPhaseIdentifier> identifiers = new HashMap<>();
    private IWyckoffPhaseIdentifier defaultIdentifier;
    private String defaultIdentifierType = "structure-swing";
    private boolean enableFallback = true;

    public WyckoffPhaseIdentifierFactory(
            ClassicalWyckoffPhaseIdentifier classicalIdentifier,
            DerivativesFuturesOIWyckoffPhaseIdentifier derivativesIdentifier,
            HeikinAshiWyckoffPhaseIdentifier heikinAshiIdentifier,
            MarketProfileTPOWyckoffPhaseIdentifier marketProfileIdentifier,
            RenkoWyckoffPhaseIdentifier renkoIdentifier,
            StructureSwingWyckoffPhaseIdentifier structureSwingIdentifier,
            VolumeBasedWyckoffPhaseIdentifier volumeBasedIdentifier,
            CompositeWyckoffPhaseIdentifier compositeIdentifier) {

        // Register all identifiers
        registerIdentifier(classicalIdentifier);
        registerIdentifier(derivativesIdentifier);
        registerIdentifier(heikinAshiIdentifier);
        registerIdentifier(marketProfileIdentifier);
        registerIdentifier(renkoIdentifier);
        registerIdentifier(structureSwingIdentifier);
        registerIdentifier(volumeBasedIdentifier);
        registerIdentifier(compositeIdentifier);

        // Set default to StructureSwing as per the original configuration
        this.defaultIdentifier = structureSwingIdentifier;

        logger.info("Default Wyckoff phase identifier set to: {}", defaultIdentifier.getIdentifierType());
    }

    private void registerIdentifier(IWyckoffPhaseIdentifier identifier) {
        String type = identifier.getIdentifierType().toLowerCase();
        identifiers.put(type, identifier);
        logger.info("Registered Wyckoff phase identifier: {} - {}", type, identifier.getDescription());
    }
    
    /**
     * Get the default phase identifier
     */
    public IWyckoffPhaseIdentifier getDefaultIdentifier() {
        return defaultIdentifier;
    }
    
    /**
     * Get a specific phase identifier by type
     * 
     * @param type The identifier type (e.g., "classical", "volume-based", "ml-based")
     * @return The identifier implementation, or default if not found and fallback is enabled
     */
    public IWyckoffPhaseIdentifier getIdentifier(String type) {
        if (type == null || type.isEmpty()) {
            return defaultIdentifier;
        }
        
        IWyckoffPhaseIdentifier identifier = identifiers.get(type.toLowerCase());
        
        if (identifier == null) {
            logger.warn("Identifier type '{}' not found", type);
            if (enableFallback) {
                logger.info("Using fallback identifier: {}", defaultIdentifier.getIdentifierType());
                return defaultIdentifier;
            }
            throw new IllegalArgumentException("Unknown identifier type: " + type);
        }
        
        return identifier;
    }
    
    /**
     * Get all available identifier types
     */
    public Set<String> getAvailableTypes() {
        return identifiers.keySet();
    }
    
    /**
     * Check if a specific identifier type is available
     */
    public boolean hasIdentifier(String type) {
        return type != null && identifiers.containsKey(type.toLowerCase());
    }
    
    /**
     * Get information about all available identifiers
     */
    public Map<String, String> getIdentifierInfo() {
        Map<String, String> info = new HashMap<>();
        for (Map.Entry<String, IWyckoffPhaseIdentifier> entry : identifiers.entrySet()) {
            info.put(entry.getKey(), entry.getValue().getDescription());
        }
        return info;
    }
    
    /**
     * Set the default identifier type at runtime
     * 
     * @param type The identifier type to set as default
     */
    public void setDefaultIdentifierType(String type) {
        IWyckoffPhaseIdentifier identifier = getIdentifier(type);
        if (identifier != null) {
            defaultIdentifier = identifier;
            defaultIdentifierType = type;
            logger.info("Default identifier changed to: {}", type);
        }
    }
    
    /**
     * Create a composite identifier that uses multiple implementations
     * and returns the phase with highest confidence
     * 
     * @param types The identifier types to combine
     * @return A composite identifier
     */
    public IWyckoffPhaseIdentifier createCompositeIdentifier(String... types) {
        if (types == null || types.length == 0) {
            return defaultIdentifier;
        }
        
        IWyckoffPhaseIdentifier[] identifierArray = new IWyckoffPhaseIdentifier[types.length];
        for (int i = 0; i < types.length; i++) {
            identifierArray[i] = getIdentifier(types[i]);
        }
        
        return new CompositeWyckoffPhaseIdentifier(identifierArray);
    }
}