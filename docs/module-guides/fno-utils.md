# fno-utils - Utility Functions

## Purpose
Utility classes and helper functions for trading operations, data processing, and strategy management.

## Maven Dependency
```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-utils</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

This automatically includes `fno-models` as a transitive dependency.

## Key Utilities

### CandleUtils - Candlestick Operations

**Timeframe Conversion:**
```java
import com.vish.fno.util.CandleUtils;

// Convert 1-minute candles to 15-minute
List<Candlestick> oneMinCandles = // ... your 1-min data
List<Candlestick> fifteenMinCandles = CandleUtils.convertToTimeFrame(
    oneMinCandles,
    "15minute"  // Options: "5minute", "15minute", "30minute", "60minute", "day"
);

// Convert to daily candles
List<Candlestick> dailyCandles = CandleUtils.convertToTimeFrame(oneMinCandles, "day");
```

**Candlestick Pattern Analysis:**
```java
Candlestick candle = candles.get(candles.size() - 1);

// Check candle characteristics
boolean isBullish = CandleUtils.isBullish(candle);
boolean isBearish = CandleUtils.isBearish(candle);

// Get candle measurements
Double bodySize = CandleUtils.getCandleBody(candle);
Double upperWick = CandleUtils.getUpperWick(candle);
Double lowerWick = CandleUtils.getLowerWick(candle);

// Pattern detection
if (CandleUtils.isBullish(candle) && lowerWick > bodySize * 2) {
    System.out.println("Potential hammer pattern");
}
```

**Merge Candles:**
```java
// Merge multiple candles into one
List<Candlestick> candlesToMerge = candles.subList(0, 5);
Candlestick merged = CandleUtils.mergeCandlesticks(candlesToMerge);
```

### TimeUtils - Trading Time Operations

**Market Hours:**
```java
import com.vish.fno.util.TimeUtils;

// Check if market is currently open
if (TimeUtils.isMarketOpen(LocalDateTime.now())) {
    executeStrategy();
}

// Check if specific time is within trading hours (9:15 AM - 3:30 PM IST)
LocalDateTime checkTime = LocalDateTime.of(2024, 9, 28, 10, 30);
boolean isTradingHour = TimeUtils.isTradingHour(checkTime);
```

**Trading Days:**
```java
// Get next trading day (skips weekends and holidays)
LocalDate today = LocalDate.now();
LocalDate nextTradingDay = TimeUtils.getNextTradingDay(today);

// Check if a specific date is a holiday
boolean isHoliday = TimeUtils.isHoliday(LocalDate.of(2024, 10, 2)); // Gandhi Jayanti
```

**Market Timings:**
```java
LocalDate date = LocalDate.of(2024, 9, 28);
LocalDateTime marketOpen = TimeUtils.getMarketOpenTime(date);   // 9:15 AM
LocalDateTime marketClose = TimeUtils.getMarketCloseTime(date); // 3:30 PM
```

### OptionsMetaDataUtils - Options Calculations

**ATM Strike Calculation:**
```java
import com.vish.fno.util.OptionsMetaDataUtils;

// Get At-The-Money strike for NIFTY (strike interval = 50)
double spotPrice = 19537.25;
Double atmStrike = OptionsMetaDataUtils.getATMStrike(spotPrice, 50);
// Returns: 19550.0

// For BANKNIFTY (strike interval = 100)
Double bnfAtmStrike = OptionsMetaDataUtils.getATMStrike(43257.80, 100);
// Returns: 43300.0
```

**Expiry Calculation:**
```java
// Get next weekly expiry (Thursday)
LocalDate nextWeekly = OptionsMetaDataUtils.getNextWeeklyExpiry(LocalDate.now());

// Get next monthly expiry (last Thursday)
LocalDate nextMonthly = OptionsMetaDataUtils.getNextMonthlyExpiry(LocalDate.now());
```

**Option Symbol Generation:**
```java
String optionSymbol = OptionsMetaDataUtils.getOptionSymbol(
    "NIFTY",                        // underlying
    LocalDate.of(2024, 9, 26),      // expiry
    19500.0,                         // strike
    "CE"                             // option type
);
// Returns: "NIFTY24SEP19500CE"
```

### FileUtils - File I/O Operations

**Read/Write Candlestick Data:**
```java
import com.vish.fno.util.FileUtils;

// Write candles to JSON file
List<Candlestick> candles = // ... your data
FileUtils.writeCandlesToFile(candles, "/path/to/data.json");

// Read candles from JSON file
List<Candlestick> loadedCandles = FileUtils.readCandlesFromFile("/path/to/data.json");
```

**Read Instruments from CSV:**
```java
List<Instrument> instruments = FileUtils.readInstrumentsFromCsv("/path/to/instruments.csv");
```

### HeikinAshi - Smoothed Candlesticks

**Convert to Heikin-Ashi:**
```java
import com.vish.fno.util.chart.HeikinAshi;

List<Candlestick> regularCandles = // ... your OHLC data
List<Candlestick> heikinAshiCandles = HeikinAshi.convert(regularCandles);

// Use HA candles for smoother trend identification
Candlestick lastHA = heikinAshiCandles.get(heikinAshiCandles.size() - 1);
if (CandleUtils.isBullish(lastHA)) {
    System.out.println("Strong uptrend confirmed");
}
```

**Heikin-Ashi Formula:**
- HA Close = (Open + High + Low + Close) / 4
- HA Open = (Previous HA Open + Previous HA Close) / 2
- HA High = Max(High, HA Open, HA Close)
- HA Low = Min(Low, HA Open, HA Close)

### FixedTargetAndStopLossStrategy - Risk Management

**Basic Usage:**
```java
import com.vish.fno.util.orderflow.FixedTargetAndStopLossStrategy;

// Create strategy: Target=100 points, Stop-loss=50 points
FixedTargetAndStopLossStrategy riskStrategy =
    new FixedTargetAndStopLossStrategy(100.0, 50.0);

double entryPrice = 19500.0;

// For BUY orders
double targetBuy = riskStrategy.calculateTarget(entryPrice, "BUY");      // 19600.0
double stopLossBuy = riskStrategy.calculateStopLoss(entryPrice, "BUY");  // 19450.0

// For SELL orders (reverses)
double targetSell = riskStrategy.calculateTarget(entryPrice, "SELL");      // 19400.0
double stopLossSell = riskStrategy.calculateStopLoss(entryPrice, "SELL");  // 19550.0
```

**Integration with Orders:**
```java
public class TradingService {
    private final FixedTargetAndStopLossStrategy tsl =
        new FixedTargetAndStopLossStrategy(100.0, 50.0);

    public void placeOrderWithExits(double entryPrice, String transactionType) {
        // Place entry order
        IndexOrderRequest entry = IndexOrderRequest.builder()
            .symbol("NIFTY24SEPFUT")
            .quantity(50)
            .orderType("MARKET")
            .transactionType(transactionType)
            .product("MIS")
            .build();

        String orderId = broker.placeOrder(entry);

        // Calculate exit levels
        double target = tsl.calculateTarget(entryPrice, transactionType);
        double stopLoss = tsl.calculateStopLoss(entryPrice, transactionType);

        // Place target order
        IndexOrderRequest targetOrder = IndexOrderRequest.builder()
            .symbol("NIFTY24SEPFUT")
            .quantity(50)
            .orderType("LIMIT")
            .price(target)
            .transactionType(transactionType.equals("BUY") ? "SELL" : "BUY")
            .product("MIS")
            .build();

        // Place stop-loss order
        IndexOrderRequest slOrder = IndexOrderRequest.builder()
            .symbol("NIFTY24SEPFUT")
            .quantity(50)
            .orderType("SL-M")
            .triggerPrice(stopLoss)
            .transactionType(transactionType.equals("BUY") ? "SELL" : "BUY")
            .product("MIS")
            .build();

        broker.placeOrder(targetOrder);
        broker.placeOrder(slOrder);
    }
}
```

### CompressionUtils - Data Compression

**Compress/Decompress Market Data:**
```java
import com.vish.fno.util.CompressionUtils;

// Compress data for storage
byte[] compressed = CompressionUtils.compress(largeDataString);

// Decompress for processing
String original = CompressionUtils.decompress(compressed);
```

## Common Patterns

### Pattern 1: Multi-Timeframe Analysis
```java
public class MultiTimeframeAnalyzer {
    public boolean isStrongTrend(List<Candlestick> oneMinData) {
        // Convert to multiple timeframes
        List<Candlestick> fifteenMin = CandleUtils.convertToTimeFrame(oneMinData, "15minute");
        List<Candlestick> thirtyMin = CandleUtils.convertToTimeFrame(oneMinData, "30minute");
        List<Candlestick> hourly = CandleUtils.convertToTimeFrame(oneMinData, "60minute");

        // Check all timeframes for bullish trend
        boolean bullish15 = CandleUtils.isBullish(fifteenMin.get(fifteenMin.size() - 1));
        boolean bullish30 = CandleUtils.isBullish(thirtyMin.get(thirtyMin.size() - 1));
        boolean bullish60 = CandleUtils.isBullish(hourly.get(hourly.size() - 1));

        return bullish15 && bullish30 && bullish60;
    }
}
```

### Pattern 2: Market Hours Validation
```java
@Service
public class StrategyExecutor {
    public void execute() {
        if (!TimeUtils.isMarketOpen(LocalDateTime.now())) {
            logger.info("Market is closed. Skipping execution.");
            return;
        }

        if (TimeUtils.isHoliday(LocalDate.now())) {
            logger.info("Today is a holiday. Skipping execution.");
            return;
        }

        // Execute strategy
        runTradingLogic();
    }
}
```

### Pattern 3: Options Strategy Builder
```java
public class OptionsStrategyBuilder {
    public OptionBasedOrderRequest buildStraddleOrder(String underlying, double spot) {
        // Get ATM strike
        int strikeInterval = underlying.equals("NIFTY") ? 50 : 100;
        Double atmStrike = OptionsMetaDataUtils.getATMStrike(spot, strikeInterval);

        // Get next weekly expiry
        LocalDate expiry = OptionsMetaDataUtils.getNextWeeklyExpiry(LocalDate.now());

        // Build call order
        return OptionBasedOrderRequest.builder()
            .symbol(underlying)
            .strikePrice(atmStrike)
            .optionType("CE")
            .expiryDate(expiry)
            .quantity(underlying.equals("NIFTY") ? 50 : 15)
            .orderType("MARKET")
            .transactionType("SELL")
            .product("NRML")
            .build();
    }
}
```

### Pattern 4: Data Preprocessing with Heikin-Ashi
```java
public class TrendIdentifier {
    public String identifyTrend(List<Candlestick> candles) {
        // Convert to Heikin-Ashi for smoother trend
        List<Candlestick> haCandles = HeikinAshi.convert(candles);

        // Check last 3 HA candles
        int size = haCandles.size();
        boolean allBullish = IntStream.range(size - 3, size)
            .mapToObj(haCandles::get)
            .allMatch(CandleUtils::isBullish);

        boolean allBearish = IntStream.range(size - 3, size)
            .mapToObj(haCandles::get)
            .allMatch(CandleUtils::isBearish);

        if (allBullish) return "STRONG_UPTREND";
        if (allBearish) return "STRONG_DOWNTREND";
        return "SIDEWAYS";
    }
}
```

## Best Practices

1. **Cache converted timeframes** instead of recalculating
2. **Always check market hours** before placing orders
3. **Use Heikin-Ashi** for trend identification, not exact entry/exit
4. **Validate file paths** before reading/writing
5. **Use proper strike intervals** (NIFTY=50, BANKNIFTY=100)

## Thread Safety

- All utility methods are **static** and thread-safe
- **CandleUtils, TimeUtils, OptionsMetaDataUtils**: Safe for concurrent use
- **FileUtils**: Use synchronization if multiple threads write to same file

## Performance Considerations

- **Timeframe conversion**: O(n) complexity, cache results
- **Heikin-Ashi conversion**: O(n), processes sequentially
- **File operations**: I/O bound, consider async for large files
