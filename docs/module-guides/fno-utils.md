# fno-utils Module Guide

Utility functions for candlestick manipulation, time operations, file handling, order flow, and data caching.

**Module:** `com.vish.fno:fno-utils:1.0.0`

**Dependencies:** fno-models (transitive), Jackson, Lombok

---

## Package Overview

| Package | Description |
|---------|-------------|
| `com.vish.fno.util` | Core utilities (CandleUtils, TimeUtils, Utils, FnoConstants) |
| `com.vish.fno.util.chart` | HeikinAshi transformations |
| `com.vish.fno.util.helper` | Caching (DataCache, TimeSource, CandlestickDataProvider) |

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
| `getCandleData(String)` | `List<Candle>` | Read from JSON file |
| `getPrevDayCandleData(String)` | `List<Candle>` | Previous day candles |
| `getSmaData(String)` / `getEmaData(String)` / `getBBData(String)` | `List<Double>` | Read indicator data |

---

### TimeUtils

All methods static and thread-safe. Default timezone: Asia/Kolkata (IST). Trading hours: 9:15 AM - 3:30 PM (index 0-375).

| Method | Returns | Description |
|--------|---------|-------------|
| `currentTime()` | `Date` | Current system time |
| `getTime()` / `getTime(Date)` | `String` | Time as "HH:mm" |
| `getTodayDate()` | `String` | Today as "yyyy-MM-dd" |
| `getStringDate(Date)` | `String` | Date as "yyyy-MM-dd" |
| `getStringDateTime(Date)` | `String` | Datetime as "yyyy-MM-dd HH:mm:ss.SSS" |
| `getDateObject(String)` | `Date` | Parse "yyyy-MM-dd" |
| `getDateTimeObjectMinute(String)` | `Date` | Parse "yyyy-MM-dd HH:mm" |
| `getIndexOfTimeStamp(Date)` | `int` | Minute index (0-375, -1 if outside hours) |
| `getTimeByIndex(int)` | `String` | Time for minute index |
| `getOpeningTime()` / `getClosingTime()` | `Date` | Today at 9:15 AM / 3:30 PM |
| `appendOpeningTimeToDate(Date)` | `Date` | Set time to 9:15 AM |
| `appendClosingTimeToDate(Date)` | `Date` | Set time to 3:30 PM |
| `getPreviousWorkDay(Date)` | `Date` | Previous weekday |
| `getDatesBetween(Date, Date)` | `List<Date>` | Weekdays in range |
| `getNDaysBefore(long)` | `Date` | N days before today |
| `getTimeElapsed(long)` | `String` | Human-readable elapsed time |
| `isWithinTradingHours(long)` | `boolean` | Check if in trading hours |
| `parseCandlestickTimestamp(String)` | `long` | Parse ms or ISO-8601 |
| `formatDateTime(long)` | `String` | Format epoch to "yyyy-MM-dd HH:mm:ss" |

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

### Utils

All methods static and thread-safe.

| Method | Returns | Description |
|--------|---------|-------------|
| `getStringRoundedPrice(double)` | `String` | Format with K/M suffixes |
| `format(double)` | `String` | 2 decimal places |
| `round(double)` | `double` | Nearest 0.05 (5 paise) |
| `roundTo5Paise(double)` | `BigDecimal` | BigDecimal to 5 paise |
| `roundToNearest(BigDecimal, BigDecimal)` | `BigDecimal` | Round to increment |
| `getTopNLines(Throwable, int)` | `String` | Top N stack trace lines |

---

### ActiveOrderFormatter

Static utility for formatting ActiveOrder to CSV/log formats. Thread-safe.

| Method | Returns | Description |
|--------|---------|-------------|
| `csvHeader(ActiveOrder)` | `String` | CSV header with extra data keys |
| `toCSV(ActiveOrder)` | `String` | CSV row (type-specific) |
| `orderLog(ActiveOrder)` | `String` | Formatted log string |

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

Instance-based, NOT thread-safe. Uses `File.separator` for cross-platform paths.

| Method | Description |
|--------|-------------|
| `saveCandlestickData(List<Candle>, symbol, date)` | Save to `data/{symbol}_{date}.json` |
| `createDirectoryIfNotExist(path)` | Create directory |
| `saveTickData(symbol, tick)` | Save tick (overwrite) |
| `appendTickToFile(symbol, tick)` | Append tick |
| `logCompletedOrder(ActiveOrder)` | Log to `orderLog/` directory |

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

Base class for DataCache with automatic tick memory management. Thread-safe for tick operations (ConcurrentHashMap + ConcurrentLinkedDeque). Max 100 ticks per symbol with FIFO eviction.

### DataCacheImpl

Consolidated DataCache implementation. Constructor: `DataCacheImpl(CandlestickDataProvider, HolidayCalendar, TimeSource)`

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

In-memory intraday cache by symbol. NOT thread-safe.

| Method | Description |
|--------|-------------|
| `get(symbol)` | Get cached candles |
| `getLatestCandle(symbol)` | Most recent candle |
| `update(symbol, candles)` | Update cache |
| `clear(symbol)` | Remove data |

### HistoricDataCache

Spring `@Component` for historical data (date → symbol → candles). NOT thread-safe.

---

## Enums

### Trend

`WEAK_UPTREND`, `UPTREND`, `STRONG_UPTREND`, `WEAK_DOWNTREND`, `DOWNTREND`, `STRONG_DOWNTREND`, `INDECISIVE`, `SIDEWAYS`, `ACCUMULATION`, `DISTRIBUTION`, `CONSOLIDATION`, `BREAKOUT`, `REVERSAL`

---

## Thread Safety

| Component | Thread-Safe | Notes |
|-----------|-------------|-------|
| CandleUtils, TimeUtils, CandlePatternUtils, Utils | ✅ | Static methods |
| JsonUtils, CompressionUtils | ✅ | Static methods |
| AbstractDataCache (tick ops) | ✅ | ConcurrentHashMap + ConcurrentLinkedDeque |
| TimeProvider | ✅ | Instance methods |
| FileUtils, CandleStickCache, HistoricDataCache | ❌ | Instance-based |

---

## Error Handling

- **File operations:** May throw `IOException` or `RuntimeException`
- **Time parsing:** Returns null on failure
- `TimeUtils.parseCandlestickTimestamp()` returns current time on failure
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

Includes fno-models transitively.
