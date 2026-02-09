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
| `buyOrder(symbol, qty, tag, isPlace)` | `Optional<KiteOpenOrder>` | Place buy order |
| `sellOrder(symbol, qty, tag, isPlace)` | `Optional<KiteOpenOrder>` | Place sell order |
| `getOrders()` | `List<Order>` | All orders for day |
| `getPositions()` | `Map<String, List<Position>>` | Net and day positions |

### Historical Data

| Method | Returns | Description |
|--------|---------|-------------|
| `getHistoricalData(from, to, symbol, interval, continuous)` | `Optional<HistoricalData>` | Historical candles |
| `getEntireDayHistoricalData(from, to, symbol, interval)` | `Optional<HistoricalData>` | Intraday candles |

**Note:** Both methods now return `Optional<HistoricalData>` instead of nullable `HistoricalData`. Returns `Optional.empty()` when KiteService is not initialized, instrument token is not found, or API call fails.

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
OrderParams params = OrderUtils.createMarketOrderWithParameters(symbol, qty, Constants.TRANSACTION_TYPE_BUY, tag);
```

Pre-configured: MARKET order, MIS product, NFO exchange, DAY validity.

### InstrumentFileUtils

Thread-safe instrument cache persistence.

| Method | Description |
|--------|-------------|
| `saveInstrumentCache(List<Instrument>)` | Save to `instrument_cache/instruments_<date>.json` |
| `loadInstrumentCache(int)` | Load from N days ago (0=today) |

### InstrumentCache

Internal thread-safe cache with double-checked locking. Returns defensive copies and unmodifiable collections.

### TickMapper

```java
Ticker ticker = TickMapper.mapTick(tick, symbol);
```

Converts Zerodha Tick to internal Ticker format. Sets `tickReceivedTime` to the exact moment of mapping for latency analysis.

| Field | Source |
|-------|--------|
| `tickTimestamp` | From Kite `Tick.getTickTimestamp()` |
| `tickReceivedTime` | `new Date()` at mapping time |

### OrderDetailsLogger

| Method | Description |
|--------|-------------|
| `logMarketDepth(Ticker)` | Log buy/sell depth |
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
| InstrumentCache | ✅ | Double-checked locking |
| KiteWebSocket | ✅ | CopyOnWriteArrayList for token lists, volatile isConnected |
| OrderUtils | ✅ | Static methods |
| InstrumentFileUtils | ✅ | Thread-safe IO |

### ApiRateLimiter (Internal)

Package-private class that serializes all Kite API calls through a fair `ReentrantLock` to prevent concurrent API access. Features:
- 12-second lock acquisition timeout (configurable via `lockTimeoutSeconds`)
- Wait time logging when lock contention exceeds 100ms
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
