# fno-utils Module Guide

Utility functions for candlestick manipulation, time operations, file handling, order flow, and data caching.

**Module:** `com.vish.fno:fno-utils:1.0.0`

**Dependencies:** fno-models (transitive), Jackson, Lombok

---

## Package Overview

| Package | Description |
|---------|-------------|
| `com.vish.fno.util` | Core utilities (CandleUtils, TimeUtils, PriceUtils, FileUtils, FnoConstants) |
| `com.vish.fno.util.chart` | HeikinAshi transformations |
| `com.vish.fno.util.helper` | Caching (DataCache, TimeSource, CandlestickDataProvider, TradingHoursValidator) |
| `com.vish.fno.util.position` | Position sizing (PositionSizingService, PositionSize, LotSizeProvider) |

---

## Core Utilities

### CandleUtils

All methods static and thread-safe.

| Method | Returns | Description |
|--------|---------|-------------|
| `isBullish(Candle)` | `boolean` | close > open |
| `isBearish(Candle)` | `boolean` | close < open |
| `getBodyLength(Candle)` | `double` | \|close - open\| |
| `getTotalLength(Candle)` | `double` | high - low |
| `getUpperWick(Candle)` | `double` | high - max(open, close) |
| `getLowerWick(Candle)` | `double` | min(open, close) - low |
| `getBodySizePercentage(Candle)` | `double` | Body size as % of total (0.0-1.0) |
| `contains(Candle, double)` | `boolean` | Price within candle range |
| `findLocalMinimum(List<Candle>, int, int)` | `int` | Index of local min (-1 if not found) |
| `findLocalMaximum(List<Candle>, int, int)` | `int` | Index of local max (-1 if not found) |

**Note:** File reading methods (`getCandleData`, `getPrevDayCandleData`, `getSmaData`, `getEmaData`, `getBBData`) have been moved to `FileUtils`.

---

### TimeUtils

All methods static and thread-safe. Default timezone: Asia/Kolkata (IST). Trading hours: 9:15 AM - 3:30 PM (index 0-375).

**`timeArray`** is now immutable (`Collections.unmodifiableList`).

**Methods returning `Optional` (breaking change from null returns):**

| Method | Returns | Description |
|--------|---------|-------------|
| `getTime(Date)` | `Optional<String>` | Time as "HH:mm", empty if null input |
| `getStringDateTime(Date)` | `Optional<String>` | Datetime as "yyyy-MM-dd HH:mm:ss.SSS", empty if null |
| `getDateObject(String)` | `Optional<Date>` | Parse "yyyy-MM-dd", empty on parse error |
| `getDateTimeObjectMinute(String)` | `Optional<Date>` | Parse "yyyy-MM-dd HH:mm", empty on error |
| `getTimeStringForZonedDateString(String)` | `Optional<String>` | Time from zoned datetime string |
| `getDateTimeStringForZonedDateString(String)` | `Optional<String>` | DateTime from zoned datetime string |
| `getDateTimeForZonedDateString(String)` | `Optional<Date>` | Date from zoned datetime string |

**Convenience methods (fail-fast):**

| Method | Returns | Description |
|--------|---------|-------------|
| `parseOrderDate(String)` | `Date` | Parse zoned datetime to minute-truncated Date. Throws `IllegalStateException` on failure. Use in order builders. |

**Methods with unchanged signatures:**

| Method | Returns | Description |
|--------|---------|-------------|
| `currentTime()` | `Date` | Current system time (**@Deprecated** - use `TimeSource`) |
| `getTime()` | `String` | Current time as "HH:mm" (**@Deprecated** - use `TimeSource.now()`) |
| `getTodayDate()` | `String` | Today as "yyyy-MM-dd" (**@Deprecated** - use `TimeSource.getTodaysDateString()` or `getStringDate(date)`) |
| `getStringDate(Date)` | `String` | Date as "yyyy-MM-dd" (empty string if null) |
| `getStringYear(Date)` | `String` | Year as format string (empty if null) |
| `getIndexOfTimeStamp(Date)` | `int` | Minute index (0-375, -1 if outside hours) |
| `getTimeByIndex(int)` | `String` | Time for minute index |
| `getOpeningTime()` / `getClosingTime()` | `Date` | Today at 9:15 AM / 3:30 PM (**@Deprecated** - use `appendOpeningTimeToDate(date)` / `appendClosingTimeToDate(date)`) |
| `appendOpeningTimeToDate(Date)` | `Date` | Set time to 9:15 AM |
| `appendClosingTimeToDate(Date)` | `Date` | Set time to 3:30 PM |
| `getPreviousWorkDay(Date)` | `Date` | Previous weekday |
| `getDatesBetween(Date, Date)` | `List<Date>` | Weekdays in range |
| `getNDaysBefore(long)` | `Date` | N days before today (**@Deprecated** - use `getNDaysBefore(Date, long)`) |
| `getNDaysBefore(Date, long)` | `Date` | N days before given date |
| `getTimeElapsed(long)` | `String` | Human-readable elapsed time |
| `isWithinTradingHours(long)` | `boolean` | Check if in trading hours |
| `parseCandlestickTimestamp(String)` | `long` | Parse ms or ISO-8601 |
| `parseDateTimeToEpoch(String)` | `long` | Parse datetime string to epoch ms |
| `formatDateTime(long)` | `String` | Format epoch to "yyyy-MM-dd HH:mm:ss" |
| `fromEpochMilli(long)` | `LocalDateTime` | Convert epoch to IST LocalDateTime |
| `getLocalDateFromDate(Date)` | `LocalDate` | Convert Date to IST LocalDate |

---

### CandlePatternUtils

All methods static and thread-safe.

**Single-Candle Patterns:**

| Method | Pattern | Criteria |
|--------|---------|----------|
| `isHammer(Candle)` | Hammer | Lower wick > 2× body, upper < 0.3× body |
| `isInvertedHammer(Candle)` | Inverted Hammer | Upper wick > 2× body, lower < 0.3× body |
| `isShootingStar(Candle)` | Shooting Star | Inverted hammer + bearish close |
| `isDoji(Candle)` | Doji | Body < 10% of range |
| `isStrongBullish(Candle)` | Strong Bullish | Body > 60%, close in upper half |
| `isStrongBearish(Candle)` | Strong Bearish | Body > 60%, close in lower half |

**Two-Candle Patterns:**

| Method | Pattern |
|--------|---------|
| `isBullishEngulfing(prev, current)` | Bullish Engulfing |
| `isBearishEngulfing(prev, current)` | Bearish Engulfing |
| `isDarkCloudCover(prev, current)` | Dark Cloud Cover |
| `isPiercingPattern(prev, current)` | Piercing Pattern |
| `isTweezerBottom(prev, current)` | Tweezer Bottom (lows within 1%) |
| `isTweezerTop(prev, current)` | Tweezer Top (highs within 1%) |
| `isBullishHarami(prev, current)` | Bullish Harami |
| `isBearishHarami(prev, current)` | Bearish Harami |

**Three-Candle Patterns:** `isMorningStar(candles, index)`, `isEveningStar(candles, index)`, `isThreeWhiteSoldiers(candles, index)`, `isThreeBlackCrows(candles, index)`

---

### PriceUtils

Renamed from `Utils`. All methods static and thread-safe.

| Method | Returns | Description |
|--------|---------|-------------|
| `getStringRoundedPrice(double)` | `String` | Format with K/M suffixes |
| `format(double)` | `String` | 2 decimal places |
| `round(double)` | `double` | Nearest 0.05 (5 paise) |
| `roundTo5Paise(double)` | `BigDecimal` | BigDecimal to 5 paise |
| `roundToNearest(BigDecimal, BigDecimal)` | `BigDecimal` | Round to increment |
| `getTopNLines(Throwable, int)` | `String` | Top N stack trace lines |

---

### FnoConstants

Interface with trading constants.

**Exchanges:** `NSE`, `NFO`, `BFO`, `BSE`

**Instruments:** `CE`, `PE`, `FUT`

**Account Types:** `EQUITY`, `NET`, `DAY`

**Index Names:** `NIFTY_50`, `NIFTY_BANK`, `NIFTY_FIN_SERVICE`, `NIFTY_MIDCAP_SELECT`, `BANKEX`, `SENSEX`

**Derivative Symbols:** `DERIVATIVE_NIFTY`, `DERIVATIVE_BANKNIFTY`, `DERIVATIVE_FINNIFTY`, `DERIVATIVE_MIDCPNIFTY`, `DERIVATIVE_BANKEX`, `DERIVATIVE_SENSEX`

**Mapping:** `INDEX_TO_DERIVATIVE` - Maps index names to derivative trading symbols (e.g., "NIFTY 50" → "NIFTY")

**Date Formats:** `DATE_TIME_SEC_T_FORMAT`, `DATE_TIME_MS_FORMAT`, `DATE_TIME_SEC_FORMAT`, `DATE_TIME_FORMAT`, `DATE_FORMAT`, `TIME_FORMAT`, `YEAR_FORMAT`

---

## Chart Utilities

### HeikinAshi

Converts regular candles to Heikin Ashi. All methods static and thread-safe.

| Method | Returns | Description |
|--------|---------|-------------|
| `getCandles(List<Candle>)` | `List<Candle>` | HA candles (1-min) |
| `getCandles(List<Candle>, int)` | `List<Candle>` | HA candles (N-min) |
| `getIntradayCompleteCandle(List<Candle>, int)` | `List<Candle>` | HA excluding partial last |

**Formula:** HA Close = (O+H+L+C)/4, HA Open = (prevHAOpen + prevHAClose)/2, HA High = max(H, HAO, HAC), HA Low = min(L, HAO, HAC)

### TimeFrameUtils

All methods static and thread-safe.

| Method | Returns | Description |
|--------|---------|-------------|
| `mergeCandle(List<Candle>, int)` | `List<Candle>` | Merge N candles (date-grouped) |
| `mergeIntradayCompleteCandle(List<Candle>, int)` | `List<Candle>` | Merge without date grouping, no partial |
| `combine(List<Candle>)` | `Candle` | Combine multiple into one |

---

## Data Utilities

### JsonUtils

Static, thread-safe.

| Method | Returns | Description |
|--------|---------|-------------|
| `getNonFormattedObject(Object)` | `String` | Compact JSON |
| `getFormattedObject(Object)` | `String` | Pretty-printed JSON |

### CompressionUtils

Static, thread-safe. GZIP compression for tick data.

| Method | Returns | Description |
|--------|---------|-------------|
| `compressTickers(List<Ticker>)` | `byte[]` | GZIP compressed |
| `decompressTickers(byte[])` | `List<Ticker>` | Decompressed tickers |

### FileUtils

Instance methods are NOT thread-safe. Static methods are thread-safe. Uses `File.separator` for cross-platform paths.

**Instance Methods:**

| Method | Description |
|--------|-------------|
| `saveCandlestickData(List<Candle>, symbol, date)` | Save to `data/{symbol}_{date}.json` |
| `createDirectoryIfNotExist(path)` | Create directory |
| `saveTickData(symbol, tick)` | Save tick (overwrite) |
| `appendTickToFile(symbol, tick)` | Append tick |
| `logCompletedOrder(ActiveOrder)` | Log to `orderLog/` directory |

**Static File Reading Methods** (moved from CandleUtils):

| Method | Returns | Description |
|--------|---------|-------------|
| `getCandleData(String)` | `List<Candle>` | Read candles from JSON file |
| `getPrevDayCandleData(String)` | `List<Candle>` | Read previous day candles from JSON |
| `getSmaData(String)` / `getEmaData(String)` / `getBBData(String)` | `List<Double>` | Read indicator data from file |

**Static ActiveOrder Formatting Methods** (merged from removed `ActiveOrderFormatter`):

| Method | Returns | Description |
|--------|---------|-------------|
| `csvHeader(ActiveOrder)` | `String` | CSV header with extra data keys |
| `toCSV(ActiveOrder)` | `String` | CSV row (type-specific) |
| `orderLog(ActiveOrder)` | `String` | Formatted log string |

---

## Helper Package

### TimeSource Interface

Abstraction for time injection (production, backtest, unit test).

| Method | Returns | Description |
|--------|---------|-------------|
| `now()` | `LocalDateTime` | Current time |
| `todayDate()` | `Date` | Today's date |
| `currentTimeStampIndex()` | `int` | Trading minute index (0-375) |
| `getTodaysDateString()` | `String` | Today as "yyyy-MM-dd" |
| `getCurrentStringDateTime()` | `String` | Current as "yyyy-MM-dd HH:mm:ss" |

**Implementation:** `TimeProvider` - real system time, thread-safe.

### DataCache Interface

| Method | Returns | Description |
|--------|---------|-------------|
| `updateAndGetMinuteData(symbol)` | `List<Candle>` | Today's minute data |
| `updateAndGetHistoryMinuteData(date, symbol)` | `List<Candle>` | Historical minute data |
| `getNCandles(symbol, date, n)` | `List<Candle>` | Last N candles (multi-day) |
| `appendTick(symbol, ticker)` | `void` | Append tick to cache |
| `getLatestTick(symbol)` | `Ticker` | Most recent tick |
| `getTicks(symbol)` | `List<Ticker>` | All cached ticks |

### AbstractDataCache

Package-private base class for DataCache with automatic tick memory management. Thread-safe for tick operations (ConcurrentHashMap + ConcurrentLinkedDeque). Max 500 ticks per symbol with FIFO eviction.

### DataCacheImpl

Consolidated DataCache implementation. Constructor: `DataCacheImpl(CandlestickDataProvider, HolidayCalendar, TimeSource)`

Uses proper Optional chaining internally: `CandleStickCache.getLatestCandle()` returns `Optional<Candle>`, which is chained with `flatMap`/`map` for data freshness checks.

### CandlestickDataProvider Interface

| Method | Returns | Description |
|--------|---------|-------------|
| `getEntireDayHistoryData(date, symbol)` | `Optional<SymbolData>` | Day's candlestick data |
| `getEntireDayHistoryData(date, symbol, interval)` | `Optional<SymbolData>` | With interval (minute, 5minute, day) |

### HolidayCalendar Interface

| Method | Returns | Description |
|--------|---------|-------------|
| `getPreviousNonHolidayDate(Date)` | `Date` | Previous trading date |
| `getHolidays()` | `List<String>` | Holiday dates (yyyy-MM-dd) |

### CandleStickCache

Package-private in-memory intraday cache by symbol. Thread-safe (uses `ConcurrentHashMap`).

| Method | Returns | Description |
|--------|---------|-------------|
| `get(String)` | `List<Candle>` | Get cached candles |
| `getLatestCandle(String)` | `Optional<Candle>` | Most recent candle (empty if none) |
| `update(String, List<Candle>)` | `void` | Update cache |
| `clear(String)` | `void` | Remove data |

### HistoricDataCache

Package-private cache for historical data (date -> symbol -> candles). NOT thread-safe.

### TradingHoursValidator (NEW)

Validates whether a given time falls within configured trading hours. Excludes weekends.

```java
public TradingHoursValidator(LocalTime startTradingHour, LocalTime endTradingHour)
public boolean isWithinTradingHours(LocalDateTime now)
```

Returns `false` for Saturday/Sunday regardless of time.

---

## Position Sizing Package (NEW)

Package: `com.vish.fno.util.position`

### PositionSize (Record)

```java
public record PositionSize(int quantity, int lotSize)
```

### LotSizeProvider (Functional Interface)

```java
@FunctionalInterface
public interface LotSizeProvider {
    Integer getLotSize(String symbol);
}
```

### PositionSizingService

Centralized position sizing: separates "what to trade" (strategy) from "how much to trade".

```java
public PositionSizingService(LotSizeProvider lotSizeProvider, Map<String, Integer> lotSizeMap,
                              int defaultLotSize, Map<String, Integer> quantityMultiplierMap, int defaultMultiplier)
```

| Method | Returns | Description |
|--------|---------|-------------|
| `calculatePositionSize(OrderRequest)` | `PositionSize` | Calculate quantity from lot size x multiplier |
| `calculatePositionSizeWithCashConstraint(OrderRequest, double, double)` | `PositionSize` | Cash-constrained sizing (returns qty=0 if insufficient) |

**Lot size lookup order:** Dynamic provider -> static config map -> default.
**Multiplier lookup order:** `Task.getLots()` if > 1 -> tag-based map -> default.

---

## Enums

### Trend

`WEAK_UPTREND`, `UPTREND`, `STRONG_UPTREND`, `WEAK_DOWNTREND`, `DOWNTREND`, `STRONG_DOWNTREND`, `INDECISIVE`, `SIDEWAYS`, `ACCUMULATION`, `DISTRIBUTION`, `CONSOLIDATION`, `BREAKOUT`, `REVERSAL`

---

## Thread Safety

| Component | Thread-Safe | Notes |
|-----------|-------------|-------|
| CandleUtils, TimeUtils, CandlePatternUtils, PriceUtils | ✅ | Static methods |
| JsonUtils, CompressionUtils | ✅ | Static methods |
| AbstractDataCache (tick ops) | ✅ | ConcurrentHashMap + ConcurrentLinkedDeque |
| TimeProvider | ✅ | Instance methods |
| CandleStickCache | ✅ | ConcurrentHashMap |
| TradingHoursValidator | ✅ | Immutable fields |
| PositionSizingService | ✅ | Stateless (reads only) |
| FileUtils, HistoricDataCache | ❌ | Instance-based |

---

## Error Handling

- **File operations:** May throw `IOException` or `RuntimeException`
- **Time parsing:** Returns `Optional.empty()` on failure (was null prior to Java 21 upgrade)
- `TimeUtils.parseCandlestickTimestamp()` returns current time on failure
- `CandleUtils.findLocalMinimum/Maximum()` returns -1 if not found
- `FileUtils` static methods for file reading throw `RuntimeException` on IO failure

---

## Maven Dependency

```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-utils</artifactId>
    <version>1.0.0</version>
</dependency>
```

Includes fno-models transitively.
