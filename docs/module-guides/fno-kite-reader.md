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

## Architecture

KiteService is a **thin facade** that delegates to focused internal components:

| Component | Responsibility |
|-----------|---------------|
| `KiteSession` | Authentication, KiteConnect ownership, rate-limited API access |
| `KiteOrderExecutor` | Order placement and position/order queries |
| `HistoricalDataProvider` | Historical data retrieval with continuous contract resolution |
| `InstrumentCache` | Instrument lookup, option indexing, exchange resolution |
| `KiteWebSocket` | WebSocket subscriptions, tick/order listeners |

All components are package-private except `KiteService` (the public entry point). The constructor wires everything internally:

```java
public KiteService(apiSecret, apiKey, userId, nifty100Symbols, placeOrders, connectToWebSocket) {
    session = new KiteSession(apiKey, userId, apiSecret, placeOrders);
    instrumentCache = new InstrumentCache(nifty100Symbols, session);
    dataProvider = new HistoricalDataProvider(session, instrumentCache);
    orderExecutor = new KiteOrderExecutor(session, instrumentCache);
    kiteWebSocket = new KiteWebSocket(connectToWebSocket, instrumentCache);
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
| `placeOptionOrder(OrderParams)` | `OrderResponse` | Place order with explicit params (returns null on failure) |
| `cancelOrder(orderId, variety)` | `Order` | Cancel an order (returns null on failure) |
| `getOrders()` | `List<Order>` | All orders for day |
| `getPositions()` | `Map<String, List<Position>>` | Net and day positions |

### Historical Data

| Method | Returns | Description |
|--------|---------|-------------|
| `getHistoricalData(from, to, symbol, interval, continuous)` | `Optional<HistoricalData>` | Historical candles |
| `getEntireDayHistoricalData(from, to, symbol, interval)` | `Optional<HistoricalData>` | Intraday candles |

**Note:** Both methods return `Optional<HistoricalData>`. Returns `Optional.empty()` when KiteService is not initialized, instrument token is not found, or API call fails.

**Continuous mode (`continuous=true`):** When an expired futures symbol (e.g., `NIFTY25AUGFUT`) is not found in the instrument cache, `HistoricalDataProvider` delegates to `InstrumentCache.resolveNearestFutureToken()` which matches the base name against known FUT instruments (longest match wins) and returns the earliest-expiry contract token.

### Option Symbol Resolution

| Method | Returns | Description |
|--------|---------|-------------|
| `getITMStock(index, price, isCall)` | `String` | **Nearest ITM** symbol — last strike below spot (CE) / first above (PE). Takes no offset |
| `getOTMStock(index, price, isCall)` | `String` | **Nearest OTM** symbol — first strike above spot (CE) / last below (PE). Takes no offset |
| `getOptionStock(index, price, isCall, policy)` | `String` | ⚠️ **Does NOT honour `StrikePolicy`** — see the note below |
| `OptionPriceUtils.getStrikeByPolicy(index, price, isCall, policy, instruments)` | `String` | Offset-aware resolution implementing the enum's documented formula. **Not yet wired into order placement** (ADR 0062 shadow) |
| `appendIndexITMOptions()` | `void` | Add ITM options for default indices |
| `appendAllOptionsForIndex(String)` | `void` | Subscribe to ALL options for index (100+ symbols) |

> ⚠️ **`getOptionStock` does not implement `StrikePolicy` (ADR 0062).** It dispatches all five enum
> values onto the two offset-less helpers above:
> `ITM_1, ITM_2 → getITMStock` · `OTM_1, OTM_2 → getOTMStock` · `ATM → getITMStock`.
>
> Consequences: **`ATM` resolves one strike in-the-money**, and **`ITM_2` / `OTM_2` are
> unreachable** — silently, with no warning. Measured 2026-07-24: prod strike moneyness sat in
> (0, 1.03] strike-intervals for every order regardless of policy.
>
> `getStrikeByPolicy` implements the contract correctly (`ATM = round(price/interval)`, then
> `offset` intervals toward the money, interval inferred from the live instrument ladder).
> `getOptionStock` currently computes it, logs `STRIKE_POLICY_SHADOW` when the two disagree, and
> **still returns the legacy symbol** — switching changes the strike of every production option
> order and needs a soak first. Callers wanting correct policy semantics today must call
> `getStrikeByPolicy` directly.

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
| `isExpiryDayForOption(symbol, date)` | `boolean` | Check if option expires on given date |
| `isExpiryDayForIndex(indexName, date)` | `boolean` | Check if any options for index expire on given date |
| `getInstrumentCacheSize()` | `int` | Cache size (diagnostics) |
| `getFilteredInstruments()` | `List<InstrumentSummary>` | All filtered instruments (exchange, symbol, expiry) |

---

## Models

### InstrumentSummary (Record)

```java
public record InstrumentSummary(String exchange, String symbol, String expiry)
```

Replaces the previous `Map<String, String>` representation for filtered instruments. Returned by `KiteService.getFilteredInstruments()`.

### KiteOpenOrder (Record)

```java
public record KiteOpenOrder(String orderId, boolean isOrderPlaced, Integer exceptionCode, String exceptionMessage)
```

| Scenario | isOrderPlaced | orderId | exceptionCode |
|----------|---------------|---------|---------------|
| Not initialized | `false` | `null` | `null` |
| Paper trading | `true` | `null` | `null` |
| Order placed | `true` | Order ID string | `null` |
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

**Returns:** `OrderParams` -- pre-configured with MARKET order type, MIS product, DAY validity, `marketProtection = -1` (Zerodha auto-applies default protection slabs).

```java
// KiteService resolves exchange automatically via InstrumentCache
// Direct usage (rare -- KiteService.buyOrder/sellOrder is preferred):
String exchange = instrumentCache.getExchangeForSymbol(symbol);
OrderParams params = OrderUtils.createMarketOrderWithParameters(symbol, qty, Constants.TRANSACTION_TYPE_BUY, tag, exchange);
```

### InstrumentFileUtils

Thread-safe instrument cache persistence. Uses VT-safe `ObjectMapper` via `JsonUtils.createObjectMapper()`. Returns `List.of()` (never null) on load failure.

| Method | Description |
|--------|-------------|
| `saveInstrumentCache(List<Instrument>)` | Save to `instrument_cache/instruments_<date>.json` |
| `saveFilteredInstrumentCache(Object)` | Save pretty-printed filtered instruments |
| `loadInstrumentCache(int)` | Load from N days ago (0=today), returns `List.of()` if file missing |

### InstrumentCache

Internal (package-private) thread-safe cache with double-checked locking via `ensureInitialized()`. Takes `KiteSession` (not `KiteService`) for API access. Returns defensive copies and unmodifiable collections. Constructor accepts `List<String>` but stores as `Set<String>` internally for O(1) `contains()` checks in `isInTheTrackingList()`. Lazy initialization is thread-safe: only one thread calls `initializeInstruments()`; others wait on `initLock` (ReentrantLock).

**Internal state (single volatile `CacheData` record):**

All cached data is held in an immutable `CacheData` record, assigned atomically via a single volatile reference. This replaces the former pattern of separate volatile fields (`symbolMap`, `exchangeMap`, `instrumentMap`).

- `filteredInstruments` -- `List<Instrument>` of NSE/NFO/BSE/BFO instruments in the tracking list
- `symbolInfoMap` -- `Map<String, SymbolInfo>` tradingSymbol (uppercased) to `SymbolInfo(long token, String exchange)` -- merges the former `symbolMap` and `exchangeMap` into a single lookup
- `tokenToSymbolMap` -- `Map<Long, String>` reverse lookup (token to symbol)

The `nifty100Symbols` field is now `Set<String>` (stored as `HashSet`) for O(1) `contains()` in `isInTheTrackingList()`.

Option data is computed on-the-fly via `getEarliestExpiryInstruments()` / `resolveNearestFutureToken()` from `filteredInstruments` (O(n) scans, not on hot paths).

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
| `isExpiryDayForIndex(String, Date)` | `boolean` | Check if any CE/PE options for index expire on given date |
| `getLotSizeFromFuture(String)` | `Optional<Integer>` | Lot size for index via FUT contract |
| `getAllFutureLotSizeInfo()` | `Map<String, Integer>` | All index lot sizes |
| `getInstrumentMapSize()` | `int` | Cache size (diagnostics) |
| `getAllInstruments()` | `List<InstrumentSummary>` | All instruments as InstrumentSummary records |
| `getEarliestExpiryInstruments(name, type)` | `Optional<List<Instrument>>` | Instruments for nearest expiry (O(n) scan, not on hot path) |
| `resolveNearestFutureToken(String)` | `Optional<Long>` | Resolve expired FUT symbol to nearest active contract token (longest base-name match, earliest expiry) |

#### getExchangeForSymbol

```java
public String getExchangeForSymbol(String symbol)
```

Resolves the exchange (`"NFO"` or `"BFO"`) for a given trading symbol by looking up the `symbolInfoMap` built during initialization. Falls back to `Exchange.NFO.getCode()` with a warning log if the symbol is not found or is null. Used internally by `KiteService.placeOrder()` to route orders to the correct exchange.

| Parameter | Type | Description |
|-----------|------|-------------|
| `symbol` | `String` | Trading symbol (e.g., `"BANKEX26FEB68000CE"`) |

**Returns:** `String` -- `"NFO"` or `"BFO"` (never null)

#### isExpiryDayForIndex

```java
public boolean isExpiryDayForIndex(String indexName, Date currentDate)
```

Checks if any CE/PE option contracts for the given index expire on the specified date. Uses `INDEX_TO_DERIVATIVE` mapping to resolve index names (e.g., `"NIFTY 50"` to `"NIFTY"`) before filtering. Adapts automatically to exchange schedule changes since it queries live instrument data.

| Parameter | Type | Description |
|-----------|------|-------------|
| `indexName` | `String` | Index name (e.g., `"NIFTY 50"`, `"SENSEX"`) |
| `currentDate` | `Date` | Date to check for expiry |

**Returns:** `boolean` -- `true` if any options expire on the given date; `false` if no match or either argument is null

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

**Note:** `MarketDepthProvider` and `OrderDetailsLogger` were previously in this module but have been removed. Order detail logging and market depth access are now handled in the consuming application layer.

---

## Internal Components

These are package-private classes, not directly accessible outside `com.vish.fno.reader.core`.

### KiteSession

Manages authentication, KiteConnect SDK instance, and rate-limited API execution. Centralizes Kite API exception handling via `executeWithLockSafe()` -- callers provide a `CheckedSupplier` and a fallback value; KiteException, IOException, and JSONException are caught, logged with error context, and the fallback is returned.

| Method | Returns | Description |
|--------|---------|-------------|
| `authenticate(String, Runnable)` | `void` | Generate session, run post-auth hook |
| `isInitialised()` | `boolean` | Whether session is authenticated (`volatile`) |
| `isPlaceOrders()` | `boolean` | Whether live orders are enabled |
| `executeWithLock(Supplier<T>, String)` | `T` | Execute under rate limiter lock |
| `executeWithLockVoid(Runnable, String)` | `void` | Void variant of executeWithLock |
| `executeWithLockChecked(CheckedSupplier<T>, String)` | `T` | Checked variant (throws IOException, KiteException) |
| `executeWithLockSafe(CheckedSupplier<T>, String, T)` | `T` | Safe variant: catches KiteException/IOException/JSONException, logs error, returns fallback value |
| `getKiteSdk()` | `KiteConnect` | Raw SDK access (package-private) |

### KiteOrderExecutor

Handles order placement, cancellation, and position/order queries. Uses `@RequiredArgsConstructor` with `KiteSession` and `InstrumentCache`. Exception handling centralized in `KiteSession.executeWithLockSafe()` -- `placeOptionOrder()`, `cancelOrder()`, `getOrders()`, and `getPositions()` use safe execution with fallback values (null, `List.of()`, `Map.of()`).

| Method | Returns | Description |
|--------|---------|-------------|
| `buyOrder(symbol, qty, tag, isPlace)` | `Optional<KiteOpenOrder>` | Place buy order |
| `sellOrder(symbol, qty, tag, isPlace)` | `Optional<KiteOpenOrder>` | Place sell order |
| `placeOptionOrder(OrderParams)` | `OrderResponse` | Place order with explicit params (returns null on failure) |
| `cancelOrder(orderId, variety)` | `Order` | Cancel an order via Kite SDK (returns null on failure) |
| `getOrders()` | `List<Order>` | All orders for day (returns `List.of()` on failure) |
| `getPositions()` | `Map<String, List<Position>>` | Net and day positions (returns `Map.of()` on failure) |
| `logExistingOrdersAndPositions(symbol, tag)` | `void` | Debug logging for existing orders |

### HistoricalDataProvider

Retrieves historical data with continuous contract resolution. Uses `@RequiredArgsConstructor` with `KiteSession` and `InstrumentCache`. Uses `executeWithLockChecked` for API calls -- propagates exceptions to caller for retry decisions.

| Method | Returns | Description |
|--------|---------|-------------|
| `getHistoricalData(from, to, symbol, interval)` | `Optional<HistoricalData>` | Intraday candles (delegates to 5-arg variant with `continuous=false`) |
| `getHistoricalData(from, to, symbol, interval, continuous)` | `Optional<HistoricalData>` | Historical candles with optional continuous contract resolution |

**Continuous contract resolution:** When `continuous=true` and a symbol is not found (e.g., expired `NIFTY24AUGFUT`), delegates to `InstrumentCache.resolveNearestFutureToken()` which matches the base name against known FUT instruments and returns the earliest-expiry contract token.

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
    KiteOpenOrder openOrder = result.orElse(null);
    if (openOrder != null && openOrder.exceptionCode() != null) {
        // Handle API error using openOrder.exceptionCode() and openOrder.exceptionMessage()
    }
}
// On success: result.get().orderId() returns the Kite order ID string
```

---

## Thread Safety

| Component | Thread-Safe | Notes |
|-----------|-------------|-------|
| KiteService | ✅ | Thin facade, delegates to thread-safe components |
| KiteSession | ✅ | `volatile initialised` flag; API calls serialized via `ApiRateLimiter` (fair `ReentrantLock`) |
| KiteOrderExecutor | ✅ | All operations go through KiteSession's lock |
| HistoricalDataProvider | ✅ | All operations go through KiteSession's lock |
| InstrumentCache | ✅ | Double-checked locking with `ReentrantLock` (VT-safe), single volatile `CacheData` record (immutable, atomic assignment), `Set<String>` for nifty100Symbols |
| KiteWebSocket | ✅ | `ReentrantLock` (VT-safe) for all token mutations; `volatile` on `tickerProvider`, `isConnected`, `onTickerArrivalListener`, `onOrderUpdateListener` |
| OrderUtils | ✅ | Static methods |
| InstrumentFileUtils | ✅ | Thread-safe IO, `DateTimeFormatter` (immutable) |

### ApiRateLimiter (Internal)

Package-private class that serializes all Kite API calls through a fair `ReentrantLock` to prevent concurrent API access. Owned by `KiteSession`. Features:
- 12-second lock acquisition timeout (`lockTimeoutSeconds` -- `static volatile` for test hook, cross-thread visibility)
- Wait time logging when lock contention exceeds `LOCK_WAIT_LOG_THRESHOLD_MS` (100ms)
- `executeWithLockChecked` variant propagates `IOException`/`KiteException`
- `executeWithLockVoid` variant for void operations
- `CheckedSupplier<T>` functional interface for operations that throw `IOException`/`KiteException`

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
