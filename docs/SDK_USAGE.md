# FnOSdk Usage Guide

## For AI Agents and Developers

This document serves as the entry point for consuming FnOSdk modules in your trading applications.

## Knowledge Paths Structure

```
docs/module-guides/
├── fno-models.md          - Core data models (orders, candles, instruments)
├── fno-utils.md           - Utility functions (candle ops, time utils, options utils)
├── fno-technicals.md      - Technical indicators and Greeks
└── fno-kite-reader.md     - Kite Connect API integration
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

## Module Dependencies

```
fno-kite-reader
    └── fno-utils
        └── fno-models (foundation)

fno-technicals
    ├── fno-utils
    │   └── fno-models
    └── fno-models
```

**Rule**: Adding fno-kite-reader or fno-technicals automatically includes all dependencies.

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

### Scenario 2: Place Index Futures Order

**Read**: [fno-models.md - IndexOrderRequest](module-guides/fno-models.md#indexorderrequest---index-futures-orders)

```java
import com.vish.fno.model.order.orderrequest.IndexOrderRequest;

IndexOrderRequest order = IndexOrderRequest.builder()
    .symbol("NIFTY24SEPFUT")
    .quantity(50)
    .orderType("MARKET")
    .transactionType("BUY")
    .product("MIS")
    .build();
```

### Scenario 3: Convert Minute Candles to 15-Minute

**Read**: [fno-utils.md - CandleUtils](module-guides/fno-utils.md#candleutils---candlestick-operations)

```java
import com.vish.fno.util.CandleUtils;

List<Candlestick> fifteenMin = CandleUtils.convertToTimeFrame(oneMinCandles, "15minute");
```

### Scenario 4: Calculate Option Greeks

**Read**: [fno-technicals.md - Options Greeks](module-guides/fno-technicals.md#options-greeks)

```java
import com.vish.fno.technical.greeks.*;

double delta = Delta.calculate(spot, strike, tte, rfr, iv, "CE");
double theta = Theta.calculate(spot, strike, tte, rfr, iv, "CE");
```

### Scenario 5: Fetch Historical Data from Kite

**Read**: [fno-kite-reader.md - HistoricalDataService](module-guides/fno-kite-reader.md#historicaldataservice---historical-candlestick-data)

```java
import com.vish.fno.reader.service.HistoricalDataService;

List<HistoricalData> data = histService.getHistoricalData(
    instrumentToken,
    LocalDate.of(2024, 9, 1),
    LocalDate.of(2024, 9, 30),
    "day"
);
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
