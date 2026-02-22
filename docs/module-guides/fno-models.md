# fno-models - Core Data Models

Foundation module providing core POJOs and interfaces for F&O trading operations.

## Maven Dependency
```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-models</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

## Package Overview

| Package | Description |
|---------|-------------|
| `com.vish.fno.model.order.orderrequest` | Order request interfaces and implementations |
| `com.vish.fno.model.order.activeorder` | Active order tracking and lifecycle |
| `com.vish.fno.model.order` | Order sell details, exit reasons |
| `com.vish.fno.model.helper` | Order flow and ITM resolver interfaces |
| `com.vish.fno.model.cache` | Order caching utilities |
| `com.vish.fno.model.util` | Model utilities (rounding, formatting) |
| `com.vish.fno.model.wyckoff` | Wyckoff phase models and interfaces |
| `com.vish.fno.model.strategy` | Strategy interfaces (Task, Strategy) |
| `com.vish.fno.model.candle` | Candle metadata records |

---

## Order Request Package

### OrderRequest Interface

| Method | Returns | Description |
|--------|---------|-------------|
| `getIndex()` | `String` | Trading index/symbol |
| `getBuyThreshold()` | `double` | Price threshold for order |
| `getTarget()` | `double` | Target price |
| `getExpirationTimestamp()` | `int` | Order expiration time index |
| `getTag()` | `String` | Unique order tag |
| `getTask()` | `Task` | Associated task |
| `verifyBuyThreshold(Ticker)` | `Optional<OrderRequest>` | Returns order if threshold crossed |

### Implementations

| Class | Use Case | Key Difference |
|-------|----------|----------------|
| `IndexOrderRequest` | Index futures/options | Has `optionSymbol`, `callOrder` flag |
| `OptionBasedOrderRequest` | Option premium trading | No option symbol, premium-based |
| `TickBasedOrderRequest` | High-frequency trading | `verifyBuyThreshold()` always returns self |

**Builder Pattern:**
```java
IndexOrderRequest.builder("TAG", "NIFTY", task)
    .optionSymbol("NIFTY24OCT19500CE").buyThreshold(19500.0)
    .target(19600.0).stopLoss(19450.0).callOrder(true).build();
```

---

## Active Order Package

### ActiveOrder Interface

| Method | Returns | Description |
|--------|---------|-------------|
| `getIndex()` | `String` | Trading index |
| `getDate()` | `Date` | Order date |
| `getTarget()` / `getStopLoss()` | `double` | Price levels |
| `getBuyPrice()` / `getSellPrice()` | `double` | Execution prices |
| `setStopLoss(double)` | `void` | Updates stop loss (trailing logic) |
| `getBuyQuantity()` / `getSoldQuantity()` | `int` | Quantities |
| `incrementSoldQuantity(int, double)` | `void` | Tracks partial exits |
| `isActive()` / `setActive(boolean)` | `boolean`/`void` | Active status |
| `closeOrder(double, int, String)` | `void` | Closes the order |
| `isTargetAchieved(double)` | `boolean` | Checks if target hit |
| `isStopLossHit(double)` | `boolean` | Checks if SL hit |
| `getProfit()` | `double` | Calculates P&L |
| `getEntryTimeStamp()` | `int` | Entry minute index (for hold duration checks) |

**Note:** For CSV export and logging, use `FileUtils.csvHeader()`, `FileUtils.toCSV()`, and `FileUtils.orderLog()` from fno-utils.

### AbstractActiveOrder

Base class with protected fields: `tag`, `date`, `entryTimeStamp`, `exitTimeStamp`, `buyThreshold`, `buyPrice`, `buyQuantity`, `soldQuantity`, `sellPrice`, `target`, `stopLoss`, `extraData`, `stopLossRevisionCount`, `stopLossRevision`

Consolidated `toString()` with `appendToStringFields(StringBuilder)` hook -- subclasses override to add extra fields (e.g., `ActiveIndexOrder` appends `optionSymbol`). The `toString()` output conditionally includes `kiteOrderId` from the `extraData` map when present, aiding order tracking in logs.

### Implementations

| Class | Source | Stop Loss Behavior |
|-------|--------|-------------------|
| `ActiveIndexOrder` | `IndexOrderRequest` | Call: only up, Put: only down |
| `OptionBasedActiveOrder` | `OptionBasedOrderRequest` | Only up (always long) |
| `TickBasedActiveOrder` | `TickBasedOrderRequest` | Same as ActiveIndexOrder |

### ActiveOrderFactory

Uses Java 21 switch expression. Throws `IllegalArgumentException` for unknown `OrderRequest` types (no longer returns null).

```java
public static ActiveOrder createOrder(OrderRequest orderRequest, double ltp, int timestamp, String orderEntryTimestamp,
                                      int quantity, int lotSize)
```

```java
ActiveOrder order = ActiveOrderFactory.createOrder(orderRequest, ltp, timestamp, timestampString, quantity, lotSize);
// Throws IllegalArgumentException if orderRequest type is unknown
```

---

## Supporting Records

### OrderSellDetailModel

```java
public record OrderSellDetailModel(boolean sellOrder, int quantity, OrderSellReason reason, ActiveOrder order)
```
Constructor `new OrderSellDetailModel(false)` creates "don't sell" instance.

### ExitDetail

```java
public record ExitDetail(Integer quantity, Double sellPrice, Double sellOptionPrice)
```
Factory methods: `ExitDetail.forRegularOrder(qty, price)`, `ExitDetail.forIndexOrder(qty, indexPrice, optionPrice)`

### OrderSellReason Enum

`TARGET_HIT`, `STOP_LOSS_HIT`, `EXPIRY_TIME_REACHED`, `MAX_HOLD_DURATION_REACHED`

---

## Helper Interfaces

### OrderFlowHandler

| Method | Description |
|--------|-------------|
| `verifyAndSellOrder(String, Ticker)` | Evaluates sell conditions |
| `verifyAndBuyOrder(Ticker, OrderRequest)` | Evaluates buy conditions |

### ITMResolver

| Method | Returns | Description |
|--------|---------|-------------|
| `resolveITMSymbol(String, double, boolean)` | `String` | ITM option symbol |
| `resolveOTMSymbol(String, double, boolean)` | `String` | OTM option symbol |
| `prepareSymbols()` | `void` | Refreshes symbol mappings |

**Implementations:** `KiteITMResolver` (fno-kite-reader), `BacktestITMResolver` (consumer projects)

---

## Cache Package

### OrderCache

Thread-safe cache for order requests, active orders, and completed orders with cash management. Maintains secondary symbol indices (`ConcurrentHashMap`) for O(1) lookups by index symbol. The hot-path methods `checkEntryInOpenOrders` and `getActiveOrderForSymbol` are called on every tick (~520/sec); the symbol index avoids full-list scans.

```java
OrderCache cache = new OrderCache(100000.0);
cache.addOrderRequest(request);
cache.deductCash(orderCost);  // Synchronized
cache.addCash(sellValue);     // Synchronized
double cash = cache.getAvailableCash();  // Volatile read
List<ActiveOrder> completed = cache.getCompletedOrders();  // Orders moved from active on removal
```

| Method | Thread-Safe | Description |
|--------|-------------|-------------|
| `getAvailableCash()` | ✅ Volatile | Read cash balance |
| `deductCash(double)` | ✅ Synchronized | Atomic deduction |
| `addCash(double)` | ✅ Synchronized | Atomic addition |
| `checkEntryInOpenOrders(Ticker, String)` | ✅ | O(1) symbol index lookup, check trigger conditions |
| `isNotInActiveOrders(OrderRequest)` | ✅ | Check if order is not already active (uses symbol index) |
| `addOrderRequest(OrderRequest)` | ✅ | Add (removes duplicates, updates symbol index) |
| `appendActiveOrder(ActiveOrder)` | ✅ | Add active order + update symbol index |
| `removeActiveOrder(ActiveOrder)` | ✅ | Remove from active + index, add to completed |
| `getActiveOrderForSymbol(String)` | ✅ | O(1) symbol index lookup, returns `List.copyOf()` |
| `getCompletedOrders()` | ✅ | Get list of completed orders |
| `removeExpiredOpenOrders(int)` | ✅ | Clean expired orders + update symbol index |

**Note:** `removeActiveOrder()` removes from `activeOrders` and the symbol index, then moves the order to `completedOrders` for historical tracking.

---

## Core Data Models

### Candle (Record)

```java
public record Candle(String time, double open, double high, double low, double close, Long volume, Long oi)
```

### Ticker (Record)

```java
public record Ticker(String mode, boolean tradable, long instrumentToken, String instrumentSymbol,
    double lastTradedPrice, double highPrice, double lowPrice, double openPrice, double closePrice,
    double change, double lastTradedQuantity, double averageTradePrice, long volumeTradedToday,
    double totalBuyQuantity, double totalSellQuantity, Date lastTradedTime, double oi,
    double openInterestDayHigh, double openInterestDayLow, Date tickTimestamp, Date tickReceivedTime,
    Map<String, List<Depth>> depth) implements Comparable<Ticker>
```

| Field | Type | Description |
|-------|------|-------------|
| `tickTimestamp` | `Date` | Timestamp from exchange when tick was generated |
| `tickReceivedTime` | `Date` | Timestamp when tick was received/mapped by SDK |

**Note:** `tickReceivedTime` enables latency analysis between exchange tick generation and SDK processing.

### Metadata Records

```java
public record CandleMetaData(String symbol, String date)
public record OptionMetaData(String symbol, String date, String expiryDate)
@Document(collection = "minute_history_data")
public record SymbolData(@Id CandleMetaData record, List<Candle> data)
```

---

## Strategy Interfaces

### Task Interface

| Method | Returns | Description |
|--------|---------|-------------|
| `getIndex()` | `String` | Trading index |
| `isEnabled()` | `boolean` | Active status |
| `isExpiryDayOrders()` | `boolean` | Trade on expiry |
| `getLots()` | `int` | Lot multiplier (default: 1) |

### Strategy Hierarchy

```
Strategy (base)
├── MinuteStrategy extends Strategy
│   ├── IndexBasedStrategy extends MinuteStrategy
│   └── OptionBasedStrategy extends MinuteStrategy
└── TickBasedStrategy extends Strategy
```

---

## Wyckoff Models

### WyckoffPhase Enum

| Phase | Description |
|-------|-------------|
| `ACCUMULATION_PHASE_A/B/C/D` | Accumulation sub-phases |
| `DISTRIBUTION_PHASE_A/B/C/D` | Distribution sub-phases |
| `MARKUP` | Uptrend (HH/HL) |
| `MARKDOWN` | Downtrend (LH/LL) |
| `REACCUMULATION` / `REDISTRIBUTION` | Continuation patterns |
| `CONSOLIDATION` | Sideways movement |
| `UNKNOWN` | Unable to determine |

Methods: `getPhaseName()`, `getDescription()`, `isAccumulation()`, `isDistribution()`, `isMarkup()`, `isMarkdown()`

### IWyckoffPhaseIdentifier Interface

| Method | Returns | Description |
|--------|---------|-------------|
| `identifyPhase(List<Candle>, int)` | `WyckoffPhase` | Identifies current phase |
| `getIdentifierType()` | `String` | Identifier name |
| `getDescription()` | `String` | Description |
| `getPhaseConfidence(...)` | `double` | Confidence (0.0-1.0) |
| `getMinimumDataPoints()` | `int` | Required data points |
| `reset()` | `void` | Resets state |

### WyckoffIndicators (Record)

```java
public record WyckoffIndicators(double pricePosition, double volumeAnalysis, double trendStrength,
    double rangePosition, double supplyDemandBalance, double volatility, double momentum,
    double relativeStrength, boolean hasSpring, boolean hasUpthrust, boolean hasSignOfStrength,
    boolean hasSignOfWeakness, double supportLevel, double resistanceLevel, double averageVolume,
    double currentVolume, int daysInRange, double rangeWidth)
```

---

## ModelUtils

| Method | Returns | Description |
|--------|---------|-------------|
| `round(double)` | `double` | Round to nearest 0.05 (5 paise) |
| `roundTo5Paise(double)` | `BigDecimal` | BigDecimal with 2 decimals |
| `roundToNearest(BigDecimal, BigDecimal)` | `BigDecimal` | Round to increment |
| `getStringDate(Date)` | `String` | "yyyy-MM-dd" |
| `getStringTime(Date)` | `String` | "HH:mm:ss.SSS" |
| `getStringDateTime(Date)` | `String` | "yyyy-MM-dd HH:mm" |

---

## Thread Safety

| Component | Thread-Safe | Notes |
|-----------|-------------|-------|
| OrderCache (cash ops) | ✅ | Synchronized methods |
| OrderCache (collections) | ✅ | CopyOnWriteArrayList + ConcurrentHashMap symbol indices |
| Wyckoff Records | ✅ | Immutable |
| Model POJOs | ❌ | Use synchronization if shared |

---

## Edge Cases

- **OrderRequest tag**: Null becomes empty string
- **ActiveOrder stop loss**: Trailing only in beneficial direction
- **OrderCache**: `addOrderRequest()` removes duplicates first, rebuilds symbol index entry
- **OrderCache**: `removeActiveOrder()` removes from active list + symbol index, then moves to completed
- **OrderCache**: Symbol indices (`orderRequestsBySymbol`, `activeOrdersBySymbol`) are updated on every mutation

---

## See Also

- **fno-utils**: Utilities including FileUtils (order formatting)
- **fno-kite-reader**: KiteITMResolver implementation
- **fno-technicals**: Technical indicators using Candle data
- **fno-phase-analyzer**: Wyckoff phase identifier implementations
