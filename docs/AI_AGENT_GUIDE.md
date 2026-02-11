# AI Agent Guide for FnOSdk

## Overview

FnOSdk provides comprehensive documentation designed specifically for AI coding assistants (Claude Code, GitHub Copilot, etc.) to help them understand and use the SDK modules correctly when working on trading applications.

## Documentation Structure

### 1. Entry Point: `SDK_USAGE.md`

Located in `docs/`, this document provides:
- Quick reference table for common use cases
- Module dependency structure
- Installation instructions
- Links to detailed module guides

### 2. Module-Specific Guides: `docs/module-guides/`

Each module has a comprehensive guide with complete API documentation:
- **fno-models.md** - Core data models (orders, candles, instruments, Task interface with getLots())
- **fno-utils.md** - Utility functions (candle ops, time utils, price utils, order formatting via FileUtils)
- **fno-technicals.md** - Technical indicators and Greeks
- **fno-kite-reader.md** - Kite Connect API integration (core package: KiteService, KiteWebSocket, InstrumentCache)
- **fno-strategy-utils.md** - Strategy utilities (trend analysis, CPR, price action, partial profit booking)
- **fno-phase-analyzer.md** - Wyckoff phase identification and market regime detection

### 3. Developer Guide: `CLAUDE.md`

Located at the root, this is for **FnOSdk development only** (not for SDK consumers):
- Build commands and testing
- Module architecture
- Code quality standards (PMD rules)
- Java 21 patterns and conventions

**How AI agents should use documentation:**
When an AI agent needs to:
- Create order requests → Read `docs/module-guides/fno-models.md`
- Calculate technical indicators → Read `docs/module-guides/fno-technicals.md`
- Integrate Kite API → Read `docs/module-guides/fno-kite-reader.md`
- Use utilities → Read `docs/module-guides/fno-utils.md`
- Trend analysis / CPR → Read `docs/module-guides/fno-strategy-utils.md`
- Wyckoff phase identification → Read `docs/module-guides/fno-phase-analyzer.md`
- Multi-lot strategy configuration → Read `docs/module-guides/fno-models.md#task-interface`

### 4. Project-Specific Integration

For projects using FnOSdk, reference module guides directly:

```markdown
## SDK Dependencies: FnOSdk

**SDK Location**: `/path/to/FnOSdk/`
**Documentation**: `/path/to/FnOSdk/docs/`

**Module Guides** (for AI agents):
- Technical indicators → `/path/to/FnOSdk/docs/module-guides/fno-technicals.md`
- Order models → `/path/to/FnOSdk/docs/module-guides/fno-models.md`
- Utilities → `/path/to/FnOSdk/docs/module-guides/fno-utils.md`
- Kite integration → `/path/to/FnOSdk/docs/module-guides/fno-kite-reader.md`
- Strategy utilities → `/path/to/FnOSdk/docs/module-guides/fno-strategy-utils.md`
- Wyckoff phases → `/path/to/FnOSdk/docs/module-guides/fno-phase-analyzer.md`

When generating code using FnOSdk, AI agents should read the specific module guide for exact API signatures and examples.
```

---

## Key Classes Quick Reference

| Class | Package | Purpose |
|-------|---------|---------|
| `Candle` | `com.vish.fno.model` | OHLCV record (7 fields) |
| `Ticker` | `com.vish.fno.model` | Real-time tick record (21 fields) |
| `OrderRequest` | `com.vish.fno.model.order.orderrequest` | Order creation interface |
| `ActiveOrder` | `com.vish.fno.model.order.activeorder` | Active trade tracking interface |
| `CandleUtils` | `com.vish.fno.util` | Static candle analysis methods |
| `PriceUtils` | `com.vish.fno.util` | Price rounding and formatting |
| `FileUtils` | `com.vish.fno.util` | File I/O and order formatting (CSV, logs) |
| `TimeUtils` | `com.vish.fno.util` | Date/time and trading hours |
| `SimpleMovingAverage` | `com.vish.fno.technical.indicators.ma` | SMA indicator |
| `RelativeStrengthIndex` | `com.vish.fno.technical.indicators` | RSI indicator |
| `KiteService` | `com.vish.fno.reader.core` | Main Kite API facade |
| `KiteWebSocket` | `com.vish.fno.reader.core` | Real-time tick streaming |
| `InstrumentCache` | `com.vish.fno.reader.core` | Symbol/token/exchange mapping |
| `HATrendUtils` | `com.vish.fno.strategy` | Heikin-Ashi trend analysis |
| `CPRUtils` | `com.vish.fno.strategy.util` | Central Pivot Range calculations |
| `PartialRevisingStopLoss` | `com.vish.fno.strategy.orderflow` | Dynamic stop-loss with partial profit |
| `WyckoffPhaseIdentifierFactory` | `com.vish.fno.phase.factory` | Wyckoff phase identifier factory |

---

## How AI Agents Should Use This Documentation

### Scenario 1: User asks to implement a moving average crossover strategy

**AI Agent Action:**
1. Identify need for technical indicator → requires `fno-technicals` module
2. Read `docs/module-guides/fno-technicals.md`
3. Find "Simple Moving Average (SMA)" section with API reference
4. Generate code using the documented pattern

**Result:**
```java
import com.vish.fno.technical.indicators.ma.SimpleMovingAverage;

SimpleMovingAverage sma20 = new SimpleMovingAverage(20);
List<Double> smaValues = sma20.calculate(candles);
```

### Scenario 2: User asks to place a futures order via Kite

**AI Agent Action:**
1. Read `docs/module-guides/fno-models.md` for IndexOrderRequest
2. Read `docs/module-guides/fno-kite-reader.md` for KiteService
3. Generate code using Builder pattern as documented

**Result:**
```java
import com.vish.fno.model.order.orderrequest.IndexOrderRequest;
import com.vish.fno.reader.core.KiteService;

IndexOrderRequest order = IndexOrderRequest.builder("TAG", "NIFTY", task)
    .optionSymbol("NIFTY24OCT19500CE")
    .buyThreshold(19500.0).target(19600.0).stopLoss(19450.0)
    .callOrder(true).build();

Optional<KiteOpenOrder> result = kiteService.buyOrder(symbol, qty, tag, true);
```

### Scenario 3: User asks to calculate option Greeks

**AI Agent Action:**
1. Read `docs/module-guides/fno-technicals.md`
2. Find "Options Greeks" section with Delta, Gamma, Theta, Vega

**Result:**
```java
import com.vish.fno.technical.greeks.Delta;

double delta = Delta.calculateDelta(spot, strike, tte, rfr, iv, true); // true for call
```

### Scenario 4: User asks for CPR levels and trend analysis

**AI Agent Action:**
1. Read `docs/module-guides/fno-strategy-utils.md`
2. Find CPRUtils and HATrendUtils sections

**Result:**
```java
import com.vish.fno.strategy.util.CPRUtils;
import com.vish.fno.strategy.HATrendUtils;

Map<String, Float> pivots = CPRUtils.getFloorPivots(previousDayCandle);
Trend currentTrend = HATrendUtils.getTrend(candles);
```

### Scenario 5: User asks for Wyckoff phase identification

**AI Agent Action:**
1. Read `docs/module-guides/fno-phase-analyzer.md`

**Result:**
```java
import com.vish.fno.phase.factory.WyckoffPhaseIdentifierFactory;
import com.vish.fno.phase.factory.WyckoffIdentifierType;

WyckoffPhaseIdentifierFactory factory = new WyckoffPhaseIdentifierFactory();
IWyckoffPhaseIdentifier identifier = factory.getIdentifier(WyckoffIdentifierType.STRUCTURE_SWING);
WyckoffPhase phase = identifier.identifyPhase(candles, candles.size() - 1);
```

---

## Important Conventions

### Code Style
- **Logging:** Always use Lombok `@Slf4j` with `log.info()`, `log.debug()` - never `System.out.println()`
- **Imports:** No wildcard imports (`import java.util.*`) - always explicit
- **Optional:** Use `Optional` for methods that may fail - never `.orElse(null)`
- **Java 21:** Use pattern matching switch, records, `.toList()` (not `.collect(Collectors.toList())`)
- **Thread safety:** `ConcurrentHashMap` for shared maps, `CopyOnWriteArrayList` for read-heavy lists

### Module Dependency Order
```
fno-models (foundation) → fno-utils → fno-technicals → fno-kite-reader → fno-strategy-utils → fno-phase-analyzer
```

Adding a higher-level module automatically includes all its dependencies.

---

## Testing AI Agent Understanding

To verify an AI agent correctly uses the documentation, test with:

| Prompt | Expected Usage |
|--------|---------------|
| "Calculate 20-period SMA" | `new SimpleMovingAverage(20)` + `calculate(candles)` |
| "Place a market order for NIFTY" | `IndexOrderRequest.builder()` with correct fields |
| "Calculate delta for ATM call" | `Delta.calculateDelta(spot, strike, tte, rfr, iv, true)` |
| "Get historical data from Kite" | `kiteService.getHistoricalData(from, to, symbol, "minute", false)` |
| "Format order as CSV" | `FileUtils.toCSV(order)` and `FileUtils.csvHeader(order)` |
| "Round price to 5 paise" | `PriceUtils.roundTo5Paise(price)` |
| "Detect Wyckoff phase" | `WyckoffPhaseIdentifierFactory` + `identifyPhase()` |

---

## Summary

FnOSdk's **modular documentation** (`docs/module-guides/*.md`) serves as a comprehensive knowledge base that AI agents can query to generate correct, production-ready code. The documentation is:

- **Complete**: Every public API documented in its module guide
- **Practical**: Every feature has working, tested examples
- **Contextual**: Integration patterns show real-world usage across modules
- **Maintainable**: Updated when code changes (via `.claude/agents/fnosdk-doc-watcher`)
- **AI-Optimized**: Exact signatures, parameter types, and return values for code generation

**Key Documentation Files:**
- `docs/SDK_USAGE.md` - Entry point with quick reference
- `docs/module-guides/fno-models.md` - Order models, market data structures, Task interface
- `docs/module-guides/fno-utils.md` - Utility functions (PriceUtils, FileUtils, CandleUtils, TimeUtils)
- `docs/module-guides/fno-technicals.md` - Technical indicators and Greeks
- `docs/module-guides/fno-kite-reader.md` - Kite Connect API integration (core package)
- `docs/module-guides/fno-strategy-utils.md` - Strategy utilities (CPR, trend analysis, partial profit booking)
- `docs/module-guides/fno-phase-analyzer.md` - Wyckoff phase identification and market regime detection
