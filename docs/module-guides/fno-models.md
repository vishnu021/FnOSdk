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
| `com.vish.fno.model.order` | Order sell details, exit reasons, SteppedStepProfile |
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

### StopLossType

```java
import com.vish.fno.model.order.StopLossType;
```

Determines exit strategy behavior for an order.

| Constant | Description |
|----------|-------------|
| `FIXED` | Exit full position on target or stop-loss hit |
| `PARTIAL_REVISING` | Partial profit-taking at target with trailing stop-loss (HA-based) |
| `DUAL_TARGET` | 2-target partial exit: T1 sells group 1, SL revises to T1, T2 sells rest |
| `TRIPLE_TARGET` | 3-target partial exit: T1/T2/T3 with SL revision at each level |
| `TRAILING_MULTITARGET` | T1/T2 fixed exits, remainder trails with SL revision on new highs/lows |
| `BREAKEVEN_TRAILING` | Moves SL to entry (breakeven) once trade goes green, then trails at 50% convergence toward new extremes |
| `STEPPED` | Uses intermediate targets as SL revision checkpoints without partial selling (1-lot safe) |

Used by fno-strategy-utils to dispatch to the correct `TargetAndStopLossStrategy` implementation.

### SteppedStepProfile

```java
import com.vish.fno.model.order.SteppedStepProfile;
```

Predefined step ratio profiles for `StopLossType.STEPPED`. Controls where SL revision checkpoints are placed as fractions of target distance.

| Profile | Fractions | Best For |
|---------|-----------|----------|
| `EVEN` | 33.3% / 66.7% / 100% | Medium targets (30-50pt) -- default |
| `FIBONACCI` | 38.2% / 61.8% / 100% | Wide structural targets (50-150pt) |
| `CONSERVATIVE` | 50% / 75% / 100% | Short targets (20-30pt) |
| `AGGRESSIVE` | 25% / 50% / 100% | Earliest breakeven protection |

**Method:** `getFractions()` returns `List<Double>` -- the checkpoint fractions for the profile.

Backtest data (100-day, Nov 2025 - Mar 2026): FIBONACCI best for wide targets (TkExtremaGold +26%), EVEN for medium (WyckSpring, NR4Sweep baseline), CONSERVATIVE marginal for short targets.

### StrikePolicy

```java
import com.vish.fno.model.order.StrikePolicy;
```

Controls how many strike intervals away from ATM the option entry strike is chosen. Formula: `targetStrike = ATM + (isCall ? -1 : +1) * offset * strikeInterval`.

| Constant | Offset | Description |
|----------|--------|-------------|
| `OTM_2` | `-2` | 2 strikes out-of-the-money |
| `OTM_1` | `-1` | 1 strike out-of-the-money |
| `ATM` | `0` | At-the-money (nearest strike) |
| `ITM_1` | `1` | 1 strike in-the-money |
| `ITM_2` | `2` | 2 strikes in-the-money |

| Method | Returns | Description |
|--------|---------|-------------|
| `getOffset()` | `int` | Signed offset from ATM |

Used by `Task.getStrikePolicy()` and `KiteService.getOptionStock()` for policy-based strike selection.

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
| `verifyBuyThreshold(Ticker)` | `Optional<OrderRequest>` | Returns order if threshold crossed |
| `getMaxHoldDuration()` | `int` | Default `0` (no limit). Max minutes to hold before time-stop (triple barrier) |
| `getExtraData()` | `Map<String, String>` | Default `Map.of()`. Strategy-specific key-value pairs copied into `ActiveOrder.extraData` on creation |
| `isCallOrder()` | `boolean` | Default returns `true`; override for put orders |

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
| `MultiTargetOrderRequest` | Multi-target index strategies | Requires `target.size() >= 2`, `task.getLots() >= 2`. Creates `MultiTargetActiveIndexOrder` |
| `MultiTargetTickOrderRequest` | Multi-target tick strategies | `verifyBuyThreshold()` always returns self. Creates `MultiTargetTickActiveOrder` |

All five store `Target target` (not `double`), `int maxHoldDuration`, and `Map<String, String> extraData`. Each provides a partial Lombok builder class with a backward-compatible `.target(double)` overload that wraps to `Target.of(val)`. Lombok also generates `.target(Target)` for multi-target usage. If `extraData` is null at construction, it defaults to `Map.of()`.

**Builder Pattern (single target — backward compatible):**
```java
IndexOrderRequest.builder("TAG", "NIFTY", task)
    .optionSymbol("NIFTY24OCT19500CE").buyThreshold(19500.0)
    .target(19600.0).stopLoss(19450.0).callOrder(true)
    .maxHoldDuration(30)
    .extraData(Map.of("signal", "breakout"))
    .build();
```

**Builder Pattern (multi-target with MultiTargetOrderRequest):**
```java
import com.vish.fno.model.order.Target;
import com.vish.fno.model.order.orderrequest.MultiTargetOrderRequest;

MultiTargetOrderRequest.builder("TAG", "NIFTY", task)
    .optionSymbol("NIFTY24OCT19500CE").buyThreshold(19500.0)
    .target(Target.of(19600.0, 19650.0)).stopLoss(19450.0).callOrder(true)
    .maxHoldDuration(45)
    .build();
```

**Builder Pattern (multi-target tick-based):**
```java
import com.vish.fno.model.order.orderrequest.MultiTargetTickOrderRequest;

MultiTargetTickOrderRequest.builder("TAG", "NIFTY", task)
    .optionSymbol("NIFTY24OCT19500CE").buyThreshold(19500.0)
    .target(Target.of(19600.0, 19650.0, 19700.0)).stopLoss(19450.0).callOrder(true)
    .extraData(Map.of("subSignal", "momentum"))
    .build();
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

`getExtraData()` returns `Collections.unmodifiableMap(extraData)` -- external callers can read but not mutate. Use `appendExtraData(key, value)` to add entries. On construction, all entries from `orderRequest.getExtraData()` are copied into a mutable `HashMap`, and `entryDateTime` is automatically added.

Consolidated `toString()` with `appendToStringFields(StringBuilder)` hook -- subclasses override to add extra fields (e.g., `ActiveIndexOrder` appends `optionSymbol`). The `toString()` output conditionally includes `kiteOrderId` from the `extraData` map when present, aiding order tracking in logs.

### Implementations

| Class | Source | Stop Loss Behavior |
|-------|--------|-------------------|
| `ActiveIndexOrder` | `IndexOrderRequest` | Call: only up, Put: only down |
| `OptionBasedActiveOrder` | `OptionBasedOrderRequest` | Only up (always long) |
| `TickBasedActiveOrder` | `TickBasedOrderRequest` | Same as ActiveIndexOrder |
| `MultiTargetActiveIndexOrder` | `MultiTargetOrderRequest` | Same as ActiveIndexOrder + multi-target tracking |
| `MultiTargetTickActiveOrder` | `MultiTargetTickOrderRequest` | Same as TickBasedActiveOrder + multi-target tracking |

### MultiTargetOrder Interface

```java
import com.vish.fno.model.order.activeorder.MultiTargetOrder;
```

Extends `ActiveOrder` for orders with multiple target levels and partial exits. Implemented by `MultiTargetActiveIndexOrder` and `MultiTargetTickActiveOrder`. Used by `DualTargetStopLossStrategy`, `TripleTargetStopLossStrategy`, and `TrailingMultiTargetStopLossStrategy` (fno-strategy-utils).

| Method | Returns | Description |
|--------|---------|-------------|
| `advanceTarget()` | `void` | Advances to next target level; revises SL to current target price |
| `getCurrentTargetQuantity()` | `int` | Lot-aligned quantity to sell at current target level |
| `hasMoreTargets()` | `boolean` | `true` if there are unreached targets |
| `getCurrentTarget()` | `double` | Price of the current active target |
| `getCurrentTargetIndex()` | `int` | 0-based index of the current target |

**Quantity Distribution:** Lots are distributed evenly across targets. Remainder lots go to the last target. Example: 7 lots with 3 targets = [2, 2, 3] lots per target.

### MultiTargetActiveIndexOrder / MultiTargetTickActiveOrder

Both mirror their single-target counterparts (`ActiveIndexOrder` / `TickBasedActiveOrder`) with additional multi-target state:
- `currentTargetIndex` (int) -- tracks which target is active (0-based)
- `targetQuantities` (List<Integer>) -- immutable, lot-aligned quantities per target
- `appendToStringFields()` appends `targetIndex=N/M` to log output

### ActiveOrderFactory

Uses Java 21 pattern matching switch expression. Throws `IllegalArgumentException` for unknown `OrderRequest` types.

```java
public static ActiveOrder createOrder(OrderRequest orderRequest, double ltp, int timestamp, String orderEntryTimestamp,
                                      int quantity, int lotSize)
```

**Dispatch order** (multi-target cases MUST precede their single-target parents due to subtype matching):

| `OrderRequest` type | Creates |
|---------------------|---------|
| `MultiTargetOrderRequest` | `MultiTargetActiveIndexOrder` |
| `MultiTargetTickOrderRequest` | `MultiTargetTickActiveOrder` |
| `IndexOrderRequest` | `ActiveIndexOrder` |
| `OptionBasedOrderRequest` | `OptionBasedActiveOrder` |
| `TickBasedOrderRequest` | `TickBasedActiveOrder` |

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
| `getStopLossStrategy()` | `StopLossType` | Stop-loss type (default: `FIXED`) |
| `getStrikePolicy()` | `StrikePolicy` | Strike selection policy (default: `ATM`) |
| `getAllowedSessions()` | `List<String>` | Allowed trading sessions (default: empty = all allowed) |
| `getSteppedStepRatios()` | `List<Double>` | Custom step fractions for STEPPED SL (default: empty = 33/66/100%) |
| `getSteppedStepProfile()` | `SteppedStepProfile` | Step profile for STEPPED SL (default: `EVEN`). Non-EVEN overrides `getSteppedStepRatios()` |

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
- **MultiTargetOrder quantity distribution**: `computeQuantities(numTargets, totalLots, lotSize)` divides lots evenly; remainder lots added to the last target. If `lotSize <= 0`, all quantities are 0.
- **MultiTargetOrder advanceTarget()**: No-op if all targets already reached (`currentTargetIndex >= target.size()`)
- **MultiTargetOrder getCurrentTarget()**: Returns the last target price if all targets are exhausted
- **ActiveOrderFactory switch order**: `MultiTargetOrderRequest` and `MultiTargetTickOrderRequest` cases must appear before `IndexOrderRequest` and `TickBasedOrderRequest` respectively, since the multi-target types do not extend the single-target types but are matched first

---

## See Also

- **fno-utils**: Utilities including FileUtils (order formatting)
- **fno-kite-reader**: KiteITMResolver implementation
- **fno-technicals**: Technical indicators using Candle data
- **fno-phase-analyzer**: Wyckoff phase identifier implementations
