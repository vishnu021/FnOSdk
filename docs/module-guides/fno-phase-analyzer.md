# fno-phase-analyzer - Wyckoff Phase Identification

## Overview

Advanced Wyckoff market phase identification using multiple algorithmic strategies. Identifies accumulation, distribution, markup, and markdown phases for F&O trading.

## Maven Dependency

```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-phase-analyzer</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Module Dependencies:** `fno-models`, `fno-utils`, `fno-technicals`, `fno-strategy-utils`

## Packages

| Package | Purpose |
|---------|---------|
| `com.vish.fno.phase.wyckoff` | Phase identifier implementations |
| `com.vish.fno.phase.factory` | Factory classes (`WyckoffPhaseIdentifierFactory`, `WyckoffIdentifierType`) |
| `com.vish.fno.phase.service` | Service classes for analysis |
| `com.vish.fno.phase.util` | Shared utilities (`ATRCalculator`) |

---

## Wyckoff Phases

| Phase Type | Phases | Description |
|------------|--------|-------------|
| **Accumulation** | A, B, C, D | Smart money building positions (bullish) |
| **Distribution** | A, B, C, D | Smart money distributing positions (bearish) |
| **Trending** | MARKUP, MARKDOWN | Active up/down trends |
| **Continuation** | REACCUMULATION, REDISTRIBUTION, CONSOLIDATION | Pauses within trends |
| **Undefined** | UNKNOWN | Unable to determine |

---

## Phase Identifiers

### Comparison Table

| Identifier | Reliability | Best For | Min Data | Special Data |
|------------|-------------|----------|----------|--------------|
| `MarketProfileTPO` | 4.5/5 | Intraday, auction analysis | 30 | Volume profile (optional) |
| `DerivativesFuturesOI` | 4.5/5 | Index futures | 5 | Open Interest (required) |
| `StructureSwing` | 4.0/5 | Clear execution, adaptable | 20 | None |
| `Renko` | 3.5/5 | Trend/range separation | 14 | None |
| `Classical` | Varies | General-purpose | 5 | None |
| `VolumeBased` | Varies | Volume analysis | 10 | Accurate volume |
| `HeikinAshi` | Varies | Noise reduction | 10 | None |
| `Composite` | Combined | Consensus-based | Max of all | Depends on constituents |

### IWyckoffPhaseIdentifier Interface

```java
public interface IWyckoffPhaseIdentifier {
    WyckoffPhase identifyPhase(List<Candle> data, int currentIndex);
    double getPhaseConfidence(List<Candle> data, int currentIndex);
    String getIdentifierType();
    String getDescription();
    int getMinimumDataPoints();
    boolean supportsRealTimeAnalysis();
    void reset();
}
```

---

## WyckoffIdentifierType Enum

Type-safe enumeration for identifier selection.

| Type | Key | Default |
|------|-----|---------|
| `CLASSICAL` | "classical" | No |
| `VOLUME_BASED` | "volume-based" | No |
| `HEIKIN_ASHI` | "heikin-ashi" | No |
| `RENKO` | "renko" | No |
| `STRUCTURE_SWING` | "structure-swing" | **Yes** |
| `MARKET_PROFILE` | "market-profile" | No |
| `DERIVATIVES_OI` | "derivatives-oi" | No |
| `COMPOSITE` | "composite" | No |

**Key Methods:**

| Method | Returns | Description |
|--------|---------|-------------|
| `create()` | `IWyckoffPhaseIdentifier` | Creates identifier instance |
| `getKey()` | `String` | Returns string key |
| `fromKey(String)` | `WyckoffIdentifierType` | Finds type by key (null if not found) |
| `fromKeyOrDefault(String)` | `WyckoffIdentifierType` | Finds type or returns default |
| `getDefault()` | `WyckoffIdentifierType` | Returns STRUCTURE_SWING |
| `isValidKey(String)` | `boolean` | Validates key |

---

## WyckoffPhaseIdentifierFactory

Factory with lazy initialization and EnumMap caching.

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `getIdentifier(WyckoffIdentifierType)` | type | `IWyckoffPhaseIdentifier` | Type-safe retrieval |
| `getDefault()` | - | `IWyckoffPhaseIdentifier` | Default identifier |
| `createComposite(WyckoffIdentifierType...)` | types | `IWyckoffPhaseIdentifier` | Composite from multiple |
| `getAvailableTypes()` | - | `WyckoffIdentifierType[]` | All available types |
| `clearCache()` | - | `void` | Clears cached instances |

**Example:**

```java
import com.vish.fno.phase.factory.WyckoffPhaseIdentifierFactory;
import com.vish.fno.phase.factory.WyckoffIdentifierType;
import com.vish.fno.model.wyckoff.IWyckoffPhaseIdentifier;
import com.vish.fno.model.wyckoff.WyckoffPhase;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PhaseAnalysis {
    private final WyckoffPhaseIdentifierFactory factory = new WyckoffPhaseIdentifierFactory();

    public void analyze(List<Candle> candles) {
        // Single identifier
        IWyckoffPhaseIdentifier identifier = factory.getIdentifier(WyckoffIdentifierType.STRUCTURE_SWING);
        WyckoffPhase phase = identifier.identifyPhase(candles, candles.size() - 1);
        double confidence = identifier.getPhaseConfidence(candles, candles.size() - 1);
        log.info("Phase: {} (confidence: {}%)", phase.getPhaseName(), confidence * 100);

        // High-reliability composite
        IWyckoffPhaseIdentifier composite = factory.createComposite(
            WyckoffIdentifierType.DERIVATIVES_OI,
            WyckoffIdentifierType.MARKET_PROFILE,
            WyckoffIdentifierType.STRUCTURE_SWING
        );
        WyckoffPhase consensusPhase = composite.identifyPhase(candles, candles.size() - 1);
    }
}
```

---

## Services

### WyckoffPhaseService

Real-time phase identification with thread-safe caching and strategy recommendations.

**Thread-Safety:** Uses `ConcurrentHashMap` and `CopyOnWriteArrayList`.

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `getCurrentPhase(String, Ticker)` | symbol, tick | `WyckoffPhase` | Real-time phase (hourly cached) |
| `getPhaseFromCandles(List<Candle>)` | candles | `WyckoffPhase` | Phase from candles |
| `getPhaseWithConfidence(List<Candle>)` | candles | `PhaseWithConfidence` | Phase + confidence |
| `getStrategyForPhase(WyckoffPhase)` | phase | `String` | Strategy recommendation |
| `switchIdentifier(WyckoffIdentifierType)` | type | `void` | Switch identifier at runtime |
| `getCurrentIdentifierType()` | - | `WyckoffIdentifierType` | Current identifier type |
| `clearCache(String)` | symbol | `void` | Clear caches for symbol |

**Strategy Mappings:**

| Phase | Primary Strategy |
|-------|------------------|
| MARKUP | WyckoffBreakoutStrategy, PullbackBuyingStrategy |
| MARKDOWN | BreakdownTradingStrategy, ContinuationShortingStrategy |
| ACCUMULATION_PHASE_C | SpringTradingStrategy |
| DISTRIBUTION_PHASE_C | UpthrustTradingStrategy |
| CONSOLIDATION | MeanReversionStrategy |

**Example:**

```java
import com.vish.fno.phase.service.WyckoffPhaseService;
import com.vish.fno.phase.factory.WyckoffIdentifierType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TradingApp {
    private final WyckoffPhaseService phaseService;

    public void onTick(Ticker tick) {
        WyckoffPhase phase = phaseService.getCurrentPhase("NIFTY 50", tick);
        String strategy = phaseService.getStrategyForPhase(phase);
        log.info("Phase: {}, Strategy: {}", phase.getPhaseName(), strategy);

        if (phase == WyckoffPhase.ACCUMULATION_PHASE_D) {
            log.info("Sign of strength - consider long positions");
        }
    }

    public void switchToOIAnalysis() {
        phaseService.switchIdentifier(WyckoffIdentifierType.DERIVATIVES_OI);
    }
}
```

### WyckoffAnalysisService

Daily batch analysis with CSV export.

| Method | Parameters | Returns |
|--------|-----------|---------|
| `analyzeSymbol(String, LocalDate, LocalDate, String)` | symbol, start, end, outputPath | `Map<LocalDate, WyckoffPhase>` |

### WyckoffHourlyAnalysisService

Intraday hourly analysis.

| Method | Parameters | Returns |
|--------|-----------|---------|
| `analyzeHourlyPhases(String, LocalDate, LocalDate, String)` | symbol, start, end, outputPath | `Map<LocalDateTime, WyckoffPhase>` |

---

## Utilities

### ATRCalculator

Shared utility for Average True Range calculation used by multiple identifiers.

```java
import com.vish.fno.phase.util.ATRCalculator;

double atr = ATRCalculator.calculateATR(candles, 14);
```

| Method | Parameters | Returns |
|--------|-----------|---------|
| `calculateATR(List<Candle>, int)` | candles, period | `double` |

---

## Usage Patterns

### Basic Phase Identification

```java
IWyckoffPhaseIdentifier identifier = WyckoffIdentifierType.STRUCTURE_SWING.create();
WyckoffPhase phase = identifier.identifyPhase(candles, candles.size() - 1);

if (phase.isAccumulation()) {
    // Bullish setup
} else if (phase.isDistribution()) {
    // Bearish setup
}
```

### High-Confidence Trading

```java
WyckoffPhaseIdentifierFactory factory = new WyckoffPhaseIdentifierFactory();
IWyckoffPhaseIdentifier composite = factory.createComposite(
    WyckoffIdentifierType.DERIVATIVES_OI,
    WyckoffIdentifierType.STRUCTURE_SWING
);

double confidence = composite.getPhaseConfidence(candles, candles.size() - 1);
if (confidence > 0.75) {
    // High confidence - safe to trade
}
```

### Dynamic Identifier Selection

```java
WyckoffIdentifierType type = hasOIData
    ? WyckoffIdentifierType.DERIVATIVES_OI
    : WyckoffIdentifierType.CLASSICAL;

IWyckoffPhaseIdentifier identifier = factory.getIdentifier(type);
```

---

## Identifier Selection Guide

**By Reliability:**
1. MarketProfileTPO, DerivativesFuturesOI (4.5/5)
2. StructureSwing (4.0/5)
3. Renko (3.5/5)
4. Classical, VolumeBased, HeikinAshi (general purpose)

**By Timeframe:**
- Intraday: MarketProfileTPO, StructureSwing, VolumeBased
- Swing: Classical, DerivativesFuturesOI, HeikinAshi
- Position: StructureSwing, Classical, Renko

**By Data Availability:**
- No special data: Classical, HeikinAshi, VolumeBased, Renko
- With Open Interest: DerivativesFuturesOI
- With Volume Profile: MarketProfileTPO

---

## Best Practices

1. **Check confidence scores** - Don't trade on confidence < 0.6
2. **Call `reset()`** when switching symbols
3. **Validate data size** - Ensure `data.size() >= getMinimumDataPoints()`
4. **Handle UNKNOWN phase** - Don't trade when phase is unclear
5. **Use composite for critical decisions** - Multiple viewpoints increase reliability

---

## Bug Fixes

- **`Double.MIN_VALUE` initialization fix (Feb 2026):** `DerivativesFuturesOIWyckoffPhaseIdentifier` and `StructureSwingWyckoffPhaseIdentifier` previously initialized `highestHigh`/`boxTop`/`maxPrice` to `Double.MIN_VALUE` (smallest positive double, ~4.9e-324) instead of `-Double.MAX_VALUE`. This caused incorrect high detection when all prices were negative or when comparing against near-zero values. Fixed to `-Double.MAX_VALUE` for correct min/max tracking.

---

## Thread Safety

| Component | Thread-Safe | Notes |
|-----------|-------------|-------|
| WyckoffPhaseService | ✅ | `volatile` phaseIdentifier/identifierType; handles concurrent access internally |
| StructureSwingWyckoffPhaseIdentifier | ✅ | `ReentrantLock` (swingStateLock) protects `identifyPhase()`/`getPhaseConfidence()`/`reset()` -- mutable swing state shared across these three methods |
| CompositeWyckoffPhaseIdentifier | ✅ | `ConcurrentHashMap` for phaseConfidenceCache, phaseScores, phaseVotes |
| MarketProfileTPOWyckoffPhaseIdentifier | Partial | Mutable TPO/volume state -- not externally synchronized |
| Other identifiers | No | Mutable state without synchronization -- use ThreadLocal or external locking |

```java
// For non-thread-safe identifiers:
ThreadLocal<IWyckoffPhaseIdentifier> threadLocal =
    ThreadLocal.withInitial(ClassicalWyckoffPhaseIdentifier::new);
```

---

## See Also

- [fno-models.md](fno-models.md) - WyckoffPhase enum, Candle model
- [fno-utils.md](fno-utils.md) - CandleUtils, HeikinAshi
- [fno-strategy-utils.md](fno-strategy-utils.md) - HATrendUtils
