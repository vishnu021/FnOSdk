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

## Best Practices

1. **Always use Builder pattern** for order requests
2. **Validate required fields** before creating orders
3. **Use proper enums** for orderType, transactionType, product
4. **Set appropriate lot sizes** (NIFTY=50, BANKNIFTY=15, etc.)
5. **MongoDB integration** available via `@Document` annotation

## Dependencies

- **Spring Boot Data MongoDB**: For persistence (optional)
- **Jackson**: For JSON serialization
- **Lombok**: For reducing boilerplate

## Thread Safety

All model classes are POJOs and not thread-safe by default. If sharing across threads, use proper synchronization or immutable copies.

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
