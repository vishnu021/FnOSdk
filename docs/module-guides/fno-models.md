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

#### Candlestick - OHLCV Data
```java
@Data
@Document
public class Candlestick {
    private LocalDateTime date;
    private Double open;
    private Double high;
    private Double low;
    private Double close;
    private Long volume;
    private Long oi;  // Open interest (optional)
}
```

**Usage:**
```java
// Creating candlestick data
Candlestick candle = new Candlestick();
candle.setDate(LocalDateTime.of(2024, 9, 28, 9, 15));
candle.setOpen(19500.0);
candle.setHigh(19550.0);
candle.setLow(19480.0);
candle.setClose(19520.0);
candle.setVolume(1000000L);
candle.setOi(5000000L);

// Using in a list
List<Candlestick> candles = new ArrayList<>();
candles.add(candle);
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

### Enums

#### Strategy - Trading Strategies
```java
public enum Strategy {
    SCALPING,
    DAY_TRADING,
    SWING_TRADING,
    POSITIONAL,
    HEDGING,
    ARBITRAGE
}
```

#### StrategyType - Strategy Categories
```java
public enum StrategyType {
    INDEX,          // Index futures based
    OPTION,         // Options based
    TICK_BASED      // Real-time tick based
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

### Pattern 2: Building Candlestick Data for Backtesting
```java
public class DataLoader {
    public List<Candlestick> loadHistoricalData(String filePath) {
        List<Candlestick> candles = new ArrayList<>();

        // Read from CSV/JSON and populate
        // ... file reading logic

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
