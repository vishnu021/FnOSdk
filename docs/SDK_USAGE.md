# FnOSdk Usage Guide

## For AI Agents and Developers

This document serves as the entry point for consuming FnOSdk modules in your trading applications.

**Requirements:** Java 21+, Spring Boot 3.4.1

## Knowledge Paths Structure

```
docs/module-guides/
├── fno-models.md          - Core data models (orders, candles, instruments, Task interface)
├── fno-utils.md           - Utility functions (candle ops, time utils, price utils, order formatting)
├── fno-technicals.md      - Technical indicators and Greeks
├── fno-kite-reader.md     - Kite Connect API integration (diagnostics, WebSocket)
├── fno-strategy-utils.md  - Strategy utilities (trend analysis, CPR, price action)
└── fno-phase-analyzer.md  - Wyckoff phase identification and market regime analysis
```

**For AI Coding Assistants**: Each module guide contains:
- Complete API reference with method signatures
- Working code examples for every feature
- Common integration patterns
- Best practices and error handling

## Quick Reference

### When to use which module?

| Use Case | Modules Needed | Reference |
|----------|---------------|-----------|
| **Backtesting** | fno-models, fno-utils, fno-technicals | [fno-technicals.md](module-guides/fno-technicals.md) |
| **Live Trading** | All modules | [fno-kite-reader.md](module-guides/fno-kite-reader.md) |
| **Technical Analysis Only** | fno-models, fno-technicals | [fno-technicals.md](module-guides/fno-technicals.md) |
| **Data Processing** | fno-models, fno-utils | [fno-utils.md](module-guides/fno-utils.md) |
| **Order Management** | fno-models, fno-kite-reader | [fno-models.md](module-guides/fno-models.md) |
| **Trend Analysis & CPR** | fno-models, fno-utils, fno-strategy-utils | [fno-strategy-utils.md](module-guides/fno-strategy-utils.md) |
| **Price Action Trading** | fno-models, fno-utils, fno-strategy-utils | [fno-strategy-utils.md](module-guides/fno-strategy-utils.md) |
| **Partial Profit Booking** | fno-models, fno-utils, fno-strategy-utils | [fno-strategy-utils.md](module-guides/fno-strategy-utils.md) |
| **Wyckoff Phase Analysis** | fno-models, fno-utils, fno-phase-analyzer | [fno-phase-analyzer.md](module-guides/fno-phase-analyzer.md) |
| **Market Regime Detection** | fno-models, fno-utils, fno-phase-analyzer | [fno-phase-analyzer.md](module-guides/fno-phase-analyzer.md) |
| **Multi-Lot Strategy** | fno-models (Task.getLots()) | [fno-models.md](module-guides/fno-models.md#task-interface) |
| **Position Sizing** | fno-utils (PositionSizingService) | [fno-utils.md](module-guides/fno-utils.md#position-sizing-package-new) |

## Module Dependencies

```
fno-kite-reader
    └── fno-utils
        └── fno-models (foundation)

fno-technicals
    ├── fno-utils
    │   └── fno-models
    └── fno-models

fno-strategy-utils
    ├── fno-technicals
    │   ├── fno-utils
    │   │   └── fno-models
    │   └── fno-models
    ├── fno-utils
    │   └── fno-models
    └── fno-models

fno-phase-analyzer
    ├── fno-strategy-utils
    │   └── (all fno-strategy-utils dependencies)
    ├── fno-technicals
    │   └── (all fno-technicals dependencies)
    ├── fno-utils
    │   └── fno-models
    └── fno-models
```

**Rule**: Adding fno-kite-reader, fno-phase-analyzer, or fno-strategy-utils automatically includes all dependencies.

## Installation

### Maven Dependencies

```xml
<dependencies>
    <!-- For backtesting/simulation -->
    <dependency>
        <groupId>com.vish.fno</groupId>
        <artifactId>fno-models</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>com.vish.fno</groupId>
        <artifactId>fno-technicals</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- For advanced strategies (CPR, trend analysis, price action) -->
    <dependency>
        <groupId>com.vish.fno</groupId>
        <artifactId>fno-strategy-utils</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- For Wyckoff phase analysis and market regime detection -->
    <dependency>
        <groupId>com.vish.fno</groupId>
        <artifactId>fno-phase-analyzer</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- For live trading (includes all above) -->
    <dependency>
        <groupId>com.vish.fno</groupId>
        <artifactId>fno-kite-reader</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### Local Installation

```bash
cd /Users/vishnushankar/workspace/FnOSdk
mvn clean install
```

## Common Scenarios

### Scenario 1: Calculate RSI for NIFTY

**Read**: [fno-technicals.md - RSI section](module-guides/fno-technicals.md#relative-strength-index-rsi)

```java
import com.vish.fno.technical.indicators.RelativeStrengthIndex;

RelativeStrengthIndex rsi14 = new RelativeStrengthIndex(14);
List<Double> rsiValues = rsi14.calculate(candles);
```

### Scenario 2: Create Index Order Request

**Read**: [fno-models.md - IndexOrderRequest](module-guides/fno-models.md#indexorderrequest)

```java
import com.vish.fno.model.order.orderrequest.IndexOrderRequest;
import com.vish.fno.model.Task;
import java.util.Date;

Task task = Task.builder().enabled(true).expiryDayOrders(true).build();
IndexOrderRequest order = IndexOrderRequest.builder("STRATEGY_1", "NIFTY", task)
    .optionSymbol("NIFTY24SEPFUT")
    .date(new Date())
    .timestamp(915)
    .expirationTimestamp(1530)
    .buyThreshold(19500.0)
    .target(19600.0)
    .stopLoss(19450.0)
    .callOrder(true)
    .build();
```

### Scenario 3: Convert Minute Candles to 15-Minute

**Read**: [fno-utils.md - TimeFrameUtils](module-guides/fno-utils.md#timeframeutils)

```java
import com.vish.fno.util.candle.TimeFrameUtils;
import com.vish.fno.model.Candle;

List<Candle> fifteenMin = TimeFrameUtils.mergeCandle(oneMinCandles, 15);
```

### Scenario 4: Calculate Option Greeks

**Read**: [fno-technicals.md - Options Greeks](module-guides/fno-technicals.md#options-greeks)

```java
import com.vish.fno.technical.greeks.Delta;
import com.vish.fno.technical.greeks.Theta;

double delta = Delta.calculateDelta(spot, strike, tte, rfr, iv, true);  // true for call
double theta = Theta.calculateTheta(spot, strike, tte, rfr, iv, true);  // true for call
```

### Scenario 5: Fetch Historical Data from Kite

**Read**: [fno-kite-reader.md - Historical Data](module-guides/fno-kite-reader.md#historical-data)

```java
import com.vish.fno.reader.core.KiteService;

Optional<HistoricalData> data = kiteService.getHistoricalData(from, to, "NIFTY 50", "minute", false);
data.ifPresent(d -> log.info("Candles: {}", d.dataArrayList.size()));
```

### Scenario 6: Detect Heikin-Ashi Trend

**Read**: [fno-strategy-utils.md - HATrendUtils](module-guides/fno-strategy-utils.md#hatrendutils---heikin-ashi-trend-analysis)

```java
import com.vish.fno.strategy.HATrendUtils;
import com.vish.fno.util.Trend;

Trend currentTrend = HATrendUtils.getTrend(candles);

if (currentTrend == Trend.UPTREND) {
    // Consider call entries
}
```

### Scenario 7: Calculate CPR Levels

**Read**: [fno-strategy-utils.md - CPRUtils](module-guides/fno-strategy-utils.md#cprutils---central-pivot-range-calculations)

```java
import com.vish.fno.strategy.util.CPRUtils;

Map<String, Float> pivots = CPRUtils.getFloorPivots(previousDayCandle);

float pivot = pivots.get("pivotPoint");
float cprWidth = pivots.get("topCentralPivot") - pivots.get("bottomCentralPivot");
```

### Scenario 8: Partial Profit Booking with Dynamic Stop Loss

**Read**: [fno-strategy-utils.md - PartialRevisingStopLoss](module-guides/fno-strategy-utils.md#partialrevisingstoploss---dynamic-stop-loss-strategy)

```java
import com.vish.fno.strategy.orderflow.PartialRevisingStopLoss;
import com.vish.fno.model.order.OrderSellDetailModel;

PartialRevisingStopLoss strategy = new PartialRevisingStopLoss(dataCache);
OrderSellDetailModel sellDecision = strategy.isTargetAchieved(order, currentPrice);

if (sellDecision.isSell()) {
    // Execute partial or full exit
}
```

### Scenario 9: Identify Wyckoff Market Phase

**Read**: [fno-phase-analyzer.md - Phase Identification](module-guides/fno-phase-analyzer.md#example-usage-pattern-applies-to-all-identifiers)

```java
import com.vish.fno.phase.factory.WyckoffPhaseIdentifierFactory;
import com.vish.fno.phase.factory.WyckoffIdentifierType;
import com.vish.fno.model.wyckoff.WyckoffPhase;

WyckoffPhaseIdentifierFactory factory = new WyckoffPhaseIdentifierFactory();
IWyckoffPhaseIdentifier identifier = factory.getIdentifier(WyckoffIdentifierType.STRUCTURE_SWING);

WyckoffPhase phase = identifier.identifyPhase(candles, candles.size() - 1);
double confidence = identifier.getPhaseConfidence(candles, candles.size() - 1);

if (confidence > 0.7 && phase.isAccumulation()) {
    // Consider long positions
}
```

### Scenario 10: Configure Multi-Lot Trading

**Read**: [fno-models.md - Task Interface](module-guides/fno-models.md#task-interface)

```java
import com.vish.fno.model.Task;

// Implement Task interface with getLots() for multi-lot trading
public class TradingTask implements Task {
    private final int lots;  // Number of lots multiplier

    @Override
    public int getLots() {
        return lots > 0 ? lots : 1;  // Default to 1
    }
}

// Usage: total quantity = task.getLots() * lotSize
int quantity = tradingTask.getLots() * 50;  // 3 lots * 50 = 150
```

## For Project Integration

### Add to your project's CLAUDE.md:

```markdown
## SDK Reference: FnOSdk

This project uses FnOSdk for trading operations.

**Knowledge Paths**: `/Users/vishnushankar/workspace/FnOSdk/docs/module-guides/`

**Quick Reference**:
- Technical indicators → [fno-technicals.md](../FnOSdk/docs/module-guides/fno-technicals.md)
- Order models → [fno-models.md](../FnOSdk/docs/module-guides/fno-models.md)
- Utility functions → [fno-utils.md](../FnOSdk/docs/module-guides/fno-utils.md)
- Kite Connect API → [fno-kite-reader.md](../FnOSdk/docs/module-guides/fno-kite-reader.md)
- Strategy utilities → [fno-strategy-utils.md](../FnOSdk/docs/module-guides/fno-strategy-utils.md)
- Wyckoff phases → [fno-phase-analyzer.md](../FnOSdk/docs/module-guides/fno-phase-analyzer.md)

When generating code using FnOSdk modules, reference the appropriate guide above for:
- Exact API signatures
- Working code examples
- Integration patterns
- Error handling
```

## AI Agent Instructions

When a user asks to use FnOSdk functionality:

1. **Identify the module** needed (refer to "Quick Reference" table above)
2. **Read the relevant guide** from `docs/module-guides/`
3. **Use exact API signatures** from the guide
4. **Include error handling** as shown in examples
5. **Follow integration patterns** for the use case

### Example AI Workflow:

**User Request**: "Calculate 20-period SMA for NIFTY data"

**AI Actions**:
1. Identifies need for technical indicator → `fno-technicals`
2. Reads `/Users/vishnushankar/workspace/FnOSdk/docs/module-guides/fno-technicals.md`
3. Finds SMA section with exact API
4. Generates code:
```java
import com.vish.fno.technical.indicators.ma.SimpleMovingAverage;

SimpleMovingAverage sma20 = new SimpleMovingAverage(20);
List<Double> smaValues = sma20.calculate(candles);
```

## Support

- **Development Guide**: See `/Users/vishnushankar/workspace/FnOSdk/CLAUDE.md` for working ON FnOSdk
- **Module Guides**: See `docs/module-guides/*.md` for USING FnOSdk
- **Build Issues**: Check FnOSdk/README.md

## Version

Current version: **1.0.0-SNAPSHOT**

## License

[Specify license]
