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
| `com.vish.fno.strategy.orderflow` | TargetAndStopLossStrategy, PartialRevisingStopLoss, DualTargetRevisingStoplossStrategy, BreakevenTrailingStopLossStrategy, SteppedStopLossStrategy, OrderManagerUtils |

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

### BreakevenTrailingStopLossStrategy

Extends `AbstractTargetAndStopLossStrategy`. Once the trade goes green (LTP crosses entry + buffer in favorable direction), SL moves to entry+buffer (breakeven). After that, trails at 50% convergence toward new price extremes. Works with 1 lot (no minimum lot requirement unlike `PARTIAL_REVISING`).

**Two phases:**
1. **Pre-breakeven**: Standard FIXED behavior -- check target and SL as normal
2. **Post-breakeven**: SL = entry + buffer (minimum). On each new favorable extreme, SL moves to midpoint between entry+buffer and extreme, locking in progressively more profit

**Constructors:**

```java
public BreakevenTrailingStopLossStrategy()
public BreakevenTrailingStopLossStrategy(double breakevenBuffer)
```

- Default constructor uses 3.0pt buffer (backward compatible).
- `breakevenBuffer`: index points beyond entry for the breakeven SL level. CE: `entry + buffer`, PE: `entry - buffer`. Higher buffer = more guaranteed profit per exit, fewer trades reach threshold.

```java
public OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp)
public OrderSellDetailModel isStopLossHit(ActiveOrder order, double ltp)
```

- `isTargetAchieved()`: Checks if trade is green past buffer, moves SL to breakeven on first occurrence. Tracks trailing extremes and revises SL via 50% convergence. Standard target check for final exit.
- `isStopLossHit()`: Overrides base class. Sells ALL remaining quantity on SL breach. Logs whether SL was hit pre-breakeven or post-breakeven.

**Trailing SL revision:** CE: `newSL = breakevenLevel + (extreme - breakevenLevel) * 0.5`. PE: `newSL = breakevenLevel - (breakevenLevel - extreme) * 0.5`. SL only moves in beneficial direction.

**Thread safety:** `breakevenAchieved` and `trailingExtremes` use `ConcurrentHashMap`. Key format: `tag_index_entryTimeStamp`.

**Best suited for:** Wide-SL strategies (SLPanicFE, TkExtremaGold) where trades that go green often reverse to full SL losses.

```java
import com.vish.fno.strategy.orderflow.BreakevenTrailingStopLossStrategy;

TargetAndStopLossStrategy defaultStrategy = new BreakevenTrailingStopLossStrategy();      // 3.0pt buffer
TargetAndStopLossStrategy widerBuffer    = new BreakevenTrailingStopLossStrategy(5.0);    // 5.0pt buffer
// SL auto-moves to breakeven once trade goes green, then trails toward new extremes
```

### SteppedStopLossStrategy

Extends `AbstractTargetAndStopLossStrategy`. Auto-generates intermediate SL revision checkpoints between entry and target. No partial selling -- full exit only at final target or SL hit. Works with any order type (1-lot safe).

**For standard orders:** Auto-generates steps using configurable fractions (default: 33%/66%/100%) of target distance.
**For `MultiTargetOrder`:** Uses strategy-defined intermediate targets directly.

**Constructors:**

```java
public SteppedStopLossStrategy()
public SteppedStopLossStrategy(double breakevenBuffer)
```

- Default constructor uses 0.0 buffer (SL moves to exact entry on first step -- backward compatible).
- `breakevenBuffer`: index points beyond entry for first-step SL revision. CE: `entry + buffer`, PE: `entry - buffer`. With buffer 5.0: SL moves to entry+5pts, guaranteeing ~Rs 634 per exit at 3 lots NIFTY ITM_1.

**Step fraction resolution priority** (via `resolveStepFractions(ActiveOrder)`):
1. **Enum profile**: `Task.getSteppedStepProfile()` -- if non-EVEN, uses profile fractions (e.g., FIBONACCI = 38.2/61.8/100%, LATE_67 = 66.7/100%)
2. **Raw ratios**: `Task.getSteppedStepRatios()` -- sorted, auto-appends 1.0 if missing
3. **Default**: EVEN (33%/66%/100%)

**Step progression (CE example, entry=22500, target=22600, SL=22475, FIBONACCI profile, buffer=0):**
- Steps at fractions [0.382, 0.618, 1.0]: [22538.2, 22561.8, 22600]
- Price crosses 22538.2 (38.2%) --> SL moves to 22500 (entry + buffer = breakeven)
- Price crosses 22561.8 (61.8%) --> SL moves to 22538.2
- Price reaches 22600 (100%) --> SELL at full target
- If reverses from 22570 --> SL hit at 22538.2 = +38.2pt profit (not -25pt loss)

**With buffer=5.0 (LATE_67 profile, CE, entry=22500, target=22600):**
- Steps at fractions [0.667, 1.0]: [22566.7, 22600]
- Price crosses 22566.7 (67%) --> SL moves to 22505 (entry + 5pt buffer)
- Price reaches 22600 (100%) --> SELL at full target

```java
public OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp)
public OrderSellDetailModel isStopLossHit(ActiveOrder order, double ltp)
```

- `isTargetAchieved()`: For standard orders, creates/retrieves `StepState` (auto-generated steps). Checks each uncrossed step; first step revises SL to entry+buffer, subsequent steps to previous step level, final step triggers full exit. For `MultiTargetOrder`, uses `advanceTarget()` with same SL ratchet logic.
- `isStopLossHit()`: Cleans up step state, logs step level reached at SL hit.

**Thread safety:** `stepStates` uses `ConcurrentHashMap<String, StepState>` with `computeIfAbsent()`. Key format: `tag_index_entryTimeStamp`.

**Best suited for:** High R:R strategies (TkExtremaGold 4:1+, NR4Sweep 3.7:1) where aggressive trailing kills big winners but FIXED loses everything on reversals. Use FIBONACCI profile for wide structural targets, EVEN for medium targets, LATE_67/LATE_75 for retest-pattern strategies.

```java
import com.vish.fno.strategy.orderflow.SteppedStopLossStrategy;
import com.vish.fno.model.order.SteppedStepProfile;

TargetAndStopLossStrategy strategy     = new SteppedStopLossStrategy();        // 0pt buffer (exact breakeven)
TargetAndStopLossStrategy withBuffer   = new SteppedStopLossStrategy(5.0);     // 5pt buffer on first step
// Step fractions resolved from Task: profile (FIBONACCI/CONSERVATIVE/AGGRESSIVE/LATE_67/LATE_75) > raw ratios > default EVEN
```

### OrderManagerUtils

Static utility for exit conditions.

```java
OrderSellDetailModel exit = OrderManagerUtils.isExitCondition(strategy, ltp, timestampIndex, order);
```

**Exit Priority:**
1. Time-based (index > 368, ~3:23 PM)
2. Max hold duration (triple barrier time stop) -- reads `order.getOrderRequest().getMaxHoldDuration()` against elapsed minutes since `order.getEntryTimeStamp()`
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
| BreakevenTrailingStopLossStrategy | ✅ | `breakevenAchieved` and `trailingExtremes` use `ConcurrentHashMap` |
| SteppedStopLossStrategy | ✅ | `stepStates` uses `ConcurrentHashMap` with `computeIfAbsent()` |
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
