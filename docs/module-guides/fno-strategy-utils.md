# fno-strategy-utils Module Guide

Advanced strategy utilities: Heikin-Ashi trend analysis, price action detection, CPR calculations, order flow management.

## Maven Dependency
```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-strategy-utils</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Dependencies:** fno-models, fno-utils, fno-technicals

---

## Package Overview

| Package | Description |
|---------|-------------|
| `com.vish.fno.strategy` | HATrendUtils, Point2D, PointType |
| `com.vish.fno.strategy.util` | CPRUtils |
| `com.vish.fno.strategy.priceaction` | DataAnalyser, Point, ChartPoint, Vector2, Line |
| `com.vish.fno.strategy.orderflow` | TargetAndStopLossStrategy, PartialRevisingStopLoss, DualTargetRevisingStoplossStrategy, OrderManagerUtils |

---

## Trend Analysis

### HATrendUtils

Static, thread-safe. Weighted scoring with 5-candle window.

| Method | Returns | Description |
|--------|---------|-------------|
| `getTrend(List<Candle>)` | `Trend` | Current trend (UPTREND, DOWNTREND, INDECISIVE, CONSOLIDATION) |
| `getTrendList(List<Candle>)` | `List<Trend>` | Complete trend history |
| `getSmoothedTrends(List<Candle>)` | `List<Trend>` | Weighted smoothed trends |
| `getMaximaMinima(List<Candle>)` | `List<Point2D>` | Local maxima/minima with coordinates |

**Scoring:** HH/HL +5, strong candle +3, regular candle +1

### Point2D

```java
@Data
public class Point2D implements Comparable<Point2D> {
    int x;         // Time index
    double y;      // Price level
    PointType type; // MINIMA, MAXIMA, BOTH
}
```

---

## CPR Calculations

### CPRUtils

```java
Map<String, Float> pivots = CPRUtils.getFloorPivots(previousDayCandle);
```

**Returns:** `pivotPoint`, `topCentralPivot`, `bottomCentralPivot`, `r1`-`r4`, `s1`-`s4`

**Formulas:**
- Pivot = (H + L + C) / 3
- BC = (H + L) / 2
- TC = Pivot + (Pivot - BC)

**Interpretation:** Narrow CPR (<0.2% of pivot) = trending day, Wide CPR (>1%) = sideways day

---

## Price Action Analysis

### DataAnalyser

Static, thread-safe.

| Method | Returns | Description |
|--------|---------|-------------|
| `getMaximaMinimaPoints(candles, range)` | `List<Point2D>` | Merged swing points |
| `calculateMinimaPoints(candles, range)` | `Set<Point2D>` | Support levels |
| `calculateMaximaPoints(candles, range)` | `Set<Point2D>` | Resistance levels |
| `findCollinearPoints(points, startIndex)` | `Set<Line>` | Lines through 3+ collinear points |
| `getActiveLines(candles, lines, isMaxima)` | `SortedSet<Line>` | Unbroken trendlines |
| `removeShortLines(lines)` | `void` | Remove < 40 candles |
| `removeTooClosePoints(lines)` | `void` | Remove < 9 candles apart |
| `joinLongCollinearPoints(lines)` | `void` | Merge overlapping |
| `containsPoint(candle, y, isMaxima)` | `boolean` | Check candle contains price |

### Line

Trendline through 3+ points.

| Method | Returns | Description |
|--------|---------|-------------|
| `Line(Point... args)` | - | Constructor |
| `getY(float x)` | `float` | Y at given x (y = mx + c) |
| `getSlope()` | `float` | Angle in degrees |
| `getXAxisLength()` | `float` | Line span |
| `getFirstPoint()` / `getLastPoint()` | `Point` | Endpoints (via `TreeSet.first()`/`last()`) |
| `getPoints()` | `SortedSet<Point>` | All points |
| `isActiveLine()` | `boolean` | Not broken |
| `getHitPoint()` | `Point` | Breakout point |

### Point

```java
@Getter @Setter
public class Point extends Vector2 implements Comparable<Point> {
    private PointType type;
    public Point(float x, float y)
}
```

### Vector2

Minimal 2D vector with public `x`, `y` fields (float). Constructor: `Vector2(float x, float y)`. Implements `equals()`, `hashCode()`, `toString()`. No arithmetic methods -- reduced to data-only class.

---

## Order Flow Management

### TargetAndStopLossStrategy Interface

```java
public interface TargetAndStopLossStrategy {
    OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp);
    OrderSellDetailModel isStopLossHit(ActiveOrder order, double ltp);
}
```

### AbstractTargetAndStopLossStrategy

Base class implementing `TargetAndStopLossStrategy`. Provides shared call/put logic via protected methods:
- `checkTargetAchieved(order, ltp)` -- Call: `ltp > target`, Put: `ltp < target` (reads `order.getOrderRequest().getTarget().first()` — uses the first/primary target price from the `Target` value class)
- `checkStopLossHit(order, ltp)` -- Call: `ltp < stopLoss`, Put: `ltp > stopLoss`
- `isStopLossHit(order, ltp)` -- Implemented: sells remaining quantity on SL hit

Subclasses override `isTargetAchieved()` and optionally `isStopLossHit()`.

### FixedTargetAndStopLossStrategy

Extends `AbstractTargetAndStopLossStrategy`. Exits full position on target/SL hit. No revision.

```java
TargetAndStopLossStrategy strategy = new FixedTargetAndStopLossStrategy();
OrderSellDetailModel result = strategy.isTargetAchieved(order, ltp);
if (result.sellOrder()) { /* sell */ }
```

### PartialRevisingStopLoss

Partial booking with trailing stop-loss using Heikin-Ashi lows/highs.

```java
PartialRevisingStopLoss strategy = new PartialRevisingStopLoss(candleStore);
```

**Partial Booking Logic:**
- 1-2 lots: Sell all on target
- 3+ lots: Sell 2/3, trail remaining

**Lots to Sell:** 1->1, 2-3->2, 4-5->3, 6-7->4, 8+->2/3 total

**Stop Loss Revision:** Call orders trail to HA low, Put orders trail to HA high. Uses `ActiveOrder.isCallOrder()` directly (no pattern matching needed -- each subclass implements `isCallOrder()`).

**SL Revision Throttling:** Tracks HA candle count per order via `lastRevisionCandleCount` (`ConcurrentHashMap<String, Integer>`). Key is `tradingSymbol + "-" + entryTimeStamp`. Only revises stop-loss when a new HA candle has formed (candle count changed), preventing redundant per-tick revision within the same candle. Null/empty guards on candle lists return early without revision.

### DualTargetRevisingStoplossStrategy

Extends `AbstractTargetAndStopLossStrategy`. Planned dual-target strategy where first target acts as stop-loss in reverse direction. **Not yet implemented** -- `isTargetAchieved()` throws `UnsupportedOperationException`. Inherits `isStopLossHit()` from base class (functional for call/put logic).

```java
// NOT YET IMPLEMENTED - isTargetAchieved() throws UnsupportedOperationException
// isStopLossHit() works via inherited AbstractTargetAndStopLossStrategy
DualTargetRevisingStoplossStrategy strategy = new DualTargetRevisingStoplossStrategy();
```

### DualTargetStopLossStrategy

Extends `AbstractTargetAndStopLossStrategy`. 2-target partial exit strategy operating on `MultiTargetOrder` instances.

**Flow:** T1 hit --> sell group 1, SL revises to T1 price. T2 hit --> sell remaining. SL hit at any point --> sell ALL remaining.

```java
public OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp)
```

- Returns no-sell (`new OrderSellDetailModel(false)`) for non-`MultiTargetOrder` instances or when all targets exhausted.
- On target hit: calls `multiOrder.advanceTarget()` (which revises SL), returns `OrderSellDetailModel(true, qty, TARGET_HIT, order)`.
- Inherits `isStopLossHit()` from `AbstractTargetAndStopLossStrategy` -- sells ALL remaining quantity on SL breach.

```java
import com.vish.fno.strategy.orderflow.DualTargetStopLossStrategy;

TargetAndStopLossStrategy strategy = new DualTargetStopLossStrategy();
OrderSellDetailModel result = strategy.isTargetAchieved(multiTargetOrder, ltp);
if (result.sellOrder()) { /* partial sell of result.quantity() */ }
```

### TripleTargetStopLossStrategy

Extends `AbstractTargetAndStopLossStrategy`. 3-target partial exit strategy. Identical core logic to `DualTargetStopLossStrategy` -- the `MultiTargetOrder` interface handles target iteration internally. Exists as a separate `StopLossType` dispatch target.

**Flow:** T1 hit --> sell group 1, SL-->T1. T2 hit --> sell group 2, SL-->T2. T3 hit --> sell group 3. SL hit at any point --> sell ALL remaining.

```java
public OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp)
```

Same behavior as `DualTargetStopLossStrategy.isTargetAchieved()`.

### TrailingMultiTargetStopLossStrategy

Extends `AbstractTargetAndStopLossStrategy`. T1/T2 are fixed partial exits; remaining position enters trailing mode with SL revision on new price extremes.

**Flow:** T1 hit --> sell group 1, SL-->T1. T2 hit --> sell group 2, SL-->T2. After all fixed targets: trailing mode -- SL revises toward price on new highs (CE) or new lows (PE). SL hit at any point --> sell ALL remaining.

```java
public OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp)
public OrderSellDetailModel isStopLossHit(ActiveOrder order, double ltp)
```

- `isTargetAchieved()`: Same partial-exit logic as `DualTargetStopLossStrategy` while targets remain. After all targets exhausted, updates trailing extreme and revises SL (returns no-sell).
- `isStopLossHit()`: Overrides base class. On SL breach, sells ALL remaining quantity and cleans up trailing state. Uses `ConcurrentHashMap<String, Double>` for trailing extremes.

**Trailing SL revision:** CE orders: new SL = `extreme - (extreme - currentSL) * 0.5` (moves SL up toward high). PE orders: new SL = `extreme + (currentSL - extreme) * 0.5` (moves SL down toward low).

**Thread safety:** `trailingExtremes` uses `ConcurrentHashMap` with `merge()` for atomic extreme updates. Key format: `tag_index`.

```java
import com.vish.fno.strategy.orderflow.TrailingMultiTargetStopLossStrategy;

TargetAndStopLossStrategy strategy = new TrailingMultiTargetStopLossStrategy();
// Fixed targets processed first, then trailing mode kicks in automatically
```

### OrderManagerUtils

Static utility for exit conditions.

```java
OrderSellDetailModel exit = OrderManagerUtils.isExitCondition(strategy, ltp, timestampIndex, order);
```

**Exit Priority:**
1. Time-based (index > 368, ~3:23 PM)
2. Max hold duration (triple barrier time stop) — reads `OrderMetadata.maxHoldDuration` via `order.getOrderRequest().getOrderMetadata()` against elapsed minutes since `order.getEntryTimeStamp()`
3. Stop-loss
4. Target

---

## Bug Fixes

- **`Double.MIN_VALUE` in `getMaximaMinima()` (Feb 2026):** The `maxima` fallback in `HATrendUtils.getMaximaMinima()` used `Double.MIN_VALUE` (smallest positive double) instead of `-Double.MAX_VALUE`. Fixed to correctly identify maxima when candle highs could theoretically be near zero or negative.

---

## Thread Safety

| Component | Thread-Safe | Notes |
|-----------|-------------|-------|
| HATrendUtils, CPRUtils, DataAnalyser | ✅ | Static, stateless |
| OrderManagerUtils, FixedTargetAndStopLossStrategy, DualTargetRevisingStoplossStrategy | ✅ | Stateless |
| DualTargetStopLossStrategy, TripleTargetStopLossStrategy | ✅ | Stateless; delegates state to `MultiTargetOrder` |
| TrailingMultiTargetStopLossStrategy | ✅ | `trailingExtremes` uses `ConcurrentHashMap` with atomic `merge()` |
| Point2D, Line | No | Mutable |
| PartialRevisingStopLoss | Partial | `lastRevisionCandleCount` uses ConcurrentHashMap; modifies order state via CandleStore |

---

## Performance

- HATrendUtils: O(n), 5-candle window
- DataAnalyser collinearity: O(n³)
- Active line detection: O(lines × candles)

**Tips:** Use range 10-20 for peak detection, cache HA candles, filter lines early

---

## See Also

- **fno-models**: Order and candle models
- **fno-utils**: HeikinAshi, CandleUtils
- **fno-technicals**: Technical indicators
