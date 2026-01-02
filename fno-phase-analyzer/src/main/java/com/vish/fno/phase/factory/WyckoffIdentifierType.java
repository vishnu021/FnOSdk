package com.vish.fno.phase.factory;

import com.vish.fno.model.wyckoff.IWyckoffPhaseIdentifier;
import com.vish.fno.phase.wyckoff.ClassicalWyckoffPhaseIdentifier;
import com.vish.fno.phase.wyckoff.CompositeWyckoffPhaseIdentifier;
import com.vish.fno.phase.wyckoff.DerivativesFuturesOIWyckoffPhaseIdentifier;
import com.vish.fno.phase.wyckoff.HeikinAshiWyckoffPhaseIdentifier;
import com.vish.fno.phase.wyckoff.MarketProfileTPOWyckoffPhaseIdentifier;
import com.vish.fno.phase.wyckoff.RenkoWyckoffPhaseIdentifier;
import com.vish.fno.phase.wyckoff.StructureSwingWyckoffPhaseIdentifier;
import com.vish.fno.phase.wyckoff.VolumeBasedWyckoffPhaseIdentifier;

import java.util.function.Supplier;

/**
 * Enumeration of available Wyckoff phase identifier types.
 * Each type provides a factory method to create its corresponding identifier instance.
 *
 * <p>Usage with modern switch expressions (Java 17+):
 * <pre>{@code
 * IWyckoffPhaseIdentifier identifier = switch(type) {
 *     case CLASSICAL -> type.create();
 *     case VOLUME_BASED -> type.create();
 *     default -> WyckoffIdentifierType.STRUCTURE_SWING.create();
 * };
 * }</pre>
 */
public enum WyckoffIdentifierType {

    /**
     * Traditional Wyckoff methodology focusing on accumulation/distribution phases
     */
    CLASSICAL(
        "classical",
        "Traditional Wyckoff analysis with accumulation/distribution phases",
        ClassicalWyckoffPhaseIdentifier::new
    ),

    /**
     * Volume profile analysis using volume distribution patterns
     */
    VOLUME_BASED(
        "volume-based",
        "Volume profile analysis with distribution patterns",
        VolumeBasedWyckoffPhaseIdentifier::new
    ),

    /**
     * Heikin Ashi smoothed trend analysis
     */
    HEIKIN_ASHI(
        "heikin-ashi",
        "Heikin Ashi smoothed trend analysis",
        HeikinAshiWyckoffPhaseIdentifier::new
    ),

    /**
     * Renko noise-filtered analysis
     */
    RENKO(
        "renko",
        "Renko brick-based noise-filtered analysis",
        RenkoWyckoffPhaseIdentifier::new
    ),

    /**
     * Market structure and swing analysis (default)
     */
    STRUCTURE_SWING(
        "structure-swing",
        "Market structure and swing-based analysis",
        StructureSwingWyckoffPhaseIdentifier::new,
        true // Mark as default
    ),

    /**
     * Market Profile Time Price Opportunity analysis
     */
    MARKET_PROFILE(
        "market-profile",
        "Market Profile TPO-based analysis",
        MarketProfileTPOWyckoffPhaseIdentifier::new
    ),

    /**
     * Derivatives and futures Open Interest analysis
     */
    DERIVATIVES_OI(
        "derivatives-oi",
        "Derivatives Futures OI-based analysis",
        DerivativesFuturesOIWyckoffPhaseIdentifier::new
    ),

    /**
     * Composite multi-strategy analysis
     * Note: Composite identifiers require additional configuration and are created via factory
     */
    COMPOSITE(
        "composite",
        "Composite multi-strategy analysis",
        () -> {
            throw new UnsupportedOperationException(
                "Composite identifiers must be created via WyckoffPhaseIdentifierFactory.createComposite()"
            );
        }
    );

    private final String key;
    private final String description;
    private final Supplier<IWyckoffPhaseIdentifier> factory;
    private final boolean isDefault;

    /**
     * Constructor for non-default identifier types
     */
    WyckoffIdentifierType(String key, String description, Supplier<IWyckoffPhaseIdentifier> factory) {
        this(key, description, factory, false);
    }

    /**
     * Constructor with explicit default flag
     */
    WyckoffIdentifierType(String key, String description, Supplier<IWyckoffPhaseIdentifier> factory, boolean isDefault) {
        this.key = key;
        this.description = description;
        this.factory = factory;
        this.isDefault = isDefault;
    }

    /**
     * Create a new instance of this identifier type
     *
     * @return New identifier instance
     * @throws UnsupportedOperationException for COMPOSITE type (use factory method instead)
     */
    public IWyckoffPhaseIdentifier create() {
        return factory.get();
    }

    /**
     * Get the string key for this identifier type
     */
    public String getKey() {
        return key;
    }

    /**
     * Get the description of this identifier type
     */
    public String getDescription() {
        return description;
    }

    /**
     * Check if this is the default identifier type
     */
    public boolean isDefault() {
        return isDefault;
    }

    /**
     * Get the default identifier type
     *
     * @return Default identifier type (STRUCTURE_SWING)
     */
    public static WyckoffIdentifierType getDefault() {
        for (WyckoffIdentifierType type : values()) {
            if (type.isDefault) {
                return type;
            }
        }
        return STRUCTURE_SWING; // Fallback
    }

    /**
     * Find identifier type by key (case-insensitive)
     *
     * @param key The identifier key
     * @return Matching identifier type, or null if not found
     */
    public static WyckoffIdentifierType fromKey(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }

        String normalized = key.toLowerCase().trim();
        for (WyckoffIdentifierType type : values()) {
            if (type.key.equals(normalized)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Find identifier type by key with fallback to default
     *
     * @param key The identifier key
     * @return Matching identifier type, or default if not found
     */
    public static WyckoffIdentifierType fromKeyOrDefault(String key) {
        WyckoffIdentifierType type = fromKey(key);
        return type != null ? type : getDefault();
    }

    /**
     * Check if a key corresponds to a valid identifier type
     *
     * @param key The identifier key to check
     * @return true if valid, false otherwise
     */
    public static boolean isValidKey(String key) {
        return fromKey(key) != null;
    }

    @Override
    public String toString() {
        return key;
    }
}
