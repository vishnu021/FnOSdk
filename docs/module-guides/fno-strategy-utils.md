# fno-strategy-utils Module Guide

## Overview

Advanced strategy utilities for F&O trading, including:
- **Heikin-Ashi trend analysis** - Smoothed trend detection using HA candles
- **Price action analysis** - Maxima/minima detection, support/resistance lines
- **CPR (Central Pivot Range) calculations** - Floor pivots and support/resistance levels
- **Order flow management** - Partial profit booking with dynamic stop-loss revision

## Maven Dependency

```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-strategy-utils</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Module Dependencies:**
- `fno-models` - Core data models
- `fno-utils` - Utility functions and helpers
- `fno-technicals` - Technical indicators

## Module Structure

```
com.vish.fno.strategy
├── HATrendUtils              # Heikin-Ashi trend analysis
├── Point2D                   # 2D point representation
├── PointType                 # Point type enumeration
├── util/
│   └── CPRUtils              # Central Pivot Range calculations
├── priceaction/
│   ├── Point                 # Chart point with coordinates
│   ├── ChartPoint            # Time-based chart point
│   ├── Vector2               # 2D vector mathematics
│   ├── DataAnalyser          # Price action pattern detection
│   └── Line                  # Line through multiple points
└── orderflow/
    ├── TargetAndStopLossStrategy    # Strategy interface for order exits
    ├── FixedTargetAndStopLossStrategy  # Fixed target/SL implementation
    ├── PartialRevisingStopLoss      # Dynamic stop-loss management
    └── OrderManagerUtils            # Order exit condition utilities
```

## Core Components

### HATrendUtils - Heikin-Ashi Trend Analysis

Utility class for detecting trends using Heikin-Ashi candle smoothing with weighted scoring.

#### Public API

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `getTrend(...)` | candles | `Trend` | Current trend (UPTREND, DOWNTREND, INDECISIVE, CONSOLIDATION) |
| `getTrendList(...)` | candles | `List<Trend>` | Complete trend history with consolidation detection |
| `getSmoothedTrends(...)` | candles | `List<Trend>` | Weighted smoothed trends using 5-candle window |
| `getMaximaMinima(...)` | candles | `List<Point2D>` | List of local maxima and minima points with coordinates |

**Example:**

```java
import com.vish.fno.strategy.HATrendUtils;
import com.vish.fno.model.Candle;
import com.vish.fno.util.Trend;
import com.vish.fno.strategy.Point2D;
import com.vish.fno.strategy.PointType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TrendAnalyzer {
    public void analyzeTrend(List<Candle> niftyCandles) {
        Trend currentTrend = HATrendUtils.getTrend(niftyCandles);

        log.info("Current market trend: {}", currentTrend);

        if (currentTrend == Trend.UPTREND) {
            log.info("Market in uptrend - consider call options");
        } else if (currentTrend == Trend.DOWNTREND) {
            log.info("Market in downtrend - consider put options");
        } else if (currentTrend == Trend.CONSOLIDATION) {
            log.info("Market consolidating - wait for breakout");
        }

        // Find swing points
        List<Point2D> swingPoints = HATrendUtils.getMaximaMinima(niftyCandles);

        for (Point2D point : swingPoints) {
            if (point.getType() == PointType.MAXIMA) {
                log.info("Resistance at index {} with price {}", point.getX(), point.getY());
            } else if (point.getType() == PointType.MINIMA) {
                log.info("Support at index {} with price {}", point.getX(), point.getY());
            }
        }
    }
}
```

**Algorithm Details:**
- Weighted scoring system with 5-candle window
- Higher highs/lower lows: +5 points
- Strong bullish/bearish candles: +3 points
- Regular bullish/bearish candles: +1 point

**Edge Cases:**
- Empty candle list: Returns empty list or INDECISIVE trend
- Thread safety: All methods are static and stateless - thread-safe

---

### Point2D - 2D Point Representation

Represents a point on a chart with x (time index), y (price), and optional type.

```java
@Data
public class Point2D implements Comparable<Point2D> {
    int x;
    double y;
    PointType type;

    public Point2D(int x, double y)
    public Point2D(int x, double y, PointType type)
    public int compareTo(Point2D o)
}
```

**Fields:**
- `x`: Time index (candle position in the series)
- `y`: Price level
- `type`: Point classification (MINIMA, MAXIMA, BOTH)

**PointType Enum:**
```java
public enum PointType {
    MINIMA,   // Local minimum (support level)
    MAXIMA,   // Local maximum (resistance level)
    BOTH      // Point that acts as both support and resistance
}
```

---

### CPRUtils - Central Pivot Range Calculations

Calculates floor pivots and CPR levels from previous day's candle.

```java
public static Map<String, Float> getFloorPivots(Candle previousDayCandle)
```

**Returns:** Map containing:
- `"pivotPoint"`: Central pivot (H + L + C) / 3
- `"topCentralPivot"`: TC = Pivot + (Pivot - BC)
- `"bottomCentralPivot"`: BC = (H + L) / 2
- `"r1"`, `"r2"`, `"r3"`, `"r4"`: Resistance levels 1-4
- `"s1"`, `"s2"`, `"s3"`, `"s4"`: Support levels 1-4

**Example:**

```java
import com.vish.fno.strategy.util.CPRUtils;
import com.vish.fno.model.Candle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CPRCalculator {
    public void calculateDailyLevels(Candle previousDayCandle) {
        Map<String, Float> pivots = CPRUtils.getFloorPivots(previousDayCandle);

        float pivot = pivots.get("pivotPoint");
        float tc = pivots.get("topCentralPivot");
        float bc = pivots.get("bottomCentralPivot");
        float cprWidth = tc - bc;

        log.info("Daily Pivot: {}", pivot);
        log.info("CPR Range: {} to {} (width: {})", bc, tc, cprWidth);
        log.info("Resistance levels: R1={}, R2={}, R3={}, R4={}",
            pivots.get("r1"), pivots.get("r2"), pivots.get("r3"), pivots.get("r4"));
        log.info("Support levels: S1={}, S2={}, S3={}, S4={}",
            pivots.get("s1"), pivots.get("s2"), pivots.get("s3"), pivots.get("s4"));

        // Narrow CPR indicates potential trending day
        if (cprWidth < pivot * 0.002) {
            log.info("Narrow CPR detected - expect trending day");
        }

        // Wide CPR indicates potential sideways day
        if (cprWidth > pivot * 0.01) {
            log.info("Wide CPR detected - expect sideways day");
        }
    }
}
```

**CPR Width Forecast:**
- **Narrow CPR** (< 0.2% of pivot): Indicates potential breakout/trending day
- **Wide CPR** (> 1% of pivot): Indicates potential sideways/consolidation day

---

## Price Action Analysis

### DataAnalyser - Advanced Price Action Detection

Analyzes candle data to find support/resistance levels, collinear points, and trendlines.

#### Public Methods

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `getMaximaMinimaPoints(...)` | candles, range | `List<Point2D>` | Merged list of significant swing points |
| `calculateMinimaPoints(...)` | candles, range | `Set<Point2D>` | Set of local minima (support levels) |
| `calculateMaximaPoints(...)` | candles, range | `Set<Point2D>` | Set of local maxima (resistance levels) |
| `findCollinearPoints(...)` | pointsSet, startIndex | `Set<Line>` | Set of lines through 3+ collinear points |
| `getActiveLines(...)` | candles, lines, isMaxima | `SortedSet<Line>` | Lines that haven't been broken |
| `removeShortLines(...)` | lines | `void` | Removes lines < 40 candles (modifies in place) |
| `removeTooClosePoints(...)` | lines | `void` | Removes lines with points <9 candles apart |
| `joinLongCollinearPoints(...)` | collinearPoints | `void` | Merges overlapping line segments |
| `removePointCoincidingWithLines(...)` | candleData, points, isMaxima | `void` | Removes lines that intersect candle bodies |
| `containsPoint(...)` | c, y, isMaxima | `boolean` | True if candle body contains given price level |

**Example - Complete Price Action Analysis:**

```java
import com.vish.fno.strategy.priceaction.DataAnalyser;
import com.vish.fno.strategy.priceaction.Point;
import com.vish.fno.strategy.priceaction.Line;
import com.vish.fno.strategy.Point2D;
import com.vish.fno.strategy.PointType;
import com.vish.fno.model.Candle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SupportResistanceAnalyzer {
    public void analyzeChart(List<Candle> candles) {
        int range = 10;  // Look 5 candles on each side

        // 1. Find swing points
        List<Point2D> swingPoints = DataAnalyser.getMaximaMinimaPoints(candles, range);
        Set<Point2D> maxima = DataAnalyser.calculateMaximaPoints(candles, range);

        for (Point2D point : swingPoints) {
            if (point.getType() == PointType.MAXIMA) {
                log.info("Resistance: {} at candle {}", point.getY(), point.getX());
            } else if (point.getType() == PointType.MINIMA) {
                log.info("Support: {} at candle {}", point.getY(), point.getX());
            }
        }

        // 2. Find trendlines (3+ collinear points)
        Set<Point> points = maxima.stream()
            .map(p -> new Point(p.getX(), (float) p.getY()))
            .collect(Collectors.toSet());

        Set<Line> trendlines = DataAnalyser.findCollinearPoints(points, 0);
        log.info("Found {} initial trendlines", trendlines.size());

        // 3. Clean up trendlines
        DataAnalyser.joinLongCollinearPoints(trendlines);   // Merge overlapping lines
        DataAnalyser.removeShortLines(trendlines);          // Remove < 40 candles
        DataAnalyser.removeTooClosePoints(trendlines);      // Remove points < 9 candles apart
        DataAnalyser.removePointCoincidingWithLines(candles, trendlines, true);

        // 4. Get active trendlines
        SortedSet<Line> activeLines = DataAnalyser.getActiveLines(candles, trendlines, true);
        log.info("Found {} active resistance trendlines", activeLines.size());

        // 5. Check if current price is near a trendline
        Candle currentCandle = candles.get(candles.size() - 1);
        for (Line line : activeLines) {
            float projectedPrice = line.getY(candles.size() - 1);

            if (DataAnalyser.containsPoint(currentCandle, projectedPrice, true)) {
                log.info("Price touching resistance trendline at {}", projectedPrice);
            }
        }
    }
}
```

**Algorithm Details:**
- **Maxima/Minima Detection**: Uses local peak detection within specified range
- **Collinearity Detection**: Triangle perimeter method with delta threshold 0.0004
- **Active Line Detection**: Line is active if not broken by candle bodies

---

### Line - Trendline Through Multiple Points

Represents a line connecting 3 or more price action points.

#### Public Methods

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `Line(...)` | Point... args | - | Constructor with variable number of points |
| `getY(float)` | x | `float` | Y coordinate at given x using y = mx + c |
| `getSlope()` | - | `float` | Line angle in degrees |
| `getXAxisLength()` | - | `float` | Length of line along x-axis |
| `getFirstPoint()` | - | `Point` | Leftmost point |
| `getLastPoint()` | - | `Point` | Rightmost point |
| `getPoints()` | - | `SortedSet<Point>` | Sorted set of all points on the line |
| `isSameLineSegment(...)` | that | `boolean` | True if shares 2+ points with another line |
| `isPointTooClose()` | - | `boolean` | True if any two consecutive points < 9 candles apart |
| `getHitPoint()` | - | `Point` | Point where line was touched/broken (null if never) |
| `setHitPoint(...)` | hitPoint | `void` | Sets hit point for breakout tracking |
| `getActiveExtension()` | - | `Point` | Active extension point (null if not extended) |
| `setActiveExtension(...)` | activeExtension | `void` | Sets active extension |
| `isActiveLine()` | - | `boolean` | True if line is currently active (not broken) |
| `setActiveLine(...)` | activeLine | `void` | Sets active line flag |
| `calculateSlopeAndConstant()` | - | `void` | Recalculates line equation from current points |

**Example:**

```java
import com.vish.fno.strategy.priceaction.Line;
import com.vish.fno.strategy.priceaction.Point;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TrendlineExample {
    public void analyzeTrendline() {
        Point p1 = new Point(10, 18200);
        Point p2 = new Point(30, 18350);
        Point p3 = new Point(50, 18500);

        Line trendline = new Line(p1, p2, p3);

        float currentCandle = 60;
        float projectedPrice = trendline.getY(currentCandle);

        log.info("Trendline slope: {} degrees", trendline.getSlope());
        log.info("Projected price at candle {}: {}", currentCandle, projectedPrice);
        log.info("Line spans {} candles", trendline.getXAxisLength());

        // Track breakouts
        if (trendline.isActiveLine()) {
            log.info("Trendline still active");
        } else {
            Point breakpoint = trendline.getHitPoint();
            log.info("Trendline broken at: {}", breakpoint);
        }
    }
}
```

---

### Point - Price Action Point

```java
@Getter
@Setter
public class Point extends Vector2 implements Comparable<Point> {
    private PointType type;

    public Point(float x, float y)
    public float getX()
    public float getY()
}
```

**Fields:**
- `x`: Candle index
- `y`: Price level
- `type`: MINIMA, MAXIMA, or BOTH

---

### ChartPoint - Time-Based Chart Point

Converts index-based Point to human-readable time-based representation.

```java
@Data
@AllArgsConstructor
public class ChartPoint implements Comparable<ChartPoint> {
    private String time;
    private double value;

    public ChartPoint(Point point)
    public ChartPoint(Point point, int timeframe)
}
```

---

### Vector2 - 2D Vector Mathematics

Low-level 2D vector utility for geometric calculations (from LibGDX).

**Core Methods:**
```java
public class Vector2 {
    public float x;
    public float y;

    public Vector2(float x, float y)
    public float len()           // Length of vector
    public float dst(Vector2 v)  // Distance to another vector
    public float dot(Vector2 v)  // Dot product
    public Vector2 add(Vector2 v) // Add vector
    public Vector2 sub(Vector2 v) // Subtract vector
    public Vector2 scl(float scalar) // Scale vector
    public Vector2 nor()         // Normalize vector
    public float angleRad()      // Angle in radians
}
```

**Additional Methods:**

Vector2 is adapted from LibGDX and includes 25+ additional methods for advanced vector mathematics including `len2()`, `dst2()`, `setLength()`, `limit()`, `clamp()`, `crs()`, `setAngleRad()`, `rotateRad()`, etc.

For complete API documentation, refer to the [LibGDX Vector2 documentation](https://libgdx.badlogicgames.com/ci/nightlies/docs/api/com/badlogic/gdx/math/Vector2.html).

---

## Order Flow Management

### TargetAndStopLossStrategy - Order Exit Strategy Interface

Interface defining the contract for target and stop-loss management strategies.

```java
public interface TargetAndStopLossStrategy {
    OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp);
    OrderSellDetailModel isStopLossHit(ActiveOrder order, double ltp);
}
```

**Parameters:**
- `order`: Active order being monitored
- `ltp`: Last traded price

**Returns:**
- `OrderSellDetailModel`: Contains sell decision (`sellOrder` boolean), quantity to sell, and sell reason

**Implementations:**
- `FixedTargetAndStopLossStrategy`: Fixed target/SL without revision
- `PartialRevisingStopLoss`: Partial booking with dynamic trailing stop-loss

---

### FixedTargetAndStopLossStrategy - Fixed Target/SL Implementation

Simple strategy that exits the full position when target or stop-loss is hit, without any revision.

```java
@Slf4j
public class FixedTargetAndStopLossStrategy implements TargetAndStopLossStrategy {
    public OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp)
    public OrderSellDetailModel isStopLossHit(ActiveOrder order, double ltp)
}
```

**Example:**

```java
import com.vish.fno.strategy.orderflow.FixedTargetAndStopLossStrategy;
import com.vish.fno.strategy.orderflow.TargetAndStopLossStrategy;
import com.vish.fno.model.order.OrderSellDetailModel;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SimpleOrderManager {
    private final TargetAndStopLossStrategy strategy = new FixedTargetAndStopLossStrategy();

    public void checkExit(ActiveOrder order, double currentPrice) {
        // Check stop-loss first
        OrderSellDetailModel slResult = strategy.isStopLossHit(order, currentPrice);
        if (slResult.sellOrder()) {
            log.info("Stop-loss hit at {}. Exiting {} lots", currentPrice, slResult.getSellQuantity());
            executeSell(order, slResult.getSellQuantity());
            return;
        }

        // Check target
        OrderSellDetailModel targetResult = strategy.isTargetAchieved(order, currentPrice);
        if (targetResult.sellOrder()) {
            log.info("Target achieved at {}. Exiting {} lots", currentPrice, targetResult.getSellQuantity());
            executeSell(order, targetResult.getSellQuantity());
        }
    }

    private void executeSell(ActiveOrder order, int quantity) {
        // Sell order execution
    }
}
```

**Strategy Behavior:**
- **Target hit**: Sells entire position (`order.getBuyQuantity()`)
- **Stop-loss hit**: Sells entire position
- **No revision**: Target and stop-loss remain fixed throughout

**Thread Safety:** ✅ Stateless - thread-safe

---

### OrderManagerUtils - Order Exit Condition Utility

Static utility for comprehensive exit condition checking including time-based exits, stop-loss, and target.

```java
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OrderManagerUtils {
    public static OrderSellDetailModel isExitCondition(
        TargetAndStopLossStrategy targetAndStopLossStrategy,
        double ltp,
        int timestampIndex,
        ActiveOrder order)
}
```

**Parameters:**
- `targetAndStopLossStrategy`: Strategy implementation to use
- `ltp`: Last traded price
- `timestampIndex`: Current intraday minute index (0-375 for 9:15 AM - 3:30 PM)
- `order`: Active order to check

**Returns:**
- `OrderSellDetailModel`: Exit decision with quantity and reason

**Example:**

```java
import com.vish.fno.strategy.orderflow.OrderManagerUtils;
import com.vish.fno.strategy.orderflow.FixedTargetAndStopLossStrategy;
import com.vish.fno.model.order.OrderSellDetailModel;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IntradayOrderManager {
    private final TargetAndStopLossStrategy strategy = new FixedTargetAndStopLossStrategy();

    public void onTick(ActiveOrder order, double ltp, int minuteIndex) {
        OrderSellDetailModel exitDecision = OrderManagerUtils.isExitCondition(
            strategy, ltp, minuteIndex, order
        );

        if (exitDecision.sellOrder()) {
            log.info("Exit triggered: reason={}, quantity={}, ltp={}",
                exitDecision.getReason(), exitDecision.getSellQuantity(), ltp);
            executeSell(order, exitDecision.getSellQuantity());
        }
    }

    private void executeSell(ActiveOrder order, int quantity) {
        // Sell execution
    }
}
```

**Exit Priority:**
1. **Time-based exit** (index > 368, ~3:23 PM): Sells all remaining quantity
2. **Stop-loss check**: Uses provided strategy
3. **Target check**: Uses provided strategy

**Constants:**
- `INTRADAY_EXIT_POSITION_TIME_INDEX = 368`: Exit time (approximately 3:23 PM)

**Thread Safety:** ✅ Static utility - thread-safe

---

### PartialRevisingStopLoss - Dynamic Stop Loss Strategy

Implements partial profit booking with trailing stop-loss using Heikin-Ashi lows/highs.

```java
@Slf4j
@RequiredArgsConstructor
public class PartialRevisingStopLoss implements TargetAndStopLossStrategy {
    private final DataCache dataCache;

    public OrderSellDetailModel isTargetAchieved(ActiveOrder order, double ltp)
    public OrderSellDetailModel isStopLossHit(ActiveOrder order, double ltp)
}
```

**Parameters:**
- `dataCache`: Cache for retrieving minute-level candle data
- `order`: Active order to manage
- `ltp`: Current last traded price

**Returns:**
- `OrderSellDetailModel`: Contains sell decision, quantity, and reason

**Example:**

```java
import com.vish.fno.strategy.orderflow.PartialRevisingStopLoss;
import com.vish.fno.model.order.OrderSellDetailModel;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.util.helper.DataCache;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrderManager {
    private final PartialRevisingStopLoss strategy;
    private final DataCache dataCache;

    public OrderManager(DataCache dataCache) {
        this.dataCache = dataCache;
        this.strategy = new PartialRevisingStopLoss(dataCache);
    }

    public void manageOrder(ActiveOrder order, double currentPrice) {
        // Check if target achieved
        OrderSellDetailModel targetResult = strategy.isTargetAchieved(order, currentPrice);

        if (targetResult.isSell()) {
            log.info("Selling {} lots at {}, reason: {}",
                targetResult.getSellQuantity(), currentPrice, targetResult.getReason());

            executeSell(order, targetResult.getSellQuantity());

            if (targetResult.getSellQuantity() < order.getBuyQuantity()) {
                log.info("Partial booking done. Trailing stop-loss activated.");
            }
        }

        // Check if stop loss hit
        OrderSellDetailModel stopResult = strategy.isStopLossHit(order, currentPrice);

        if (stopResult.isSell()) {
            log.info("Stop loss hit at {}. Exiting remaining {} lots",
                currentPrice, stopResult.getSellQuantity());
            executeSell(order, stopResult.getSellQuantity());
        }
    }

    private void executeSell(ActiveOrder order, int quantity) {
        // Sell order execution logic
    }
}
```

**Strategy Logic:**

**Partial Profit Booking:**
- If 1-2 lots bought: Sell all on target
- If 3+ lots bought: Sell 2/3 of quantity on first target hit, activate trailing stop-loss for remaining

**Stop Loss Revision:**
- For **call orders**: Revises stop-loss to Heikin-Ashi candle low (trailing up)
- For **put orders**: Revises stop-loss to Heikin-Ashi candle high (trailing down)

**Lots to Sell Calculation:**
- 1 lot → Sell 1
- 2-3 lots → Sell 2
- 4-5 lots → Sell 3
- 6-7 lots → Sell 4
- 8+ lots → Sell 2/3 of total

---

## Integration Patterns

### Complete Trading Strategy with CPR and Trend Analysis

```java
import com.vish.fno.strategy.HATrendUtils;
import com.vish.fno.strategy.util.CPRUtils;
import com.vish.fno.strategy.orderflow.PartialRevisingStopLoss;
import com.vish.fno.model.Candle;
import com.vish.fno.util.Trend;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompleteTradingStrategy {
    private final PartialRevisingStopLoss orderStrategy;
    private final DataCache dataCache;

    public CompleteTradingStrategy(DataCache dataCache) {
        this.dataCache = dataCache;
        this.orderStrategy = new PartialRevisingStopLoss(dataCache);
    }

    public void analyzeAndTrade(List<Candle> dailyCandles, List<Candle> intradayCandles) {
        // 1. Calculate CPR levels from previous day
        Candle previousDay = dailyCandles.get(dailyCandles.size() - 2);
        Map<String, Float> pivots = CPRUtils.getFloorPivots(previousDay);

        float cprWidth = pivots.get("topCentralPivot") - pivots.get("bottomCentralPivot");
        boolean narrowCPR = cprWidth < pivots.get("pivotPoint") * 0.002;

        log.info("CPR Width: {} ({})", cprWidth, narrowCPR ? "Narrow" : "Wide");

        // 2. Detect current trend using Heikin-Ashi
        Trend currentTrend = HATrendUtils.getTrend(intradayCandles);
        log.info("Current trend: {}", currentTrend);

        // 3. Find support/resistance levels
        List<Point2D> swingPoints = HATrendUtils.getMaximaMinima(intradayCandles);

        // 4. Trading logic
        double currentPrice = intradayCandles.get(intradayCandles.size() - 1).getClose();

        if (narrowCPR && currentTrend == Trend.UPTREND) {
            log.info("Narrow CPR + Uptrend: Consider call entry");

            // Entry above R1
            if (currentPrice > pivots.get("r1")) {
                log.info("Price crossed R1, entering call");
                // Place call order with target at R2
            }
        } else if (narrowCPR && currentTrend == Trend.DOWNTREND) {
            log.info("Narrow CPR + Downtrend: Consider put entry");

            // Entry below S1
            if (currentPrice < pivots.get("s1")) {
                log.info("Price crossed S1, entering put");
                // Place put order with target at S2
            }
        }
    }
}
```

## Performance Considerations

**HATrendUtils:**
- Window size: 5 candles (configurable via WINDOW_SIZE constant)
- Time complexity: O(n) for trend calculation

**DataAnalyser:**
- Range-based peak detection: O(n * range)
- Collinearity check: O(n³) for finding all lines
- Active line detection: O(lines * candles)

**PartialRevisingStopLoss:**
- Fetches minute data on every revision (cache recommended)

**Optimization Tips:**
- Use appropriate range values (10-20 candles typical)
- Cache Heikin-Ashi candles if used repeatedly
- Filter short/invalid lines early

## Thread Safety

| Component | Thread Safety | Notes |
|-----------|---------------|-------|
| HATrendUtils | ✅ Thread-safe | All static methods, no shared state |
| CPRUtils | ✅ Thread-safe | Pure calculations, no side effects |
| DataAnalyser | ✅ Thread-safe | Static methods with no mutable state |
| OrderManagerUtils | ✅ Thread-safe | Static utility, no mutable state |
| FixedTargetAndStopLossStrategy | ✅ Thread-safe | Stateless implementation |
| Point2D | ⚠️ Not thread-safe | Mutable object with setters |
| Line | ⚠️ Not thread-safe | Mutable points set |
| PartialRevisingStopLoss | ⚠️ Not thread-safe | Modifies order state, requires external sync |

## Common Use Cases

1. **Trend Following Strategy** - Use HATrendUtils to identify trend direction
2. **CPR Breakout Trading** - Trade breakouts from narrow CPR ranges
3. **Support/Resistance Trading** - Use DataAnalyser to find swing levels
4. **Partial Profit Booking** - Use PartialRevisingStopLoss for risk management
5. **Trendline Breakouts** - Detect active trendlines and trade breakouts

## See Also

- **fno-models** - Core order and candle models
- **fno-utils** - HeikinAshi, CandleUtils, TimeUtils
- **fno-technicals** - Technical indicators (RSI, Moving Averages)
- **SDK_USAGE.md** - Complete SDK integration guide
