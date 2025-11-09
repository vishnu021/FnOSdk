# fno-phase-analyzer - Wyckoff Phase Identification

## Purpose
Advanced Wyckoff market phase identification using multiple algorithmic strategies. Identifies accumulation, distribution, markup, and markdown phases for F&O trading with high reliability.

## Maven Dependency
```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-phase-analyzer</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Module Dependencies
- **fno-models**: Core data models (Candle, Wyckoff models)
- **fno-utils**: Utility functions (CandleUtils, HeikinAshi, TimeUtils)
- **fno-strategy-utils**: Strategy utilities (HATrendUtils, Point2D)

## Key Package

### `com.vish.fno.phase.wyckoff` - Phase Identification Strategies

---

## Core Concepts

### Wyckoff Market Phases
The module identifies 14 distinct market phases based on Richard Wyckoff's methodology:

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

---

## Phase Identifier Implementations

### 1. ClassicalWyckoffPhaseIdentifier

Traditional Wyckoff methodology using volume analysis, price action, and market structure.

**Class Signature:**
```java
public class ClassicalWyckoffPhaseIdentifier implements IWyckoffPhaseIdentifier
```

**Key Methods:**
```java
@Override
public WyckoffPhase identifyPhase(List<Candle> data, int currentIndex)
```
- **Parameters:**
  - `data`: List of candlestick data (must not be null or empty)
  - `currentIndex`: Index in the data list to analyze (0-based)
- **Returns:** `WyckoffPhase` - The identified market phase
- **Description:** Identifies Wyckoff phase using classical methodology with volume analysis, price position, trend strength, momentum, and volatility. Optimized for hourly timeframes with ultra-sensitive thresholds.

```java
@Override
public String getIdentifierType()
```
- **Returns:** `"Classical"`

```java
@Override
public String getDescription()
```
- **Returns:** Description of the classical methodology

```java
@Override
public double getPhaseConfidence(List<Candle> data, int currentIndex)
```
- **Parameters:**
  - `data`: List of candlestick data
  - `currentIndex`: Index to analyze
- **Returns:** Confidence score (0.0 to 1.0)
- **Description:** Calculates confidence based on data availability, trend strength, volume signals, and pattern detection (springs/upthrusts).

```java
@Override
public int getMinimumDataPoints()
```
- **Returns:** `5` - Minimum candles required for analysis

```java
@Override
public boolean supportsRealTimeAnalysis()
```
- **Returns:** `true` - Supports real-time analysis

**Usage Example:**
```java
import com.vish.fno.phase.wyckoff.ClassicalWyckoffPhaseIdentifier;
import com.vish.fno.model.Candle;
import com.vish.fno.model.wyckoff.WyckoffPhase;

public class WyckoffAnalyzer {
    private ClassicalWyckoffPhaseIdentifier identifier = new ClassicalWyckoffPhaseIdentifier();

    public void analyzeMarket(List<Candle> candles) {
        int currentIndex = candles.size() - 1;

        // Identify current phase
        WyckoffPhase phase = identifier.identifyPhase(candles, currentIndex);

        // Get confidence score
        double confidence = identifier.getPhaseConfidence(candles, currentIndex);

        System.out.println("Phase: " + phase.getPhaseName());
        System.out.println("Confidence: " + (confidence * 100) + "%");

        // Make trading decisions based on phase
        if (phase.isAccumulation() && confidence > 0.7) {
            System.out.println("Consider LONG positions");
        } else if (phase.isDistribution() && confidence > 0.7) {
            System.out.println("Consider SHORT positions or exit longs");
        }
    }
}
```

**Configuration Constants:**
- `LOOKBACK_PERIOD = 5`: Very short for hourly sensitivity
- `VOLUME_LOOKBACK = 3`: Volume analysis lookback
- `SPRING_THRESHOLD = 0.998`: Ultra sensitive spring detection (0.2% below support)
- `UPTHRUST_THRESHOLD = 1.002`: Ultra sensitive upthrust detection (0.2% above resistance)
- `VOLUME_SPIKE_THRESHOLD = 1.2`: 20% above average for volume spike
- `TREND_THRESHOLD = 0.002`: 0.2% for trend detection
- `STRONG_TREND_THRESHOLD = 0.005`: 0.5% for clear trends

---

### 2. VolumeBasedWyckoffPhaseIdentifier

Focuses on volume patterns and price-volume relationships.

**Class Signature:**
```java
public class VolumeBasedWyckoffPhaseIdentifier implements IWyckoffPhaseIdentifier
```

**Key Methods:**
```java
@Override
public WyckoffPhase identifyPhase(List<Candle> data, int currentIndex)
```
- **Parameters:**
  - `data`: List of candlestick data
  - `currentIndex`: Index to analyze
- **Returns:** `WyckoffPhase` - Identified phase based on volume patterns
- **Description:** Analyzes buying pressure (bullish volume vs bearish volume), volume spikes, volume trends, and price-volume divergences to identify phases.

```java
@Override
public String getIdentifierType()
```
- **Returns:** `"Volume-Based"`

```java
@Override
public double getPhaseConfidence(List<Candle> data, int currentIndex)
```
- **Returns:** Confidence score (0.0 to 1.0) based on volume clarity
- **Description:** Higher confidence with volume spikes, clear buying/selling pressure, and strong volume trends.

```java
@Override
public int getMinimumDataPoints()
```
- **Returns:** `10` - Volume lookback period

**Usage Example:**
```java
import com.vish.fno.phase.wyckoff.VolumeBasedWyckoffPhaseIdentifier;

public class VolumeAnalysis {
    private VolumeBasedWyckoffPhaseIdentifier identifier = new VolumeBasedWyckoffPhaseIdentifier();

    public void analyzeVolumePatterns(List<Candle> candles) {
        int currentIndex = candles.size() - 1;

        WyckoffPhase phase = identifier.identifyPhase(candles, currentIndex);
        double confidence = identifier.getPhaseConfidence(candles, currentIndex);

        // Volume-based trading decisions
        if (phase == WyckoffPhase.ACCUMULATION_PHASE_A && confidence > 0.7) {
            System.out.println("High volume at lows - potential accumulation");
        } else if (phase == WyckoffPhase.MARKUP && confidence > 0.8) {
            System.out.println("Strong buying pressure confirmed by volume");
        }
    }
}
```

**Key Patterns Detected:**
- **High volume + price advance** = Markup or Sign of Strength
- **High volume + price decline** = Markdown or Sign of Weakness
- **High volume + sideways price** = Accumulation/Distribution Phase A/B
- **Low volume + price advance** = Test phase (Accumulation Phase C)
- **Low volume + price decline** = Test phase (Distribution Phase C)

**Configuration Constants:**
- `VOLUME_LOOKBACK = 10`: Bars for volume analysis
- `HIGH_VOLUME_THRESHOLD = 1.5`: 50% above average
- `LOW_VOLUME_THRESHOLD = 0.7`: 30% below average
- `PRICE_CHANGE_THRESHOLD = 0.005`: 0.5% price movement

---

### 3. HeikinAshiWyckoffPhaseIdentifier

Uses Heikin-Ashi candles to smooth price action and filter noise.

**Class Signature:**
```java
public class HeikinAshiWyckoffPhaseIdentifier implements IWyckoffPhaseIdentifier
```

**Key Methods:**
```java
@Override
public WyckoffPhase identifyPhase(List<Candle> data, int currentIndex)
```
- **Parameters:**
  - `data`: List of regular candlestick data (will be converted to Heikin-Ashi internally)
  - `currentIndex`: Index to analyze
- **Returns:** `WyckoffPhase` - Identified phase using smoothed HA candles
- **Description:** Converts regular candles to Heikin-Ashi, analyzes HA-specific patterns (strong candles with no wicks, dojis at extremes, consecutive same-color candles), and uses HATrendUtils for trend detection.

```java
@Override
public String getIdentifierType()
```
- **Returns:** `"Heikin-Ashi"`

```java
@Override
public double getPhaseConfidence(List<Candle> data, int currentIndex)
```
- **Returns:** Confidence score (0.0 to 1.0)
- **Description:** Higher confidence with consecutive strong candles, clear reversal patterns with dojis, and sufficient data history.

```java
@Override
public int getMinimumDataPoints()
```
- **Returns:** `10` - Minimum for HA analysis

**Usage Example:**
```java
import com.vish.fno.phase.wyckoff.HeikinAshiWyckoffPhaseIdentifier;
import com.vish.fno.util.chart.HeikinAshi;

public class HeikinAshiAnalyzer {
    private HeikinAshiWyckoffPhaseIdentifier identifier = new HeikinAshiWyckoffPhaseIdentifier();

    public void analyzeSmoothTrends(List<Candle> candles) {
        WyckoffPhase phase = identifier.identifyPhase(candles, candles.size() - 1);
        double confidence = identifier.getPhaseConfidence(candles, candles.size() - 1);

        // HA reduces false signals
        if (phase == WyckoffPhase.MARKUP && confidence > 0.7) {
            System.out.println("Clear uptrend confirmed by HA - low noise");
        }

        // Check for reversal patterns
        if (phase == WyckoffPhase.ACCUMULATION_PHASE_C) {
            System.out.println("Spring pattern detected in smoothed HA data");
        }
    }
}
```

**Key Advantages:**
- **Smoother trend identification** - Reduces market noise
- **Better reversal detection** - Doji patterns at extremes
- **Reduced false signals** - Filters out wicks and volatility
- **Clearer consolidation zones** - Better Phase B identification

**HA-Specific Patterns:**
- **Strong bullish candles** (no lower wick) = Strong MARKUP
- **Strong bearish candles** (no upper wick) = Strong MARKDOWN
- **Consecutive bullish candles** (>3) = Confirmed uptrend
- **Dojis at top** = Potential distribution
- **Dojis at bottom** = Potential accumulation

**Configuration Constants:**
- `MIN_CANDLES_FOR_ANALYSIS = 10`
- `TREND_LOOKBACK = 20`
- `CONSOLIDATION_THRESHOLD = 0.003`: 0.3% range
- `STRONG_TREND_THRESHOLD = 0.01`: 1% move
- `VOLUME_SPIKE_THRESHOLD = 1.5`: 50% above average

---

### 4. RenkoWyckoffPhaseIdentifier

Fixed price movement blocks (bricks) to filter noise and identify trends.

**Class Signature:**
```java
public class RenkoWyckoffPhaseIdentifier implements IWyckoffPhaseIdentifier
```

**Key Methods:**
```java
@Override
public WyckoffPhase identifyPhase(List<Candle> data, int currentIndex)
```
- **Parameters:**
  - `data`: List of candlestick data
  - `currentIndex`: Index to analyze
- **Returns:** `WyckoffPhase` - Phase based on Renko brick patterns
- **Description:** Calculates dynamic brick size using ATR, builds Renko bricks from price data, analyzes brick direction patterns (uninterrupted staircases = trends, alternating bricks = consolidation, reversals = springs/upthrusts).

```java
@Override
public String getIdentifierType()
```
- **Returns:** `"Renko"`

```java
@Override
public void reset()
```
- **Description:** Clears internal Renko brick cache. Call when switching symbols or restarting analysis.

```java
@Override
public int getMinimumDataPoints()
```
- **Returns:** `14` - ATR period for brick size calculation

**Usage Example:**
```java
import com.vish.fno.phase.wyckoff.RenkoWyckoffPhaseIdentifier;

public class RenkoAnalyzer {
    private RenkoWyckoffPhaseIdentifier identifier = new RenkoWyckoffPhaseIdentifier();

    public void analyzeNoiseFiltered(List<Candle> candles) {
        // Reset when switching symbols
        identifier.reset();

        WyckoffPhase phase = identifier.identifyPhase(candles, candles.size() - 1);
        double confidence = identifier.getPhaseConfidence(candles, candles.size() - 1);

        // Renko patterns
        if (phase == WyckoffPhase.MARKUP && confidence > 0.8) {
            System.out.println("Uninterrupted upward brick staircase - strong trend");
        } else if (phase == WyckoffPhase.ACCUMULATION_PHASE_C) {
            System.out.println("Brief downward excursion with reversal - spring pattern");
        }
    }
}
```

**Renko Patterns:**
- **Uninterrupted staircase up** (≥3 bricks) = MARKUP
- **Uninterrupted staircase down** (≥3 bricks) = MARKDOWN
- **Brief excursion with reversal** (down→up) = Spring (Phase C)
- **Brief excursion with reversal** (up→down) = Upthrust (Phase C)
- **Lateral bricks** (>50% alternating) = Phase B consolidation

**Reliability:** 3.5/5.0
**Best for:** Trend/range separation, noise filtering
**Weakness:** Parameter-sensitive, can repaint on small moves

**Configuration Constants:**
- `ATR_PERIOD = 14`
- `BRICK_SIZE_MULTIPLIER = 0.75`: 0.5-1.0 × ATR
- `MIN_BRICKS_FOR_TREND = 3`
- `LOOKBACK_BRICKS = 10`

---

### 5. StructureSwingWyckoffPhaseIdentifier

Uses Higher Highs/Higher Lows (HH/HL) and Lower Lows/Lower Highs (LL/LH) patterns.

**Class Signature:**
```java
public class StructureSwingWyckoffPhaseIdentifier implements IWyckoffPhaseIdentifier
```

**Key Methods:**
```java
@Override
public WyckoffPhase identifyPhase(List<Candle> data, int currentIndex)
```
- **Parameters:**
  - `data`: List of candlestick data
  - `currentIndex`: Index to analyze
- **Returns:** `WyckoffPhase` - Phase based on swing structure
- **Description:** Identifies fractal pivot points (swing highs/lows), uses Donchian channels for box boundaries, detects HH/HL sequences (uptrend), LL/LH sequences (downtrend), and failed breakouts (Phase C).

```java
@Override
public String getIdentifierType()
```
- **Returns:** `"StructureSwing"`

```java
@Override
public void reset()
```
- **Description:** Clears swing point cache and box boundaries. Call when switching analysis context.

```java
@Override
public int getMinimumDataPoints()
```
- **Returns:** `20` - Donchian period for box boundaries

**Usage Example:**
```java
import com.vish.fno.phase.wyckoff.StructureSwingWyckoffPhaseIdentifier;

public class SwingStructureAnalyzer {
    private StructureSwingWyckoffPhaseIdentifier identifier = new StructureSwingWyckoffPhaseIdentifier();

    public void analyzeSwingPatterns(List<Candle> candles) {
        identifier.reset(); // Clear previous swing data

        WyckoffPhase phase = identifier.identifyPhase(candles, candles.size() - 1);
        double confidence = identifier.getPhaseConfidence(candles, candles.size() - 1);

        // Swing-based decisions
        if (phase == WyckoffPhase.MARKUP && confidence > 0.7) {
            System.out.println("Persistent HH/HL pattern - confirmed uptrend");
        } else if (phase == WyckoffPhase.ACCUMULATION_PHASE_C) {
            System.out.println("Failed breakout below support - spring detected");
        }
    }
}
```

**Key Concepts:**
- **Fractal Pivots:** 2-3 bar lookback for swing points
- **Donchian Channels:** 20-40 period for box boundaries
- **Minimum Swing Size:** 0.5 × ATR to filter noise
- **Failed Breakouts:** 0.2% threshold for false breaks

**Swing Patterns:**
- **Persistent HH/HL** (≥2-3 swings) = MARKUP
- **Persistent LL/LH** (≥2-3 swings) = MARKDOWN
- **Failed breakout down** = Spring (Accumulation Phase C)
- **Failed breakout up** = Upthrust (Distribution Phase C)
- **Alternating swings in box** = Phase B consolidation

**Reliability:** 4.0/5.0
**Best for:** Clear execution logic, adaptable, execution triggers
**Weakness:** Subject to wick noise on 1-min timeframes

**Configuration Constants:**
- `FRACTAL_LOOKBACK = 2`: 2-3 bar lookback
- `DONCHIAN_PERIOD = 20`: 20-40 for box
- `MIN_SWING_SIZE_MULTIPLIER = 0.5`: 0.5 × ATR
- `MIN_SWINGS_FOR_TREND = 2`: 2-3 swings to confirm
- `FAILED_BREAKOUT_THRESHOLD = 0.002`: 0.2% for false break

---

### 6. MarketProfileTPOWyckoffPhaseIdentifier

Uses auction market theory and Time Price Opportunity (TPO) analysis.

**Class Signature:**
```java
public class MarketProfileTPOWyckoffPhaseIdentifier implements IWyckoffPhaseIdentifier
```

**Key Methods:**
```java
@Override
public WyckoffPhase identifyPhase(List<Candle> data, int currentIndex)
```
- **Parameters:**
  - `data`: List of candlestick data
  - `currentIndex`: Index to analyze
- **Returns:** `WyckoffPhase` - Phase based on Market Profile patterns
- **Description:** Builds TPO histogram at price levels, calculates Point of Control (POC), Value Area High/Low (VAH/VAL), identifies profile shapes (bell, P-shape, B-shape, elongated), detects single prints and tails (Phase C indicators), tracks value area migration for trending phases.

```java
@Override
public String getIdentifierType()
```
- **Returns:** `"MarketProfileTPO"`

```java
@Override
public void reset()
```
- **Description:** Clears TPO counts, volume profile, and value area calculations.

```java
@Override
public int getMinimumDataPoints()
```
- **Returns:** `30` - Reasonable session data

**Usage Example:**
```java
import com.vish.fno.phase.wyckoff.MarketProfileTPOWyckoffPhaseIdentifier;

public class MarketProfileAnalyzer {
    private MarketProfileTPOWyckoffPhaseIdentifier identifier = new MarketProfileTPOWyckoffPhaseIdentifier();

    public void analyzeIntraday(List<Candle> sessionCandles) {
        identifier.reset(); // Clear previous session data

        WyckoffPhase phase = identifier.identifyPhase(sessionCandles, sessionCandles.size() - 1);
        double confidence = identifier.getPhaseConfidence(sessionCandles, sessionCandles.size() - 1);

        // Market Profile patterns
        if (phase == WyckoffPhase.CONSOLIDATION && confidence > 0.7) {
            System.out.println("Bell-shaped profile - balanced auction");
        } else if (phase == WyckoffPhase.MARKUP && confidence > 0.8) {
            System.out.println("Elongated profile with value migration up - trend day");
        } else if (phase == WyckoffPhase.ACCUMULATION_PHASE_C) {
            System.out.println("Single prints with excess at bottom - spring pattern");
        }
    }
}
```

**Market Profile Concepts:**
- **TPO (Time Price Opportunity):** Time spent at each price level
- **Point of Control (POC):** Price with highest TPO count
- **Value Area:** 70% of all TPOs centered around POC
- **Initial Balance (IB):** First 20% of session range
- **Single Prints:** TPOs < 20% of mode - indicate rapid movement
- **Excess:** Single TPOs at extremes - rejection

**Profile Shapes:**
- **Bell Shape** (60%+ in middle) = Balanced auction, Phase B
- **P-Shape** (>50% top-heavy) = Late buying, Distribution
- **B-Shape** (>50% bottom-heavy) = Late selling, Accumulation
- **Elongated** = Trend day, Markup/Markdown
- **Double Distribution** = Bimodal, transition phases

**Patterns:**
- **Bell + sideways** = Phase B consolidation
- **Single prints + excess at bottom** = Spring (Phase C)
- **Single prints + excess at top** = Upthrust (Phase C)
- **Value migration up + IB break** = MARKUP trend day
- **Failed auction back to value** = Exhaustion, Phase E

**Reliability:** 4.5/5.0 (Highest for balance vs imbalance)
**Best for:** Intraday session analysis, auction clarity
**Note:** Best with futures volume profile data

**Configuration Constants:**
- `TPO_SIZE = 0.001`: 0.1% price increments
- `VALUE_AREA_PERCENT = 0.70`: 70% of TPOs
- `MIN_TPOS_FOR_PROFILE = 30`
- `SINGLE_PRINT_THRESHOLD = 0.2`: < 20% of mode
- `TAIL_THRESHOLD = 0.15`: Bottom/top 15%
- `IB_RANGE_PERCENT = 0.20`: First 20% of session

---

### 7. DerivativesFuturesOIWyckoffPhaseIdentifier

Uses futures Open Interest (OI) changes to identify real market positioning.

**Class Signature:**
```java
public class DerivativesFuturesOIWyckoffPhaseIdentifier implements IWyckoffPhaseIdentifier
```

**Key Methods:**
```java
@Override
public WyckoffPhase identifyPhase(List<Candle> data, int currentIndex)
```
- **Parameters:**
  - `data`: List of candlestick data (must include OI via `candle.oi()`)
  - `currentIndex`: Index to analyze
- **Returns:** `WyckoffPhase` - Phase based on OI patterns
- **Description:** Analyzes price-OI relationships (Price↑ OI↑ = Long buildup, Price↑ OI↓ = Short covering, Price↓ OI↑ = Short buildup, Price↓ OI↓ = Long unwinding), detects OI spikes (Phase C), identifies OI divergences (weak buying/selling).

**Prerequisites:**
- Candle data must include Open Interest: `candle.oi()` must be populated
- Works best with NSE futures data (NIFTY, BANKNIFTY)

```java
@Override
public String getIdentifierType()
```
- **Returns:** `"DerivativesFuturesOI"`

```java
@Override
public void reset()
```
- **Description:** Clears OI history cache.

```java
@Override
public int getMinimumDataPoints()
```
- **Returns:** `5` - Minimum for OI analysis

**Usage Example:**
```java
import com.vish.fno.phase.wyckoff.DerivativesFuturesOIWyckoffPhaseIdentifier;
import com.vish.fno.model.Candle;

public class OIAnalyzer {
    private DerivativesFuturesOIWyckoffPhaseIdentifier identifier = new DerivativesFuturesOIWyckoffPhaseIdentifier();

    public void analyzeFuturesOI(List<Candle> candles) {
        // Ensure candles have OI data
        for (Candle c : candles) {
            if (c.oi() == null) {
                throw new IllegalArgumentException("OI data required");
            }
        }

        WyckoffPhase phase = identifier.identifyPhase(candles, candles.size() - 1);
        double confidence = identifier.getPhaseConfidence(candles, candles.size() - 1);

        // OI-based trading decisions
        if (phase == WyckoffPhase.MARKUP && confidence > 0.8) {
            System.out.println("Long buildup confirmed - fresh longs entering");
        } else if (phase == WyckoffPhase.ACCUMULATION_PHASE_D && confidence > 0.7) {
            System.out.println("Short covering detected - potential trend reversal");
        } else if (phase == WyckoffPhase.ACCUMULATION_PHASE_C) {
            System.out.println("OI spike on failed breakout - spring pattern");
        }
    }
}
```

**OI Patterns (The Four Pillars):**

1. **Price ↑ + OI ↑ = Long Buildup**
   - Fresh longs entering market
   - Indicates: Accumulation/Markup
   - Confidence: High

2. **Price ↑ + OI ↓ = Short Covering**
   - Shorts exiting positions
   - Indicates: Late Markup or exhaustion
   - Confidence: Moderate (can reverse)

3. **Price ↓ + OI ↑ = Short Buildup**
   - Fresh shorts entering market
   - Indicates: Distribution/Markdown
   - Confidence: High

4. **Price ↓ + OI ↓ = Long Unwinding**
   - Longs exiting positions
   - Indicates: Late Markdown or potential bottom
   - Confidence: Moderate

**Advanced Patterns:**
- **OI spike + failed breakout** = Phase C (spring/upthrust)
- **Flat OI + sideways price** = Phase B consolidation
- **Bullish divergence** (Price ↓ OI ↓) = Weak selling, potential accumulation
- **Bearish divergence** (Price ↑ OI ↓) = Weak buying, potential distribution

**Reliability:** 4.5/5.0 (Highest for indices)
**Best for:** Position confirmation, fewer false breakouts, institutional activity
**Note:** Requires futures data feed with OI information

**Configuration Constants:**
- `OI_LOOKBACK_PERIOD = 20`: 5-20 bars
- `SIGNIFICANT_OI_CHANGE = 0.05`: 5% OI change
- `SIGNIFICANT_PRICE_CHANGE = 0.005`: 0.5% price change
- `OI_SPIKE_THRESHOLD = 0.10`: 10% OI spike
- `TREND_CONFIRMATION_BARS = 3`

---

### 8. CompositeWyckoffPhaseIdentifier

Combines multiple identifier strategies for highest confidence.

**Class Signature:**
```java
public class CompositeWyckoffPhaseIdentifier implements IWyckoffPhaseIdentifier
```

**Constructor:**
```java
public CompositeWyckoffPhaseIdentifier(IWyckoffPhaseIdentifier... identifiers)
```
- **Parameters:**
  - `identifiers`: Varargs array of phase identifiers to combine
- **Throws:** `IllegalArgumentException` if no identifiers provided

**Key Methods:**
```java
@Override
public WyckoffPhase identifyPhase(List<Candle> data, int currentIndex)
```
- **Parameters:**
  - `data`: List of candlestick data
  - `currentIndex`: Index to analyze
- **Returns:** `WyckoffPhase` - Phase with highest weighted confidence across all identifiers
- **Description:** Collects phases from all identifiers, calculates weighted average confidence for each phase, returns the phase with the highest score.

```java
@Override
public String getIdentifierType()
```
- **Returns:** `"Composite"`

```java
@Override
public double getPhaseConfidence(List<Candle> data, int currentIndex)
```
- **Returns:** Weighted confidence score for the identified phase
- **Description:** Uses cached confidence from most recent identification.

```java
public Map<String, WyckoffPhase> getDetailedAnalysis(List<Candle> data, int currentIndex)
```
- **Parameters:**
  - `data`: List of candlestick data
  - `currentIndex`: Index to analyze
- **Returns:** `Map<String, WyckoffPhase>` - Phase identified by each strategy
- **Description:** Returns breakdown of what each individual identifier determined.

```java
public Map<String, Double> getConfidenceScores(List<Candle> data, int currentIndex)
```
- **Parameters:**
  - `data`: List of candlestick data
  - `currentIndex`: Index to analyze
- **Returns:** `Map<String, Double>` - Confidence score from each identifier
- **Description:** Returns individual confidence scores for transparency.

```java
@Override
public void reset()
```
- **Description:** Resets all underlying identifiers and clears cache.

**Usage Example:**
```java
import com.vish.fno.phase.wyckoff.*;

public class CompositeAnalyzer {
    public void analyzeWithMultipleStrategies(List<Candle> candles) {
        // Create individual identifiers
        ClassicalWyckoffPhaseIdentifier classical = new ClassicalWyckoffPhaseIdentifier();
        VolumeBasedWyckoffPhaseIdentifier volume = new VolumeBasedWyckoffPhaseIdentifier();
        StructureSwingWyckoffPhaseIdentifier structure = new StructureSwingWyckoffPhaseIdentifier();
        HeikinAshiWyckoffPhaseIdentifier heikinAshi = new HeikinAshiWyckoffPhaseIdentifier();

        // Create composite
        CompositeWyckoffPhaseIdentifier composite = new CompositeWyckoffPhaseIdentifier(
            classical, volume, structure, heikinAshi
        );

        int currentIndex = candles.size() - 1;

        // Get consensus phase
        WyckoffPhase consensusPhase = composite.identifyPhase(candles, currentIndex);
        double consensusConfidence = composite.getPhaseConfidence(candles, currentIndex);

        System.out.println("Consensus Phase: " + consensusPhase.getPhaseName());
        System.out.println("Consensus Confidence: " + (consensusConfidence * 100) + "%");

        // Get detailed breakdown
        Map<String, WyckoffPhase> breakdown = composite.getDetailedAnalysis(candles, currentIndex);
        Map<String, Double> confidences = composite.getConfidenceScores(candles, currentIndex);

        System.out.println("\nDetailed Analysis:");
        for (String type : breakdown.keySet()) {
            WyckoffPhase phase = breakdown.get(type);
            double confidence = confidences.get(type);
            System.out.printf("%s: %s (%.1f%%)\n", type, phase, confidence * 100);
        }

        // Trade only if high consensus
        if (consensusConfidence > 0.8) {
            System.out.println("High confidence consensus - safe to trade");
        }
    }
}
```

**Advanced Usage - Custom Weighting:**
```java
public class WeightedComposite {
    public void customWeighting(List<Candle> candles) {
        // Combine high-reliability strategies only
        DerivativesFuturesOIWyckoffPhaseIdentifier oi = new DerivativesFuturesOIWyckoffPhaseIdentifier();
        MarketProfileTPOWyckoffPhaseIdentifier tpo = new MarketProfileTPOWyckoffPhaseIdentifier();
        StructureSwingWyckoffPhaseIdentifier swing = new StructureSwingWyckoffPhaseIdentifier();

        // All have 4.0+ reliability scores
        CompositeWyckoffPhaseIdentifier highReliability = new CompositeWyckoffPhaseIdentifier(
            oi,    // 4.5/5.0
            tpo,   // 4.5/5.0
            swing  // 4.0/5.0
        );

        WyckoffPhase phase = highReliability.identifyPhase(candles, candles.size() - 1);
        double confidence = highReliability.getPhaseConfidence(candles, candles.size() - 1);

        System.out.println("High-reliability consensus: " + phase);
        System.out.println("Confidence: " + (confidence * 100) + "%");
    }
}
```

**Best Practices:**
- Use 3-5 identifiers for optimal balance (more isn't always better)
- Combine complementary strategies (e.g., volume + structure + OI)
- Check detailed breakdown when consensus is low (<0.6)
- Reset composite when switching symbols or timeframes

---

### 9. WyckoffPhaseIdentifierFactory

Factory for creating and managing phase identifier instances.

**Class Signature:**
```java
public class WyckoffPhaseIdentifierFactory
```

**Constructor:**
```java
public WyckoffPhaseIdentifierFactory(
    ClassicalWyckoffPhaseIdentifier classicalIdentifier,
    DerivativesFuturesOIWyckoffPhaseIdentifier derivativesIdentifier,
    HeikinAshiWyckoffPhaseIdentifier heikinAshiIdentifier,
    MarketProfileTPOWyckoffPhaseIdentifier marketProfileIdentifier,
    RenkoWyckoffPhaseIdentifier renkoIdentifier,
    StructureSwingWyckoffPhaseIdentifier structureSwingIdentifier,
    VolumeBasedWyckoffPhaseIdentifier volumeBasedIdentifier,
    CompositeWyckoffPhaseIdentifier compositeIdentifier)
```
- **Parameters:** All available identifier implementations
- **Description:** Registers all identifiers and sets default to StructureSwing (4.0/5.0 reliability).

**Key Methods:**
```java
public IWyckoffPhaseIdentifier getDefaultIdentifier()
```
- **Returns:** The default identifier (StructureSwing)

```java
public IWyckoffPhaseIdentifier getIdentifier(String type)
```
- **Parameters:**
  - `type`: Identifier type (case-insensitive): "classical", "volume-based", "heikin-ashi", "renko", "structureswing", "marketprofiletpo", "derivativesfuturesoi", "composite"
- **Returns:** The requested identifier, or default if not found and fallback enabled
- **Throws:** `IllegalArgumentException` if type not found and fallback disabled

```java
public Set<String> getAvailableTypes()
```
- **Returns:** Set of all available identifier type names

```java
public boolean hasIdentifier(String type)
```
- **Parameters:**
  - `type`: Identifier type to check
- **Returns:** `true` if the type is registered

```java
public Map<String, String> getIdentifierInfo()
```
- **Returns:** Map of identifier type → description for all registered identifiers

```java
public void setDefaultIdentifierType(String type)
```
- **Parameters:**
  - `type`: Identifier type to set as default
- **Description:** Changes the default identifier at runtime

```java
public IWyckoffPhaseIdentifier createCompositeIdentifier(String... types)
```
- **Parameters:**
  - `types`: Varargs array of identifier type names to combine
- **Returns:** A new composite identifier combining the specified types
- **Description:** Convenience method for creating custom composite identifiers

**Usage Example:**
```java
import com.vish.fno.phase.wyckoff.WyckoffPhaseIdentifierFactory;

@Configuration
public class WyckoffConfig {
    @Bean
    public WyckoffPhaseIdentifierFactory factory(
        ClassicalWyckoffPhaseIdentifier classical,
        VolumeBasedWyckoffPhaseIdentifier volume,
        HeikinAshiWyckoffPhaseIdentifier heikinAshi,
        RenkoWyckoffPhaseIdentifier renko,
        StructureSwingWyckoffPhaseIdentifier structureSwing,
        MarketProfileTPOWyckoffPhaseIdentifier marketProfile,
        DerivativesFuturesOIWyckoffPhaseIdentifier oi,
        CompositeWyckoffPhaseIdentifier composite) {

        return new WyckoffPhaseIdentifierFactory(
            classical, oi, heikinAshi, marketProfile,
            renko, structureSwing, volume, composite
        );
    }
}

@Service
public class PhaseAnalysisService {
    @Autowired
    private WyckoffPhaseIdentifierFactory factory;

    public void analyzeWithFactory(List<Candle> candles, String strategy) {
        // Get available types
        Set<String> types = factory.getAvailableTypes();
        System.out.println("Available strategies: " + types);

        // Use specific identifier
        IWyckoffPhaseIdentifier identifier = factory.getIdentifier(strategy);
        WyckoffPhase phase = identifier.identifyPhase(candles, candles.size() - 1);

        System.out.println("Phase using " + strategy + ": " + phase.getPhaseName());
    }

    public void useHighReliabilityComposite(List<Candle> candles) {
        // Create composite on-the-fly
        IWyckoffPhaseIdentifier composite = factory.createCompositeIdentifier(
            "derivativesfuturesoi",  // 4.5/5.0
            "marketprofiletpo",      // 4.5/5.0
            "structureswing"         // 4.0/5.0
        );

        WyckoffPhase phase = composite.identifyPhase(candles, candles.size() - 1);
        double confidence = composite.getPhaseConfidence(candles, candles.size() - 1);

        System.out.println("High-reliability phase: " + phase);
        System.out.println("Confidence: " + (confidence * 100) + "%");
    }

    public void switchDefault(String newDefault) {
        factory.setDefaultIdentifierType(newDefault);
        System.out.println("Default changed to: " + newDefault);
    }

    public void listAllIdentifiers() {
        Map<String, String> info = factory.getIdentifierInfo();
        System.out.println("All registered identifiers:");
        for (Map.Entry<String, String> entry : info.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
    }
}
```

**Spring Boot Configuration Example:**
```java
@SpringBootApplication
public class TradingApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradingApplication.class, args);
    }

    @Bean
    public ClassicalWyckoffPhaseIdentifier classicalIdentifier() {
        return new ClassicalWyckoffPhaseIdentifier();
    }

    @Bean
    public VolumeBasedWyckoffPhaseIdentifier volumeIdentifier() {
        return new VolumeBasedWyckoffPhaseIdentifier();
    }

    // ... other identifier beans

    @Bean
    public CompositeWyckoffPhaseIdentifier compositeIdentifier(
        ClassicalWyckoffPhaseIdentifier classical,
        VolumeBasedWyckoffPhaseIdentifier volume,
        StructureSwingWyckoffPhaseIdentifier swing) {
        return new CompositeWyckoffPhaseIdentifier(classical, volume, swing);
    }
}
```

---

## Integration Patterns

### Pattern 1: Multi-Strategy Analysis
```java
import com.vish.fno.phase.wyckoff.*;
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
            double classicalConf = classical.getPhaseConfidence(candles, index);
            double oiConf = oiAnalyzer.getPhaseConfidence(candles, index);
            double tpoConf = tpoAnalyzer.getPhaseConfidence(candles, index);

            if (classicalConf >= oiConf && classicalConf >= tpoConf) {
                return new AnalysisResult(classicalPhase, classicalConf, false);
            } else if (oiConf >= tpoConf) {
                return new AnalysisResult(oiPhase, oiConf, false);
            } else {
                return new AnalysisResult(tpoPhase, tpoConf, false);
            }
        }
    }

    record AnalysisResult(WyckoffPhase phase, double confidence, boolean consensus) {}
}
```

### Pattern 2: Real-Time Phase Tracking
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
        int currentIndex = candleBuffer.size() - 1;
        WyckoffPhase newPhase = identifier.identifyPhase(candleBuffer, currentIndex);

        // Detect phase transitions
        if (newPhase != currentPhase) {
            onPhaseTransition(currentPhase, newPhase);
            currentPhase = newPhase;
        }
    }

    private void onPhaseTransition(WyckoffPhase oldPhase, WyckoffPhase newPhase) {
        System.out.println("Phase transition: " + oldPhase + " -> " + newPhase);

        // Trading logic based on transitions
        if (oldPhase.isAccumulation() && newPhase.isMarkup()) {
            System.out.println("Accumulation complete - entering markup phase");
            // Enter long positions
        } else if (oldPhase.isDistribution() && newPhase.isMarkdown()) {
            System.out.println("Distribution complete - entering markdown phase");
            // Enter short positions or exit longs
        }
    }
}
```

### Pattern 3: Backtesting with Phase Analysis
```java
import com.vish.fno.phase.wyckoff.CompositeWyckoffPhaseIdentifier;
import com.vish.fno.model.Candle;
import com.vish.fno.model.wyckoff.WyckoffPhase;

public class WyckoffBacktester {
    private final CompositeWyckoffPhaseIdentifier identifier;
    private final List<Candle> historicalData;

    public BacktestResults backtest(int minConfidence) {
        int longEntries = 0;
        int shortEntries = 0;
        double pnl = 0.0;

        for (int i = 50; i < historicalData.size(); i++) {
            WyckoffPhase phase = identifier.identifyPhase(historicalData, i);
            double confidence = identifier.getPhaseConfidence(historicalData, i);

            // Only trade high-confidence signals
            if (confidence < minConfidence / 100.0) continue;

            Candle current = historicalData.get(i);

            // Entry logic
            if (phase == WyckoffPhase.ACCUMULATION_PHASE_D) {
                // Enter long
                longEntries++;
                double entry = current.close();

                // Find exit
                for (int j = i + 1; j < historicalData.size(); j++) {
                    WyckoffPhase futurePhase = identifier.identifyPhase(historicalData, j);
                    if (futurePhase.isDistribution() || futurePhase == WyckoffPhase.MARKDOWN) {
                        double exit = historicalData.get(j).close();
                        pnl += (exit - entry);
                        break;
                    }
                }
            } else if (phase == WyckoffPhase.DISTRIBUTION_PHASE_D) {
                // Enter short
                shortEntries++;
                double entry = current.close();

                for (int j = i + 1; j < historicalData.size(); j++) {
                    WyckoffPhase futurePhase = identifier.identifyPhase(historicalData, j);
                    if (futurePhase.isAccumulation() || futurePhase == WyckoffPhase.MARKUP) {
                        double exit = historicalData.get(j).close();
                        pnl += (entry - exit);
                        break;
                    }
                }
            }
        }

        return new BacktestResults(longEntries, shortEntries, pnl);
    }

    record BacktestResults(int longTrades, int shortTrades, double totalPnL) {}
}
```

### Pattern 4: Integration with Technical Indicators
```java
import com.vish.fno.phase.wyckoff.ClassicalWyckoffPhaseIdentifier;
import com.vish.fno.technicals.MovingAverage;
import com.vish.fno.technicals.RelativeStrengthIndex;
import com.vish.fno.model.Candle;
import com.vish.fno.model.wyckoff.WyckoffPhase;

public class PhaseWithIndicators {
    private final ClassicalWyckoffPhaseIdentifier wyckoff;
    private final MovingAverage ma50;
    private final RelativeStrengthIndex rsi;

    public TradingSignal generateSignal(List<Candle> candles) {
        int index = candles.size() - 1;

        // Get Wyckoff phase
        WyckoffPhase phase = wyckoff.identifyPhase(candles, index);
        double phaseConf = wyckoff.getPhaseConfidence(candles, index);

        // Calculate indicators
        double ma50Value = ma50.calculate(candles, index);
        double rsiValue = rsi.calculate(candles, index);

        Candle current = candles.get(index);
        double price = current.close();

        // Confirm Wyckoff phases with technical indicators
        if (phase.isAccumulation() && price > ma50Value && rsiValue < 40) {
            return TradingSignal.STRONG_BUY;
        } else if (phase == WyckoffPhase.ACCUMULATION_PHASE_D && rsiValue > 50) {
            return TradingSignal.BUY;
        } else if (phase.isDistribution() && price < ma50Value && rsiValue > 60) {
            return TradingSignal.STRONG_SELL;
        } else if (phase == WyckoffPhase.DISTRIBUTION_PHASE_D && rsiValue < 50) {
            return TradingSignal.SELL;
        }

        return TradingSignal.HOLD;
    }

    enum TradingSignal {
        STRONG_BUY, BUY, HOLD, SELL, STRONG_SELL
    }
}
```

---

## Strategy Selection Guide

### By Reliability (Highest to Lowest)

1. **MarketProfileTPO** (4.5/5.0)
   - Best for: Intraday analysis, institutional activity
   - Requires: Futures volume data

2. **DerivativesFuturesOI** (4.5/5.0)
   - Best for: Index futures (NIFTY, BANKNIFTY)
   - Requires: Open Interest data

3. **StructureSwing** (4.0/5.0)
   - Best for: Clear execution logic, adaptable
   - Caution: Wick noise on 1-min

4. **Renko** (3.5/5.0)
   - Best for: Trend/range separation
   - Caution: Parameter-sensitive

5. **Classical** (varies)
   - Best for: General-purpose, no special data required
   - Note: Tuned for hourly timeframes

6. **VolumeBA sed** (varies)
   - Best for: Volume-centric analysis
   - Requires: Accurate volume data

7. **HeikinAshi** (varies)
   - Best for: Noise reduction, smoother trends
   - Note: Lags on rapid moves

### By Timeframe

**Intraday (1-min, 5-min):**
- MarketProfileTPO (best for sessions)
- StructureSwing (good for swings)
- VolumeBasedcaution with noise)

**Short-term (15-min, 30-min, 1-hour):**
- Classical (tuned for hourly)
- DerivativesFuturesOI (excellent for futures)
- HeikinAshi (smooth trends)

**Medium-term (Daily, Weekly):**
- StructureSwing (clear swing patterns)
- Classical (long-term trends)
- Renko (filter daily noise)

### By Data Availability

**No special data:**
- Classical
- HeikinAshi
- VolumeBasedRenko

**With Open Interest:**
- DerivativesFuturesOI (highly recommended)
- Composite (combine OI + others)

**With Volume Profile:**
- MarketProfileTPO (best choice)

### Recommended Combinations

**Conservative (High Confidence):**
```java
CompositeWyckoffPhaseIdentifier conservative = new CompositeWyckoffPhaseIdentifier(
    derivativesFuturesOI,  // 4.5
    marketProfileTPO,      // 4.5
    structureSwing         // 4.0
);
// Only trade when consensus ≥ 0.8
```

**Balanced (Medium Confidence):**
```java
CompositeWyckoffPhaseIdentifier balanced = new CompositeWyckoffPhaseIdentifier(
    structureSwing,
    classical,
    volumeBased
);
// Trade when consensus ≥ 0.6
```

**Aggressive (Lower Confidence, More Signals):**
```java
// Use single high-reliability identifier
StructureSwingWyckoffPhaseIdentifier aggressive = new StructureSwingWyckoffPhaseIdentifier();
// Trade when confidence ≥ 0.5
```

---

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

---

## Common Pitfalls

1. **Insufficient data** - Always ensure data.size() > getMinimumDataPoints()
2. **Ignoring confidence** - Low confidence = high risk
3. **Wrong timeframe** - Classical tuned for hourly, TPO for intraday
4. **Missing OI data** - DerivativesFuturesOI requires candle.oi()
5. **Forgetting reset()** - Can cause stale state when switching symbols
6. **Over-trading** - Wait for high-confidence phase transitions
7. **Ignoring volume** - Wyckoff heavily relies on volume analysis

---

## Thread Safety

All identifier implementations are **NOT thread-safe** by default. If using across multiple threads:

```java
// Option 1: Synchronize access
synchronized (identifier) {
    WyckoffPhase phase = identifier.identifyPhase(data, index);
}

// Option 2: Create separate instances per thread
ThreadLocal<IWyckoffPhaseIdentifier> threadLocalIdentifier =
    ThreadLocal.withInitial(ClassicalWyckoffPhaseIdentifier::new);
```

---

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
- Consider lazy initialization for factory patterns
- Use composite only when necessary (computational cost multiplies)

---

## Dependencies

This module depends on:
- **fno-models**: Core models (Candle, WyckoffPhase, IWyckoffPhaseIdentifier, WyckoffIndicators)
- **fno-utils**: Utilities (CandleUtils, HeikinAshi, TimeUtils)
- **fno-strategy-utils**: Strategy utilities (HATrendUtils, Point2D, Trend)

External dependencies:
- SLF4J for logging (used by factory)

---

## Future Enhancements

Planned additions:
- Machine Learning-based identifier (ML patterns)
- Tick-level analysis for HFT
- Multi-timeframe analysis
- Automated strategy optimization
- Real-time alerts on phase transitions

---

## See Also

- **fno-models documentation**: `/docs/module-guides/fno-models.md#wyckoff-models`
- **Wyckoff Method Resources**: Classic Wyckoff literature
- **fno-utils documentation**: `/docs/module-guides/fno-utils.md`
- **Integration guide**: `/docs/AI_AGENT_GUIDE.md`
