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

Base class with call/put logic:
- **Call:** Target when `ltp > target`, SL when `ltp < stopLoss`
- **Put:** Target when `ltp < target`, SL when `ltp > stopLoss`

### FixedTargetAndStopLossStrategy

Exits full position on target/SL hit. No revision.

```java
TargetAndStopLossStrategy strategy = new FixedTargetAndStopLossStrategy();
OrderSellDetailModel result = strategy.isTargetAchieved(order, ltp);
if (result.sellOrder()) { /* sell */ }
```

### PartialRevisingStopLoss

Partial booking with trailing stop-loss using Heikin-Ashi lows/highs.

```java
PartialRevisingStopLoss strategy = new PartialRevisingStopLoss(dataCache);
```

**Partial Booking Logic:**
- 1-2 lots: Sell all on target
- 3+ lots: Sell 2/3, trail remaining

**Lots to Sell:** 1→1, 2-3→2, 4-5→3, 6-7→4, 8+→2/3 total

**Stop Loss Revision:** Call orders trail to HA low, Put orders trail to HA high. Uses `isCallOrder()` helper: for `ActiveIndexOrder` checks `callOrder` flag, for `OptionBasedActiveOrder` always returns `true`.

### DualTargetRevisingStoplossStrategy

Planned dual-target strategy where first target acts as stop-loss in reverse direction. **Not yet implemented** - throws `UnsupportedOperationException` if used.

```java
// NOT YET IMPLEMENTED - will throw UnsupportedOperationException
DualTargetRevisingStoplossStrategy strategy = new DualTargetRevisingStoplossStrategy();
strategy.isTargetAchieved(order, ltp); // throws UnsupportedOperationException
```

### OrderManagerUtils

Static utility for exit conditions.

```java
OrderSellDetailModel exit = OrderManagerUtils.isExitCondition(strategy, ltp, timestampIndex, order);
```

**Exit Priority:**
1. Time-based (index > 368, ~3:23 PM)
2. Max hold duration (triple barrier time stop) — checks `extraData["maxHoldDuration"]` against elapsed minutes since `order.getEntryTimeStamp()`
3. Stop-loss
4. Target

---

## Thread Safety

| Component | Thread-Safe | Notes |
|-----------|-------------|-------|
| HATrendUtils, CPRUtils, DataAnalyser | ✅ | Static, stateless |
| OrderManagerUtils, FixedTargetAndStopLossStrategy | ✅ | Stateless |
| Point2D, Line | ❌ | Mutable |
| PartialRevisingStopLoss | ❌ | Modifies order state |

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
