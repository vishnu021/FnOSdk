package com.vish.fno.phase.factory;

import com.vish.fno.model.wyckoff.IWyckoffPhaseIdentifier;
import com.vish.fno.phase.wyckoff.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Modern enum-based factory for creating Wyckoff phase identifiers.
 * Uses Java 17+ features including sealed interfaces, pattern matching, and enhanced switch expressions.
 *
 * <p>Example usage with enum:
 * <pre>{@code
 * var factory = new WyckoffPhaseIdentifierFactory();
 * IWyckoffPhaseIdentifier identifier = factory.getIdentifier(WyckoffIdentifierType.CLASSICAL);
 * }</pre>
 *
 * <p>Example with type-safe pattern matching:
 * <pre>{@code
 * WyckoffIdentifierType type = WyckoffIdentifierType.VOLUME_BASED;
 * var identifier = switch (type) {
 *     case CLASSICAL, VOLUME_BASED -> factory.getIdentifier(type);
 *     case COMPOSITE -> factory.createComposite(
 *         WyckoffIdentifierType.CLASSICAL,
 *         WyckoffIdentifierType.VOLUME_BASED
 *     );
 *     default -> factory.getDefault();
 * };
 * }</pre>
 */
public class WyckoffPhaseIdentifierFactory {

    private static final Logger log = LoggerFactory.getLogger(WyckoffPhaseIdentifierFactory.class);

    private final Map<WyckoffIdentifierType, IWyckoffPhaseIdentifier> identifiers;
    private WyckoffIdentifierType defaultType;

    /**
     * Create factory with lazy initialization.
     * Identifiers are created on-demand when first requested.
     */
    public WyckoffPhaseIdentifierFactory() {
        this.identifiers = new EnumMap<>(WyckoffIdentifierType.class);
        this.defaultType = WyckoffIdentifierType.getDefault();
        log.info("WyckoffPhaseIdentifierFactory initialized with default: {}", defaultType.getKey());
    }

    /**
     * Get identifier by enum type (recommended, type-safe approach)
     *
     * @param type The identifier type enum
     * @return Identifier instance (created on first access, then cached)
     * @throws IllegalArgumentException if type is COMPOSITE (use createComposite() instead)
     */
    public IWyckoffPhaseIdentifier getIdentifier(WyckoffIdentifierType type) {
        if (type == WyckoffIdentifierType.COMPOSITE) {
            throw new IllegalArgumentException(
                "Cannot create COMPOSITE identifier directly. Use createComposite() method instead."
            );
        }

        return identifiers.computeIfAbsent(type, t -> {
            log.info("Creating new identifier instance: {} - {}", t.getKey(), t.getDescription());
            return t.create();
        });
    }

    /**
     * Get identifier by string key (legacy support, less type-safe)
     *
     * @param key The identifier key
     * @return Identifier instance wrapped in Optional
     * @deprecated Use {@link #getIdentifier(WyckoffIdentifierType)} for type-safe access
     */
    @Deprecated(since = "1.1", forRemoval = false)
    public Optional<IWyckoffPhaseIdentifier> getIdentifier(String key) {
        WyckoffIdentifierType type = WyckoffIdentifierType.fromKey(key);
        if (type == null) {
            log.warn("Unknown identifier key: '{}'", key);
            return Optional.empty();
        }
        return Optional.of(getIdentifier(type));
    }

    /**
     * Get identifier by string key with automatic fallback to default
     *
     * @param key The identifier key
     * @return Identifier instance (never null)
     */
    public IWyckoffPhaseIdentifier getIdentifierOrDefault(String key) {
        return getIdentifier(key).orElseGet(this::getDefault);
    }

    /**
     * Get the default identifier
     *
     * @return Default identifier instance
     */
    public IWyckoffPhaseIdentifier getDefault() {
        return getIdentifier(defaultType);
    }

    /**
     * Get the default identifier type enum
     *
     * @return Default identifier type
     */
    public WyckoffIdentifierType getDefaultType() {
        return defaultType;
    }

    /**
     * Set the default identifier type
     *
     * @param type New default type
     * @throws IllegalArgumentException if type is COMPOSITE
     */
    public void setDefaultType(WyckoffIdentifierType type) {
        if (type == WyckoffIdentifierType.COMPOSITE) {
            throw new IllegalArgumentException("Cannot set COMPOSITE as default identifier");
        }
        this.defaultType = type;
        log.info("Default identifier changed to: {}", type.getKey());
    }

    /**
     * Create a composite identifier combining multiple strategies.
     * Uses varargs for flexible configuration.
     *
     * @param types The identifier types to combine (minimum 1)
     * @return Composite identifier that aggregates results from all types
     * @throws IllegalArgumentException if no types provided or types include COMPOSITE
     */
    public IWyckoffPhaseIdentifier createComposite(WyckoffIdentifierType... types) {
        if (types == null || types.length == 0) {
            throw new IllegalArgumentException("At least one identifier type required for composite");
        }

        for (WyckoffIdentifierType type : types) {
            if (type == WyckoffIdentifierType.COMPOSITE) {
                throw new IllegalArgumentException("Cannot nest COMPOSITE identifiers");
            }
        }

        var identifierArray = Stream.of(types)
            .map(this::getIdentifier)
            .toArray(IWyckoffPhaseIdentifier[]::new);

        log.info("Creating composite identifier with {} strategies: {}",
            types.length,
            Stream.of(types).map(WyckoffIdentifierType::getKey).collect(Collectors.joining(", "))
        );

        return new CompositeWyckoffPhaseIdentifier(identifierArray);
    }

    /**
     * Create composite identifier from string keys (legacy support)
     *
     * @param keys String keys for identifier types
     * @return Composite identifier
     * @throws IllegalArgumentException if any key is invalid
     * @deprecated Use {@link #createComposite(WyckoffIdentifierType...)} for type-safe access
     */
    @Deprecated(since = "1.1", forRemoval = false)
    public IWyckoffPhaseIdentifier createComposite(String... keys) {
        var types = Stream.of(keys)
            .map(WyckoffIdentifierType::fromKey)
            .peek(type -> {
                if (type == null) throw new IllegalArgumentException("Invalid identifier key");
            })
            .toArray(WyckoffIdentifierType[]::new);

        return createComposite(types);
    }

    /**
     * Get all available identifier types
     *
     * @return Array of all identifier types (excluding COMPOSITE)
     */
    public WyckoffIdentifierType[] getAvailableTypes() {
        return Stream.of(WyckoffIdentifierType.values())
            .filter(type -> type != WyckoffIdentifierType.COMPOSITE)
            .toArray(WyckoffIdentifierType[]::new);
    }

    /**
     * Get metadata about all available identifiers
     *
     * @return Map of identifier key to description
     */
    public Map<String, String> getIdentifierInfo() {
        return Stream.of(WyckoffIdentifierType.values())
            .collect(Collectors.toMap(
                WyckoffIdentifierType::getKey,
                WyckoffIdentifierType::getDescription
            ));
    }

    /**
     * Check if a type is currently cached (instantiated)
     *
     * @param type The identifier type
     * @return true if instance exists in cache
     */
    public boolean isCached(WyckoffIdentifierType type) {
        return identifiers.containsKey(type);
    }

    /**
     * Clear cached instances (useful for testing or memory management)
     */
    public void clearCache() {
        identifiers.clear();
        log.info("Identifier cache cleared");
    }

    /**
     * Pre-warm cache by instantiating all identifier types (except COMPOSITE)
     */
    public void preWarmCache() {
        Stream.of(WyckoffIdentifierType.values())
            .filter(type -> type != WyckoffIdentifierType.COMPOSITE)
            .forEach(this::getIdentifier);
        log.info("Cache pre-warmed with {} identifiers", identifiers.size());
    }
}
