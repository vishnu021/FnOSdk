# fno-utils Module Guide

Utility functions for candlestick manipulation, time operations, file handling, order flow strategies, and data caching.

**Module:** `com.vish.fno:fno-utils:1.0.0`

**Dependencies:** fno-models, Jackson, Lombok, Spring Boot Starter Data MongoDB

**Key Features:** Candlestick analysis, time utilities (IST), Heikin Ashi transformations, timeframe conversion, file operations, order flow strategies, data caching

---

## Core Utilities

### CandleUtils

**Package:** `com.vish.fno.util`

Utility methods for candlestick analysis, data loading, and pattern recognition. All methods are static and thread-safe.

**Methods:**

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `isBullish(Candle candle)` | `candle` | `boolean` | Determines if candle is bullish (close > open) |
| `isBearish(Candle candle)` | `candle` | `boolean` | Determines if candle is bearish (close < open) |
| `getBodyLength(Candle candle)` | `candle` | `double` | Absolute body length (distance between open and close) |
| `getTotalLength(Candle candle)` | `candle` | `double` | Total length (high - low) |
| `getUpperWick(Candle candle)` | `candle` | `double` | Upper wick length (high - max(open, close)) |
| `getLowerWick(Candle candle)` | `candle` | `double` | Lower wick length (min(open, close) - low) |
| `getBodySizePercentage(Candle candle)` | `candle` | `double` | Body size as % of total range (0.0-1.0) |
| `getCandleData(String filePath)` | `filePath` | `List<Candle>` | Reads candles from JSON file |
| `getPrevDayCandleData(String filePath)` | `filePath` | `List<Candle>` | Reads previous day's candles from JSON |
| `getSmaData(String fileName)` | `fileName` | `List<Double>` | Reads SMA values from file |
| `getEmaData(String fileName)` | `fileName` | `List<Double>` | Reads EMA values from file |
| `getBBData(String fileName)` | `fileName` | `List<Double>` | Reads Bollinger Band values from file |
| `readFile(String filename)` | `filename` | `String` | Reads complete file contents |
| `contains(Candle candle, double value)` | `candle`, `value` | `boolean` | Checks if price level is within candle range |
| `findLocalMinimum(List<Candle> candles, int startIndex, int window)` | `candles`, `startIndex`, `window` | `int` | Finds index of local minimum (-1 if not found) |
| `findLocalMaximum(List<Candle> candles, int startIndex, int window)` | `candles`, `startIndex`, `window` | `int` | Finds index of local maximum (-1 if not found) |

**Example:**
```java
import com.vish.fno.model.Candle;
import com.vish.fno.util.CandleUtils;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

@Slf4j
public class CandleAnalysisExample {
    public void analyzeCandleData() {
        List<Candle> candles = CandleUtils.getCandleData("/data/NIFTY_2025-01-15.json");

        for (Candle candle : candles) {
            if (CandleUtils.isBullish(candle)) {
                double bodyPct = CandleUtils.getBodySizePercentage(candle);
                log.info("Bullish candle: body={}%", bodyPct * 100);
            }
        }

        int minIndex = CandleUtils.findLocalMinimum(candles, 3, 3);
        if (minIndex != -1) {
            Candle supportCandle = candles.get(minIndex);
            log.info("Local minimum at index {}: low={}", minIndex, supportCandle.low());
        }
    }
}
```

---

### TimeUtils

**Package:** `com.vish.fno.util`

Comprehensive time utilities for trading hours, date conversions, and IST timezone operations. All methods are static and thread-safe.

**Thread-Safety:** All methods are thread-safe. Uses `DateTimeFormatter` (immutable) instead of `SimpleDateFormat`. No synchronized blocks needed.

**Default Timezone:** Asia/Kolkata (IST)

**Trading Hours:** 9:15 AM to 3:30 PM IST (index 0-375)

**Methods:**

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `getTimeStringForZonedDateString(String date)` | `date` | `String` | Extracts time from zoned datetime ("HH:mm") |
| `getDateTimeStringForZonedDateString(String date)` | `date` | `String` | Converts zoned datetime to local ("yyyy-MM-dd HH:mm") |
| `getDateTimeForZonedDateString(String date)` | `date` | `Date` | Converts zoned datetime to Date object |
| `getTime(Date timeStamp)` | `timeStamp` | `String` | Extracts time from Date ("HH:mm") |
| `getTime()` | - | `String` | Current time ("HH:mm") |
| `getDateTimeObjectMinute(String date)` | `date` | `Date` | Parses datetime ("yyyy-MM-dd HH:mm") |
| `getDateObject(String date)` | `date` | `Date` | Parses date ("yyyy-MM-dd") |
| `getTodayDate()` | - | `String` | Today's date ("yyyy-MM-dd") |
| `getStringDate(Date date)` | `date` | `String` | Formats date ("yyyy-MM-dd") |
| `getStringDateTime(Date timeStamp)` | `timeStamp` | `String` | Formats datetime with ms ("yyyy-MM-dd HH:mm:ss.SSS") |
| `getStringYear(Date date)` | `date` | `String` | Two-digit year ("yy") |
| `getIndexOfTimeStamp(Date timeStamp)` | `timeStamp` | `int` | Minute index within trading hours (0-375, -1 if outside) |
| `getTimeByIndex(int index)` | `index` | `String` | Time string for minute index ("HH:mm") |
| `currentTime()` | - | `Date` | Current system time |
| `appendOpeningTimeToDate(Date day)` | `day` | `Date` | Sets time to 9:15 AM |
| `appendClosingTimeToDate(Date day)` | `day` | `Date` | Sets time to 3:30 PM |
| `getOpeningTime()` | - | `Date` | Today at 9:15 AM |
| `getClosingTime()` | - | `Date` | Today at 3:30 PM |
| `getPreviousWorkDay(Date date)` | `date` | `Date` | Previous working day (handles weekends, not holidays) |
| `getDatesBetween(Date startDate, Date endDate)` | `startDate`, `endDate` | `List<Date>` | All weekdays in range (excludes weekends) |
| `getNDaysBefore(long n)` | `n` | `Date` | Date N days before today |
| `getNDaysBefore(Date date, long n)` | `date`, `n` | `Date` | Date N days before reference date |
| `getTimeElapsed(long milliseconds)` | `milliseconds` | `String` | Human-readable elapsed time format |
| `parseCandlestickTimestamp(String timeStr)` | `timeStr` | `long` | Parses timestamp (ms or ISO-8601) |
| `isWithinTradingHours(long timestamp)` | `timestamp` | `boolean` | Checks if timestamp is 9:15 AM - 3:30 PM IST |
| `fromEpochMilli(long epochMilli)` | `epochMilli` | `LocalDateTime` | Converts epoch ms to LocalDateTime (IST) |
| `parseDateTimeToEpoch(String dateTimeStr)` | `dateTimeStr` | `long` | Parses various datetime formats to epoch ms |
| `formatDateTime(long timestamp)` | `timestamp` | `String` | Formats epoch ms to "yyyy-MM-dd HH:mm:ss" |

**Example:**
```java
import com.vish.fno.util.TimeUtils;
import lombok.extern.slf4j.Slf4j;
import java.util.Date;

@Slf4j
public class TimeUtilsExample {
    public void useTimeUtils() {
        Date now = TimeUtils.currentTime();
        int timeIndex = TimeUtils.getIndexOfTimeStamp(now);

        if (timeIndex >= 0 && timeIndex <= 375) {
            log.info("Trading minute index: {}", timeIndex);
            String timeStr = TimeUtils.getTimeByIndex(timeIndex);
            log.info("Time: {}", timeStr);
        }

        Date marketOpen = TimeUtils.getOpeningTime();
        Date marketClose = TimeUtils.getClosingTime();
        log.info("Market hours: {} to {}", TimeUtils.getTime(marketOpen), TimeUtils.getTime(marketClose));

        String zonedDate = "2025-01-15T09:15:00+0530";
        String timeStr = TimeUtils.getTimeStringForZonedDateString(zonedDate);
        log.info("Extracted time: {}", timeStr);
    }
}
```

---

### CandlePatternUtils

**Package:** `com.vish.fno.util`

Identifies candlestick patterns for Wyckoff phase trading and technical analysis. All methods are static and thread-safe.

**Pattern Categories:** Single-candle (Hammer, Doji), Two-candle (Engulfing, Piercing), Three-candle (Morning Star, Three White Soldiers)

**Methods:**

| Method | Parameters | Returns | Pattern Type | Criteria |
|--------|------------|---------|--------------|----------|
| `isHammer(Candle candle)` | `candle` | `boolean` | Single | Lower shadow > 2× body, upper shadow < 0.3× body |
| `isInvertedHammer(Candle candle)` | `candle` | `boolean` | Single | Upper shadow > 2× body, lower shadow < 0.3× body |
| `isShootingStar(Candle candle)` | `candle` | `boolean` | Single | Inverted hammer with bearish close |
| `isDoji(Candle candle)` | `candle` | `boolean` | Single | Body size < 10% of total range |
| `isStrongBullish(Candle candle)` | `candle` | `boolean` | Single | Body > 60% of range, close in upper half |
| `isStrongBearish(Candle candle)` | `candle` | `boolean` | Single | Body > 60% of range, close in lower half |
| `isBullishEngulfing(Candle prev, Candle current)` | `prev`, `current` | `boolean` | Two | Current bullish body engulfs previous bearish body |
| `isBearishEngulfing(Candle prev, Candle current)` | `prev`, `current` | `boolean` | Two | Current bearish body engulfs previous bullish body |
| `isDarkCloudCover(Candle prev, Candle current)` | `prev`, `current` | `boolean` | Two | Bearish reversal pattern |
| `isPiercingPattern(Candle prev, Candle current)` | `prev`, `current` | `boolean` | Two | Bullish reversal pattern |
| `isTweezerBottom(Candle prev, Candle current)` | `prev`, `current` | `boolean` | Two | Matching lows (within 1% tolerance) |
| `isTweezerTop(Candle prev, Candle current)` | `prev`, `current` | `boolean` | Two | Matching highs (within 1% tolerance) |
| `isBullishHarami(Candle prev, Candle current)` | `prev`, `current` | `boolean` | Two | Small bullish candle contained within large bearish |
| `isBearishHarami(Candle prev, Candle current)` | `prev`, `current` | `boolean` | Two | Small bearish candle contained within large bullish |
| `isMorningStar(List<Candle> candles, int index)` | `candles`, `index` | `boolean` | Three | Bullish reversal (large bearish + small star + large bullish) |
| `isEveningStar(List<Candle> candles, int index)` | `candles`, `index` | `boolean` | Three | Bearish reversal (large bullish + small star + large bearish) |
| `isThreeWhiteSoldiers(List<Candle> candles, int index)` | `candles`, `index` | `boolean` | Three | Three consecutive bullish candles |
| `isThreeBlackCrows(List<Candle> candles, int index)` | `candles`, `index` | `boolean` | Three | Three consecutive bearish candles |

**Example:**
```java
import com.vish.fno.model.Candle;
import com.vish.fno.util.CandlePatternUtils;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

@Slf4j
public class PatternRecognitionExample {
    public void identifyPatterns(List<Candle> candles) {
        for (int i = 2; i < candles.size(); i++) {
            Candle current = candles.get(i);
            Candle prev = candles.get(i - 1);

            if (CandlePatternUtils.isHammer(current)) {
                log.info("Hammer at index {} - potential bullish reversal", i);
            }

            if (CandlePatternUtils.isBullishEngulfing(prev, current)) {
                log.info("Bullish Engulfing at index {}", i);
            }

            if (CandlePatternUtils.isMorningStar(candles, i)) {
                log.info("Morning Star at index {} - strong bullish reversal", i);
            }
        }
    }
}
```

---

### FileUtils

**Package:** `com.vish.fno.util`

Handles file operations for candlestick data, tick data, and order logging. Instance-based class, not thread-safe.

**Platform Compatibility:** Uses `File.separator` for cross-platform path compatibility (Windows/Linux/macOS).

**Constructor:**
```java
public FileUtils()
```
Initializes with default Jackson ObjectMapper configuration (indented JSON, default directories).

**Methods:**

| Method | Parameters | Description |
|--------|------------|-------------|
| `saveCandlestickData(List<Candle> candles, String symbol, String date)` | `candles`, `symbol`, `date` | Saves candles to `data/{symbol}_{date}.json` |
| `createDirectoryIfNotExist(String path)` | `path` | Creates directory if not exists |
| `saveTickData(String symbol, Object tick)` | `symbol`, `tick` | Saves tick data (overwrites) |
| `appendTickToFile(String symbol, Object tick)` | `symbol`, `tick` | Appends tick data to file |
| `logCompletedOrder(ActiveOrder order)` | `order` | Logs completed order to `orderLog/{tag}-{index}-{date}-{timestamp}.json` |

**Example:**
```java
import com.vish.fno.model.Candle;
import com.vish.fno.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

@Slf4j
public class FilePersistenceExample {
    public void saveTradingData() {
        FileUtils fileUtils = new FileUtils();
        List<Candle> candles = List.of(
            new Candle("2025-01-15 09:15", 19500.0, 19550.0, 19480.0, 19540.0, 1000L, 500L)
        );

        fileUtils.saveCandlestickData(candles, "NIFTY50", "2025-01-15");
        log.info("Candlestick data saved successfully");
    }
}
```

---

### Utils

**Package:** `com.vish.fno.util`

General utility methods for price formatting, rounding, and error formatting. All methods are static and thread-safe.

**Methods:**

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `getStringRoundedPrice(final double price)` | `price` | `String` | Formats with K/M suffixes (e.g., "1.5K", "2.5M") |
| `format(double price)` | `price` | `String` | Formats to 2 decimal places |
| `round(double price)` | `price` | `double` | Rounds to nearest 0.05 (5 paise) |
| `roundTo5Paise(double price)` | `price` | `BigDecimal` | BigDecimal rounded to 5 paise |
| `roundToNearest(final BigDecimal value, final BigDecimal increment)` | `value`, `increment` | `BigDecimal` | Rounds to nearest increment |
| `getTopNLines(Throwable throwable, int n)` | `throwable`, `n` | `String` | Extracts top N stack trace lines |

**Example:**
```java
import com.vish.fno.util.Utils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PriceFormattingExample {
    public void formatPrices() {
        double orderValue = 2500000.0;
        String formatted = Utils.getStringRoundedPrice(orderValue);
        log.info("Order value: {}", formatted); // "2.50M"

        double price = 19547.33;
        double rounded = Utils.round(price);
        log.info("Rounded to 5 paise: {}", rounded); // 19547.35
    }
}
```

---

### FnoConstants

**Package:** `com.vish.fno.util`

Interface defining constants for exchanges, instruments, trading symbols, derivative mappings, directory paths, and date/time formats.

**Exchange Names:**
```java
String NSE = "NSE";
String NFO = "NFO";
String BFO = "BFO";
String BSE = "BSE";
```

**Instrument Types:**
```java
String CE = "CE";   // Call Option
String PE = "PE";   // Put Option
String FUT = "FUT"; // Futures
```

**Account/Position Types:**
```java
String EQUITY = "equity";
String NET = "net";
String DAY = "day";
```

**Index Names (NSE Display):**
```java
String NIFTY_50 = "NIFTY 50";
String NIFTY_BANK = "NIFTY BANK";
String NIFTY_FIN_SERVICE = "NIFTY FIN SERVICE";
String NIFTY_MIDCAP_SELECT = "NIFTY MIDCAP SELECT";
String BANKEX = "BANKEX";
String SENSEX = "SENSEX";
```

**Derivative Trading Symbols:**
```java
String DERIVATIVE_NIFTY = "NIFTY";
String DERIVATIVE_BANKNIFTY = "BANKNIFTY";
String DERIVATIVE_FINNIFTY = "FINNIFTY";
String DERIVATIVE_MIDCPNIFTY = "MIDCPNIFTY";
String DERIVATIVE_BANKEX = "BANKEX";
String DERIVATIVE_SENSEX = "SENSEX";
```

**Index-to-Derivative Mapping:**
```java
Map<String, String> INDEX_TO_DERIVATIVE = Map.of(
    NIFTY_50, DERIVATIVE_NIFTY,
    NIFTY_BANK, DERIVATIVE_BANKNIFTY,
    NIFTY_FIN_SERVICE, DERIVATIVE_FINNIFTY,
    NIFTY_MIDCAP_SELECT, DERIVATIVE_MIDCPNIFTY,
    BANKEX, DERIVATIVE_BANKEX,
    SENSEX, DERIVATIVE_SENSEX
);
```

Handles the mismatch in Indian Stock Market where index names differ from F&O trading symbols (e.g., "NIFTY 50" trades as "NIFTY" in derivatives).

**Stock Symbols:** `BAJFINANCE`, `HDFCBANK`, `HINDUNILVR`, `RELIANCE`

**Date/Time Formats:**
```java
String DATE_TIME_SEC_T_FORMAT = "yyyy-MM-dd'T'HH:mm:ssX";
String DATE_TIME_MS_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
String DATE_TIME_SEC_FORMAT = "yyyy-MM-dd HH:mm:ss";
String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm";
String DATE_FORMAT = "yyyy-MM-dd";
String TIME_FORMAT = "HH:mm";
String YEAR_FORMAT = "yy";
```

**Chart Types:** `MINUTE`, `CANDLESTICK`, `VOLUME`, `LINE`, `BAR`

**Directory Names:** `directory = "instrument_cache"`, `tick_directory = "tick"`

**Usage Example:**
```java
import static com.vish.fno.util.FnoConstants.NSE;
import static com.vish.fno.util.FnoConstants.NFO;
import static com.vish.fno.util.FnoConstants.CE;
import static com.vish.fno.util.FnoConstants.NIFTY_50;
import static com.vish.fno.util.FnoConstants.DERIVATIVE_NIFTY;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConstantsUsageExample {
    public void useConstants() {
        log.info("Trading on exchange: {}", NFO);
        log.info("Option type: {}", CE);

        String derivativeSymbol = FnoConstants.INDEX_TO_DERIVATIVE.get(NIFTY_50);
        log.info("NIFTY 50 trades as: {}", derivativeSymbol); // "NIFTY"
    }
}

---

### ActiveOrderFormatter

**Package:** `com.vish.fno.util`

Utility class for formatting ActiveOrder instances to CSV and log formats. Handles type-specific formatting for all ActiveOrder implementations. All methods are static and thread-safe.

**Methods:**

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `csvHeader(ActiveOrder order)` | `order` | `String` | CSV header with extra data keys from order |
| `toCSV(ActiveOrder order)` | `order` | `String` | CSV row with type-specific formatting (ActiveIndexOrder, TickBasedActiveOrder, OptionBasedActiveOrder) |
| `orderLog(ActiveOrder order)` | `order` | `String` | Formatted log string with type-specific formatting |

**Type-Specific Formatting:**
- **ActiveIndexOrder**: Includes index, timestamps, prices, call/put flag, profit calculation based on direction
- **TickBasedActiveOrder**: Similar to ActiveIndexOrder with tick-specific formatting
- **OptionBasedActiveOrder**: Includes index, timestamps, prices, always calculates profit as `(sellPrice - buyPrice) * quantity`

**Example:**
```java
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.activeorder.ActiveOrderFactory;
import com.vish.fno.model.order.orderrequest.IndexOrderRequest;
import com.vish.fno.util.ActiveOrderFormatter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrderFormattingExample {
    public void formatOrders(IndexOrderRequest orderRequest) {
        ActiveOrder activeOrder = ActiveOrderFactory.createOrder(
            orderRequest, 19505.0, 920, "2024-10-28 09:20:00", 50, 50
        );

        // Generate CSV header
        String header = ActiveOrderFormatter.csvHeader(activeOrder);
        log.info("CSV Header: {}", header);

        // Convert to CSV row
        String csvRow = ActiveOrderFormatter.toCSV(activeOrder);
        log.info("CSV: {}", csvRow);

        // Generate order log
        String orderLog = ActiveOrderFormatter.orderLog(activeOrder);
        log.info("{}", orderLog);
    }
}
```

---

## Chart Utilities

### HeikinAshi

**Package:** `com.vish.fno.util.chart`

Converts regular candlestick data to Heikin Ashi candles for smoothed trend visualization. All methods are static and thread-safe.

**Formula:**
- HA Close = (Open + High + Low + Close) / 4
- HA Open = (Previous HA Open + Previous HA Close) / 2
- HA High = Max(High, HA Open, HA Close)
- HA Low = Min(Low, HA Open, HA Close)

**Methods:**

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `getCandles(List<Candle> allCandles)` | `allCandles` | `List<Candle>` | Converts to HA candles (1-minute) |
| `getCandles(List<Candle> allCandles, int timeFrame)` | `allCandles`, `timeFrame` | `List<Candle>` | Converts to HA candles (N-minute) |
| `getIntradayCompleteCandle(List<Candle> allCandles, int timeFrame)` | `allCandles`, `timeFrame` | `List<Candle>` | HA candles excluding partial last candle |

**Example:**
```java
import com.vish.fno.model.Candle;
import com.vish.fno.util.chart.HeikinAshi;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

@Slf4j
public class HeikinAshiExample {
    public void convertToHeikinAshi(List<Candle> regularCandles) {
        List<Candle> ha5Min = HeikinAshi.getCandles(regularCandles, 5);
        log.info("Created {} 5-minute HA candles", ha5Min.size());

        List<Candle> haComplete = HeikinAshi.getIntradayCompleteCandle(regularCandles, 5);
        log.info("Complete intraday HA candles: {}", haComplete.size());
    }
}
```

---

### TimeFrameUtils

**Package:** `com.vish.fno.util`

Converts candlestick data between different timeframes. All methods are static and thread-safe.

**Methods:**

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `mergeCandle(List<Candle> allCandles, int n)` | `allCandles`, `n` | `List<Candle>` | Merges N candles into one, grouping by date |
| `mergeIntradayCompleteCandle(List<Candle> allCandles, int n)` | `allCandles`, `n` | `List<Candle>` | Merges without date grouping, excludes partial last group |
| `combine(List<Candle> candleList)` | `candleList` | `Candle` | Combines multiple candles into single candle |

**Combination Logic:**
- Open: First candle's open
- Close: Last candle's close
- High: Maximum high
- Low: Minimum low
- Volume: Sum of volumes
- OI: Sum of open interest

**Example:**
```java
import com.vish.fno.model.Candle;
import com.vish.fno.util.TimeFrameUtils;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

@Slf4j
public class TimeFrameConversionExample {
    public void convertTimeFrames(List<Candle> minuteCandles) {
        List<Candle> fiveMinCandles = TimeFrameUtils.mergeCandle(minuteCandles, 5);
        log.info("Converted {} 1-min to {} 5-min candles", minuteCandles.size(), fiveMinCandles.size());

        List<Candle> completeCandles = TimeFrameUtils.mergeIntradayCompleteCandle(minuteCandles, 5);
        log.info("Complete intraday candles: {}", completeCandles.size());
    }
}
```

---

## Data Utilities

### JsonUtils

**Package:** `com.vish.fno.util`

JSON serialization utilities. All methods are static and thread-safe.

**Methods:**
- `getNonFormattedObject(Object order)`: Compact JSON (single line)
- `getFormattedObject(Object order)`: Pretty-printed JSON (indented, multi-line)

**Example:**
```java
import com.vish.fno.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonSerializationExample {
    public void serializeOrders(Object order) {
        String compactJson = JsonUtils.getNonFormattedObject(order);
        log.info("Compact: {}", compactJson);

        String formattedJson = JsonUtils.getFormattedObject(order);
        log.info("Formatted:\n{}", formattedJson);
    }
}
```

---

### CompressionUtils

**Package:** `com.vish.fno.util`

GZIP compression/decompression for tick data storage optimization. All methods are static and thread-safe.

**Methods:**
- `compressTickers(List<Ticker> tickers)`: Compresses tickers to GZIP byte array
- `decompressTickers(byte[] compressedData)`: Decompresses GZIP data to ticker list

**Example:**
```java
import com.vish.fno.model.Ticker;
import com.vish.fno.util.CompressionUtils;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.util.List;

@Slf4j
public class CompressionExample {
    public void compressTickData(List<Ticker> tickers) {
        try {
            byte[] compressed = CompressionUtils.compressTickers(tickers);
            log.info("Compressed {} tickers to {} bytes", tickers.size(), compressed.length);

            List<Ticker> decompressed = CompressionUtils.decompressTickers(compressed);
            log.info("Decompressed {} tickers", decompressed.size());
        } catch (IOException e) {
            log.error("Compression failed", e);
        }
    }
}
```

---

## Helper Package

### CandleStickCache

**Package:** `com.vish.fno.util.helper`

In-memory cache for intraday candlestick data by symbol. Not thread-safe.

**Methods:**
- `get(String symbol)`: Retrieves cached candles
- `getLatestCandle(String symbol)`: Gets most recent candle
- `update(String symbol, List<Candle> data)`: Updates cached candles
- `clear(String symbol)`: Removes cached data

**Example:**
```java
import com.vish.fno.model.Candle;
import com.vish.fno.util.helper.CandleStickCache;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

@Slf4j
public class CandleCachingExample {
    private final CandleStickCache cache = new CandleStickCache();

    public void cacheCandles() {
        String symbol = "NIFTY50";
        List<Candle> candles = List.of(/* ... */);

        cache.update(symbol, candles);
        Candle latest = cache.getLatestCandle(symbol);
        if (latest != null) {
            log.info("Latest candle: close={}", latest.close());
        }

        cache.clear(symbol);
    }
}
```

---

### AbstractDataCache

**Package:** `com.vish.fno.util.helper`

Abstract base class for implementing tick data caching strategies with automatic memory management. Thread-safe for tick operations.

**Memory Management:** Automatically limits cache to 100 ticks per symbol using deque-based FIFO eviction.

**Protected Fields:**
```java
protected final Map<String, Ticker> latestTicks;
protected final Map<String, Deque<Ticker>> ticksCache;
private static final int MAX_TICKS_PER_SYMBOL = 100;
```

**Public Methods:**

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `appendTick(String, Ticker)` | `symbol`, `tick` | `void` | Appends tick, evicts oldest if > 100 ticks. Thread-safe (ConcurrentLinkedDeque) |
| `getLatestTick(String)` | `symbol` | `Ticker` | Returns most recent tick for symbol |
| `getTicks(String)` | `symbol` | `List<Ticker>` | Returns all cached ticks as List (converts from Deque, backward compatible) |

**Implementation Details:**
- Uses `ConcurrentHashMap` for thread-safe tick storage
- Uses `ConcurrentLinkedDeque` for O(1) append/eviction (addLast/removeFirst)
- Automatic FIFO eviction when cache exceeds 100 ticks per symbol
- Returns defensive copy (ArrayList) from getTicks() for backward compatibility

**Subclasses must implement:** `updateAndGetMinuteData()`, `updateAndGetHistoryMinuteData()`, `getNCandles()`

**Example:**
```java
import com.vish.fno.model.Ticker;
import com.vish.fno.util.helper.AbstractDataCache;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MyDataCache extends AbstractDataCache {
    public void processIncomingTicks(String symbol, Ticker tick) {
        appendTick(symbol, tick); // Auto-evicts if > 100 ticks

        Ticker latest = getLatestTick(symbol);
        log.info("Latest tick for {}: LTP={}", symbol, latest.lastTradedPrice());

        List<Ticker> allTicks = getTicks(symbol);
        log.info("Cached {} ticks for {}", allTicks.size(), symbol); // Max 100
    }
}
```

---

### TimeProvider

**Package:** `com.vish.fno.util.helper`

Testable time operations for trading applications. Thread-safe.

**Methods:**

| Method | Returns | Description |
|--------|---------|-------------|
| `now()` | `LocalDateTime` | Current LocalDateTime |
| `todayDate()` | `Date` | Today's date |
| `currentTimeStampIndex()` | `int` | Current time index (0-375) |
| `getTodaysDateString()` | `String` | Today's date ("yyyy-MM-dd") |
| `getCurrentStringDateTime()` | `String` | Current datetime ("yyyy-MM-dd HH:mm:ss") |

**Example:**
```java
import com.vish.fno.util.helper.TimeProvider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimeProviderExample {
    private final TimeProvider timeProvider;

    public TimeProviderExample(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    public void logCurrentTime() {
        int timeIndex = timeProvider.currentTimeStampIndex();
        log.info("Current time index: {}", timeIndex);

        String dateStr = timeProvider.getTodaysDateString();
        log.info("Today: {}", dateStr);
    }
}
```

---

### DataCache

**Package:** `com.vish.fno.util.helper`

Interface defining contract for data caching implementations.

**Methods:**
- `updateAndGetMinuteData(String symbol)`: Updates and retrieves minute data
- `updateAndGetHistoryMinuteData(String date, String symbol)`: Retrieves historical minute data
- `getNCandles(final String symbol, final Date date, final int n)`: Gets N most recent candles
- `getNCandles(final String symbol, final Date date, final int n, List<Candle> todaysCandles)`: Gets N candles with optional today's candles
- `appendTick(String tickSymbol, Ticker ticker)`: Appends tick
- `getLatestTick(String symbol)`: Gets latest tick
- `getTicks(String symbol)`: Gets all cached ticks

---

### CandlestickDataProvider

**Package:** `com.vish.fno.util.helper`

Interface for providing candlestick data. Abstracts data source for production (Kite API) and backtest (file-based) environments.

**Implementations:**
- `CandlestickService` (OrderManager) - Production implementation using Kite API
- `BacktestCandlestickService` (Backtest) - Backtest implementation using file-based data

**Methods:**

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `getEntireDayHistoryData(String date, String symbol)` | `date` (yyyy-MM-dd), `symbol` | `Optional<SymbolData>` | Gets entire day's candlestick data (default minute interval) |
| `getEntireDayHistoryData(String date, String symbol, String interval)` | `date`, `symbol`, `interval` (e.g., "minute", "5minute", "day") | `Optional<SymbolData>` | Gets entire day's candlestick data with specified interval |

---

### HolidayCalendar

**Package:** `com.vish.fno.util.helper`

Interface for holiday calendar operations. Abstracts holiday lookup for production and backtest environments.

**Implementations:**
- `CalendarService` (OrderManager) - Production implementation
- `CalendarService` (BacktestRunner) - Backtest implementation

**Methods:**

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `getPreviousNonHolidayDate(Date date)` | `date` | `Date` | Gets previous trading date (excludes weekends and holidays) |
| `getHolidays()` | - | `List<String>` | Gets list of holiday dates in yyyy-MM-dd format |

---

### DataCacheImpl

**Package:** `com.vish.fno.util.helper`

Consolidated DataCache implementation that works with both production and backtest environments. Extends `AbstractDataCache` (tick caching) and adds candlestick data caching using interface abstractions.

**Usage:**
- **OrderManager (Production):** Inject `CandlestickService` and `CalendarService`
- **BacktestRunner:** Inject `BacktestCandlestickService` and `CalendarService`

**Constructor:**
```java
public DataCacheImpl(CandlestickDataProvider candlestickDataProvider,
                     HolidayCalendar holidayCalendar,
                     TimeProvider timeProvider)
```

**Methods:**

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `updateAndGetMinuteData(String symbol)` | `symbol` | `List<Candle>` | Updates intraday cache and returns minute data for today |
| `updateAndGetHistoryMinuteData(String date, String symbol)` | `date` (yyyy-MM-dd), `symbol` | `List<Candle>` | Retrieves historical minute data with caching |
| `getNCandles(String symbol, Date date, int n)` | `symbol`, `date`, `n` | `List<Candle>` | Gets N most recent candles, spanning multiple days if needed (max 5 days lookback) |
| `getNCandles(String symbol, Date date, int n, List<Candle> todaysCandles)` | `symbol`, `date`, `n`, `todaysCandles` | `List<Candle>` | Delegates to `getNCandles(symbol, date, n)` |

**Example:**
```java
import com.vish.fno.model.Candle;
import com.vish.fno.util.helper.CandlestickDataProvider;
import com.vish.fno.util.helper.DataCacheImpl;
import com.vish.fno.util.helper.HolidayCalendar;
import com.vish.fno.util.helper.TimeProvider;
import lombok.extern.slf4j.Slf4j;
import java.util.Date;

@Slf4j
public class DataCacheExample {
    public void useDataCache(CandlestickDataProvider dataProvider,
                             HolidayCalendar holidayCalendar,
                             TimeProvider timeProvider) {
        DataCacheImpl cache = new DataCacheImpl(dataProvider, holidayCalendar, timeProvider);

        // Get today's minute data
        List<Candle> todaysCandles = cache.updateAndGetMinuteData("NIFTY50");
        log.info("Today's candles: {}", todaysCandles.size());

        // Get historical data
        List<Candle> historicalCandles = cache.updateAndGetHistoryMinuteData("2025-01-15", "NIFTY50");
        log.info("Historical candles: {}", historicalCandles.size());

        // Get last 100 candles spanning multiple days
        List<Candle> last100 = cache.getNCandles("NIFTY50", new Date(), 100);
        log.info("Last 100 candles: {}", last100.size());
    }
}
```

---

### HistoricDataCache

**Package:** `com.vish.fno.util.helper`

Spring-managed cache for historical candlestick data (date → symbol → candles). Not thread-safe. Annotated with `@Component`.

**Methods:**
- `getDataCache()`: Gets entire cache (nested map)
- `getData(String date, String symbol)`: Retrieves cached data
- `update(String date, String symbol, List<Candle> candleStickData)`: Updates cached data

**Example:**
```java
import com.vish.fno.model.Candle;
import com.vish.fno.util.helper.HistoricDataCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoricalDataService {
    private final HistoricDataCache historicCache;

    public void cacheHistoricalData() {
        String date = "2025-01-15";
        String symbol = "NIFTY50";
        List<Candle> candles = List.of(/* ... */);

        historicCache.update(date, symbol, candles);
        List<Candle> cached = historicCache.getData(date, symbol);
        log.info("Retrieved {} cached candles", cached.size());
    }
}
```

---

## Enums

### Trend

**Package:** `com.vish.fno.util`

Enumeration of market trend states.

**Values:**
- `WEAK_UPTREND`, `UPTREND`, `STRONG_UPTREND`
- `WEAK_DOWNTREND`, `DOWNTREND`, `STRONG_DOWNTREND`
- `INDECISIVE`, `SIDEWAYS`, `ACCUMULATION`, `DISTRIBUTION`, `CONSOLIDATION`, `BREAKOUT`, `REVERSAL`

**Example:**
```java
import com.vish.fno.util.Trend;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TrendAnalysisExample {
    public void analyzeTrend(Trend currentTrend) {
        switch (currentTrend) {
            case STRONG_UPTREND -> log.info("Strong bullish momentum");
            case STRONG_DOWNTREND -> log.info("Strong bearish momentum");
            case ACCUMULATION -> log.info("Accumulation phase");
            case BREAKOUT -> log.info("Breakout detected");
            default -> log.info("Trend: {}", currentTrend);
        }
    }
}
```

---

## Thread Safety

**Thread-safe (static methods):** CandleUtils, TimeUtils, CandlePatternUtils, Utils, CompressionUtils, JsonUtils

**Thread-safe (tick operations):** AbstractDataCache (uses ConcurrentHashMap and ConcurrentLinkedDeque)

**Instance-based (not thread-safe):** FileUtils, CandleStickCache, HistoricDataCache

**Thread-safe (instance methods):** TimeProvider

**Note:** AbstractDataCache is thread-safe for appendTick(), getLatestTick(), and getTicks() operations. Subclass implementations of updateAndGetMinuteData() may require additional synchronization.

---

## Performance Considerations

- Use `TimeFrameUtils.mergeIntradayCompleteCandle()` for real-time to avoid partial candles
- Cache historical data using `HistoricDataCache` to reduce file I/O
- Use `CompressionUtils` for large tick datasets
- Prefer `CandleUtils.getBodySizePercentage()` over manual calculations

---

## Error Handling

**File operations:** May throw `IOException` or `RuntimeException`

**Time parsing:** Returns null on failure; always check for null

**Edge cases:**
- `TimeUtils.parseCandlestickTimestamp()` returns current time on failure (graceful degradation)
- `CandleUtils.findLocalMinimum/Maximum()` returns -1 if not found
- `LimitedCache` returns empty list for non-existent keys

---

## Maven Dependency

```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-utils</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Note:** fno-utils automatically includes fno-models as a transitive dependency.
