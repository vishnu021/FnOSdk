# fno-models - Core Data Models

## Purpose
Core data models and POJOs for F&O trading operations. This is the foundation module that other modules depend on.

## Maven Dependency
```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-models</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Key Packages

### `com.vish.fno.model.order.orderrequest` - Order Requests

#### IndexOrderRequest - Index Futures Orders
```java
@Data
@Builder
public class IndexOrderRequest implements OrderRequest {
    private String symbol;          // e.g., "NIFTY24SEPFUT", "BANKNIFTY24OCTFUT"
    private Integer quantity;       // Lot size multiple (50 for NIFTY, 15 for BANKNIFTY)
    private String orderType;       // "MARKET", "LIMIT", "SL", "SL-M"
    private Double price;           // Required for LIMIT orders
    private Double triggerPrice;    // Required for SL/SL-M orders
    private String transactionType; // "BUY" or "SELL"
    private String product;         // "MIS" (intraday), "NRML" (delivery)
    private String validity;        // "DAY", "IOC"
}
```

**Usage:**
```java
// Market order
IndexOrderRequest marketOrder = IndexOrderRequest.builder()
    .symbol("NIFTY24SEPFUT")
    .quantity(50)
    .orderType("MARKET")
    .transactionType("BUY")
    .product("MIS")
    .build();

// Limit order
IndexOrderRequest limitOrder = IndexOrderRequest.builder()
    .symbol("BANKNIFTY24OCTFUT")
    .quantity(15)
    .orderType("LIMIT")
    .price(43500.0)
    .transactionType("SELL")
    .product("NRML")
    .validity("DAY")
    .build();

// Stop-loss order
IndexOrderRequest slOrder = IndexOrderRequest.builder()
    .symbol("NIFTY24SEPFUT")
    .quantity(50)
    .orderType("SL")
    .price(19600.0)
    .triggerPrice(19550.0)
    .transactionType("SELL")
    .product("MIS")
    .build();
```

#### OptionBasedOrderRequest - Options Orders
```java
@Data
@Builder
public class OptionBasedOrderRequest implements OrderRequest {
    private String symbol;          // Underlying: "NIFTY", "BANKNIFTY"
    private Double strikePrice;     // Strike price
    private String optionType;      // "CE" (Call) or "PE" (Put)
    private LocalDate expiryDate;   // Option expiry date
    private Integer quantity;       // Lot size multiple
    private String orderType;       // "MARKET", "LIMIT", "SL", "SL-M"
    private Double price;           // Premium price for LIMIT orders
    private String transactionType; // "BUY" or "SELL"
    private String product;         // "MIS", "NRML"
}
```

**Usage:**
```java
// Buy NIFTY call option
OptionBasedOrderRequest buyCall = OptionBasedOrderRequest.builder()
    .symbol("NIFTY")
    .strikePrice(19500.0)
    .optionType("CE")
    .expiryDate(LocalDate.of(2024, 9, 26))
    .quantity(50)
    .orderType("LIMIT")
    .price(150.0)
    .transactionType("BUY")
    .product("MIS")
    .build();

// Sell BANKNIFTY put option
OptionBasedOrderRequest sellPut = OptionBasedOrderRequest.builder()
    .symbol("BANKNIFTY")
    .strikePrice(43000.0)
    .optionType("PE")
    .expiryDate(LocalDate.of(2024, 9, 26))
    .quantity(15)
    .orderType("MARKET")
    .transactionType("SELL")
    .product("NRML")
    .build();
```

#### TickBasedOrderRequest - Tick Strategy Orders
```java
@Data
@Builder
public class TickBasedOrderRequest implements OrderRequest {
    // For real-time tick-based strategies
    private String symbol;
    private Integer quantity;
    private String orderType;
    private String transactionType;
    private String product;
}
```

### `com.vish.fno.model.order.activeorder` - Active Order Tracking

#### ActiveOrderFactory - Create Active Orders
```java
public class ActiveOrderFactory {
    public static ActiveOrder createActiveOrder(OrderRequest request, String orderId)
}
```

**Usage:**
```java
IndexOrderRequest orderRequest = IndexOrderRequest.builder()
    .symbol("NIFTY24SEPFUT")
    .quantity(50)
    .orderType("MARKET")
    .transactionType("BUY")
    .build();

String orderId = "240928000123456"; // From broker

ActiveOrder activeOrder = ActiveOrderFactory.createActiveOrder(orderRequest, orderId);
// Returns ActiveIndexOrder instance
```

### `com.vish.fno.model` - Market Data Models

#### Candle - OHLCV Data (Java Record)
```java
public record Candle(
    String time,
    double open,
    double high,
    double low,
    double close,
    Long volume,
    Long oi
)
```

**Parameters:**
- `time`: Timestamp as String (e.g., "2024-09-28 09:15:00")
- `open`: Opening price
- `high`: Highest price in the period
- `low`: Lowest price in the period
- `close`: Closing price
- `volume`: Total volume traded (can be null)
- `oi`: Open interest (can be null)

**Usage:**
```java
import com.vish.fno.model.Candle;

// Creating candle data (Java record - immutable)
Candle candle = new Candle(
    "2024-09-28 09:15:00",
    19500.0,
    19550.0,
    19480.0,
    19520.0,
    1000000L,
    5000000L
);

// Accessing data
double closePrice = candle.close();
Long volume = candle.volume();

// Using in a list
List<Candle> candles = List.of(candle);
```

**Note:** `Candle` is a Java record (Java 17+), so it's immutable and has built-in equals(), hashCode(), and toString().

#### Ticker - Real-time Market Data (Java Record)
```java
public record Ticker(
    String mode,
    boolean tradable,
    long instrumentToken,
    String instrumentSymbol,
    double lastTradedPrice,
    double highPrice,
    double lowPrice,
    double openPrice,
    double closePrice,
    double change,
    double lastTradedQuantity,
    double averageTradePrice,
    long volumeTradedToday,
    double totalBuyQuantity,
    double totalSellQuantity,
    Date lastTradedTime,
    double oi,
    double openInterestDayHigh,
    double openInterestDayLow,
    Date tickTimestamp,
    Map<String, List<Depth>> depth
) implements Comparable<Ticker>
```

**Key Fields:**
- `instrumentToken`: Unique instrument identifier
- `instrumentSymbol`: Trading symbol (e.g., "NIFTY24SEPFUT")
- `lastTradedPrice`: Current LTP
- `oi`: Open interest
- `tickTimestamp`: Timestamp of the tick
- `depth`: Market depth (bid/ask)

**Usage:**
```java
import com.vish.fno.model.Ticker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TickerProcessor {
    // Typically received from WebSocket or API
    public void processTicker(Ticker ticker, double previousLTP) {
        if (ticker.lastTradedPrice() > previousLTP) {
            log.info("Price up: {}", ticker.lastTradedPrice());
        }

        // Sort tickers by time
        List<Ticker> tickers = // ... list of tickers
        tickers.sort(Ticker::compareTo); // Sorts by tickTimestamp
    }
}
```

#### SymbolData - Historical Data Storage (Java Record)
```java
@Document(collection = "minute_history_data")
public record SymbolData(
    @Id CandleMetaData record,
    List<Candle> data
)
```

**Usage:**
```java
import com.vish.fno.model.SymbolData;
import com.vish.fno.model.Candle;
import com.vish.fno.model.CandleMetaData;

// Create metadata
CandleMetaData metadata = new CandleMetaData(
    instrumentToken,
    "NIFTY24SEPFUT",
    "2024-09-28",
    "minute"
);

// Create symbol data with candles
SymbolData symbolData = new SymbolData(metadata, candles);

// Store in MongoDB (Spring Data)
symbolDataRepository.save(symbolData);
```

#### Instrument - Trading Instrument Metadata
```java
@Data
@Document
public class Instrument {
    private Long instrumentToken;
    private String tradingSymbol;
    private String name;
    private LocalDate expiry;
    private Double strikePrice;
    private String instrumentType;  // "FUT", "CE", "PE"
    private String segment;         // "NFO-FUT", "NFO-OPT"
    private String exchange;        // "NFO"
    private Double tickSize;
    private Integer lotSize;
}
```

#### OptionsMetaData - Options Contract Info
```java
@Data
public class OptionsMetaData {
    private String underlying;      // "NIFTY", "BANKNIFTY"
    private Double strikePrice;
    private String optionType;      // "CE", "PE"
    private LocalDate expiryDate;
}
```

### `com.vish.fno.model.order` - Order Enums

#### OrderSellReason - Order Exit Reasons
```java
public enum OrderSellReason {
    TARGET_HIT,
    STOP_LOSS_HIT,
    EXPIRY_TIME_REACHED
}
```

**Usage:**
```java
import com.vish.fno.model.order.OrderSellReason;

// When exiting an order, specify the reason
public void exitOrder(ActiveOrder order, double currentPrice) {
    OrderSellReason reason;

    if (currentPrice >= order.getTargetPrice()) {
        reason = OrderSellReason.TARGET_HIT;
    } else if (currentPrice <= order.getStopLoss()) {
        reason = OrderSellReason.STOP_LOSS_HIT;
    } else if (isExpiryTime()) {
        reason = OrderSellReason.EXPIRY_TIME_REACHED;
    }

    sellOrder(order, reason);
}
```

### `com.vish.fno.model.strategy` - Strategy Interfaces

#### Strategy - Base Strategy Interface
```java
public interface Strategy {
    void initialise(Task task);
    Task getTask();
    String getTag();
}
```

**Implementing a Custom Strategy:**
```java
import com.vish.fno.model.strategy.Strategy;
import com.vish.fno.model.Task;

public class MyTradingStrategy implements Strategy {
    private Task task;
    private String tag;

    @Override
    public void initialise(Task task) {
        this.task = task;
        this.tag = "MY_CUSTOM_STRATEGY";
        // Initialize strategy parameters
        setupIndicators();
        loadHistoricalData();
    }

    @Override
    public Task getTask() {
        return task;
    }

    @Override
    public String getTag() {
        return tag;
    }

    private void setupIndicators() {
        // Setup technical indicators
    }

    private void loadHistoricalData() {
        // Load required data
    }
}
```

#### MinuteStrategy - Minute-level Trading
```java
public interface MinuteStrategy extends Strategy {
    // For strategies that execute on minute candles
}
```

**Usage:**
```java
import com.vish.fno.model.strategy.MinuteStrategy;
import com.vish.fno.model.Candle;

public class MovingAverageCrossStrategy implements MinuteStrategy {
    private Task task;

    public void onCandle(Candle candle) {
        // Execute strategy logic on each minute candle
    }

    @Override
    public void initialise(Task task) {
        this.task = task;
    }

    @Override
    public Task getTask() {
        return task;
    }

    @Override
    public String getTag() {
        return "MA_CROSS";
    }
}
```

#### IndexBasedStrategy - Index Futures Trading
```java
public interface IndexBasedStrategy extends MinuteStrategy {
    // For NIFTY/BANKNIFTY futures strategies
}
```

**Usage:**
```java
import com.vish.fno.model.strategy.IndexBasedStrategy;
import com.vish.fno.model.order.orderrequest.IndexOrderRequest;

public class IndexMomentumStrategy implements IndexBasedStrategy {
    private Task task;

    public IndexOrderRequest generateOrder(String symbol, double price) {
        return IndexOrderRequest.builder()
            .symbol(symbol)
            .quantity(50)
            .orderType("MARKET")
            .transactionType("BUY")
            .product("MIS")
            .build();
    }

    @Override
    public void initialise(Task task) {
        this.task = task;
    }

    @Override
    public Task getTask() {
        return task;
    }

    @Override
    public String getTag() {
        return "INDEX_MOMENTUM";
    }
}
```

#### OptionBasedStrategy - Options Trading
```java
public interface OptionBasedStrategy extends MinuteStrategy {
    // For options trading strategies
}
```

**Usage:**
```java
import com.vish.fno.model.strategy.OptionBasedStrategy;
import com.vish.fno.model.order.orderrequest.OptionBasedOrderRequest;

public class StrangleStrategy implements OptionBasedStrategy {
    private Task task;

    public OptionBasedOrderRequest[] createStrangle(String underlying,
                                                      double spot,
                                                      double distance) {
        OptionBasedOrderRequest call = OptionBasedOrderRequest.builder()
            .symbol(underlying)
            .strikePrice(spot + distance)
            .optionType("CE")
            .expiryDate(getNextExpiry())
            .quantity(50)
            .orderType("MARKET")
            .transactionType("SELL")
            .product("MIS")
            .build();

        OptionBasedOrderRequest put = OptionBasedOrderRequest.builder()
            .symbol(underlying)
            .strikePrice(spot - distance)
            .optionType("PE")
            .expiryDate(getNextExpiry())
            .quantity(50)
            .orderType("MARKET")
            .transactionType("SELL")
            .product("MIS")
            .build();

        return new OptionBasedOrderRequest[]{call, put};
    }

    @Override
    public void initialise(Task task) {
        this.task = task;
    }

    @Override
    public Task getTask() {
        return task;
    }

    @Override
    public String getTag() {
        return "STRANGLE";
    }
}
```

#### TickBasedStrategy - Real-time Tick Trading
```java
public interface TickBasedStrategy extends Strategy {
    // For high-frequency tick-based strategies
}
```

**Usage:**
```java
import com.vish.fno.model.strategy.TickBasedStrategy;
import com.vish.fno.model.Ticker;

public class ScalpingStrategy implements TickBasedStrategy {
    private Task task;

    public void onTick(Ticker ticker) {
        // Execute logic on every tick
        if (shouldEnter(ticker)) {
            placeOrder(ticker);
        }
    }

    @Override
    public void initialise(Task task) {
        this.task = task;
    }

    @Override
    public Task getTask() {
        return task;
    }

    @Override
    public String getTag() {
        return "SCALPING";
    }
}
```

## Common Patterns

### Pattern 1: Creating Orders for Live Trading
```java
public class OrderService {
    public void placeIndexOrder() {
        IndexOrderRequest order = IndexOrderRequest.builder()
            .symbol("NIFTY24SEPFUT")
            .quantity(50)
            .orderType("MARKET")
            .transactionType("BUY")
            .product("MIS")
            .build();

        // Pass to broker API
        String orderId = brokerService.placeOrder(order);

        // Track as active order
        ActiveOrder active = ActiveOrderFactory.createActiveOrder(order, orderId);
        activeOrderRepository.save(active);
    }
}
```

### Pattern 2: Building Candle Data for Backtesting
```java
import com.vish.fno.model.Candle;

public class DataLoader {
    public List<Candle> loadHistoricalData(String filePath) {
        List<Candle> candles = new ArrayList<>();

        // Read from CSV/JSON and populate
        // Example: parsing CSV row
        Candle candle = new Candle(
            "2024-09-28 09:15:00",
            19500.0,
            19550.0,
            19480.0,
            19520.0,
            1000000L,
            5000000L
        );
        candles.add(candle);

        return candles;
    }
}
```

### Pattern 3: Options Order with Expiry Calculation
```java
public class OptionsOrderBuilder {
    public OptionBasedOrderRequest buildWeeklyOptionOrder(String underlying, double strike) {
        LocalDate nextWeeklyExpiry = getNextThursday(LocalDate.now());

        return OptionBasedOrderRequest.builder()
            .symbol(underlying)
            .strikePrice(strike)
            .optionType("CE")
            .expiryDate(nextWeeklyExpiry)
            .quantity(50)
            .orderType("LIMIT")
            .price(calculatePremium(underlying, strike, nextWeeklyExpiry))
            .transactionType("BUY")
            .product("MIS")
            .build();
    }

    private LocalDate getNextThursday(LocalDate date) {
        while (date.getDayOfWeek() != DayOfWeek.THURSDAY) {
            date = date.plusDays(1);
        }
        return date;
    }
}
```

## Wyckoff Phase Analysis Models

### `com.vish.fno.model.wyckoff` - Wyckoff Market Phase Models

#### WyckoffPhase - Market Phase Enumeration

Enum representing Wyckoff market phases for technical analysis.

```java
public enum WyckoffPhase
```

**Enum Constants:**

**Accumulation Phases:**
- `ACCUMULATION_PHASE_A` - "Stopping the Prior Downtrend - PS, SC, AR, ST"
- `ACCUMULATION_PHASE_B` - "Building a Cause - Testing supply and demand"
- `ACCUMULATION_PHASE_C` - "Spring/Shakeout - Testing support levels"
- `ACCUMULATION_PHASE_D` - "Sign of Strength - Breaking resistance"

**Distribution Phases:**
- `DISTRIBUTION_PHASE_A` - "Stopping the Prior Uptrend - PSY, BC, AR, ST"
- `DISTRIBUTION_PHASE_B` - "Building a Cause - Testing demand and supply"
- `DISTRIBUTION_PHASE_C` - "Upthrust - Testing resistance levels"
- `DISTRIBUTION_PHASE_D` - "Sign of Weakness - Breaking support"

**Trending Phases:**
- `MARKUP` - "Uptrend - Higher highs and higher lows"
- `MARKDOWN` - "Downtrend - Lower highs and lower lows"

**Continuation Phases:**
- `REACCUMULATION` - "Continuation pattern in an uptrend"
- `REDISTRIBUTION` - "Continuation pattern in a downtrend"
- `CONSOLIDATION` - "Sideways movement - Range bound"
- `UNKNOWN` - "Unable to determine phase"

**Methods:**
```java
public String getPhaseName()
```
- **Returns:** Human-readable phase name

```java
public String getDescription()
```
- **Returns:** Detailed description of the phase

```java
public boolean isAccumulation()
```
- **Returns:** `true` if this is an accumulation phase (A, B, C, or D)

```java
public boolean isDistribution()
```
- **Returns:** `true` if this is a distribution phase (A, B, C, or D)

```java
public boolean isMarkup()
```
- **Returns:** `true` if phase is MARKUP or ACCUMULATION_PHASE_D

```java
public boolean isMarkdown()
```
- **Returns:** `true` if phase is MARKDOWN or DISTRIBUTION_PHASE_D

**Usage Example:**
```java
import com.vish.fno.model.wyckoff.WyckoffPhase;

public class PhaseAnalyzer {
    public void analyzePhase(WyckoffPhase phase) {
        System.out.println("Phase: " + phase.getPhaseName());
        System.out.println("Description: " + phase.getDescription());

        // Trading logic based on phase type
        if (phase.isAccumulation()) {
            System.out.println("Consider long positions");
        } else if (phase.isDistribution()) {
            System.out.println("Consider short positions or exit longs");
        }

        // Specific phase actions
        if (phase == WyckoffPhase.ACCUMULATION_PHASE_D) {
            System.out.println("Sign of Strength detected - strong buy signal");
        } else if (phase == WyckoffPhase.DISTRIBUTION_PHASE_C) {
            System.out.println("Upthrust detected - potential reversal");
        }

        // Check markup/markdown
        if (phase.isMarkup()) {
            System.out.println("Uptrend confirmed - follow the trend");
        } else if (phase.isMarkdown()) {
            System.out.println("Downtrend confirmed - avoid longs");
        }
    }
}
```

---

#### IWyckoffPhaseIdentifier - Phase Identification Interface

Interface for implementing Wyckoff phase identification strategies.

```java
public interface IWyckoffPhaseIdentifier
```

**Core Methods:**
```java
WyckoffPhase identifyPhase(List<Candle> data, int currentIndex)
```
- **Parameters:**
  - `data`: List of candlestick data (must not be null or empty)
  - `currentIndex`: The index in the data list to analyze (0-based)
- **Returns:** The identified Wyckoff phase
- **Description:** Analyzes market data to identify the current Wyckoff phase.

```java
String getIdentifierType()
```
- **Returns:** Implementation name (e.g., "Classical", "Volume-Based", "ML-Based")
- **Description:** Returns the type/name of this identifier implementation.

```java
String getDescription()
```
- **Returns:** Description of the identification methodology
- **Description:** Explains the approach used by this identifier.

**Optional Methods (with defaults):**
```java
default double getPhaseConfidence(List<Candle> data, int currentIndex)
```
- **Parameters:**
  - `data`: List of candlestick data
  - `currentIndex`: Index to analyze
- **Returns:** Confidence score (0.0 to 1.0)
- **Default:** Returns 0.5 (moderate confidence)
- **Description:** Calculates confidence in the identified phase. Higher values indicate higher certainty.

```java
default int getMinimumDataPoints()
```
- **Returns:** Minimum number of data points needed
- **Default:** Returns 5
- **Description:** Returns the minimum number of candles required for accurate identification.

```java
default boolean supportsRealTimeAnalysis()
```
- **Returns:** `true` if real-time analysis is supported
- **Default:** Returns `true`
- **Description:** Indicates whether this identifier can be used for live/real-time market analysis.

```java
default void reset()
```
- **Description:** Resets any internal state or caches. Useful when switching symbols or starting new analysis.
- **Default:** Does nothing (override if your implementation has state)

**Implementation Example:**
```java
import com.vish.fno.model.wyckoff.IWyckoffPhaseIdentifier;
import com.vish.fno.model.wyckoff.WyckoffPhase;
import com.vish.fno.model.Candle;
import java.util.List;

public class CustomWyckoffIdentifier implements IWyckoffPhaseIdentifier {

    @Override
    public WyckoffPhase identifyPhase(List<Candle> data, int currentIndex) {
        if (data == null || data.isEmpty() || currentIndex < 0) {
            return WyckoffPhase.UNKNOWN;
        }

        // Your custom logic here
        double trendStrength = calculateTrendStrength(data, currentIndex);
        double volumeRatio = calculateVolumeRatio(data, currentIndex);

        if (trendStrength > 0.5 && volumeRatio > 1.5) {
            return WyckoffPhase.MARKUP;
        } else if (trendStrength < -0.5 && volumeRatio > 1.5) {
            return WyckoffPhase.MARKDOWN;
        } else if (Math.abs(trendStrength) < 0.1 && volumeRatio > 1.2) {
            return WyckoffPhase.ACCUMULATION_PHASE_B;
        }

        return WyckoffPhase.CONSOLIDATION;
    }

    @Override
    public String getIdentifierType() {
        return "Custom";
    }

    @Override
    public String getDescription() {
        return "Custom Wyckoff identifier using proprietary trend and volume analysis";
    }

    @Override
    public double getPhaseConfidence(List<Candle> data, int currentIndex) {
        // Calculate confidence based on signal strength
        double trendStrength = Math.abs(calculateTrendStrength(data, currentIndex));
        double volumeRatio = calculateVolumeRatio(data, currentIndex);

        double confidence = 0.5; // Base confidence

        if (trendStrength > 0.7) confidence += 0.2;
        if (volumeRatio > 1.5) confidence += 0.2;

        return Math.min(1.0, confidence);
    }

    @Override
    public int getMinimumDataPoints() {
        return 20; // Need 20 candles for our analysis
    }

    private double calculateTrendStrength(List<Candle> data, int currentIndex) {
        // Your trend calculation logic
        return 0.0;
    }

    private double calculateVolumeRatio(List<Candle> data, int currentIndex) {
        // Your volume analysis logic
        return 1.0;
    }
}
```

**Usage with Factory Pattern:**
```java
import com.vish.fno.model.wyckoff.IWyckoffPhaseIdentifier;
import com.vish.fno.model.Candle;
import java.util.List;

public class PhaseAnalysisService {
    private final IWyckoffPhaseIdentifier identifier;

    public PhaseAnalysisService(IWyckoffPhaseIdentifier identifier) {
        this.identifier = identifier;
    }

    public void analyze(List<Candle> candles) {
        // Check minimum data requirement
        if (candles.size() < identifier.getMinimumDataPoints()) {
            System.out.println("Insufficient data. Need at least " +
                identifier.getMinimumDataPoints() + " candles");
            return;
        }

        int currentIndex = candles.size() - 1;

        // Identify phase
        WyckoffPhase phase = identifier.identifyPhase(candles, currentIndex);
        double confidence = identifier.getPhaseConfidence(candles, currentIndex);

        System.out.println("Identifier: " + identifier.getIdentifierType());
        System.out.println("Phase: " + phase.getPhaseName());
        System.out.println("Confidence: " + (confidence * 100) + "%");
        System.out.println("Description: " + identifier.getDescription());

        // Only trade on high confidence
        if (confidence > 0.7) {
            if (phase.isMarkup()) {
                System.out.println("HIGH CONFIDENCE BUY SIGNAL");
            } else if (phase.isMarkdown()) {
                System.out.println("HIGH CONFIDENCE SELL SIGNAL");
            }
        }
    }
}
```

---

#### WyckoffIndicators - Technical Indicators Record

Java record containing Wyckoff analysis indicators.

```java
public record WyckoffIndicators(
    double pricePosition,
    double volumeAnalysis,
    double trendStrength,
    double rangePosition,
    double supplyDemandBalance,
    double volatility,
    double momentum,
    double relativeStrength,
    boolean hasSpring,
    boolean hasUpthrust,
    boolean hasSignOfStrength,
    boolean hasSignOfWeakness,
    double supportLevel,
    double resistanceLevel,
    double averageVolume,
    double currentVolume,
    int daysInRange,
    double rangeWidth
)
```

**Parameters:**
- `pricePosition`: Current price position within the range (0.0 to 1.0)
- `volumeAnalysis`: Volume ratio compared to average (e.g., 1.5 = 50% above average)
- `trendStrength`: Trend strength indicator (-1.0 to 1.0, positive = uptrend, negative = downtrend)
- `rangePosition`: Position within the trading range (0.0 = bottom, 1.0 = top)
- `supplyDemandBalance`: Supply vs demand balance (-1.0 = all supply, 1.0 = all demand)
- `volatility`: Current market volatility (as ratio of price range to average price)
- `momentum`: Price momentum indicator (absolute price change)
- `relativeStrength`: Relative strength compared to previous periods
- `hasSpring`: `true` if spring pattern detected (false breakdown below support)
- `hasUpthrust`: `true` if upthrust pattern detected (false breakout above resistance)
- `hasSignOfStrength`: `true` if sign of strength detected (strong move through resistance)
- `hasSignOfWeakness`: `true` if sign of weakness detected (strong move through support)
- `supportLevel`: Identified support level price
- `resistanceLevel`: Identified resistance level price
- `averageVolume`: Average volume over lookback period
- `currentVolume`: Current bar/candle volume
- `daysInRange`: Number of periods price stayed within the range
- `rangeWidth`: Width of the trading range (resistance - support)

**Usage Example:**
```java
import com.vish.fno.model.wyckoff.WyckoffIndicators;
import com.vish.fno.model.Candle;
import java.util.List;

public class IndicatorAnalyzer {
    public WyckoffIndicators calculateIndicators(List<Candle> data, int currentIndex) {
        // Calculate all indicator values
        double pricePos = calculatePricePosition(data, currentIndex);
        double volumeAnalysis = calculateVolumeAnalysis(data, currentIndex);
        double trendStr = calculateTrendStrength(data, currentIndex);
        // ... calculate other values

        // Create immutable indicators record
        return new WyckoffIndicators(
            pricePos,                  // pricePosition
            volumeAnalysis,            // volumeAnalysis
            trendStr,                  // trendStrength
            pricePos,                  // rangePosition
            0.5,                       // supplyDemandBalance
            0.02,                      // volatility
            100.0,                     // momentum
            55.0,                      // relativeStrength
            false,                     // hasSpring
            false,                     // hasUpthrust
            true,                      // hasSignOfStrength
            false,                     // hasSignOfWeakness
            19400.0,                   // supportLevel
            19600.0,                   // resistanceLevel
            1000000.0,                 // averageVolume
            1500000.0,                 // currentVolume
            15,                        // daysInRange
            200.0                      // rangeWidth
        );
    }

    public void analyzeIndicators(WyckoffIndicators indicators) {
        // Access indicator values
        System.out.println("Price Position: " + (indicators.pricePosition() * 100) + "%");
        System.out.println("Volume: " + (indicators.volumeAnalysis() * 100) + "% of average");
        System.out.println("Trend Strength: " + indicators.trendStrength());

        // Check for Wyckoff events
        if (indicators.hasSpring()) {
            System.out.println("SPRING DETECTED - Potential accumulation complete");
        }

        if (indicators.hasSignOfStrength()) {
            System.out.println("SIGN OF STRENGTH - Breaking resistance");
        }

        // Analyze volume
        if (indicators.currentVolume() > indicators.averageVolume() * 1.5) {
            System.out.println("High volume detected - significant interest");
        }

        // Range analysis
        System.out.println("Support: " + indicators.supportLevel());
        System.out.println("Resistance: " + indicators.resistanceLevel());
        System.out.println("Range Width: " + indicators.rangeWidth());
        System.out.println("Days in Range: " + indicators.daysInRange());
    }

    // Helper methods
    private double calculatePricePosition(List<Candle> data, int index) {
        // Implementation
        return 0.65; // Example: 65% from bottom to top of range
    }

    private double calculateVolumeAnalysis(List<Candle> data, int index) {
        // Implementation
        return 1.3; // Example: 30% above average
    }

    private double calculateTrendStrength(List<Candle> data, int index) {
        // Implementation
        return 0.8; // Example: Strong uptrend
    }
}
```

**Integration with Phase Identifiers:**
```java
import com.vish.fno.model.wyckoff.WyckoffIndicators;
import com.vish.fno.model.wyckoff.WyckoffPhase;
import com.vish.fno.model.wyckoff.IWyckoffPhaseIdentifier;

public class AdvancedWyckoffAnalyzer implements IWyckoffPhaseIdentifier {
    @Override
    public WyckoffPhase identifyPhase(List<Candle> data, int currentIndex) {
        WyckoffIndicators indicators = calculateIndicators(data, currentIndex);

        // Use indicators to determine phase
        if (indicators.hasSpring() && indicators.pricePosition() < 0.3) {
            return WyckoffPhase.ACCUMULATION_PHASE_C;
        }

        if (indicators.hasSignOfStrength() && indicators.volumeAnalysis() > 1.5) {
            return WyckoffPhase.ACCUMULATION_PHASE_D;
        }

        if (indicators.trendStrength() > 0.5 && indicators.momentum() > 0) {
            return WyckoffPhase.MARKUP;
        }

        if (indicators.hasUpthrust() && indicators.pricePosition() > 0.7) {
            return WyckoffPhase.DISTRIBUTION_PHASE_C;
        }

        if (indicators.daysInRange() > 10 && Math.abs(indicators.trendStrength()) < 0.1) {
            if (indicators.supplyDemandBalance() > 0) {
                return WyckoffPhase.ACCUMULATION_PHASE_B;
            } else {
                return WyckoffPhase.DISTRIBUTION_PHASE_B;
            }
        }

        return WyckoffPhase.CONSOLIDATION;
    }

    @Override
    public String getIdentifierType() {
        return "AdvancedIndicators";
    }

    @Override
    public String getDescription() {
        return "Advanced Wyckoff identification using comprehensive indicator analysis";
    }

    private WyckoffIndicators calculateIndicators(List<Candle> data, int currentIndex) {
        // Calculate all indicators and return record
        // ... implementation
        return null; // Placeholder
    }
}
```

**Note:** `WyckoffIndicators` is a Java record (Java 17+), making it immutable and providing automatic `equals()`, `hashCode()`, and `toString()` implementations.

---

### Wyckoff Models Integration Example

Complete example showing all Wyckoff models working together:

```java
import com.vish.fno.model.wyckoff.*;
import com.vish.fno.model.Candle;
import java.util.List;

public class WyckoffTradingSystem {
    private final IWyckoffPhaseIdentifier identifier;

    public WyckoffTradingSystem(IWyckoffPhaseIdentifier identifier) {
        this.identifier = identifier;
    }

    public TradingDecision analyze(List<Candle> candles) {
        int index = candles.size() - 1;

        // Identify phase
        WyckoffPhase phase = identifier.identifyPhase(candles, index);
        double confidence = identifier.getPhaseConfidence(candles, index);

        System.out.println("=== Wyckoff Analysis ===");
        System.out.println("Phase: " + phase.getPhaseName());
        System.out.println("Description: " + phase.getDescription());
        System.out.println("Confidence: " + (confidence * 100) + "%");

        // Decision logic based on phase
        if (confidence < 0.6) {
            return new TradingDecision("HOLD", "Low confidence - wait for clarity");
        }

        // Accumulation phases
        if (phase == WyckoffPhase.ACCUMULATION_PHASE_C) {
            return new TradingDecision("PREPARE_LONG", "Spring detected - prepare to buy");
        }
        if (phase == WyckoffPhase.ACCUMULATION_PHASE_D) {
            return new TradingDecision("BUY", "Sign of Strength - enter long");
        }

        // Distribution phases
        if (phase == WyckoffPhase.DISTRIBUTION_PHASE_C) {
            return new TradingDecision("PREPARE_SHORT", "Upthrust detected - prepare to sell");
        }
        if (phase == WyckoffPhase.DISTRIBUTION_PHASE_D) {
            return new TradingDecision("SELL", "Sign of Weakness - exit longs or enter short");
        }

        // Trending phases
        if (phase.isMarkup()) {
            return new TradingDecision("HOLD_LONG", "Uptrend - hold long positions");
        }
        if (phase.isMarkdown()) {
            return new TradingDecision("AVOID_LONG", "Downtrend - avoid long positions");
        }

        // Consolidation phases
        if (phase == WyckoffPhase.ACCUMULATION_PHASE_B) {
            return new TradingDecision("ACCUMULATE", "Building cause - accumulate on dips");
        }
        if (phase == WyckoffPhase.DISTRIBUTION_PHASE_B) {
            return new TradingDecision("DISTRIBUTE", "Building cause - distribute on rallies");
        }

        return new TradingDecision("HOLD", "No clear signal");
    }

    record TradingDecision(String action, String reason) {}
}
```

For complete implementation examples and advanced phase identification strategies, see:
- **fno-phase-analyzer module**: `/docs/module-guides/fno-phase-analyzer.md`

---

## Best Practices

1. **Always use Builder pattern** for order requests
2. **Validate required fields** before creating orders
3. **Use proper enums** for orderType, transactionType, product
4. **Set appropriate lot sizes** (NIFTY=50, BANKNIFTY=15, etc.)
5. **MongoDB integration** available via `@Document` annotation
6. **Check confidence scores** when using Wyckoff phase identifiers (>0.6 recommended)
7. **Validate minimum data points** before phase identification
8. **Reset identifiers** when switching symbols or timeframes

## Dependencies

- **Spring Boot Data MongoDB**: For persistence (optional)
- **Jackson**: For JSON serialization
- **Lombok**: For reducing boilerplate

## Thread Safety

All model classes are POJOs and not thread-safe by default. If sharing across threads, use proper synchronization or immutable copies.

**Note:** Wyckoff model records (`WyckoffIndicators`) are immutable and thread-safe.

## Validation

Consider adding Bean Validation annotations in your application:
```java
@NotNull
@Min(1)
private Integer quantity;

@NotNull
@Pattern(regexp = "BUY|SELL")
private String transactionType;
```
