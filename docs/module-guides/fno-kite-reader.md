# fno-kite-reader - Kite Connect Integration

Integration with Zerodha's Kite Connect API for live market data, historical data retrieval, and order execution.

## Maven Dependency
```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-kite-reader</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Dependencies:** fno-utils (transitive: fno-models)

---

## Prerequisites

1. Zerodha Account with Kite Connect App (https://developers.kite.trade/)
2. API Key, API Secret, User ID
3. Request Token (from Kite login flow)

---

## Configuration

### Spring Boot Properties
```properties
kite.api-key=${KITE_API_KEY}
kite.api-secret=${KITE_API_SECRET}
kite.user-id=${KITE_USER_ID}
kite.place-orders=false
kite.connect-websocket=true
kite.nifty100.symbols=NIFTY 50,NIFTY BANK,TCS,INFY,RELIANCE
```

### Bean Configuration
```java
@Bean
public KiteService kiteService() {
    List<String> symbols = Arrays.asList(nifty100SymbolsStr.split(","));
    return new KiteService(apiSecret, apiKey, userId, symbols, placeOrders, connectToWebSocket);
}
```

**Constructor:** `KiteService(String apiSecret, String apiKey, String userId, List<String> nifty100Symbols, boolean placeOrders, boolean connectToWebSocket)`

### Authentication
```java
kiteService.authenticate(requestToken);
if (kiteService.isInitialised()) {
    kiteService.appendIndexITMOptions();
}
```

---

## KiteService - Primary API

### Core Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `authenticate(String)` | `void` | Generates session, initializes WebSocket |
| `isInitialised()` | `boolean` | Service ready for trading |
| `buyOrder(symbol, qty, tag, isPlace)` | `Optional<KiteOpenOrder>` | Place buy order (exchange auto-resolved) |
| `sellOrder(symbol, qty, tag, isPlace)` | `Optional<KiteOpenOrder>` | Place sell order (exchange auto-resolved) |
| `getOrders()` | `List<Order>` | All orders for day |
| `getPositions()` | `Map<String, List<Position>>` | Net and day positions |

### Historical Data

| Method | Returns | Description |
|--------|---------|-------------|
| `getHistoricalData(from, to, symbol, interval, continuous)` | `Optional<HistoricalData>` | Historical candles |
| `getEntireDayHistoricalData(from, to, symbol, interval)` | `Optional<HistoricalData>` | Intraday candles |

**Note:** Both methods return `Optional<HistoricalData>`. Returns `Optional.empty()` when KiteService is not initialized, instrument token is not found, or API call fails.

**Continuous mode (`continuous=true`):** When an expired futures symbol (e.g., `NIFTY25AUGFUT`) is not found in the instrument cache, the service automatically resolves it to the current active contract (e.g., `NIFTY25SEPFUT`) by extracting the base name and searching across year/month combinations. Uses pre-compiled regex patterns (`FUTURES_SYMBOL_PATTERN`, `BASE_NAME_PATTERN`) for efficient symbol parsing. All internal resolution methods use `Optional` with `.flatMap()` chains -- no null returns.

### Option Symbol Resolution

| Method | Returns | Description |
|--------|---------|-------------|
| `getITMStock(index, price, isCall)` | `String` | ITM option symbol |
| `getOTMStock(index, price, isCall)` | `String` | OTM option symbol |
| `appendIndexITMOptions()` | `void` | Add ITM options for default indices |
| `appendAllOptionsForIndex(String)` | `void` | Subscribe to ALL options for index (100+ symbols) |

### WebSocket Management

| Method | Returns | Description |
|--------|---------|-------------|
| `appendWebSocketSymbolsList(symbols, addFutures)` | `void` | Subscribe to additional symbols (deduplicates) |
| `setOnTickerArrivalListener(listener)` | `void` | Set tick data listener |
| `setOnOrderUpdateListener(listener)` | `void` | Set order update listener |
| `getSubscribedWebSocketTokens()` | `List<Long>` | Currently subscribed tokens |
| `getSubscribedWebSocketTokensCount()` | `int` | Count of subscribed tokens |
| `isSymbolSubscribed(String)` | `boolean` | Check if symbol subscribed |
| `getAllOptionSymbols(String)` | `List<String>` | All CE/PE symbols for index (nearest expiry) |

**WebSocket defaults:** All default indices (NIFTY 50, NIFTY BANK, BANKEX, SENSEX) have their option symbols auto-subscribed on initialization. Reconnection: max 10 retries with up to 30-second intervals.

### Instrument Utilities

| Method | Returns | Description |
|--------|---------|-------------|
| `getInstruments()` | `List<Instrument>` | Filtered instrument list |
| `getInstrument(String)` | `Optional<Long>` | Token for symbol (empty if not found) |
| `getSymbol(long)` | `String` | Symbol for token |
| `getLotSizeFromFuture(String)` | `Optional<Integer>` | Lot size for index |
| `getAllFutureLotSizeInfo()` | `Map<String, Integer>` | All index lot sizes |
| `isExpiryDayForOption(symbol, date)` | `boolean` | Check expiry |
| `getInstrumentCacheSize()` | `int` | Cache size (diagnostics) |
| `getFilteredInstruments()` | `List<Map<String, String>>` | All filtered instruments (exchange, symbol, expiry) |

---

## Models

### KiteOpenOrder (Record)

```java
public record KiteOpenOrder(Order order, boolean isOrderPlaced, Integer exceptionCode, String exceptionMessage)
```

| Scenario | isOrderPlaced | order | exceptionCode |
|----------|---------------|-------|---------------|
| Not initialized | `false` | `null` | `null` |
| Paper trading | `true` | `null` | `null` |
| Order placed | `true` | `Order` | `null` |
| Kite API error | `false` | `null` | Error code |

---

## Exceptions

### InitialisationException

Thrown during WebSocket initialization failure.

```java
try {
    KiteService service = new KiteService(..., true);
} catch (InitialisationException e) {
    KiteService service = new KiteService(..., false); // Fallback
}
```

---

## Utilities

### KiteITMResolver

Production implementation of `ITMResolver` interface.

```java
ITMResolver resolver = new KiteITMResolver(kiteService);
String itmCall = resolver.resolveITMSymbol("NIFTY 50", 19500.0, true);
```

### OrderUtils

```java
public static OrderParams createMarketOrderWithParameters(String symbol,
                                                          int orderSize,
                                                          String transactionType,
                                                          String tag,
                                                          String exchange)
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `symbol` | `String` | Trading symbol (e.g., `"NIFTY26FEB19500CE"`) |
| `orderSize` | `int` | Quantity to trade |
| `transactionType` | `String` | `Constants.TRANSACTION_TYPE_BUY` or `SELL` |
| `tag` | `String` | Order tag (truncated to 20 chars) |
| `exchange` | `String` | Exchange string (`"NFO"` or `"BFO"`) |

**Returns:** `OrderParams` -- pre-configured with MARKET order type, MIS product, DAY validity.

```java
// KiteService resolves exchange automatically via InstrumentCache
// Direct usage (rare -- KiteService.buyOrder/sellOrder is preferred):
String exchange = instrumentCache.getExchangeForSymbol(symbol);
OrderParams params = OrderUtils.createMarketOrderWithParameters(symbol, qty, Constants.TRANSACTION_TYPE_BUY, tag, exchange);
```

### InstrumentFileUtils

Thread-safe instrument cache persistence.

| Method | Description |
|--------|-------------|
| `saveInstrumentCache(List<Instrument>)` | Save to `instrument_cache/instruments_<date>.json` |
| `loadInstrumentCache(int)` | Load from N days ago (0=today) |

### InstrumentCache

Internal (package-private) thread-safe cache with double-checked locking. Returns defensive copies and unmodifiable collections.

**Key fields (all `volatile`):**
- `filteredInstruments` -- `List<Instrument>` of NSE/NFO/BSE/BFO instruments in the tracking list
- `symbolMap` -- `Map<String, Long>` tradingSymbol to instrument token
- `instrumentMap` -- `Map<Long, String>` reverse of symbolMap
- `exchangeMap` -- `Map<String, String>` tradingSymbol (uppercased) to exchange (`"NFO"` or `"BFO"`)

**Public methods:**

| Method | Returns | Description |
|--------|---------|-------------|
| `getInstruments()` | `List<Instrument>` | Unmodifiable filtered instruments (lazy-initialized) |
| `getInstrument(String)` | `Optional<Long>` | Token for symbol, empty if null/not found |
| `getSymbol(long)` | `String` | Symbol for instrument token |
| `getExchangeForSymbol(String)` | `String` | Exchange for symbol, defaults to `"NFO"` if not found |
| `getExpiryDates()` | `Set<String>` | All expiry dates in cache |
| `getAllSymbols()` | `Set<String>` | All instrument names |
| `getFilteredSymbols()` | `Map<String, String>` | tradingSymbol to name (sorted) |
| `getInstrumentForSymbol(String)` | `List<Instrument>` | Instruments matching exact tradingSymbol |
| `isExpiryDayForOption(String, Date)` | `boolean` | Check if option expires on given date |
| `getLotSizeFromFuture(String)` | `Optional<Integer>` | Lot size for index via FUT contract |
| `getAllFutureLotSizeInfo()` | `Map<String, Integer>` | All index lot sizes |
| `getInstrumentMapSize()` | `int` | Cache size (diagnostics) |

#### getExchangeForSymbol

```java
public String getExchangeForSymbol(String symbol)
```

Resolves the exchange (`"NFO"` or `"BFO"`) for a given trading symbol by looking up the `exchangeMap` built during initialization. Falls back to `"NFO"` with a warning log if the symbol is not found or is null. Used internally by `KiteService.placeOrder()` to route orders to the correct exchange.

| Parameter | Type | Description |
|-----------|------|-------------|
| `symbol` | `String` | Trading symbol (e.g., `"BANKEX26FEB68000CE"`) |

**Returns:** `String` -- `"NFO"` or `"BFO"` (never null)

### TickMapper

```java
// With depth (backward-compatible)
Ticker ticker = TickMapper.mapTick(tick, symbol);

// Without depth (saves ~15 object allocations per tick)
Ticker ticker = TickMapper.mapTick(tick, symbol, false);
```

Converts Zerodha Tick to internal Ticker format. Sets `tickReceivedTime` to the exact moment of mapping for latency analysis.

| Method | Description |
|--------|-------------|
| `mapTick(Tick, String)` | Map with depth included (backward-compatible) |
| `mapTick(Tick, String, boolean)` | Map with optional depth; `includeDepth=false` sets depth to null |

When `includeDepth=false`, the depth field is null. The `@JsonInclude(NON_NULL)` annotation on `Ticker` automatically omits null depth from JSON output. Depth mapping internally uses a loop-based implementation instead of Stream chains, eliminating ~10 intermediate object allocations even when depth IS included.

| Field | Source |
|-------|--------|
| `tickTimestamp` | From Kite `Tick.getTickTimestamp()` |
| `tickReceivedTime` | `new Date()` at mapping time |

### MarketDepthProvider

Interface that decouples depth access from the Ticker record. Enables depth-free tick pipelines while preserving order-time depth logging.

```java
public interface MarketDepthProvider {
    Map<String, List<Ticker.Depth>> getDepth(String symbol);
}
```

Production implementations cache the latest depth per symbol from raw tick data. Backtest implementations may read depth from tick files or return null.

### OrderDetailsLogger

| Method | Description |
|--------|-------------|
| `logMarketDepth(Ticker)` | Log buy/sell depth (deprecated, use provider-based overload) |
| `logMarketDepth(String, MarketDepthProvider)` | Log depth from cache-based provider |
| `logOrderLifeCycle(orders, order, orderId)` | Log order state transitions |
| `getActiveOrdersByOrderId(orders, orderId)` | Filter by Kite order ID |

---

## Error Handling

### Kite API Error Codes

| Code | Meaning | Action |
|------|---------|--------|
| 400 | Bad request | Check symbol, quantity, lot sizes |
| 403 | Forbidden | Re-authenticate |
| 429 | Rate limit | Exponential backoff |
| 500 | Server error | Retry |

### Handling Order Failures

```java
Optional<KiteOpenOrder> result = kiteService.buyOrder(symbol, qty, tag, true);
if (result.isEmpty() || !result.get().isOrderPlaced()) {
    KiteOpenOrder order = result.orElse(null);
    if (order != null && order.exceptionCode() != null) {
        // Handle API error
    }
}
```

---

## Thread Safety

| Component | Thread-Safe | Notes |
|-----------|-------------|-------|
| KiteService | ✅ | All API calls serialized via `ApiRateLimiter` (fair `ReentrantLock`) |
| InstrumentCache | ✅ | Double-checked locking, volatile fields (filteredInstruments, symbolMap, instrumentMap, exchangeMap) |
| KiteWebSocket | ✅ | CopyOnWriteArrayList for token lists, volatile isConnected |
| OrderUtils | ✅ | Static methods |
| InstrumentFileUtils | ✅ | Thread-safe IO |

### ApiRateLimiter (Internal)

Package-private class that serializes all Kite API calls through a fair `ReentrantLock` to prevent concurrent API access. Features:
- 12-second lock acquisition timeout (configurable via `lockTimeoutSeconds`)
- Wait time logging when lock contention exceeds `LOCK_WAIT_LOG_THRESHOLD_MS` (100ms)
- `executeWithLockChecked` variant propagates `IOException`/`KiteException`

---

## Best Practices

1. **Initialization:** Call `authenticate()` after creating service, check `isInitialised()`
2. **Order Placement:** Check `isOrderPlaced()` before accessing `order()` field
3. **WebSocket:** Duplicates auto-prevented, use `isSymbolSubscribed()` to check
4. **Rate Limits:** 3 req/sec, use WebSocket for real-time data
5. **Instrument Cache:** Refresh daily, use `InstrumentFileUtils` for persistence
6. **Security:** Use environment variables, rotate tokens daily (expire 3:30 AM IST)

---

## Common Pitfalls

- Forgetting to authenticate before trading
- Null order object (check `isOrderPlaced()` first)
- WebSocket init failure (catch `InitialisationException`)
- Token expiry (daily re-authentication required)
- Wrong lot sizes (NIFTY=50, BANKNIFTY=15)
- Orders outside market hours (9:15 AM - 3:30 PM IST)
- Rate limiting (>3 req/sec causes ban)
