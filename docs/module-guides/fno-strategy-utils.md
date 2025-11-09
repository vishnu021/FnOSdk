# fno-strategy-utils Module Guide

## Overview

The `fno-strategy-utils` module provides advanced strategy utilities for F&O trading, including:
- **Heikin-Ashi trend analysis** - Smoothed trend detection using HA candles
- **Price action analysis** - Maxima/minima detection, support/resistance lines
- **CPR (Central Pivot Range) calculations** - Floor pivots and support/resistance levels
- **Order flow management** - Partial profit booking with dynamic stop-loss revision

This module extends FnOSdk with sophisticated technical analysis and trading strategy components.

---

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

---

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
    └── PartialRevisingStopLoss  # Dynamic stop-loss management
```

---

## Core Components

### HATrendUtils - Heikin-Ashi Trend Analysis

Utility class for detecting trends using Heikin-Ashi candle smoothing with weighted scoring.

#### Public API

```java
public final class HATrendUtils {
    public static Trend getTrend(List<Candle> candles)
    public static List<Trend> getTrendList(List<Candle> candles)
    public static List<Trend> getSmoothedTrends(List<Candle> candles)
    public static List<Point2D> getMaximaMinima(List<Candle> candles)
}
```

**Parameters:**
- `candles`: List of standard candles to analyze

**Returns:**
- `getTrend()`: Current trend (UPTREND, DOWNTREND, INDECISIVE, CONSOLIDATION)
- `getTrendList()`: Complete trend history with consolidation detection
- `getSmoothedTrends()`: Weighted smoothed trends using 5-candle window
- `getMaximaMinima()`: List of local maxima and minima points with coordinates

**Example - Detect Current Trend:**

```java
import com.vish.fno.strategy.HATrendUtils;
import com.vish.fno.model.Candle;
import com.vish.fno.util.Trend;
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
    }
}
```

**Example - Find Swing Points:**

```java
import com.vish.fno.strategy.HATrendUtils;
import com.vish.fno.strategy.Point2D;
import com.vish.fno.strategy.PointType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SwingPointDetector {
    public void findSwingPoints(List<Candle> candles) {
        List<Point2D> swingPoints = HATrendUtils.getMaximaMinima(candles);

        for (Point2D point : swingPoints) {
            if (point.getType() == PointType.MAXIMA) {
                log.info("Resistance at index {} with price {}",
                    point.getX(), point.getY());
            } else if (point.getType() == PointType.MINIMA) {
                log.info("Support at index {} with price {}",
                    point.getX(), point.getY());
            }
        }
    }
}
```

**Algorithm Details:**

HATrendUtils uses a weighted scoring system with 5-candle window:
- Higher highs/lower lows: +5 points
- Strong bullish/bearish candles: +3 points
- Regular bullish/bearish candles: +1 point

**Edge Cases:**
- Empty candle list: Returns empty list or INDECISIVE trend
- Less than window size: Uses available candles
- Doji candles: Identified with 10% body-to-range threshold
- Thread safety: All methods are static and stateless - thread-safe

---

### Point2D - 2D Point Representation

Represents a point on a chart with x (time index), y (price), and optional type.

#### Public API

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

**Returns:**
- `compareTo()`: Comparison result based on x coordinate

**Example:**

```java
import com.vish.fno.strategy.Point2D;
import com.vish.fno.strategy.PointType;

public class ChartPointExample {
    public void createPoints() {
        // Create a maxima point at index 50 with price 18500
        Point2D resistance = new Point2D(50, 18500.0, PointType.MAXIMA);

        // Create a minima point at index 30 with price 18200
        Point2D support = new Point2D(30, 18200.0, PointType.MINIMA);

        // Points are comparable by x coordinate
        if (support.compareTo(resistance) < 0) {
            // Support came before resistance chronologically
        }
    }
}
```

**Edge Cases:**
- Uses `TimeUtils.timeArray` for string representation
- Implements proper equals/hashCode based on x and y coordinates
- Thread safety: Mutable object - not thread-safe

---

### PointType - Point Classification Enum

Enumeration for classifying chart points.

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

#### Public API

```java
public final class CPRUtils {
    public static Map<String, Float> getFloorPivots(Candle previousDayCandle)
}
```

**Parameters:**
- `previousDayCandle`: Previous trading day's OHLC candle

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
            pivots.get("r1"), pivots.get("r2"),
            pivots.get("r3"), pivots.get("r4"));
        log.info("Support levels: S1={}, S2={}, S3={}, S4={}",
            pivots.get("s1"), pivots.get("s2"),
            pivots.get("s3"), pivots.get("s4"));

        // Narrow CPR indicates potential trending day
        if (cprWidth < pivot * 0.002) {  // Less than 0.2% of pivot
            log.info("Narrow CPR detected - expect trending day");
        }

        // Wide CPR indicates potential sideways day
        if (cprWidth > pivot * 0.01) {  // Greater than 1% of pivot
            log.info("Wide CPR detected - expect sideways day");
        }
    }
}
```

**CPR Width Forecast:**

The width of the Central Pivot Range provides trading signals:
- **Narrow CPR** (< 0.2% of pivot): Indicates potential breakout/trending day
- **Wide CPR** (> 1% of pivot): Indicates potential sideways/consolidation day
- After a trending day, CPR typically becomes wide (next day sideways)
- After a sideways day, CPR typically becomes narrow (next day trending)

**Edge Cases:**
- All values rounded to 2 decimal places using `Utils.round()`
- Null candle: Will throw NullPointerException
- Thread safety: Static method with no shared state - thread-safe

---

## Price Action Analysis

### DataAnalyser - Advanced Price Action Detection

Analyzes candle data to find support/resistance levels, collinear points, and trendlines.

#### Public API

```java
public final class DataAnalyser {
    public static List<Point2D> getMaximaMinimaPoints(List<Candle> candles, int range)
    public static Set<Point2D> calculateMinimaPoints(List<Candle> candles, int range)
    public static Set<Point2D> calculateMaximaPoints(List<Candle> candles, int range)
    public static Set<Line> findCollinearPoints(Set<Point> pointsSet, int startIndex)
    public static SortedSet<Line> getActiveLines(List<Candle> candles, Set<Line> lines, boolean isMaxima)
    public static void removeShortLines(Set<Line> lines)
    public static void removeTooClosePoints(Set<Line> lines)
}
```

**Parameters:**
- `candles`: List of candles to analyze
- `range`: Look-ahead/look-behind range for peak detection
- `pointsSet`: Set of points to analyze for collinearity
- `lines`: Set of lines to filter
- `isMaxima`: True for resistance lines, false for support lines

**Returns:**
- `getMaximaMinimaPoints()`: Merged list of significant swing points
- `calculateMinimaPoints()`: Set of local minima (support levels)
- `calculateMaximaPoints()`: Set of local maxima (resistance levels)
- `findCollinearPoints()`: Set of lines through 3+ collinear points
- `getActiveLines()`: Lines that haven't been broken

**Example - Find Support/Resistance Levels:**

```java
import com.vish.fno.strategy.priceaction.DataAnalyser;
import com.vish.fno.strategy.Point2D;
import com.vish.fno.strategy.PointType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SupportResistanceFinder {
    public void findLevels(List<Candle> candles) {
        int range = 10;  // Look 5 candles on each side

        List<Point2D> swingPoints = DataAnalyser.getMaximaMinimaPoints(candles, range);

        for (Point2D point : swingPoints) {
            if (point.getType() == PointType.MAXIMA) {
                log.info("Resistance: {} at candle {}", point.getY(), point.getX());
            } else if (point.getType() == PointType.MINIMA) {
                log.info("Support: {} at candle {}", point.getY(), point.getX());
            }
        }
    }
}
```

**Example - Draw Trendlines:**

```java
import com.vish.fno.strategy.priceaction.DataAnalyser;
import com.vish.fno.strategy.priceaction.Point;
import com.vish.fno.strategy.priceaction.Line;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TrendlineDrawer {
    public void drawTrendlines(List<Candle> candles) {
        // Find swing points
        Set<Point2D> maxima = DataAnalyser.calculateMaximaPoints(candles, 10);

        // Convert to Point objects for collinearity analysis
        Set<Point> points = maxima.stream()
            .map(p -> new Point(p.getX(), (float) p.getY()))
            .collect(Collectors.toSet());

        // Find trendlines (3+ collinear points)
        Set<Line> trendlines = DataAnalyser.findCollinearPoints(points, 0);

        // Remove short lines
        DataAnalyser.removeShortLines(trendlines);

        // Get active trendlines
        SortedSet<Line> activeLines = DataAnalyser.getActiveLines(candles, trendlines, true);

        log.info("Found {} active resistance trendlines", activeLines.size());

        for (Line line : activeLines) {
            log.info("Trendline: slope={}, points={}",
                line.getSlope(), line.getPoints().size());
        }
    }
}
```

**Algorithm Details:**

**Maxima/Minima Detection:**
- Uses local peak detection within specified range
- A point is maxima if it's the highest within range/2 on both sides
- A point is minima if it's the lowest within range/2 on both sides

**Collinearity Detection:**
- Checks if 3+ points lie on approximately the same line
- Uses triangle perimeter method with tolerance
- Delta threshold: 0.0004 for collinearity detection

**Active Line Detection:**
- Line is active if it hasn't been broken by candle bodies
- Hit point correction: 30 candles
- Removes lines that are too far above current price (> 1.33x)

**Edge Cases:**
- Candle count < range: Returns empty sets
- Duplicate points at same x: Warns and keeps first
- Very close points: Filtered using MIN_DISTANCE_BETWEEN_POINTS (9 candles)
- Short lines: Filtered using MIN_LINE_LENGTH (40 candles)
- Thread safety: Static methods - thread-safe

---

### Line - Trendline Through Multiple Points

Represents a line connecting 3 or more price action points.

#### Public API

```java
public class Line implements Comparable<Line> {
    public Line(Point... args)
    public float getY(float x)
    public float getSlope()
    public float getXAxisLength()
    public Point getFirstPoint()
    public Point getLastPoint()
    public SortedSet<Point> getPoints()
    public boolean isSameLineSegment(Line that)
    public boolean isPointTooClose()
}
```

**Parameters:**
- `args`: Variable number of Point objects defining the line

**Returns:**
- `getY(x)`: Y coordinate at given x using y = mx + c
- `getSlope()`: Line angle in degrees
- `getXAxisLength()`: Length of line along x-axis
- `getFirstPoint()`: Leftmost point
- `getLastPoint()`: Rightmost point
- `isSameLineSegment()`: True if shares 2+ points with another line

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
    }
}
```

**Edge Cases:**
- Minimum 2 points required for valid line
- Slope/constant recalculated when points are added
- Points sorted automatically (implements Comparable)
- Thread safety: Mutable object - not thread-safe

---

### Point - Price Action Point

Represents a single point in price action analysis.

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

**Example:**

```java
import com.vish.fno.strategy.priceaction.Point;
import com.vish.fno.strategy.priceaction.ChartPoint;

public class ChartPointConverter {
    public void convertToTime() {
        Point indexPoint = new Point(50, 18500);

        // Convert to time-based point
        ChartPoint timePoint = new ChartPoint(indexPoint);

        // timePoint.getTime() returns "09:45" (or actual time from TimeUtils)
        // timePoint.getValue() returns 18500.0
    }
}
```

---

### Vector2 - 2D Vector Mathematics

Low-level 2D vector utility for geometric calculations (from LibGDX).

#### Public API

```java
public class Vector2 {
    public float x;
    public float y;

    public Vector2(float x, float y)
    public float len()
    public float dst(Vector2 v)
    public float dot(Vector2 v)
    public Vector2 add(Vector2 v)
    public Vector2 sub(Vector2 v)
    public Vector2 scl(float scalar)
    public Vector2 nor()
    public float angleRad()
}
```

**Example:**

```java
import com.vish.fno.strategy.priceaction.Vector2;

public class VectorMathExample {
    public void calculateDistance() {
        Vector2 point1 = new Vector2(10, 18200);
        Vector2 point2 = new Vector2(30, 18500);

        float distance = point1.dst(point2);
        float length = point2.len();
        float dotProduct = point1.dot(point2);
    }
}
```

---

## Order Flow Management

### PartialRevisingStopLoss - Dynamic Stop Loss Strategy

Implements partial profit booking with trailing stop-loss using Heikin-Ashi lows/highs.

#### Public API

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
                targetResult.getSellQuantity(),
                currentPrice,
                targetResult.getReason());

            // Execute sell order
            executeSell(order, targetResult.getSellQuantity());

            // If partial sell, stop loss will be automatically revised
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
1. If 1-2 lots bought: Sell all on target
2. If 3+ lots bought:
   - On first target hit: Sell 2/3 of quantity
   - Activate trailing stop-loss for remaining
   - Continue revising stop-loss upward

**Stop Loss Revision:**
- For **call orders**: Revises stop-loss to Heikin-Ashi candle low (trailing up)
- For **put orders**: Revises stop-loss to Heikin-Ashi candle high (trailing down)
- Only revises in favorable direction (never loosens)

**Lots to Sell Calculation:**
- 1 lot → Sell 1
- 2-3 lots → Sell 2
- 4-5 lots → Sell 3
- 6-7 lots → Sell 4
- 8+ lots → Sell 2/3 of total

**Edge Cases:**
- Zero lot size: Defaults to 1 lot
- Already sold quantity: Only sells remaining
- Invalid order type: Logs error
- Thread safety: Requires external synchronization

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

---

## Performance Considerations

**HATrendUtils:**
- Window size: 5 candles (configurable via WINDOW_SIZE constant)
- Time complexity: O(n) for trend calculation
- Memory: Creates HA candle copies

**DataAnalyser:**
- Range-based peak detection: O(n * range)
- Collinearity check: O(n³) for finding all lines
- Active line detection: O(lines * candles)

**PartialRevisingStopLoss:**
- Fetches minute data on every revision (cache recommended)
- Stop-loss revision triggered on every tick (consider throttling)

**Optimization Tips:**
- Use appropriate range values (10-20 candles typical)
- Cache Heikin-Ashi candles if used repeatedly
- Filter short/invalid lines early
- Consider batching line calculations

---

## Thread Safety

| Component | Thread Safety | Notes |
|-----------|---------------|-------|
| HATrendUtils | ✅ Thread-safe | All static methods, no shared state |
| CPRUtils | ✅ Thread-safe | Pure calculations, no side effects |
| DataAnalyser | ✅ Thread-safe | Static methods with no mutable state |
| Point2D | ⚠️ Not thread-safe | Mutable object with setters |
| Line | ⚠️ Not thread-safe | Mutable points set |
| PartialRevisingStopLoss | ⚠️ Not thread-safe | Modifies order state, requires external sync |

**Recommendation:** For multi-threaded environments, synchronize access to mutable objects (Point2D, Line, ActiveOrder).

---

## Common Use Cases

1. **Trend Following Strategy** - Use HATrendUtils to identify trend direction
2. **CPR Breakout Trading** - Trade breakouts from narrow CPR ranges
3. **Support/Resistance Trading** - Use DataAnalyser to find swing levels
4. **Partial Profit Booking** - Use PartialRevisingStopLoss for risk management
5. **Trendline Breakouts** - Detect active trendlines and trade breakouts

---

## See Also

- **fno-models** - Core order and candle models
- **fno-utils** - HeikinAshi, CandleUtils, TimeUtils
- **fno-technicals** - Technical indicators (RSI, Moving Averages)
- **SDK_USAGE.md** - Complete SDK integration guide
