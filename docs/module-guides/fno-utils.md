# fno-utils - Utility Functions

## Purpose
Utility classes and helper functions for trading operations, data processing, and strategy management.

## Maven Dependency
```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-utils</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

This automatically includes `fno-models` as a transitive dependency.

## Key Utilities

### CandleUtils - Candle Operations

**Timeframe Conversion:**
```java
import com.vish.fno.util.CandleUtils;
import com.vish.fno.model.Candle;

// Convert 1-minute candles to 15-minute
List<Candle> oneMinCandles = // ... your 1-min data
List<Candle> fifteenMinCanles = CandleUtils.convertToTimeFrame(
    oneMinCandles,
    "15minute"  // Options: "5minute", "15minute", "30minute", "60minute", "day"
);

// Convert to daily candles
List<Candle> dailyCandles = CandleUtils.convertToTimeFrame(oneMinCandles, "day");
```

**Candle Pattern Analysis:**
```java
import com.vish.fno.util.CandleUtils;
import com.vish.fno.model.Candle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PatternDetector {
    public void analyzePattern(List<Candle> candles) {
        Candle candle = candles.get(candles.size() - 1);

        // Check candle characteristics
        boolean isBullish = CandleUtils.isBullish(candle);
        boolean isBearish = CandleUtils.isBearish(candle);

        // Get candle measurements
        Double bodySize = CandleUtils.getCandleBody(candle);
        Double upperWick = CandleUtils.getUpperWick(candle);
        Double lowerWick = CandleUtils.getLowerWick(candle);

        // Pattern detection
        if (CandleUtils.isBullish(candle) && lowerWick > bodySize * 2) {
            log.info("Potential hammer pattern detected");
        }
    }
}
```

**Merge Candles:**
```java
import com.vish.fno.util.CandleUtils;
import com.vish.fno.model.Candle;

// Merge multiple candles into one
List<Candle> candlesToMerge = candles.subList(0, 5);
Candle merged = CandleUtils.mergeCandlesticks(candlesToMerge);
```

**Find Local Extremes:**
```java
import com.vish.fno.util.CandleUtils;
import com.vish.fno.model.Candle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SwingPointDetector {
    public void findSwingPoints(List<Candle> candles) {
        int window = 3; // Number of candles to check on each side
        int startIndex = window; // Start search from valid position

        // Find local minimum (support level)
        int minIndex = CandleUtils.findLocalMinimum(candles, startIndex, window);
        if (minIndex != -1) {
            Candle supportCandle = candles.get(minIndex);
            log.info("Support found at index {} with low: {}", minIndex, supportCandle.low());
        }

        // Find local maximum (resistance level)
        int maxIndex = CandleUtils.findLocalMaximum(candles, startIndex, window);
        if (maxIndex != -1) {
            Candle resistanceCandle = candles.get(maxIndex);
            log.info("Resistance found at index {} with high: {}", maxIndex, resistanceCandle.high());
        }
    }
}
```

**Signature:**
```java
public static int findLocalMinimum(List<Candle> candles, int startIndex, int window)
```
- **Parameters:**
  - `candles` - List of candlesticks to search
  - `startIndex` - Index to start searching from (must be >= window)
  - `window` - Number of candles to check on each side for comparison
- **Returns:** Index of local minimum or -1 if not found
- **Description:** Finds the index of a local minimum (a low point surrounded by higher values). Useful for identifying support levels.

```java
public static int findLocalMaximum(List<Candle> candles, int startIndex, int window)
```
- **Parameters:**
  - `candles` - List of candlesticks to search
  - `startIndex` - Index to start searching from (must be >= window)
  - `window` - Number of candles to check on each side for comparison
- **Returns:** Index of local maximum or -1 if not found
- **Description:** Finds the index of a local maximum (a high point surrounded by lower values). Useful for identifying resistance levels.

**Calculate Body Size Percentage:**
```java
import com.vish.fno.util.CandleUtils;
import com.vish.fno.model.Candle;

public class CandleStrengthAnalyzer {
    public void analyzeStrength(Candle candle) {
        // Get body size as percentage of total range (0.0 to 1.0)
        double bodySizePercentage = CandleUtils.getBodySizePercentage(candle);

        if (bodySizePercentage > 0.6) {
            System.out.println("Strong candle with large body");
        } else if (bodySizePercentage < 0.1) {
            System.out.println("Doji-like candle with very small body");
        } else {
            System.out.println("Normal candle");
        }
    }
}
```

**Signature:**
```java
public static double getBodySizePercentage(Candle candle)
```
- **Parameters:**
  - `candle` - The candlestick to analyze
- **Returns:** Body size as a percentage of total range (0.0 to 1.0). Returns 0 if range is 0.
- **Description:** Calculates the body size of a candlestick as a percentage of the total range (high - low). Useful for identifying candle strength and patterns like doji.

### TimeUtils - Trading Time Operations

**Market Hours:**
```java
import com.vish.fno.util.TimeUtils;

// Check if market is currently open
if (TimeUtils.isMarketOpen(LocalDateTime.now())) {
    executeStrategy();
}

// Check if specific time is within trading hours (9:15 AM - 3:30 PM IST)
LocalDateTime checkTime = LocalDateTime.of(2024, 9, 28, 10, 30);
boolean isTradingHour = TimeUtils.isTradingHour(checkTime);
```

**Trading Days:**
```java
// Get next trading day (skips weekends and holidays)
LocalDate today = LocalDate.now();
LocalDate nextTradingDay = TimeUtils.getNextTradingDay(today);

// Check if a specific date is a holiday
boolean isHoliday = TimeUtils.isHoliday(LocalDate.of(2024, 10, 2)); // Gandhi Jayanti
```

**Market Timings:**
```java
LocalDate date = LocalDate.of(2024, 9, 28);
LocalDateTime marketOpen = TimeUtils.getMarketOpenTime(date);   // 9:15 AM
LocalDateTime marketClose = TimeUtils.getMarketCloseTime(date); // 3:30 PM
```

**Parse Candlestick Timestamps:**
```java
import com.vish.fno.util.TimeUtils;

public class CandleDataParser {
    public void parseTimestamps() {
        // Parse millisecond timestamp
        String msTimestamp = "1693387020000";
        long epochMs1 = TimeUtils.parseCandlestickTimestamp(msTimestamp);
        // Returns: 1693387020000

        // Parse ISO-8601 datetime string
        String isoTimestamp = "2025-08-20T09:57:00+0530";
        long epochMs2 = TimeUtils.parseCandlestickTimestamp(isoTimestamp);
        // Returns: equivalent milliseconds

        // Empty or invalid strings default to current time
        long fallback = TimeUtils.parseCandlestickTimestamp("");
        // Returns: System.currentTimeMillis()
    }
}
```

**Signature:**
```java
public static long parseCandlestickTimestamp(String timeStr)
```
- **Parameters:**
  - `timeStr` - The time string to parse (either milliseconds or ISO-8601 format like "2025-08-20T09:57:00+0530")
- **Returns:** The timestamp in milliseconds
- **Description:** Parses timestamp from candlestick time string. Handles both millisecond timestamps and ISO-8601 datetime strings. Falls back to current time if parsing fails.
- **Thread Safety:** Uses synchronized block for SimpleDateFormat access

**Check Trading Hours:**
```java
import com.vish.fno.util.TimeUtils;

public class TradingHoursValidator {
    public boolean canTrade(long timestamp) {
        // Check if timestamp falls within market hours (9:15 AM to 3:30 PM IST)
        boolean withinHours = TimeUtils.isWithinTradingHours(timestamp);

        if (!withinHours) {
            System.out.println("Outside trading hours - cannot execute");
            return false;
        }

        return true;
    }

    public void filterTradingHoursData(List<Candle> candles) {
        // Filter candles to only include those within trading hours
        List<Candle> tradingHoursCandles = candles.stream()
            .filter(c -> TimeUtils.isWithinTradingHours(
                TimeUtils.parseCandlestickTimestamp(c.timestamp())
            ))
            .collect(Collectors.toList());
    }
}
```

**Signature:**
```java
public static boolean isWithinTradingHours(long timestamp)
```
- **Parameters:**
  - `timestamp` - The timestamp in milliseconds
- **Returns:** true if within trading hours (9:15 AM to 3:30 PM IST), false otherwise
- **Description:** Validates if a timestamp falls within NSE trading hours. Uses Asia/Kolkata timezone.

**Convert Epoch to LocalDateTime:**
```java
import com.vish.fno.util.TimeUtils;
import java.time.LocalDateTime;

public class TimestampConverter {
    public void convertTimestamps() {
        long epochMilli = 1693387020000L;

        // Convert to LocalDateTime in IST timezone
        LocalDateTime istDateTime = TimeUtils.fromEpochMilli(epochMilli);
        System.out.println("IST time: " + istDateTime);
        // Output: IST time: 2023-08-30T09:57:00

        // Access components
        int hour = istDateTime.getHour();
        int minute = istDateTime.getMinute();
        int second = istDateTime.getSecond();
    }
}
```

**Signature:**
```java
public static LocalDateTime fromEpochMilli(long epochMilli)
```
- **Parameters:**
  - `epochMilli` - The timestamp in milliseconds
- **Returns:** LocalDateTime representation in Asia/Kolkata timezone
- **Description:** Converts epoch milliseconds to LocalDateTime in IST (Asia/Kolkata) timezone. Useful for extracting time components and performing time arithmetic.

**Parse DateTime to Epoch:**
```java
import com.vish.fno.util.TimeUtils;

public class DateTimeParser {
    public void parseVariousFormats() {
        // Parse ISO-8601 format
        long epoch1 = TimeUtils.parseDateTimeToEpoch("2025-08-20T09:57:00+0530");

        // Parse standard datetime format
        long epoch2 = TimeUtils.parseDateTimeToEpoch("2025-08-20 09:57:00");

        // Parse date only
        long epoch3 = TimeUtils.parseDateTimeToEpoch("2025-08-20");

        // Parse Indian date format
        long epoch4 = TimeUtils.parseDateTimeToEpoch("20-08-2025 09:57:00");
        long epoch5 = TimeUtils.parseDateTimeToEpoch("20-08-2025");

        // Invalid format falls back to current time
        long fallback = TimeUtils.parseDateTimeToEpoch("invalid-date");
    }
}
```

**Signature:**
```java
public static long parseDateTimeToEpoch(String dateTimeStr)
```
- **Parameters:**
  - `dateTimeStr` - The datetime string to parse
- **Returns:** The timestamp in milliseconds
- **Description:** Parses a datetime string to epoch milliseconds. Supports multiple formats: ISO-8601, "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "dd-MM-yyyy HH:mm:ss", "dd-MM-yyyy". Falls back to current time if parsing fails.
- **Thread Safety:** Uses synchronized block for SimpleDateFormat access

**Format Timestamp:**
```java
import com.vish.fno.util.TimeUtils;

public class TimestampFormatter {
    public void formatTimestamps() {
        long timestamp = 1693387020000L;

        // Format to human-readable datetime
        String formatted = TimeUtils.formatDateTime(timestamp);
        System.out.println(formatted);
        // Output: 2023-08-30 09:57:00

        // Use in logs
        log.info("Trade executed at: {}", TimeUtils.formatDateTime(System.currentTimeMillis()));
    }
}
```

**Signature:**
```java
public static String formatDateTime(long timestamp)
```
- **Parameters:**
  - `timestamp` - The timestamp in milliseconds
- **Returns:** Formatted datetime string in "yyyy-MM-dd HH:mm:ss" format
- **Description:** Formats a timestamp to human-readable datetime string. Useful for logging and display purposes.

### CandlePatternUtils - Candlestick Pattern Detection

**Purpose:** Utility class for identifying candlestick patterns used in technical analysis and Wyckoff phase trading. Provides methods to detect reversal and continuation patterns.

**Single-Candle Patterns:**
```java
import com.vish.fno.util.CandlePatternUtils;
import com.vish.fno.model.Candle;

public class SingleCandlePatternDetector {
    public void detectPatterns(Candle candle) {
        // Reversal patterns
        if (CandlePatternUtils.isHammer(candle)) {
            System.out.println("Hammer detected - potential bullish reversal");
        }

        if (CandlePatternUtils.isInvertedHammer(candle)) {
            System.out.println("Inverted Hammer detected");
        }

        if (CandlePatternUtils.isShootingStar(candle)) {
            System.out.println("Shooting Star detected - potential bearish reversal");
        }

        if (CandlePatternUtils.isDoji(candle)) {
            System.out.println("Doji detected - market indecision");
        }

        // Strength indicators
        if (CandlePatternUtils.isStrongBullish(candle)) {
            System.out.println("Strong bullish candle");
        }

        if (CandlePatternUtils.isStrongBearish(candle)) {
            System.out.println("Strong bearish candle");
        }
    }
}
```

**Single-Candle Pattern Signatures:**

```java
public static boolean isHammer(Candle candle)
```
- **Parameters:** `candle` - Candle to analyze
- **Returns:** true if candle is a Hammer pattern (bullish reversal)
- **Description:** Detects Hammer pattern: long lower shadow (>2x body), small upper shadow (<0.3x body), small body (<40% of range)

```java
public static boolean isInvertedHammer(Candle candle)
```
- **Parameters:** `candle` - Candle to analyze
- **Returns:** true if candle is an Inverted Hammer pattern
- **Description:** Detects Inverted Hammer: long upper shadow (>2x body), small lower shadow (<0.3x body), small body (<40% of range)

```java
public static boolean isShootingStar(Candle candle)
```
- **Parameters:** `candle` - Candle to analyze
- **Returns:** true if candle is a Shooting Star pattern (bearish reversal)
- **Description:** Shooting Star is an Inverted Hammer with bearish close (close < open)

```java
public static boolean isDoji(Candle candle)
```
- **Parameters:** `candle` - Candle to analyze
- **Returns:** true if candle is a Doji pattern
- **Description:** Detects Doji: very small body (<10% of range), indicating market indecision

```java
public static boolean isStrongBullish(Candle candle)
```
- **Parameters:** `candle` - Candle to analyze
- **Returns:** true if candle is a strong bullish candle
- **Description:** Strong bullish: large body (>60% of range), close in upper half of range

```java
public static boolean isStrongBearish(Candle candle)
```
- **Parameters:** `candle` - Candle to analyze
- **Returns:** true if candle is a strong bearish candle
- **Description:** Strong bearish: large body (>60% of range), close in lower half of range

**Two-Candle Patterns:**
```java
import com.vish.fno.util.CandlePatternUtils;
import com.vish.fno.model.Candle;
import java.util.List;

public class TwoCandlePatternDetector {
    public void detectPatterns(List<Candle> candles) {
        if (candles.size() < 2) return;

        Candle prev = candles.get(candles.size() - 2);
        Candle current = candles.get(candles.size() - 1);

        // Engulfing patterns
        if (CandlePatternUtils.isBullishEngulfing(prev, current)) {
            System.out.println("Bullish Engulfing - strong reversal signal");
        }

        if (CandlePatternUtils.isBearishEngulfing(prev, current)) {
            System.out.println("Bearish Engulfing - strong reversal signal");
        }

        // Cloud cover patterns
        if (CandlePatternUtils.isDarkCloudCover(prev, current)) {
            System.out.println("Dark Cloud Cover - bearish reversal");
        }

        if (CandlePatternUtils.isPiercingPattern(prev, current)) {
            System.out.println("Piercing Pattern - bullish reversal");
        }

        // Tweezer patterns
        if (CandlePatternUtils.isTweezerBottom(prev, current)) {
            System.out.println("Tweezer Bottom - potential bullish reversal");
        }

        if (CandlePatternUtils.isTweezerTop(prev, current)) {
            System.out.println("Tweezer Top - potential bearish reversal");
        }

        // Harami patterns
        if (CandlePatternUtils.isBullishHarami(prev, current)) {
            System.out.println("Bullish Harami - potential reversal");
        }

        if (CandlePatternUtils.isBearishHarami(prev, current)) {
            System.out.println("Bearish Harami - potential reversal");
        }
    }
}
```

**Two-Candle Pattern Signatures:**

```java
public static boolean isBullishEngulfing(Candle prev, Candle current)
```
- **Parameters:**
  - `prev` - Previous candle
  - `current` - Current candle
- **Returns:** true if current candle forms a Bullish Engulfing pattern with previous
- **Description:** Prev is bearish, current is bullish and completely engulfs prev body (current body >1.2x prev body). Strong bullish reversal signal.

```java
public static boolean isBearishEngulfing(Candle prev, Candle current)
```
- **Parameters:**
  - `prev` - Previous candle
  - `current` - Current candle
- **Returns:** true if current candle forms a Bearish Engulfing pattern with previous
- **Description:** Prev is bullish, current is bearish and completely engulfs prev body (current body >1.2x prev body). Strong bearish reversal signal.

```java
public static boolean isDarkCloudCover(Candle prev, Candle current)
```
- **Parameters:**
  - `prev` - Previous candle
  - `current` - Current candle
- **Returns:** true if pattern is a Dark Cloud Cover (bearish reversal)
- **Description:** Prev is bullish, current opens above prev high and closes below midpoint of prev body. Bearish reversal signal.

```java
public static boolean isPiercingPattern(Candle prev, Candle current)
```
- **Parameters:**
  - `prev` - Previous candle
  - `current` - Current candle
- **Returns:** true if pattern is a Piercing Pattern (bullish reversal)
- **Description:** Prev is bearish, current opens below prev low and closes above midpoint of prev body. Bullish reversal signal.

```java
public static boolean isTweezerBottom(Candle prev, Candle current)
```
- **Parameters:**
  - `prev` - Previous candle
  - `current` - Current candle
- **Returns:** true if pattern is a Tweezer Bottom
- **Description:** Both candles have nearly identical lows (within 1% tolerance), prev is bearish, current is bullish. Potential bullish reversal.

```java
public static boolean isTweezerTop(Candle prev, Candle current)
```
- **Parameters:**
  - `prev` - Previous candle
  - `current` - Current candle
- **Returns:** true if pattern is a Tweezer Top
- **Description:** Both candles have nearly identical highs (within 1% tolerance), prev is bullish, current is bearish. Potential bearish reversal.

```java
public static boolean isBullishHarami(Candle prev, Candle current)
```
- **Parameters:**
  - `prev` - Previous candle
  - `current` - Current candle
- **Returns:** true if pattern is a Bullish Harami
- **Description:** Prev is bearish, current is bullish and body is contained within prev body (current body <50% of prev body). Potential reversal.

```java
public static boolean isBearishHarami(Candle prev, Candle current)
```
- **Parameters:**
  - `prev` - Previous candle
  - `current` - Current candle
- **Returns:** true if pattern is a Bearish Harami
- **Description:** Prev is bullish, current is bearish and body is contained within prev body (current body <50% of prev body). Potential reversal.

**Three-Candle Patterns:**
```java
import com.vish.fno.util.CandlePatternUtils;
import com.vish.fno.model.Candle;
import java.util.List;

public class ThreeCandlePatternDetector {
    public void scanForPatterns(List<Candle> candles) {
        // Need at least 3 candles for these patterns
        if (candles.size() < 3) return;

        // Scan from index 2 onwards (need 2 previous candles)
        for (int i = 2; i < candles.size(); i++) {
            // Star patterns
            if (CandlePatternUtils.isMorningStar(candles, i)) {
                System.out.println("Morning Star at index " + i + " - strong bullish reversal");
            }

            if (CandlePatternUtils.isEveningStar(candles, i)) {
                System.out.println("Evening Star at index " + i + " - strong bearish reversal");
            }

            // Soldier/Crow patterns
            if (CandlePatternUtils.isThreeWhiteSoldiers(candles, i)) {
                System.out.println("Three White Soldiers at index " + i + " - bullish continuation");
            }

            if (CandlePatternUtils.isThreeBlackCrows(candles, i)) {
                System.out.println("Three Black Crows at index " + i + " - bearish continuation");
            }
        }
    }
}
```

**Three-Candle Pattern Signatures:**

```java
public static boolean isMorningStar(List<Candle> candles, int index)
```
- **Parameters:**
  - `candles` - List of candles
  - `index` - Index to check (must be >= 2)
- **Returns:** true if Morning Star pattern (3-candle bullish reversal) is detected
- **Description:** Pattern: (1) Large bearish candle, (2) Small-bodied star gapped down, (3) Large bullish candle closing above midpoint of first. Strong bullish reversal.

```java
public static boolean isEveningStar(List<Candle> candles, int index)
```
- **Parameters:**
  - `candles` - List of candles
  - `index` - Index to check (must be >= 2)
- **Returns:** true if Evening Star pattern (3-candle bearish reversal) is detected
- **Description:** Pattern: (1) Large bullish candle, (2) Small-bodied star gapped up, (3) Large bearish candle closing below midpoint of first. Strong bearish reversal.

```java
public static boolean isThreeWhiteSoldiers(List<Candle> candles, int index)
```
- **Parameters:**
  - `candles` - List of candles
  - `index` - Index to check (must be >= 2)
- **Returns:** true if Three White Soldiers pattern (bullish continuation) is detected
- **Description:** Three consecutive bullish candles with substantial bodies (>50% of range), each opening within previous body and closing higher. Strong bullish continuation.

```java
public static boolean isThreeBlackCrows(List<Candle> candles, int index)
```
- **Parameters:**
  - `candles` - List of candles
  - `index` - Index to check (must be >= 2)
- **Returns:** true if Three Black Crows pattern (bearish continuation) is detected
- **Description:** Three consecutive bearish candles with substantial bodies (>50% of range), each opening within previous body and closing lower. Strong bearish continuation.

**Pattern Constants:**
The utility class uses these internal thresholds:
- `BODY_TO_RANGE_RATIO = 0.6` - Minimum body size for pattern validity (60%)
- `DOJI_BODY_RATIO = 0.1` - Maximum body size for doji (10%)
- `ENGULFING_SIZE_FACTOR = 1.2` - Minimum size factor for engulfing patterns (120%)

**Comprehensive Pattern Scanner Example:**
```java
import com.vish.fno.util.CandlePatternUtils;
import com.vish.fno.model.Candle;
import java.util.List;
import java.util.ArrayList;

public class ComprehensivePatternScanner {
    public List<String> scanAllPatterns(List<Candle> candles) {
        List<String> patterns = new ArrayList<>();

        if (candles.isEmpty()) return patterns;

        // Check latest candle for single-candle patterns
        Candle latest = candles.get(candles.size() - 1);
        if (CandlePatternUtils.isHammer(latest)) patterns.add("Hammer");
        if (CandlePatternUtils.isShootingStar(latest)) patterns.add("Shooting Star");
        if (CandlePatternUtils.isDoji(latest)) patterns.add("Doji");
        if (CandlePatternUtils.isStrongBullish(latest)) patterns.add("Strong Bullish");
        if (CandlePatternUtils.isStrongBearish(latest)) patterns.add("Strong Bearish");

        // Check two-candle patterns
        if (candles.size() >= 2) {
            Candle prev = candles.get(candles.size() - 2);
            if (CandlePatternUtils.isBullishEngulfing(prev, latest))
                patterns.add("Bullish Engulfing");
            if (CandlePatternUtils.isBearishEngulfing(prev, latest))
                patterns.add("Bearish Engulfing");
            if (CandlePatternUtils.isDarkCloudCover(prev, latest))
                patterns.add("Dark Cloud Cover");
            if (CandlePatternUtils.isPiercingPattern(prev, latest))
                patterns.add("Piercing Pattern");
            if (CandlePatternUtils.isTweezerBottom(prev, latest))
                patterns.add("Tweezer Bottom");
            if (CandlePatternUtils.isTweezerTop(prev, latest))
                patterns.add("Tweezer Top");
            if (CandlePatternUtils.isBullishHarami(prev, latest))
                patterns.add("Bullish Harami");
            if (CandlePatternUtils.isBearishHarami(prev, latest))
                patterns.add("Bearish Harami");
        }

        // Check three-candle patterns
        if (candles.size() >= 3) {
            int index = candles.size() - 1;
            if (CandlePatternUtils.isMorningStar(candles, index))
                patterns.add("Morning Star");
            if (CandlePatternUtils.isEveningStar(candles, index))
                patterns.add("Evening Star");
            if (CandlePatternUtils.isThreeWhiteSoldiers(candles, index))
                patterns.add("Three White Soldiers");
            if (CandlePatternUtils.isThreeBlackCrows(candles, index))
                patterns.add("Three Black Crows");
        }

        return patterns;
    }

    public boolean hasReversalSignal(List<Candle> candles) {
        List<String> patterns = scanAllPatterns(candles);
        return patterns.stream().anyMatch(p ->
            p.contains("Engulfing") ||
            p.contains("Star") ||
            p.contains("Hammer") ||
            p.contains("Shooting Star")
        );
    }
}
```

**Thread Safety:** All CandlePatternUtils methods are static, stateless, and thread-safe.

**Null Handling:** Two-candle and three-candle pattern methods return false if null candles are provided.

### OptionsMetaDataUtils - Options Calculations

**ATM Strike Calculation:**
```java
import com.vish.fno.util.OptionsMetaDataUtils;

// Get At-The-Money strike for NIFTY (strike interval = 50)
double spotPrice = 19537.25;
Double atmStrike = OptionsMetaDataUtils.getATMStrike(spotPrice, 50);
// Returns: 19550.0

// For BANKNIFTY (strike interval = 100)
Double bnfAtmStrike = OptionsMetaDataUtils.getATMStrike(43257.80, 100);
// Returns: 43300.0
```

**Expiry Calculation:**
```java
// Get next weekly expiry (Thursday)
LocalDate nextWeekly = OptionsMetaDataUtils.getNextWeeklyExpiry(LocalDate.now());

// Get next monthly expiry (last Thursday)
LocalDate nextMonthly = OptionsMetaDataUtils.getNextMonthlyExpiry(LocalDate.now());
```

**Option Symbol Generation:**
```java
String optionSymbol = OptionsMetaDataUtils.getOptionSymbol(
    "NIFTY",                        // underlying
    LocalDate.of(2024, 9, 26),      // expiry
    19500.0,                         // strike
    "CE"                             // option type
);
// Returns: "NIFTY24SEP19500CE"
```

### FileUtils - File I/O Operations

**Read/Write Candle Data:**
```java
import com.vish.fno.util.FileUtils;
import com.vish.fno.model.Candle;

// Write candles to JSON file
List<Candle> candles = // ... your data
FileUtils.writeCandlesToFile(candles, "/path/to/data.json");

// Read candles from JSON file
List<Candle> loadedCandles = FileUtils.readCandlesFromFile("/path/to/data.json");
```

**Read Instruments from CSV:**
```java
List<Instrument> instruments = FileUtils.readInstrumentsFromCsv("/path/to/instruments.csv");
```

### HeikinAshi - Smoothed Candles

**Convert to Heikin-Ashi:**
```java
import com.vish.fno.util.chart.HeikinAshi;
import com.vish.fno.util.CandleUtils;
import com.vish.fno.model.Candle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TrendAnalyzer {
    public void analyzeTrend(List<Candle> regularCandles) {
        List<Candle> heikinAshiCandles = HeikinAshi.convert(regularCandles);

        // Use HA candles for smoother trend identification
        Candle lastHA = heikinAshiCandles.get(heikinAshiCandles.size() - 1);
        if (CandleUtils.isBullish(lastHA)) {
            log.info("Strong uptrend confirmed");
        }
    }
}
```

**Heikin-Ashi Formula:**
- HA Close = (Open + High + Low + Close) / 4
- HA Open = (Previous HA Open + Previous HA Close) / 2
- HA High = Max(High, HA Open, HA Close)
- HA Low = Min(Low, HA Open, HA Close)

### FixedTargetAndStopLossStrategy - Risk Management

**Basic Usage:**
```java
import com.vish.fno.util.orderflow.FixedTargetAndStopLossStrategy;

// Create strategy: Target=100 points, Stop-loss=50 points
FixedTargetAndStopLossStrategy riskStrategy =
    new FixedTargetAndStopLossStrategy(100.0, 50.0);

double entryPrice = 19500.0;

// For BUY orders
double targetBuy = riskStrategy.calculateTarget(entryPrice, "BUY");      // 19600.0
double stopLossBuy = riskStrategy.calculateStopLoss(entryPrice, "BUY");  // 19450.0

// For SELL orders (reverses)
double targetSell = riskStrategy.calculateTarget(entryPrice, "SELL");      // 19400.0
double stopLossSell = riskStrategy.calculateStopLoss(entryPrice, "SELL");  // 19550.0
```

**Integration with Orders:**
```java
public class TradingService {
    private final FixedTargetAndStopLossStrategy tsl =
        new FixedTargetAndStopLossStrategy(100.0, 50.0);

    public void placeOrderWithExits(double entryPrice, String transactionType) {
        // Place entry order
        IndexOrderRequest entry = IndexOrderRequest.builder()
            .symbol("NIFTY24SEPFUT")
            .quantity(50)
            .orderType("MARKET")
            .transactionType(transactionType)
            .product("MIS")
            .build();

        String orderId = broker.placeOrder(entry);

        // Calculate exit levels
        double target = tsl.calculateTarget(entryPrice, transactionType);
        double stopLoss = tsl.calculateStopLoss(entryPrice, transactionType);

        // Place target order
        IndexOrderRequest targetOrder = IndexOrderRequest.builder()
            .symbol("NIFTY24SEPFUT")
            .quantity(50)
            .orderType("LIMIT")
            .price(target)
            .transactionType(transactionType.equals("BUY") ? "SELL" : "BUY")
            .product("MIS")
            .build();

        // Place stop-loss order
        IndexOrderRequest slOrder = IndexOrderRequest.builder()
            .symbol("NIFTY24SEPFUT")
            .quantity(50)
            .orderType("SL-M")
            .triggerPrice(stopLoss)
            .transactionType(transactionType.equals("BUY") ? "SELL" : "BUY")
            .product("MIS")
            .build();

        broker.placeOrder(targetOrder);
        broker.placeOrder(slOrder);
    }
}
```

### CompressionUtils - Data Compression

**Compress/Decompress Market Data:**
```java
import com.vish.fno.util.CompressionUtils;

// Compress data for storage
byte[] compressed = CompressionUtils.compress(largeDataString);

// Decompress for processing
String original = CompressionUtils.decompress(compressed);
```

### Trend - Market Trend Classification

**Enum Values:**
```java
public enum Trend {
    WEAK_UPTREND,
    UPTREND,
    STRONG_UPTREND,
    WEAK_DOWNTREND,
    DOWNTREND,
    STRONG_DOWNTREND,
    INDECISIVE,
    SIDEWAYS,
    ACCUMULATION,
    DISTRIBUTION,
    CONSOLIDATION,
    BREAKOUT,
    REVERSAL
}
```

**Usage:**
```java
import com.vish.fno.util.Trend;
import com.vish.fno.model.Candle;

public class TrendAnalyzer {
    public Trend identifyTrend(List<Candle> candles) {
        // Analyze candles and determine trend
        Candle latest = candles.get(candles.size() - 1);
        Candle previous = candles.get(candles.size() - 2);

        if (latest.close() > previous.close()) {
            double change = ((latest.close() - previous.close()) / previous.close()) * 100;
            if (change > 2.0) return Trend.STRONG_UPTREND;
            if (change > 0.5) return Trend.UPTREND;
            return Trend.WEAK_UPTREND;
        } else if (latest.close() < previous.close()) {
            double change = ((previous.close() - latest.close()) / previous.close()) * 100;
            if (change > 2.0) return Trend.STRONG_DOWNTREND;
            if (change > 0.5) return Trend.DOWNTREND;
            return Trend.WEAK_DOWNTREND;
        }

        return Trend.SIDEWAYS;
    }

    public boolean isBullishTrend(Trend trend) {
        return trend == Trend.WEAK_UPTREND ||
               trend == Trend.UPTREND ||
               trend == Trend.STRONG_UPTREND;
    }

    public boolean isBearishTrend(Trend trend) {
        return trend == Trend.WEAK_DOWNTREND ||
               trend == Trend.DOWNTREND ||
               trend == Trend.STRONG_DOWNTREND;
    }
}
```

### JsonUtils - JSON Serialization

**Convert Objects to JSON:**
```java
import com.vish.fno.util.JsonUtils;
import com.vish.fno.model.order.orderrequest.IndexOrderRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrderLogger {
    public void logOrder(IndexOrderRequest order) {
        // Get formatted JSON (pretty-printed)
        String formattedJson = JsonUtils.getFormattedObject(order);
        log.info("Order: {}", formattedJson);
        /*
        {
          "symbol" : "NIFTY24SEPFUT",
          "quantity" : 50,
          "orderType" : "MARKET",
          "transactionType" : "BUY",
          "product" : "MIS"
        }
        */

        // Get non-formatted JSON (compact)
        String compactJson = JsonUtils.getNonFormattedObject(order);
        // {"symbol":"NIFTY24SEPFUT","quantity":50,...}

        // Store in database or log file
        saveToDatabase(compactJson);
    }
}
```

**Thread Safety:** Both methods are thread-safe and use static ObjectMapper.

### TimeFrameUtils - Timeframe Conversion

**Merge Candles by Timeframe:**
```java
import com.vish.fno.util.TimeFrameUtils;
import com.vish.fno.model.Candle;

public class DataProcessor {
    public void convertTimeframes() {
        List<Candle> oneMinCandles = // ... 1-minute candles

        // Merge 5 one-minute candles into one 5-minute candle
        List<Candle> fiveMinCandles = TimeFrameUtils.mergeCandle(oneMinCandles, 5);

        // Merge 15 one-minute candles into one 15-minute candle
        List<Candle> fifteenMinCandles = TimeFrameUtils.mergeCandle(oneMinCandles, 15);

        // Each merged candle contains:
        // - open: First candle's open
        // - high: Highest of all candles
        // - low: Lowest of all candles
        // - close: Last candle's close
        // - volume: Sum of all volumes
        // - oi: Sum of all open interests
    }
}
```

**Intraday Complete Candles Only:**
```java
import com.vish.fno.util.TimeFrameUtils;
import com.vish.fno.model.Candle;

public class BacktestDataLoader {
    public List<Candle> loadCompleteCandles(List<Candle> rawData, int n) {
        // Only returns complete sets of 'n' candles
        // Drops incomplete last group
        List<Candle> completeCandles =
            TimeFrameUtils.mergeIntradayCompleteCandle(rawData, n);

        // Example: If rawData has 377 1-min candles
        // mergeIntradayCompleteCandle(rawData, 15) returns 25 complete 15-min candles
        // (375 candles used, 2 dropped)

        return completeCandles;
    }
}
```

**Combine Multiple Candles:**
```java
import com.vish.fno.util.TimeFrameUtils;
import com.vish.fno.model.Candle;

public class CandleMerger {
    public Candle mergeHourlyCandle(List<Candle> fifteenMinCandles) {
        // Take 4 fifteen-minute candles
        List<Candle> fourCandles = fifteenMinCandles.subList(0, 4);

        // Combine into single hourly candle
        Candle hourlyCandle = TimeFrameUtils.combine(fourCandles);

        // Returns null if input is null or empty
        // Returns single candle if input has only 1 candle
        return hourlyCandle;
    }
}
```

**Thread Safety:** All TimeFrameUtils methods are stateless and thread-safe.

## Common Patterns

### Pattern 1: Multi-Timeframe Analysis
```java
import com.vish.fno.util.CandleUtils;
import com.vish.fno.model.Candle;

public class MultiTimeframeAnalyzer {
    public boolean isStrongTrend(List<Candle> oneMinData) {
        // Convert to multiple timeframes
        List<Candle> fifteenMin = CandleUtils.convertToTimeFrame(oneMinData, "15minute");
        List<Candle> thirtyMin = CandleUtils.convertToTimeFrame(oneMinData, "30minute");
        List<Candle> hourly = CandleUtils.convertToTimeFrame(oneMinData, "60minute");

        // Check all timeframes for bullish trend
        boolean bullish15 = CandleUtils.isBullish(fifteenMin.get(fifteenMin.size() - 1));
        boolean bullish30 = CandleUtils.isBullish(thirtyMin.get(thirtyMin.size() - 1));
        boolean bullish60 = CandleUtils.isBullish(hourly.get(hourly.size() - 1));

        return bullish15 && bullish30 && bullish60;
    }
}
```

### Pattern 2: Market Hours Validation
```java
@Service
public class StrategyExecutor {
    public void execute() {
        if (!TimeUtils.isMarketOpen(LocalDateTime.now())) {
            logger.info("Market is closed. Skipping execution.");
            return;
        }

        if (TimeUtils.isHoliday(LocalDate.now())) {
            logger.info("Today is a holiday. Skipping execution.");
            return;
        }

        // Execute strategy
        runTradingLogic();
    }
}
```

### Pattern 3: Options Strategy Builder
```java
public class OptionsStrategyBuilder {
    public OptionBasedOrderRequest buildStraddleOrder(String underlying, double spot) {
        // Get ATM strike
        int strikeInterval = underlying.equals("NIFTY") ? 50 : 100;
        Double atmStrike = OptionsMetaDataUtils.getATMStrike(spot, strikeInterval);

        // Get next weekly expiry
        LocalDate expiry = OptionsMetaDataUtils.getNextWeeklyExpiry(LocalDate.now());

        // Build call order
        return OptionBasedOrderRequest.builder()
            .symbol(underlying)
            .strikePrice(atmStrike)
            .optionType("CE")
            .expiryDate(expiry)
            .quantity(underlying.equals("NIFTY") ? 50 : 15)
            .orderType("MARKET")
            .transactionType("SELL")
            .product("NRML")
            .build();
    }
}
```

### Pattern 4: Data Preprocessing with Heikin-Ashi
```java
import com.vish.fno.util.chart.HeikinAshi;
import com.vish.fno.util.CandleUtils;
import com.vish.fno.model.Candle;
import java.util.stream.IntStream;

public class TrendIdentifier {
    public String identifyTrend(List<Candle> candles) {
        // Convert to Heikin-Ashi for smoother trend
        List<Candle> haCandles = HeikinAshi.convert(candles);

        // Check last 3 HA candles
        int size = haCandles.size();
        boolean allBullish = IntStream.range(size - 3, size)
            .mapToObj(haCandles::get)
            .allMatch(CandleUtils::isBullish);

        boolean allBearish = IntStream.range(size - 3, size)
            .mapToObj(haCandles::get)
            .allMatch(CandleUtils::isBearish);

        if (allBullish) return "STRONG_UPTREND";
        if (allBearish) return "STRONG_DOWNTREND";
        return "SIDEWAYS";
    }
}
```

## Best Practices

1. **Cache converted timeframes** instead of recalculating
2. **Always check market hours** before placing orders
3. **Use Heikin-Ashi** for trend identification, not exact entry/exit
4. **Validate file paths** before reading/writing
5. **Use proper strike intervals** (NIFTY=50, BANKNIFTY=100)

## Thread Safety

- All utility methods are **static** and thread-safe
- **CandleUtils, TimeUtils, OptionsMetaDataUtils**: Safe for concurrent use
- **FileUtils**: Use synchronization if multiple threads write to same file

## Performance Considerations

- **Timeframe conversion**: O(n) complexity, cache results
- **Heikin-Ashi conversion**: O(n), processes sequentially
- **File operations**: I/O bound, consider async for large files
