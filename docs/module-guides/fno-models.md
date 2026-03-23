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
| `com.vish.fno.model` | Core enums (Exchange, InstrumentType, PositionType), Candle, Ticker |
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

## Core Enums

Type-safe replacements for the string constants previously in `FnoConstants` (fno-utils). All three enums share the same API: `getCode()`, `matches(String)`, `toString()`.

### Exchange

```java
import com.vish.fno.model.Exchange;
```

| Constant | Code | Usage |
|----------|------|-------|
| `Exchange.NSE` | `"NSE"` | National Stock Exchange |
| `Exchange.NFO` | `"NFO"` | NSE F&O segment |
| `Exchange.BFO` | `"BFO"` | BSE F&O segment |
| `Exchange.BSE` | `"BSE"` | Bombay Stock Exchange |

### InstrumentType

```java
import com.vish.fno.model.InstrumentType;
```

| Constant | Code | Usage |
|----------|------|-------|
| `InstrumentType.CE` | `"CE"` | Call option |
| `InstrumentType.PE` | `"PE"` | Put option |
| `InstrumentType.FUT` | `"FUT"` | Futures contract |

### PositionType

```java
import com.vish.fno.model.PositionType;
```

| Constant | Code | Usage |
|----------|------|-------|
| `PositionType.EQUITY` | `"equity"` | Equity position |
| `PositionType.NET` | `"net"` | Net position |
| `PositionType.DAY` | `"day"` | Day position |

### Shared Enum API

| Method | Returns | Description |
|--------|---------|-------------|
| `getCode()` | `String` | Raw string code (e.g., `"NFO"`, `"CE"`, `"equity"`) |
| `matches(String value)` | `boolean` | Case-insensitive match against a raw string |
| `toString()` | `String` | Returns `getCode()` |

```java
// Migration from old FnoConstants string constants:
// Before: if (exchange.equals(FnoConstants.NFO)) { ... }
// After:  if (Exchange.NFO.matches(exchange)) { ... }

// Before: String exch = FnoConstants.NSE;
// After:  String exch = Exchange.NSE.getCode();
```

---

## Order Request Package

### OrderRequest Interface

| Method | Returns | Description |
|--------|---------|-------------|
| `getIndex()` | `String` | Trading index/symbol |
| `getBuyThreshold()` | `double` | Price threshold for order |
| `getTarget()` | `Target` | Target price(s) — returns `Target` value class wrapping one or more prices |
| `getStopLoss()` | `double` | Stop loss price |
| `getExpirationTimestamp()` | `int` | Order expiration time index |
| `getTag()` | `String` | Unique order tag |
| `getTask()` | `Task` | Associated task |
| `getDate()` | `Date` | Order date |
| `getOrderMetadata()` | `OrderMetadata` | Strategy-specific metadata (maxHoldDuration, subSignal) |
| `verifyBuyThreshold(Ticker)` | `Optional<OrderRequest>` | Returns order if threshold crossed |
| `isCallOrder()` | `boolean` | Default returns `true`; override for put orders |

### OrderMetadata

Typed value object replacing the previous `Map<String, String> extraData` on `OrderRequest`. Fields:

| Field | Type | Description |
|-------|------|-------------|
| `maxHoldDuration` | `int` | Max minutes to hold before time-stop (0 = no limit) |
| `subSignal` | `String` | Strategy sub-signal identifier (copied to `ActiveOrder.extraData` on creation) |

```java
OrderMetadata.builder().maxHoldDuration(30).subSignal("breakout").build();
```

### Target Value Class

Immutable value class wrapping one or more target prices. Package: `com.vish.fno.model.order`.

**Factory Methods:**

| Method | Description |
|--------|-------------|
| `Target.of(double...)` | Varargs: `Target.of(19600.0)` or `Target.of(t1, t2, t3)` |
| `Target.of(List<Double>)` | From list (defensive copy via `List.copyOf()`) |

**Instance Methods:**

| Method | Returns | Description |
|--------|---------|-------------|
| `first()` | `double` | First (or only) target price — primary accessor for single-target orders |
| `get(int)` | `double` | Target at index |
| `size()` | `int` | Number of targets |
| `isMultiTarget()` | `boolean` | `true` if more than one target |
| `asList()` | `List<Double>` | Immutable list of all targets |
| `toString()` | `String` | Displays full list, e.g. `[24550.0]` or `[24550.0, 24600.0, 24650.0]` |

Implements `equals()`/`hashCode()` based on the wrapped list.

### Implementations

| Class | Use Case | Key Difference |
|-------|----------|----------------|
| `IndexOrderRequest` | Index futures/options | Has `optionSymbol`, `callOrder` flag |
| `OptionBasedOrderRequest` | Option premium trading | No option symbol, premium-based |
| `TickBasedOrderRequest` | High-frequency trading | `verifyBuyThreshold()` always returns self |

All three store `Target target` (not `double`). Each provides a partial Lombok builder class with a backward-compatible `.target(double)` overload that wraps to `Target.of(val)`. Lombok also generates `.target(Target)` for multi-target usage.

**Builder Pattern (single target — backward compatible):**
```java
IndexOrderRequest.builder("TAG", "NIFTY", task)
    .optionSymbol("NIFTY24OCT19500CE").buyThreshold(19500.0)
    .target(19600.0).stopLoss(19450.0).callOrder(true).build();
```

**Builder Pattern (multi-target):**
```java
IndexOrderRequest.builder("TAG", "NIFTY", task)
    .optionSymbol("NIFTY24OCT19500CE").buyThreshold(19500.0)
    .target(Target.of(19600.0, 19650.0, 19700.0)).stopLoss(19450.0).callOrder(true).build();
```

---

## Active Order Package

### ActiveOrder Interface

Organized into semantic groups:

**Identity:**

| Method | Returns | Description |
|--------|---------|-------------|
| `getOrderRequest()` | `OrderRequest` | Source order request (single source of truth for immutable metadata) |
| `getTradingSymbol()` | `String` | Trading symbol |

**Entry state:**

| Method | Returns | Description |
|--------|---------|-------------|
| `getBuyPrice()` | `double` | Buy execution price |
| `getBuyQuantity()` | `int` | Buy quantity |
| `getLotSize()` | `int` | Lot size for the instrument |
| `getEntryTimeStamp()` | `int` | Entry minute index |
| `setOptionBuyPrice(double)` | `void` | Sets option buy price (no-op for `OptionBasedActiveOrder` where buyPrice IS the option price) |
| `getOptionBuyPrice()` | `double` | Option buy price (`buyOptionPrice` for index/tick orders, `buyPrice` for option-based orders) |

**Risk management:**

| Method | Returns | Description |
|--------|---------|-------------|
| `getStopLoss()` | `double` | Current stop-loss price (initialized from OrderRequest, updated by trailing logic) |
| `setStopLoss(double)` | `void` | Updates stop loss (trailing only in beneficial direction) |

**Exit / sell state:**

| Method | Returns | Description |
|--------|---------|-------------|
| `getSellPrice()` | `double` | Sell execution price |
| `getOptionSellPrice()` | `double` | Option sell price (`sellOptionPrice` for index/tick orders, `sellPrice` for option-based orders) |
| `getExitTimeStamp()` | `int` | Exit minute index |
| `getSoldQuantity()` | `int` | Quantity sold so far |
| `incrementSoldQuantity(int, double)` | `void` | Tracks partial exits |
| `closeOrder(double, int, String)` | `void` | Closes the order (sets sellPrice, exitTimeStamp, adds exitDateTime + profit to extraData) |

**Computed:**

| Method | Returns | Description |
|--------|---------|-------------|
| `getProfit()` | `double` | Unrealised P&L |
| `getRealisedProfit()` | `double` | Realised P&L (tracks partial exits) |
| `isCallOrder()` | `boolean` | Call (true) or put (false); abstract -- each subclass declares direction (`OptionBasedActiveOrder` always returns `true`) |

**Runtime diagnostics:**

| Method | Returns | Description |
|--------|---------|-------------|
| `getExtraData()` | `Map<String, String>` | Read-only extra data (unmodifiable) |
| `appendExtraData(String, String)` | `void` | Add key-value to extra data |

**Note:** Immutable order metadata (`date`, `buyThreshold`, `tag`, `index`, `target`, `task`) is accessed via `getOrderRequest()` -- the `ActiveOrder` interface no longer exposes `getTag()`, `getIndex()`, or `getTarget()`. Callers must use `order.getOrderRequest().getTag()`, `.getIndex()`, `.getTarget()`. Setters for `sellPrice`, `exitTimeStamp`, and `active` have been removed from the interface; `closeOrder()` is the single entry point for exit state changes. `isCallOrder()` is now abstract (was default returning `true`).

### AbstractActiveOrder

Base class storing a reference to the source `OrderRequest` plus mutable execution state: `entryTimeStamp`, `exitTimeStamp`, `buyPrice`, `buyQuantity`, `soldQuantity`, `sellPrice`, `stopLoss`, `extraData`, `stopLossRevisionCount`, `stopLossRevision`, `realisedProfit`.

Immutable order identity (`tag`, `index`, `target`, `date`, `task`) is accessed exclusively via `getOrderRequest()`.

`getExtraData()` returns `Collections.unmodifiableMap(extraData)` -- external callers can read but not mutate. Use `appendExtraData(key, value)` to add entries. On construction, `entryDateTime` is automatically added; subclasses copy `subSignal` from `OrderMetadata` if present.

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
Factory methods:
- `ExitDetail.forRegularOrder(qty, price)` -- creates with `sellOptionPrice=null`
- `ExitDetail.forIndexOrder(qty, sellPrice, sellOptionPrice)` -- creates with all fields

Uses `@JsonInclude(NON_NULL)` to omit null `sellOptionPrice` from JSON.

Exit reason is tracked at the `ActiveOrder` level via `extraData["orderExitReason"]`, not in `ExitDetail`.

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

Thread-safe cache for order requests, active orders, and completed orders with cash management. Maintains secondary symbol indices (`ConcurrentHashMap`) for O(1) lookups by index symbol and a composite key set (`activeOrderKeys`) for O(1) duplicate detection. The hot-path methods `checkEntryInOpenOrders` and `getActiveOrderForSymbol` are called on every tick (~520/sec); the symbol index and key set avoid full-list scans.

```java
OrderCache cache = new OrderCache(100000.0);
cache.addOrderRequest(request);
cache.deductCash(orderCost);  // ReentrantLock (VT-safe)
cache.addCash(sellValue);     // ReentrantLock (VT-safe)
double cash = cache.getAvailableCash();  // ReentrantLock (VT-safe)
List<ActiveOrder> completed = cache.getCompletedOrders();  // Orders moved from active on removal
```

| Method | Thread-Safe | Description |
|--------|-------------|-------------|
| `getAvailableCash()` | ✅ ReentrantLock | Read cash balance |
| `deductCash(double)` | ✅ ReentrantLock | Atomic deduction |
| `addCash(double)` | ✅ ReentrantLock | Atomic addition |
| `checkEntryInOpenOrders(Ticker, String)` | ✅ | O(1) symbol index lookup, check trigger conditions |
| `isNotInActiveOrders(OrderRequest)` | ✅ | O(1) check via `activeOrderKeys` composite key set (tag + index) |
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
| OrderCache (cash ops) | ✅ | `ReentrantLock` (VT-safe, replaces synchronized) |
| OrderCache (collections) | ✅ | CopyOnWriteArrayList + ConcurrentHashMap symbol indices + ConcurrentHashMap.newKeySet() for activeOrderKeys |
| Wyckoff Records | ✅ | Immutable |
| Model POJOs | ❌ | Use synchronization if shared |

---

## Edge Cases

- **OrderRequest tag**: Null becomes empty string
- **ActiveOrder identity**: `getTag()`, `getIndex()`, `getTarget()` removed from interface; use `getOrderRequest().getTag()`, `.getIndex()`, `.getTarget()`. Note: `.getTarget()` now returns `Target` (not `double`); use `.getTarget().first()` for the numeric price
- **ActiveOrder stop loss**: Trailing only in beneficial direction
- **ActiveOrder isCallOrder()**: Abstract method; was default returning `true` -- all subclasses must implement
- **ExitDetail**: `exitReason` removed (was 4-field record, now 3-field). Exit reason tracked at `ActiveOrder` level via `extraData["orderExitReason"]`
- **OrderCache**: `addOrderRequest()` removes duplicates first, rebuilds symbol index entry
- **OrderCache**: `removeActiveOrder()` removes from active list + symbol index, then moves to completed
- **OrderCache**: Symbol indices (`orderRequestsBySymbol`, `activeOrdersBySymbol`) and `activeOrderKeys` set are updated on every mutation

---

## See Also

- **fno-utils**: Utilities including FileUtils (order formatting)
- **fno-kite-reader**: KiteITMResolver implementation
- **fno-technicals**: Technical indicators using Candle data
- **fno-phase-analyzer**: Wyckoff phase identifier implementations
