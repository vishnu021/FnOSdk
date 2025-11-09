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

### CandleUtils - Candle Operations

**Timeframe Conversion:**
```java
import com.vish.fno.util.CandleUtils;
import com.vish.fno.model.Candle;

// Convert 1-minute candles to 15-minute
List<Candle> oneMinCandles = // ... your 1-min data
List<Candle> fifteenMinCandles = CandleUtils.convertToTimeFrame(
    oneMinCandles,
    "15minute"  // Options: "5minute", "15minute", "30minute", "60minute", "day"
);

// Convert to daily candles
List<Candle> dailyCandles = CandleUtils.convertToTimeFrame(oneMinCandles, "day");
```

**Candle Pattern Analysis:**
```java
import com.vish.fno.util.CandleUtils;
import com.vish.fno.model.Candle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PatternDetector {
    public void analyzePattern(List<Candle> candles) {
        Candle candle = candles.get(candles.size() - 1);

        // Check candle characteristics
        boolean isBullish = CandleUtils.isBullish(candle);
        boolean isBearish = CandleUtils.isBearish(candle);

        // Get candle measurements
        Double bodySize = CandleUtils.getCandleBody(candle);
        Double upperWick = CandleUtils.getUpperWick(candle);
        Double lowerWick = CandleUtils.getLowerWick(candle);

        // Pattern detection
        if (CandleUtils.isBullish(candle) && lowerWick > bodySize * 2) {
            log.info("Potential hammer pattern detected");
        }
    }
}
```

**Merge Candles:**
```java
import com.vish.fno.util.CandleUtils;
import com.vish.fno.model.Candle;

// Merge multiple candles into one
List<Candle> candlesToMerge = candles.subList(0, 5);
Candle merged = CandleUtils.mergeCandlesticks(candlesToMerge);
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

**Read/Write Candle Data:**
```java
import com.vish.fno.util.FileUtils;
import com.vish.fno.model.Candle;

// Write candles to JSON file
List<Candle> candles = // ... your data
FileUtils.writeCandlesToFile(candles, "/path/to/data.json");

// Read candles from JSON file
List<Candle> loadedCandles = FileUtils.readCandlesFromFile("/path/to/data.json");
```

**Read Instruments from CSV:**
```java
List<Instrument> instruments = FileUtils.readInstrumentsFromCsv("/path/to/instruments.csv");
```

### HeikinAshi - Smoothed Candles

**Convert to Heikin-Ashi:**
```java
import com.vish.fno.util.chart.HeikinAshi;
import com.vish.fno.util.CandleUtils;
import com.vish.fno.model.Candle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TrendAnalyzer {
    public void analyzeTrend(List<Candle> regularCandles) {
        List<Candle> heikinAshiCandles = HeikinAshi.convert(regularCandles);

        // Use HA candles for smoother trend identification
        Candle lastHA = heikinAshiCandles.get(heikinAshiCandles.size() - 1);
        if (CandleUtils.isBullish(lastHA)) {
            log.info("Strong uptrend confirmed");
        }
    }
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

### Trend - Market Trend Classification

**Enum Values:**
```java
public enum Trend {
    WEAK_UPTREND,
    UPTREND,
    STRONG_UPTREND,
    WEAK_DOWNTREND,
    DOWNTREND,
    STRONG_DOWNTREND,
    INDECISIVE,
    SIDEWAYS,
    ACCUMULATION,
    DISTRIBUTION,
    CONSOLIDATION,
    BREAKOUT,
    REVERSAL
}
```

**Usage:**
```java
import com.vish.fno.util.Trend;
import com.vish.fno.model.Candle;

public class TrendAnalyzer {
    public Trend identifyTrend(List<Candle> candles) {
        // Analyze candles and determine trend
        Candle latest = candles.get(candles.size() - 1);
        Candle previous = candles.get(candles.size() - 2);

        if (latest.close() > previous.close()) {
            double change = ((latest.close() - previous.close()) / previous.close()) * 100;
            if (change > 2.0) return Trend.STRONG_UPTREND;
            if (change > 0.5) return Trend.UPTREND;
            return Trend.WEAK_UPTREND;
        } else if (latest.close() < previous.close()) {
            double change = ((previous.close() - latest.close()) / previous.close()) * 100;
            if (change > 2.0) return Trend.STRONG_DOWNTREND;
            if (change > 0.5) return Trend.DOWNTREND;
            return Trend.WEAK_DOWNTREND;
        }

        return Trend.SIDEWAYS;
    }

    public boolean isBullishTrend(Trend trend) {
        return trend == Trend.WEAK_UPTREND ||
               trend == Trend.UPTREND ||
               trend == Trend.STRONG_UPTREND;
    }

    public boolean isBearishTrend(Trend trend) {
        return trend == Trend.WEAK_DOWNTREND ||
               trend == Trend.DOWNTREND ||
               trend == Trend.STRONG_DOWNTREND;
    }
}
```

### JsonUtils - JSON Serialization

**Convert Objects to JSON:**
```java
import com.vish.fno.util.JsonUtils;
import com.vish.fno.model.order.orderrequest.IndexOrderRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrderLogger {
    public void logOrder(IndexOrderRequest order) {
        // Get formatted JSON (pretty-printed)
        String formattedJson = JsonUtils.getFormattedObject(order);
        log.info("Order: {}", formattedJson);
        /*
        {
          "symbol" : "NIFTY24SEPFUT",
          "quantity" : 50,
          "orderType" : "MARKET",
          "transactionType" : "BUY",
          "product" : "MIS"
        }
        */

        // Get non-formatted JSON (compact)
        String compactJson = JsonUtils.getNonFormattedObject(order);
        // {"symbol":"NIFTY24SEPFUT","quantity":50,...}

        // Store in database or log file
        saveToDatabase(compactJson);
    }
}
```

**Thread Safety:** Both methods are thread-safe and use static ObjectMapper.

### TimeFrameUtils - Timeframe Conversion

**Merge Candles by Timeframe:**
```java
import com.vish.fno.util.TimeFrameUtils;
import com.vish.fno.model.Candle;

public class DataProcessor {
    public void convertTimeframes() {
        List<Candle> oneMinCandles = // ... 1-minute candles

        // Merge 5 one-minute candles into one 5-minute candle
        List<Candle> fiveMinCandles = TimeFrameUtils.mergeCandle(oneMinCandles, 5);

        // Merge 15 one-minute candles into one 15-minute candle
        List<Candle> fifteenMinCandles = TimeFrameUtils.mergeCandle(oneMinCandles, 15);

        // Each merged candle contains:
        // - open: First candle's open
        // - high: Highest of all candles
        // - low: Lowest of all candles
        // - close: Last candle's close
        // - volume: Sum of all volumes
        // - oi: Sum of all open interests
    }
}
```

**Intraday Complete Candles Only:**
```java
import com.vish.fno.util.TimeFrameUtils;
import com.vish.fno.model.Candle;

public class BacktestDataLoader {
    public List<Candle> loadCompleteCandles(List<Candle> rawData, int n) {
        // Only returns complete sets of 'n' candles
        // Drops incomplete last group
        List<Candle> completeCandles =
            TimeFrameUtils.mergeIntradayCompleteCandle(rawData, n);

        // Example: If rawData has 377 1-min candles
        // mergeIntradayCompleteCandle(rawData, 15) returns 25 complete 15-min candles
        // (375 candles used, 2 dropped)

        return completeCandles;
    }
}
```

**Combine Multiple Candles:**
```java
import com.vish.fno.util.TimeFrameUtils;
import com.vish.fno.model.Candle;

public class CandleMerger {
    public Candle mergeHourlyCandle(List<Candle> fifteenMinCandles) {
        // Take 4 fifteen-minute candles
        List<Candle> fourCandles = fifteenMinCandles.subList(0, 4);

        // Combine into single hourly candle
        Candle hourlyCandle = TimeFrameUtils.combine(fourCandles);

        // Returns null if input is null or empty
        // Returns single candle if input has only 1 candle
        return hourlyCandle;
    }
}
```

**Thread Safety:** All TimeFrameUtils methods are stateless and thread-safe.

## Common Patterns

### Pattern 1: Multi-Timeframe Analysis
```java
import com.vish.fno.util.CandleUtils;
import com.vish.fno.model.Candle;

public class MultiTimeframeAnalyzer {
    public boolean isStrongTrend(List<Candle> oneMinData) {
        // Convert to multiple timeframes
        List<Candle> fifteenMin = CandleUtils.convertToTimeFrame(oneMinData, "15minute");
        List<Candle> thirtyMin = CandleUtils.convertToTimeFrame(oneMinData, "30minute");
        List<Candle> hourly = CandleUtils.convertToTimeFrame(oneMinData, "60minute");

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
import com.vish.fno.util.chart.HeikinAshi;
import com.vish.fno.util.CandleUtils;
import com.vish.fno.model.Candle;
import java.util.stream.IntStream;

public class TrendIdentifier {
    public String identifyTrend(List<Candle> candles) {
        // Convert to Heikin-Ashi for smoother trend
        List<Candle> haCandles = HeikinAshi.convert(candles);

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
