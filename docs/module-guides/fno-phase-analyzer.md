# fno-phase-analyzer - Wyckoff Phase Identification

## Purpose
Advanced Wyckoff market phase identification using multiple algorithmic strategies. Identifies accumulation, distribution, markup, and markdown phases for F&O trading with high reliability.

**Version 1.1 Updates:**
- Introduced `WyckoffIdentifierType` enum for type-safe identifier management
- Modernized `WyckoffPhaseIdentifierFactory` with enum-based API and lazy initialization
- Added `EnumMap` caching for improved performance
- Type-safe methods in `WyckoffPhaseService` for identifier switching
- Legacy string-based methods deprecated but still supported

## Maven Dependency
```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-phase-analyzer</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Module Dependencies:**
- `fno-models` - Core data models (Candle, Wyckoff models)
- `fno-utils` - Utility functions (CandleUtils, HeikinAshi, TimeUtils)
- `fno-strategy-utils` - Strategy utilities (HATrendUtils, Point2D)

## Key Packages

- `com.vish.fno.phase.wyckoff` - Phase identification strategies and implementations
- `com.vish.fno.phase.factory` - Factory classes for creating identifiers (WyckoffPhaseIdentifierFactory, WyckoffIdentifierType)
- `com.vish.fno.phase.service` - Service classes for real-time and batch analysis

## Core Concepts

### Wyckoff Market Phases (14 Distinct Phases)

**Accumulation Phases:**
- **Phase A**: Stopping the prior downtrend (Preliminary Support, Selling Climax)
- **Phase B**: Building a cause (Testing supply and demand)
- **Phase C**: Spring/Shakeout (Testing support levels)
- **Phase D**: Sign of Strength (Breaking resistance)

**Distribution Phases:**
- **Phase A**: Stopping the prior uptrend (Preliminary Supply, Buying Climax)
- **Phase B**: Building a cause (Testing demand and supply)
- **Phase C**: Upthrust (Testing resistance levels)
- **Phase D**: Sign of Weakness (Breaking support)

**Trending Phases:**
- **MARKUP**: Uptrend with higher highs and higher lows
- **MARKDOWN**: Downtrend with lower highs and lower lows

**Continuation Phases:**
- **REACCUMULATION**: Consolidation during uptrend
- **REDISTRIBUTION**: Consolidation during downtrend
- **CONSOLIDATION**: Sideways movement, range-bound
- **UNKNOWN**: Unable to determine phase

## Phase Identifier Implementations

### Identifier Comparison Table

| Identifier | Reliability | Best For | Min Data Points | Requires Special Data |
|-----------|------------|----------|----------------|----------------------|
| **MarketProfileTPO** | 4.5/5.0 | Intraday analysis, auction clarity | 30 | Futures volume profile (optional) |
| **DerivativesFuturesOI** | 4.5/5.0 | Index futures (NIFTY, BANKNIFTY) | 5 | Open Interest data (required) |
| **StructureSwing** | 4.0/5.0 | Clear execution logic, adaptable | 20 | None |
| **Renko** | 3.5/5.0 | Trend/range separation, noise filtering | 14 | None |
| **Classical** | Varies | General-purpose, hourly timeframes | 5 | None |
| **VolumeBased** | Varies | Volume-centric analysis | 10 | Accurate volume data |
| **HeikinAshi** | Varies | Noise reduction, smoother trends | 10 | None |
| **Composite** | Combined | Highest confidence consensus | Max of all | Depends on constituents |

### Identifier Type Summary

**High Reliability (4.0+):**
- **MarketProfileTPO**: Auction market theory, TPO histogram, Value Area analysis, profile shapes
- **DerivativesFuturesOI**: Price-OI relationships (Long buildup, Short covering, etc.), OI spike detection
- **StructureSwing**: HH/HL and LL/LH patterns, fractal pivots, Donchian channels, failed breakouts

**Medium Reliability (3.5):**
- **Renko**: Fixed price movement blocks (bricks), ATR-based brick sizing, uninterrupted staircases = trends

**General Purpose:**
- **Classical**: Traditional Wyckoff with volume analysis, price position, trend strength, momentum
- **VolumeBased**: Volume patterns, buying/selling pressure, price-volume divergences
- **HeikinAshi**: HA candle smoothing, strong candles with no wicks, doji patterns at extremes

**Meta Strategy:**
- **Composite**: Combines multiple identifiers, weighted consensus, detailed breakdown

### Common Interface - IWyckoffPhaseIdentifier

All identifiers implement:
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

### Example Usage Pattern (Applies to ALL Identifiers)

```java
import com.vish.fno.phase.wyckoff.IWyckoffPhaseIdentifier;
import com.vish.fno.model.Candle;
import com.vish.fno.model.wyckoff.WyckoffPhase;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PhaseAnalysisExample {
    // Works with ANY identifier implementation
    private IWyckoffPhaseIdentifier identifier;

    public void analyzeMarket(List<Candle> candles) {
        int currentIndex = candles.size() - 1;

        // 1. Identify phase
        WyckoffPhase phase = identifier.identifyPhase(candles, currentIndex);

        // 2. Get confidence score
        double confidence = identifier.getPhaseConfidence(candles, currentIndex);

        log.info("Phase: {} ({})", phase.getPhaseName(), identifier.getIdentifierType());
        log.info("Confidence: {}", confidence * 100 + "%");

        // 3. Trade based on high-confidence signals
        if (confidence > 0.7) {
            if (phase.isAccumulation()) {
                log.info("Consider LONG positions");
            } else if (phase.isDistribution()) {
                log.info("Consider SHORT positions or exit longs");
            }
        } else {
            log.info("Low confidence - stay out");
        }
    }
}
```

### Selecting the Right Identifier

**By Reliability (Highest to Lowest):**
1. MarketProfileTPO (4.5) - Intraday, institutional activity
2. DerivativesFuturesOI (4.5) - Index futures with OI data
3. StructureSwing (4.0) - Clear execution, adaptable
4. Renko (3.5) - Trend/range separation
5. Classical, VolumeBased, HeikinAshi - General purpose

**By Timeframe:**
- **Intraday (1-min, 5-min)**: MarketProfileTPO, StructureSwing, VolumeBased
- **Short-term (15-min, 30-min, 1-hour)**: Classical, DerivativesFuturesOI, HeikinAshi
- **Medium-term (Daily, Weekly)**: StructureSwing, Classical, Renko

**By Data Availability:**
- **No special data**: Classical, HeikinAshi, VolumeBased, Renko
- **With Open Interest**: DerivativesFuturesOI (highly recommended)
- **With Volume Profile**: MarketProfileTPO (best choice)

### WyckoffIdentifierType Enum

Type-safe enumeration for available Wyckoff identifier types. Introduced in version 1.1 for improved type safety and reduced runtime errors.

**Available Types:**

| Type | Key | Description | Default |
|------|-----|-------------|---------|
| `CLASSICAL` | "classical" | Traditional Wyckoff with accumulation/distribution phases | No |
| `VOLUME_BASED` | "volume-based" | Volume profile analysis with distribution patterns | No |
| `HEIKIN_ASHI` | "heikin-ashi" | Heikin Ashi smoothed trend analysis | No |
| `RENKO` | "renko" | Renko brick-based noise-filtered analysis | No |
| `STRUCTURE_SWING` | "structure-swing" | Market structure and swing-based analysis | **Yes** |
| `MARKET_PROFILE` | "market-profile" | Market Profile TPO-based analysis | No |
| `DERIVATIVES_OI` | "derivatives-oi" | Derivatives Futures OI-based analysis | No |
| `COMPOSITE` | "composite" | Composite multi-strategy analysis (use factory method) | No |

**Key Methods:**

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `create()` | - | `IWyckoffPhaseIdentifier` | Creates new identifier instance (throws for COMPOSITE) |
| `getKey()` | - | `String` | Returns string key |
| `getDescription()` | - | `String` | Returns description |
| `isDefault()` | - | `boolean` | Checks if default type |
| `fromKey(String)` | key | `WyckoffIdentifierType` | Finds type by key (null if not found) |
| `fromKeyOrDefault(String)` | key | `WyckoffIdentifierType` | Finds type or returns default |
| `getDefault()` | - | `WyckoffIdentifierType` | Returns default type (STRUCTURE_SWING) |
| `isValidKey(String)` | key | `boolean` | Checks if key is valid |

**Example:**

```java
import com.vish.fno.phase.factory.WyckoffIdentifierType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EnumExample {
    public void useEnumFeatures() {
        // Get default type
        WyckoffIdentifierType defaultType = WyckoffIdentifierType.getDefault();
        log.info("Default: {}", defaultType.getKey()); // structure-swing

        // Create identifier from enum
        IWyckoffPhaseIdentifier identifier = WyckoffIdentifierType.CLASSICAL.create();

        // Find type by key
        WyckoffIdentifierType type = WyckoffIdentifierType.fromKey("volume-based");
        if (type != null) {
            log.info("Found: {} - {}", type.getKey(), type.getDescription());
        }

        // Safe fallback
        WyckoffIdentifierType safeType = WyckoffIdentifierType.fromKeyOrDefault("invalid-key");
        log.info("Safe type: {}", safeType.getKey()); // structure-swing

        // Validate key
        if (WyckoffIdentifierType.isValidKey("classical")) {
            log.info("Valid identifier key");
        }

        // Modern switch expression (Java 17+)
        String strategy = switch (type) {
            case CLASSICAL, VOLUME_BASED -> "conservative";
            case STRUCTURE_SWING -> "balanced";
            case COMPOSITE -> "aggressive";
            default -> "moderate";
        };
    }
}
```

### WyckoffPhaseIdentifierFactory

Modern enum-based factory with lazy initialization and caching. Uses `EnumMap` for efficient storage.

**Key Methods:**

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `getIdentifier(WyckoffIdentifierType)` | type | `IWyckoffPhaseIdentifier` | Type-safe identifier retrieval (recommended) |
| `getIdentifier(String)` | key | `Optional<IWyckoffPhaseIdentifier>` | Legacy string-based access (deprecated) |
| `getIdentifierOrDefault(String)` | key | `IWyckoffPhaseIdentifier` | String-based with automatic fallback |
| `getDefault()` | - | `IWyckoffPhaseIdentifier` | Returns default identifier instance |
| `getDefaultType()` | - | `WyckoffIdentifierType` | Returns default type enum |
| `setDefaultType(WyckoffIdentifierType)` | type | `void` | Changes default at runtime |
| `getAvailableTypes()` | - | `WyckoffIdentifierType[]` | Returns all types (excluding COMPOSITE) |
| `getIdentifierInfo()` | - | `Map<String, String>` | Returns key → description map |
| `createComposite(WyckoffIdentifierType...)` | types | `IWyckoffPhaseIdentifier` | Creates composite (varargs, type-safe) |
| `createComposite(String...)` | keys | `IWyckoffPhaseIdentifier` | Creates composite from keys (deprecated) |
| `isCached(WyckoffIdentifierType)` | type | `boolean` | Checks if instance cached |
| `clearCache()` | - | `void` | Clears all cached instances |
| `preWarmCache()` | - | `void` | Pre-instantiates all identifiers |

**Example (Modern Approach):**

```java
import com.vish.fno.phase.factory.WyckoffPhaseIdentifierFactory;
import com.vish.fno.phase.factory.WyckoffIdentifierType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PhaseAnalysisService {
    private final WyckoffPhaseIdentifierFactory factory;

    public PhaseAnalysisService(WyckoffPhaseIdentifierFactory factory) {
        this.factory = factory;
    }

    public void analyzeWithFactory(List<Candle> candles) {
        // Type-safe identifier retrieval
        IWyckoffPhaseIdentifier identifier = factory.getIdentifier(WyckoffIdentifierType.CLASSICAL);
        WyckoffPhase phase = identifier.identifyPhase(candles, candles.size() - 1);

        log.info("Phase using {}: {}", identifier.getIdentifierType(), phase.getPhaseName());
    }

    public void useHighReliabilityComposite(List<Candle> candles) {
        // Type-safe composite creation
        IWyckoffPhaseIdentifier composite = factory.createComposite(
            WyckoffIdentifierType.DERIVATIVES_OI,  // 4.5/5.0
            WyckoffIdentifierType.MARKET_PROFILE,  // 4.5/5.0
            WyckoffIdentifierType.STRUCTURE_SWING  // 4.0/5.0
        );

        WyckoffPhase phase = composite.identifyPhase(candles, candles.size() - 1);
        double confidence = composite.getPhaseConfidence(candles, candles.size() - 1);

        log.info("High-reliability phase: {} (confidence: {}%)", phase, confidence * 100);
    }

    public void dynamicIdentifierSelection(List<Candle> candles, boolean hasOI) {
        // Dynamic selection with enum
        WyckoffIdentifierType type = hasOI
            ? WyckoffIdentifierType.DERIVATIVES_OI
            : WyckoffIdentifierType.CLASSICAL;

        IWyckoffPhaseIdentifier identifier = factory.getIdentifier(type);
        WyckoffPhase phase = identifier.identifyPhase(candles, candles.size() - 1);

        log.info("Using {} for analysis: {}", type.getKey(), phase.getPhaseName());
    }
}
```

## Service Classes

### Service Overview

| Service | Purpose | Key Features |
|---------|---------|--------------|
| **WyckoffPhaseService** | Real-time phase identification | Hourly caching, strategy recommendations, symbol-specific data |
| **WyckoffAnalysisService** | Daily analysis with export | Loads historical data, aggregates candles, exports CSV |
| **WyckoffHourlyAnalysisService** | Intraday hourly analysis | Hour-by-hour phase tracking, session analysis |

### WyckoffPhaseService - Real-time Phase Identification

Service for Wyckoff phase identification with thread-safe caching and runtime identifier switching.

**Thread-Safety Implementation:**
- `CopyOnWriteArrayList` for recent data cache (thread-safe modifications)
- `ConcurrentHashMap` for hourly phase cache and hour tracking
- No external synchronization needed for public methods
- Safe for concurrent tick processing from multiple threads

**Constructor:**
```java
public WyckoffPhaseService(WyckoffPhaseIdentifierFactory identifierFactory)
```

**Key Methods:**

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `getCurrentPhase(String, Ticker)` | `symbol`, `currentTick` | `WyckoffPhase` | Real-time phase from tick data (hourly cached, thread-safe) |
| `getPhaseFromCandles(List<Candle>)` | `candles` | `WyckoffPhase` | Phase from candle list |
| `getPhaseWithConfidence(List<Candle>)` | `candles` | `PhaseWithConfidence` | Phase + confidence together |
| `getStrategyForPhase(WyckoffPhase)` | `phase` | `String` | Recommended strategy name |
| `getAlternativeStrategyForPhase(WyckoffPhase)` | `phase` | `String` | Alternative strategy name |
| `getStrategyDescription(String)` | `strategyName` | `String` | Strategy description |
| `clearCache(String)` | `symbol` | `void` | Clears all caches for symbol (thread-safe) |
| `switchIdentifier(WyckoffIdentifierType)` | `type` | `void` | Type-safe identifier switching, clears caches (recommended) |
| `switchIdentifier(String)` | `key` | `void` | String-based identifier switching (deprecated) |
| `getCurrentIdentifierType()` | - | `WyckoffIdentifierType` | Returns current identifier type enum |
| `getCurrentIdentifierTypeKey()` | - | `String` | Returns current identifier key (deprecated) |
| `getPhaseStats(String)` | `symbol` | `String` | Phase distribution statistics |

**Strategy Mappings:**
- **MARKUP**: WyckoffBreakoutStrategy, PullbackBuyingStrategy (rotates)
- **MARKDOWN**: BreakdownTradingStrategy, ContinuationShortingStrategy (rotates)
- **ACCUMULATION_PHASE_C**: SpringTradingStrategy
- **DISTRIBUTION_PHASE_C**: UpthrustTradingStrategy
- **CONSOLIDATION**: MeanReversionStrategy, AccumulationRangeStrategy (rotates)

**Example:**

```java
import com.vish.fno.phase.service.WyckoffPhaseService;
import com.vish.fno.phase.service.WyckoffPhaseService.PhaseWithConfidence;
import com.vish.fno.phase.factory.WyckoffIdentifierType;
import com.vish.fno.model.Ticker;
import com.vish.fno.model.wyckoff.WyckoffPhase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RealtimeTradingApplication {
    private final WyckoffPhaseService phaseService;

    public RealtimeTradingApplication(WyckoffPhaseService phaseService) {
        this.phaseService = phaseService;
    }

    public void onTickReceived(Ticker tick) {
        // Get current phase from tick
        WyckoffPhase phase = phaseService.getCurrentPhase("NIFTY 50", tick);

        // Get strategy recommendation
        String strategy = phaseService.getStrategyForPhase(phase);

        log.info("Current phase: {}, Recommended strategy: {}", phase.getPhaseName(), strategy);

        // Trade based on phase
        if (phase == WyckoffPhase.ACCUMULATION_PHASE_D) {
            log.info("Sign of strength - prepare long positions");
            enterLongPosition(tick);
        } else if (phase == WyckoffPhase.DISTRIBUTION_PHASE_D) {
            log.info("Sign of weakness - exit longs or prepare shorts");
            exitLongPositions();
        }
    }

    public void analyzeWithConfidence(List<Candle> candles) {
        PhaseWithConfidence result = phaseService.getPhaseWithConfidence(candles);

        log.info("Phase analysis: {}", result); // "Accumulation Phase D (confidence: 87.50%)"

        // Only trade high-confidence signals
        if (result.getConfidence() > 0.75 && result.getPhase().isAccumulation()) {
            log.info("High confidence accumulation - safe to enter longs");
        }
    }

    public void switchToHighReliabilityIdentifier() {
        // Type-safe identifier switching
        phaseService.switchIdentifier(WyckoffIdentifierType.DERIVATIVES_OI);

        WyckoffIdentifierType currentType = phaseService.getCurrentIdentifierType();
        log.info("Switched to: {} - {}", currentType.getKey(), currentType.getDescription());
    }

    public void dynamicIdentifierSelection(boolean hasOIData) {
        // Runtime identifier selection
        WyckoffIdentifierType type = hasOIData
            ? WyckoffIdentifierType.DERIVATIVES_OI
            : WyckoffIdentifierType.STRUCTURE_SWING;

        phaseService.switchIdentifier(type);
        log.info("Using identifier: {}", type.getKey());
    }

    private void enterLongPosition(Ticker tick) { /* Implementation */ }
    private void exitLongPositions() { /* Implementation */ }
}
```

### WyckoffAnalysisService - Daily Analysis with Export

**Key Methods:**

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `analyzeSymbol(String)` | symbol | `Map<LocalDate, WyckoffPhase>` | Analyzes symbol with default settings |
| `analyzeSymbol(...)` | symbol, startDate, endDate, outputPath | `Map<LocalDate, WyckoffPhase>` | Complete daily analysis with CSV export |

**Example:**

```java
import com.vish.fno.phase.service.WyckoffAnalysisService;
import com.vish.fno.model.wyckoff.WyckoffPhase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BatchAnalyzer {
    @Autowired
    private WyckoffAnalysisService analysisService;

    public void analyzePeriod() throws IOException {
        LocalDate start = LocalDate.of(2025, 8, 1);
        LocalDate end = LocalDate.of(2025, 8, 31);

        // Analyze August 2025
        Map<LocalDate, WyckoffPhase> phases =
            analysisService.analyzeSymbol("NIFTY 50", start, end, "analysis-output");

        log.info("Analyzed {} days", phases.size());

        // Find specific phases
        phases.forEach((date, phase) -> {
            if (phase == WyckoffPhase.ACCUMULATION_PHASE_D) {
                log.info("Sign of Strength on: {}", date);
            } else if (phase == WyckoffPhase.DISTRIBUTION_PHASE_D) {
                log.info("Sign of Weakness on: {}", date);
            }
        });
    }
}
```

**CSV Export Format:**
```
Date,Phase,Phase Name,Description
2025-08-01,ACCUMULATION_PHASE_B,"Accumulation - Phase B","Building a cause..."
2025-08-02,ACCUMULATION_PHASE_C,"Accumulation - Phase C (Spring)","Testing support..."
```

### WyckoffHourlyAnalysisService - Intraday Hourly Analysis

**Key Methods:**

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `analyzeHourlyPhases(...)` | symbol, startDate, endDate | `Map<LocalDateTime, WyckoffPhase>` | Hourly analysis with default output |
| `analyzeHourlyPhases(...)` | symbol, startDate, endDate, outputPath | `Map<LocalDateTime, WyckoffPhase>` | Hourly analysis with CSV export |

**Example:**

```java
import com.vish.fno.phase.service.WyckoffHourlyAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IntradayAnalyzer {
    @Autowired
    private WyckoffHourlyAnalysisService hourlyService;

    public void analyzeTradingDay() throws IOException {
        LocalDate today = LocalDate.now();

        // Analyze today's hourly phases
        Map<LocalDateTime, WyckoffPhase> hourlyPhases =
            hourlyService.analyzeHourlyPhases("NIFTY 50", today, today, "hourly-analysis");

        // Log hourly progression
        hourlyPhases.forEach((hour, phase) ->
            log.info("{}: {}",
                     hour.format(DateTimeFormatter.ofPattern("HH:mm")),
                     phase.getPhaseName()));

        // Identify market open phase
        LocalDateTime marketOpen = hourlyPhases.keySet().stream()
            .filter(dt -> dt.getHour() == 9)
            .findFirst()
            .orElse(null);

        if (marketOpen != null) {
            WyckoffPhase openPhase = hourlyPhases.get(marketOpen);
            log.info("Market opened in: {}", openPhase.getPhaseName());

            if (openPhase.isAccumulation()) {
                log.info("Bullish opening - look for long entries");
            }
        }
    }
}
```

## Integration Patterns

### Multi-Strategy Analysis

```java
import com.vish.fno.phase.wyckoff.ClassicalWyckoffPhaseIdentifier;
import com.vish.fno.phase.wyckoff.DerivativesFuturesOIWyckoffPhaseIdentifier;
import com.vish.fno.phase.wyckoff.MarketProfileTPOWyckoffPhaseIdentifier;
import com.vish.fno.model.Candle;
import com.vish.fno.model.wyckoff.WyckoffPhase;

public class MultiStrategyPhaseAnalyzer {
    private final ClassicalWyckoffPhaseIdentifier classical;
    private final DerivativesFuturesOIWyckoffPhaseIdentifier oiAnalyzer;
    private final MarketProfileTPOWyckoffPhaseIdentifier tpoAnalyzer;

    public AnalysisResult analyze(List<Candle> candles) {
        int index = candles.size() - 1;

        // Get phases from each strategy
        WyckoffPhase classicalPhase = classical.identifyPhase(candles, index);
        WyckoffPhase oiPhase = oiAnalyzer.identifyPhase(candles, index);
        WyckoffPhase tpoPhase = tpoAnalyzer.identifyPhase(candles, index);

        // Check for consensus
        boolean consensus = (classicalPhase == oiPhase && oiPhase == tpoPhase);

        if (consensus) {
            double avgConfidence = (
                classical.getPhaseConfidence(candles, index) +
                oiAnalyzer.getPhaseConfidence(candles, index) +
                tpoAnalyzer.getPhaseConfidence(candles, index)
            ) / 3.0;

            return new AnalysisResult(classicalPhase, avgConfidence, true);
        } else {
            // Use highest confidence
            // ... implementation ...
        }
    }

    record AnalysisResult(WyckoffPhase phase, double confidence, boolean consensus) {}
}
```

### Real-Time Phase Tracking

```java
import com.vish.fno.phase.wyckoff.StructureSwingWyckoffPhaseIdentifier;
import com.vish.fno.model.Candle;

public class RealTimePhaseTracker {
    private final StructureSwingWyckoffPhaseIdentifier identifier;
    private final List<Candle> candleBuffer = new ArrayList<>();
    private WyckoffPhase currentPhase = WyckoffPhase.UNKNOWN;

    public void onNewCandle(Candle candle) {
        candleBuffer.add(candle);

        // Keep buffer size reasonable
        if (candleBuffer.size() > 100) {
            candleBuffer.remove(0);
        }

        // Identify phase
        WyckoffPhase newPhase = identifier.identifyPhase(candleBuffer, candleBuffer.size() - 1);

        // Detect phase transitions
        if (newPhase != currentPhase) {
            onPhaseTransition(currentPhase, newPhase);
            currentPhase = newPhase;
        }
    }

    private void onPhaseTransition(WyckoffPhase oldPhase, WyckoffPhase newPhase) {
        // Trading logic based on transitions
        if (oldPhase.isAccumulation() && newPhase.isMarkup()) {
            // Enter long positions
        } else if (oldPhase.isDistribution() && newPhase.isMarkdown()) {
            // Enter short positions or exit longs
        }
    }
}
```

### Integration with Technical Indicators

```java
import com.vish.fno.phase.wyckoff.ClassicalWyckoffPhaseIdentifier;
import com.vish.fno.technicals.ma.SimpleMovingAverage;
import com.vish.fno.technicals.RelativeStrengthIndex;
import com.vish.fno.model.Candle;
import com.vish.fno.model.wyckoff.WyckoffPhase;

public class PhaseWithIndicators {
    private final ClassicalWyckoffPhaseIdentifier wyckoff;
    private final SimpleMovingAverage ma50;
    private final RelativeStrengthIndex rsi;

    public TradingSignal generateSignal(List<Candle> candles) {
        int index = candles.size() - 1;

        // Get Wyckoff phase
        WyckoffPhase phase = wyckoff.identifyPhase(candles, index);
        double phaseConf = wyckoff.getPhaseConfidence(candles, index);

        // Calculate indicators
        List<Double> ma50Values = ma50.calculate(candles);
        List<Double> rsiValues = rsi.calculate(candles);

        double ma50Value = ma50Values.get(ma50Values.size() - 1);
        double rsiValue = rsiValues.get(rsiValues.size() - 1);
        double price = candles.get(index).close();

        // Confirm Wyckoff phases with technical indicators
        if (phase.isAccumulation() && price > ma50Value && rsiValue < 40) {
            return TradingSignal.STRONG_BUY;
        } else if (phase.isDistribution() && price < ma50Value && rsiValue > 60) {
            return TradingSignal.STRONG_SELL;
        }

        return TradingSignal.HOLD;
    }

    enum TradingSignal {
        STRONG_BUY, BUY, HOLD, SELL, STRONG_SELL
    }
}
```

## Recommended Combinations

**Conservative (High Confidence):**
```java
import com.vish.fno.phase.factory.WyckoffPhaseIdentifierFactory;
import com.vish.fno.phase.factory.WyckoffIdentifierType;

WyckoffPhaseIdentifierFactory factory = new WyckoffPhaseIdentifierFactory();
IWyckoffPhaseIdentifier conservative = factory.createComposite(
    WyckoffIdentifierType.DERIVATIVES_OI,  // 4.5
    WyckoffIdentifierType.MARKET_PROFILE,  // 4.5
    WyckoffIdentifierType.STRUCTURE_SWING  // 4.0
);
// Only trade when consensus >= 0.8
```

**Balanced (Medium Confidence):**
```java
IWyckoffPhaseIdentifier balanced = factory.createComposite(
    WyckoffIdentifierType.STRUCTURE_SWING,
    WyckoffIdentifierType.CLASSICAL,
    WyckoffIdentifierType.VOLUME_BASED
);
// Trade when consensus >= 0.6
```

**Aggressive (Lower Confidence, More Signals):**
```java
// Use single high-reliability identifier
IWyckoffPhaseIdentifier aggressive = factory.getIdentifier(WyckoffIdentifierType.STRUCTURE_SWING);
// Or create directly:
// IWyckoffPhaseIdentifier aggressive = WyckoffIdentifierType.STRUCTURE_SWING.create();
// Trade when confidence >= 0.5
```

## Migration Guide (v1.0 → v1.1)

### String-Based to Enum-Based API

**Old Way (String-based, deprecated):**
```java
// Factory usage
WyckoffPhaseIdentifierFactory factory = new WyckoffPhaseIdentifierFactory();
Optional<IWyckoffPhaseIdentifier> identifier = factory.getIdentifier("classical");
IWyckoffPhaseIdentifier composite = factory.createComposite("classical", "volume-based");

// Service usage
WyckoffPhaseService service = new WyckoffPhaseService(factory);
service.switchIdentifier("volume-based");
String identifierKey = service.getCurrentIdentifierTypeKey();
```

**New Way (Enum-based, recommended):**
```java
import com.vish.fno.phase.factory.WyckoffIdentifierType;
import com.vish.fno.phase.factory.WyckoffPhaseIdentifierFactory;

// Factory usage
WyckoffPhaseIdentifierFactory factory = new WyckoffPhaseIdentifierFactory();
IWyckoffPhaseIdentifier identifier = factory.getIdentifier(WyckoffIdentifierType.CLASSICAL);
IWyckoffPhaseIdentifier composite = factory.createComposite(
    WyckoffIdentifierType.CLASSICAL,
    WyckoffIdentifierType.VOLUME_BASED
);

// Service usage
WyckoffPhaseService service = new WyckoffPhaseService(factory);
service.switchIdentifier(WyckoffIdentifierType.VOLUME_BASED);
WyckoffIdentifierType identifierType = service.getCurrentIdentifierType();
```

### Key Benefits of Enum-Based API

1. **Compile-time safety** - Typos caught at compile time, not runtime
2. **IDE autocomplete** - Full IDE support for available identifier types
3. **Better refactoring** - Rename refactorings work correctly
4. **No null checks** - Enum is never null (unlike Optional\<String\>)
5. **Performance** - EnumMap is more efficient than HashMap\<String, ...\>

### Backward Compatibility

All string-based methods remain functional and are marked `@Deprecated` with `forRemoval = false`. You can migrate gradually without breaking existing code.

```java
// Still works, but shows deprecation warning
factory.getIdentifier("classical");
factory.createComposite("classical", "volume-based");
service.switchIdentifier("volume-based");
service.getCurrentIdentifierTypeKey();
```

### Quick Migration Checklist

- [ ] Replace string literals with `WyckoffIdentifierType` enum constants
- [ ] Update factory method calls: `getIdentifier(String)` → `getIdentifier(WyckoffIdentifierType)`
- [ ] Update composite creation: `createComposite(String...)` → `createComposite(WyckoffIdentifierType...)`
- [ ] Update service methods: `switchIdentifier(String)` → `switchIdentifier(WyckoffIdentifierType)`
- [ ] Replace `getCurrentIdentifierTypeKey()` with `getCurrentIdentifierType()`
- [ ] Update imports:
  - `import com.vish.fno.phase.factory.WyckoffIdentifierType;`
  - `import com.vish.fno.phase.factory.WyckoffPhaseIdentifierFactory;`

## Best Practices

1. **Always check confidence scores** - Don't trade on low confidence (<0.6)
2. **Reset identifiers** when switching symbols or timeframes
3. **Use composite for critical decisions** - Combines multiple viewpoints
4. **Validate with technical indicators** - Confirm Wyckoff phases with RSI, MA, etc.
5. **Ensure sufficient data** - Check `getMinimumDataPoints()` before analysis
6. **Handle UNKNOWN phase** - Don't trade when phase is unclear
7. **Monitor phase transitions** - Most profitable entries are at transitions
8. **Use appropriate identifier for asset** - OI for futures, TPO for intraday
9. **Backtest before live trading** - Validate strategies on historical data
10. **Consider market context** - Wyckoff works best in trending/ranging markets

## Common Pitfalls

1. **Insufficient data** - Always ensure data.size() > getMinimumDataPoints()
2. **Ignoring confidence** - Low confidence = high risk
3. **Wrong timeframe** - Classical tuned for hourly, TPO for intraday
4. **Missing OI data** - DerivativesFuturesOI requires candle.oi()
5. **Forgetting reset()** - Can cause stale state when switching symbols
6. **Over-trading** - Wait for high-confidence phase transitions
7. **Ignoring volume** - Wyckoff heavily relies on volume analysis

## Thread Safety

All identifier implementations are **NOT thread-safe** by default. For multi-threaded use:

```java
// Option 1: Synchronize access
synchronized (identifier) {
    WyckoffPhase phase = identifier.identifyPhase(data, index);
}

// Option 2: Create separate instances per thread
ThreadLocal<IWyckoffPhaseIdentifier> threadLocalIdentifier =
    ThreadLocal.withInitial(ClassicalWyckoffPhaseIdentifier::new);
```

## Performance Considerations

**Computational Complexity:**
- Classical: O(n) where n = lookback period
- VolumeBased: O(n)
- HeikinAshi: O(n) + HA conversion overhead
- Renko: O(n) + brick building overhead
- StructureSwing: O(n) + fractal detection
- MarketProfileTPO: O(n × m) where m = price levels (most intensive)
- DerivativesFuturesOI: O(n)
- Composite: O(k × n) where k = number of identifiers

**Optimization Tips:**
- Cache candle data if analyzing multiple indices
- Use appropriate lookback periods (don't over-fetch)
- Use composite only when necessary (computational cost multiplies)

## See Also

- **fno-models documentation**: `/docs/module-guides/fno-models.md#wyckoff-models`
- **fno-utils documentation**: `/docs/module-guides/fno-utils.md`
- **Integration guide**: `/docs/AI_AGENT_GUIDE.md`
