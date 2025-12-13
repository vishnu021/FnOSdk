# fno-kite-reader - Kite Connect Integration

## Purpose
Integration with Zerodha's Kite Connect API for live market data, historical data retrieval, and order execution.

## Maven Dependency
```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-kite-reader</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Automatically includes `fno-utils` (and transitively `fno-models`).

## Prerequisites

1. **Zerodha Account**: Active trading account
2. **Kite Connect App**: Create app at https://developers.kite.trade/
3. **API Credentials**: API Key, API Secret, User ID, Request Token (from Kite login flow)

## Setup & Configuration

### Spring Boot Configuration

**application.properties:**
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
import com.vish.fno.reader.service.KiteService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class KiteConfiguration {
    @Value("${kite.api-key}")
    private String apiKey;

    @Value("${kite.api-secret}")
    private String apiSecret;

    @Value("${kite.user-id}")
    private String userId;

    @Value("${kite.place-orders:false}")
    private boolean placeOrders;

    @Value("${kite.connect-websocket:true}")
    private boolean connectToWebSocket;

    @Value("${kite.nifty100.symbols}")
    private String nifty100SymbolsStr;

    @Bean
    public KiteService kiteService() {
        List<String> nifty100Symbols = Arrays.asList(nifty100SymbolsStr.split(","));

        return new KiteService(
            apiSecret,           // API Secret for authentication
            apiKey,              // API Key
            userId,              // Zerodha User ID
            nifty100Symbols,     // List of symbols to track
            placeOrders,         // Enable/disable actual order placement
            connectToWebSocket   // Enable/disable WebSocket connection
        );
    }
}
```

**Constructor Signature:**
```java
public KiteService(
    String apiSecret,
    String apiKey,
    String userId,
    List<String> nifty100Symbols,
    boolean placeOrders,
    boolean connectToWebSocket
)
```

### Authentication Flow

```java
import com.vish.fno.reader.service.KiteService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class KiteAuthenticator {
    private final KiteService kiteService;

    public KiteAuthenticator(KiteService kiteService) {
        this.kiteService = kiteService;
    }

    @Bean
    public ApplicationRunner authenticateKite() {
        return args -> {
            String requestToken = "YOUR_REQUEST_TOKEN_FROM_LOGIN";
            kiteService.authenticate(requestToken);

            if (kiteService.isInitialised()) {
                log.info("KiteService authenticated successfully");
                kiteService.appendIndexITMOptions(); // Optional: Add ITM options
            }
        };
    }
}
```

## Core Services

### KiteService - Primary API

Primary service for order execution, market data, and historical data retrieval.

#### Public Methods

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `authenticate(String)` | requestToken | `void` | Generates session, sets access token, initializes WebSocket |
| `isInitialised()` | - | `boolean` | Returns true if service is ready for trading |
| `buyOrder(...)` | symbol, orderSize, tag, isPlaceOrder | `Optional<KiteOpenOrder>` | Places buy order (or dry run) |
| `sellOrder(...)` | symbol, orderSize, tag, isPlaceOrder | `Optional<KiteOpenOrder>` | Places sell order (or dry run) |
| `getOrders()` | - | `List<Order>` | Retrieves all orders for the day |
| `getPositions()` | - | `Map<String, List<Position>>` | Retrieves net and day positions |
| `getHistoricalData(...)` | from, to, symbol, interval, continuous | `HistoricalData` | Fetches historical candles |
| `getEntireDayHistoricalData(...)` | fromDate, toDate, symbol, interval | `HistoricalData` | Fetches intraday candles for today |
| `getITMStock(...)` | indexSymbol, price, isCall | `String` | Returns ITM option symbol |
| `getOTMStock(...)` | indexSymbol, price, isCall | `String` | Returns OTM option symbol |
| `appendIndexITMOptions()` | - | `void` | Adds ITM options for default indices to WebSocket |
| `appendWebSocketSymbolsList(...)` | symbols, addFutures | `void` | Subscribes to additional symbols via WebSocket |
| `setOnTickerArrivalListener(...)` | onTickerArrivalListener | `void` | Sets tick data listener |
| `setOnOrderUpdateListener(...)` | onOrderUpdateListener | `void` | Sets order update listener |
| `getInstruments()` | - | `List<Instrument>` | Returns filtered instrument list |
| `getFilteredInstruments()` | - | `List<Map<String, String>>` | Returns instruments as maps |
| `getInstrument(String)` | symbol | `Long` | Returns instrument token for symbol |
| `getSymbol(long)` | token | `String` | Returns symbol for instrument token |
| `isExpiryDayForOption(...)` | optionSymbol, date | `boolean` | Checks if option expires on given date |

#### Complete Trading Application Example

```java
import com.vish.fno.reader.service.KiteService;
import com.vish.fno.reader.model.KiteOpenOrder;
import com.vish.fno.reader.exception.InitialisationException;
import com.vish.fno.util.strategy.TargetAndStopLossStrategy;
import com.zerodhatech.models.Position;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

@Slf4j
@Configuration
public class TradingApplication {
    @Value("${kite.api-key}")
    private String apiKey;

    @Value("${kite.api-secret}")
    private String apiSecret;

    @Value("${kite.user-id}")
    private String userId;

    @Value("${kite.request-token}")
    private String requestToken;

    @Bean
    public KiteService kiteService() {
        List<String> symbols = Arrays.asList(
            "NIFTY 50", "NIFTY BANK", "TCS", "INFY"
        );

        try {
            return new KiteService(
                apiSecret, apiKey, userId, symbols,
                false,  // Paper trading mode
                true    // Enable WebSocket
            );
        } catch (InitialisationException e) {
            log.error("WebSocket initialization failed, retrying without WebSocket", e);
            return new KiteService(apiSecret, apiKey, userId, symbols, false, false);
        }
    }

    @Bean
    public ApplicationRunner startTrading(KiteService kiteService, TradingService tradingService) {
        return args -> {
            kiteService.authenticate(requestToken);

            if (!kiteService.isInitialised()) {
                log.error("Authentication failed");
                return;
            }

            // Setup WebSocket listeners
            kiteService.setOnTickerArrivalListener(ticks -> {
                ticks.forEach(tradingService::processTick);
            });

            // Append option symbols
            kiteService.appendIndexITMOptions();

            // Start strategy
            tradingService.executeStrategy();
        };
    }
}

@Slf4j
@Service
class TradingService {
    private final KiteService kiteService;
    private final TargetAndStopLossStrategy tslStrategy;

    public TradingService(KiteService kiteService) {
        this.kiteService = kiteService;
        this.tslStrategy = new TargetAndStopLossStrategy(2.0, 1.0); // 2% target, 1% SL
    }

    public void executeStrategy() {
        // Place buy order
        kiteService.buyOrder("NIFTY24DECFUT", 50, "MOMENTUM_STRATEGY", true)
            .ifPresent(order -> {
                if (order.isOrderPlaced() && order.order() != null) {
                    log.info("Buy order placed: {}", order.order().orderId);
                } else if (!order.isOrderPlaced()) {
                    log.error("Order failed: {} - {}",
                        order.exceptionCode(), order.exceptionMessage());
                }
            });
    }

    public void processTick(com.zerodhatech.models.Tick tick) {
        log.info("Tick - Token: {}, LTP: {}",
            tick.getInstrumentToken(), tick.getLastTradedPrice());
    }

    public void monitorPositions() {
        Map<String, List<Position>> positions = kiteService.getPositions();

        if (positions.isEmpty()) {
            return;
        }

        List<Position> netPositions = positions.get("net");

        for (Position pos : netPositions) {
            if (pos.quantity == 0) continue;

            double entryPrice = pos.averagePrice;
            double currentPrice = pos.lastPrice;
            boolean isLong = pos.quantity > 0;

            double target = tslStrategy.calculateTarget(entryPrice, isLong ? "BUY" : "SELL");
            double stopLoss = tslStrategy.calculateStopLoss(entryPrice, isLong ? "BUY" : "SELL");

            boolean targetHit = isLong ? (currentPrice >= target) : (currentPrice <= target);
            boolean stopLossHit = isLong ? (currentPrice <= stopLoss) : (currentPrice >= stopLoss);

            if (targetHit) {
                log.info("Target hit for {} - PnL: {}", pos.tradingSymbol, pos.pnl);
                exitPosition(pos, "TARGET_HIT");
            } else if (stopLossHit) {
                log.warn("Stop-loss hit for {} - PnL: {}", pos.tradingSymbol, pos.pnl);
                exitPosition(pos, "STOP_LOSS");
            }
        }
    }

    private void exitPosition(Position pos, String reason) {
        String exitTransactionType = pos.quantity > 0 ? "SELL" : "BUY";
        int exitQuantity = Math.abs(pos.quantity);

        if (exitTransactionType.equals("SELL")) {
            kiteService.sellOrder(pos.tradingSymbol, exitQuantity, reason, true);
        } else {
            kiteService.buyOrder(pos.tradingSymbol, exitQuantity, reason, true);
        }
    }
}
```

## Models

### KiteOpenOrder - Order Response Record

```java
package com.vish.fno.reader.model;

import com.zerodhatech.models.Order;

public record KiteOpenOrder(
    Order order,              // Kite Order object (null if not placed or error)
    boolean isOrderPlaced,    // true if order placement succeeded
    Integer exceptionCode,    // Kite API error code (null if no error)
    String exceptionMessage   // Kite API error message (null if no error)
) {}
```

**Response Scenarios:**

| Scenario | isOrderPlaced | order | exceptionCode | exceptionMessage |
|----------|---------------|-------|---------------|------------------|
| Service not initialized | `false` | `null` | `null` | `null` |
| Test/dry run mode | `true` | `null` | `null` | `null` |
| Order placed successfully | `true` | `Order` object | `null` | `null` |
| Kite API error | `false` | `null` | Error code | Error message |

## Exceptions

### InitialisationException - WebSocket Initialization Failure

```java
package com.vish.fno.reader.exception;

public class InitialisationException extends RuntimeException {
    public InitialisationException(String message, Throwable cause)
    public InitialisationException(Throwable cause)
}
```

**When Thrown:**
- During `KiteWebSocket.initialize()` when setting retry configuration fails
- Wraps underlying `KiteException` from Kite Connect SDK

**How to Handle:**
```java
try {
    KiteService service = new KiteService(..., true); // WebSocket enabled
} catch (InitialisationException e) {
    log.error("WebSocket failed", e);
    // Fallback: Create without WebSocket
    KiteService service = new KiteService(..., false);
}
```

## Utilities

### OrderUtils - Order Parameter Builder

```java
public static OrderParams createMarketOrderWithParameters(
    String symbol,
    int orderSize,
    String transactionType,
    String tag
)
```

**Pre-configured Values:**
- `orderType`: MARKET
- `product`: MIS (intraday)
- `exchange`: NFO
- `validity`: DAY
- `triggerPrice`: 0.0

**Example:**
```java
import com.vish.fno.reader.util.OrderUtils;
import com.zerodhatech.kiteconnect.utils.Constants;

OrderParams params = OrderUtils.createMarketOrderWithParameters(
    "NIFTY24DECFUT",
    50,
    Constants.TRANSACTION_TYPE_BUY,
    "ALGO_STRATEGY_V1"
);
```

---

### InstrumentFileUtils - Instrument Cache Persistence

**Thread-Safety:** All methods are thread-safe. Uses `DateTimeFormatter` instead of `SimpleDateFormat` and platform-independent `Path` API.

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `saveInstrumentCache(List<Instrument>)` | `instruments` | `void` | Saves to `instrument_cache/instruments_<date>.json` (thread-safe) |
| `saveFilteredInstrumentCache(Object)` | `instruments` | `void` | Saves with pretty-print to `filtered_instruments_<date>.json` (thread-safe) |
| `loadInstrumentCache(int)` | `days` | `List<Instrument>` | Loads from N days ago (0=today, 1=yesterday), returns null if not found |
| `getNDaysBefore(long)` | `n` | `Date` | Date N days before today |

**Example:**
```java
import com.vish.fno.reader.util.InstrumentFileUtils;
import com.zerodhatech.models.Instrument;

// Load from cache or fetch from API
List<Instrument> instruments = InstrumentFileUtils.loadInstrumentCache(0);
if (instruments == null) {
    log.warn("Cache miss - fetching from Kite API");
    instruments = kiteService.getInstruments();
    InstrumentFileUtils.saveInstrumentCache(instruments);
}
```

---

### InstrumentCache - Thread-Safe Lazy Initialization

Internal cache class used by `KiteService` for managing instrument data. Thread-safe with double-checked locking pattern.

**Package:** `com.vish.fno.reader.service` (package-private)

```java
class InstrumentCache
```

**Constructor:**
```java
public InstrumentCache(List<String> nifty100Symbols, KiteService kiteService)
```

**Thread-Safety Implementation:**
- Double-checked locking for lazy initialization
- `volatile` fields ensure safe publication across threads
- Returns defensive copies from `get()` methods
- Returns unmodifiable collections from `keySet()`
- Read-heavy workload optimized with minimal synchronization

**Key Methods:**

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `getInstruments()` | - | `List<Instrument>` | Returns unmodifiable list (thread-safe lazy init) |
| `getInstrument(String)` | `script` | `Long` | Returns instrument token for symbol |
| `getSymbol(long)` | `instrument` | `String` | Returns symbol for instrument token |
| `getAllSymbols()` | - | `Set<String>` | Returns all symbol names |
| `getFilteredSymbols()` | - | `Map<String, String>` | Returns symbol to name mapping |
| `getInstrumentForSymbol(String)` | `symbol` | `List<Instrument>` | Returns all instruments for symbol |
| `isExpiryDayForOption(String, Date)` | `optionSymbol`, `currentDate` | `boolean` | Checks if option expires today |

**Initialization Behavior:**
- First call to `getInstruments()` fetches from Kite API (network I/O)
- Subsequent calls return cached data (no locking)
- Filters instruments for configured Nifty 100 symbols
- Saves to disk via `InstrumentFileUtils`

**Example (Internal Usage):**
```java
// Used internally by KiteService
InstrumentCache cache = new InstrumentCache(nifty100Symbols, kiteService);

// Thread-safe access (multiple threads can call simultaneously)
List<Instrument> instruments = cache.getInstruments(); // Unmodifiable list
Long token = cache.getInstrument("NIFTY 50");
String symbol = cache.getSymbol(256265L);
```

**Concurrency Notes:**
- Multiple threads can safely call `getInstruments()` concurrently
- Only one thread will perform initialization (synchronized block)
- Other threads wait for initialization to complete
- After initialization, no locking overhead

---

### TickMapper - Kite Tick to Ticker Conversion

Utility for converting Zerodha Kite Tick objects to internal `Ticker` format. Used for backtesting and order management without direct `OrderManager` dependency.

```java
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TickMapper
```

**Method:**
```java
public static Ticker mapTick(Tick tick, String tickSymbol)
```

**Parameters:**
- `tick`: Zerodha Kite Tick object from WebSocket or API
- `tickSymbol`: Trading symbol for the tick

**Returns:** Internal `Ticker` record with complete market data

**Usage Example:**
```java
import com.vish.fno.reader.util.TickMapper;
import com.vish.fno.model.Ticker;
import com.zerodhatech.models.Tick;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TickConversionExample {
    public void processWebSocketTicks(ArrayList<Tick> ticks) {
        for (Tick tick : ticks) {
            String symbol = getSymbolForToken(tick.getInstrumentToken());
            Ticker ticker = TickMapper.mapTick(tick, symbol);

            log.info("Mapped ticker: symbol={}, LTP={}, OI={}",
                ticker.instrumentSymbol(), ticker.lastTradedPrice(), ticker.oi());
        }
    }
}
```

**Edge Cases:**
- Handles null market depth gracefully (returns empty map)
- Null-safe field extraction (uses Optional with defaults)
- Market depth entries preserve buy/sell segregation

---

### OrderDetailsLogger - Order Lifecycle Logging

Logging utility for order execution lifecycle and market depth analysis.

```java
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OrderDetailsLogger
```

**Methods:**

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `logMarketDepth(Ticker)` | `tick` | `void` | Logs buy/sell market depth from ticker |
| `logOrderLifeCycle(List<ActiveOrder>, Order, String)` | `activeOrders`, `order`, `orderId` | `void` | Logs order state transitions (BUY/SELL, PLACED/COMPLETE) |
| `getActiveOrdersByOrderId(List<ActiveOrder>, String)` | `activeOrders`, `orderId` | `List<ActiveOrder>` | Filters active orders by Kite order ID from extraData |

**Usage Example:**
```java
import com.vish.fno.reader.util.OrderDetailsLogger;
import com.vish.fno.model.Ticker;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.zerodhatech.models.Order;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrderMonitoringExample {
    public void monitorOrder(Ticker tick, List<ActiveOrder> activeOrders, Order kiteOrder, String orderId) {
        // Log market depth for decision making
        OrderDetailsLogger.logMarketDepth(tick);

        // Log order lifecycle events
        OrderDetailsLogger.logOrderLifeCycle(activeOrders, kiteOrder, orderId);

        // Retrieve specific orders by Kite order ID
        List<ActiveOrder> matchedOrders = OrderDetailsLogger.getActiveOrdersByOrderId(activeOrders, orderId);
        log.info("Found {} active orders for Kite order ID: {}", matchedOrders.size(), orderId);
    }
}
```

**Edge Cases:**
- `logMarketDepth()` handles null depth map gracefully (no-op)
- Logs "next buy price" and "next sell price" from depth[0] if available
- Order matching uses `kiteOrderId` key in ActiveOrder's `extraData` map
- Non-null assertion on return value (empty list if no matches)

---

## Error Handling

### Kite API Error Codes

| Code | Meaning | Action |
|------|---------|--------|
| 400 | Bad request | Check symbol format, quantity, lot sizes |
| 403 | Forbidden | Re-authenticate with fresh request token |
| 429 | Rate limit exceeded | Implement exponential backoff |
| 500 | Kite server error | Retry after delay |

**Handling KiteOpenOrder Failures:**

```java
Optional<KiteOpenOrder> result = kiteService.buyOrder(symbol, quantity, tag, true);

if (result.isEmpty()) {
    log.error("No response from order placement");
    return;
}

KiteOpenOrder order = result.get();

if (!order.isOrderPlaced()) {
    Integer errorCode = order.exceptionCode();

    if (errorCode == null) {
        log.error("Service not initialized - call authenticate() first");
    } else {
        switch (errorCode) {
            case 400 -> log.error("Bad request: {}", order.exceptionMessage());
            case 403 -> log.error("Forbidden - invalid API credentials");
            case 429 -> log.error("Rate limit exceeded - implement backoff");
            case 500 -> log.error("Kite server error: {}", order.exceptionMessage());
            default -> log.error("Kite API error {}: {}", errorCode, order.exceptionMessage());
        }
    }
    return;
}

if (order.order() == null) {
    log.info("Test order successful (paper trading mode)");
} else {
    log.info("Order placed successfully: {}", order.order().orderId);
}
```

## Best Practices

1. **Service Initialization**:
   - Always call `authenticate()` after creating `KiteService`
   - Check `isInitialised()` before placing orders
   - Handle `InitialisationException` with fallback strategy

2. **Order Placement**:
   - Always check `KiteOpenOrder.isOrderPlaced()` before accessing `order()` field
   - Handle both Kite API errors (with error codes) and generic failures
   - Use paper trading mode (`placeOrders=false`) for testing

3. **Rate Limiting**:
   - Respect Kite API rate limits (3 requests/second)
   - Use WebSocket for real-time data instead of polling
   - Implement exponential backoff for failed requests

4. **Instrument Cache**:
   - Use `InstrumentFileUtils` to cache instrument data locally
   - Refresh cache daily (instruments change daily)
   - Load from cache at startup to avoid rate limits

5. **Security**:
   - Never commit API keys/secrets to version control
   - Use environment variables or secure vaults
   - Rotate access tokens daily (they expire at 3:30 AM IST)

## Thread Safety

- **KiteService**: Not thread-safe, use synchronization or separate instances per thread
- **InstrumentCache**: Thread-safe with double-checked locking for lazy initialization, returns defensive copies and unmodifiable collections
- **KiteWebSocket**: Single instance only, callbacks execute on ticker thread
- **OrderUtils**: Thread-safe (static methods, no shared state)
- **InstrumentFileUtils**: Thread-safe for all operations (uses DateTimeFormatter and Path API)

## Common Pitfalls

1. **Forgetting to authenticate**: Always call `authenticate()` after creating service
2. **Null order object**: Check `isOrderPlaced()` before accessing `order()` field
3. **WebSocket initialization**: Catch `InitialisationException` during service creation
4. **Token expiry**: Access tokens expire daily - implement re-authentication
5. **Lot sizes**: NIFTY=50, BANKNIFTY=15 - wrong quantities cause order rejection
6. **Market hours**: Orders outside 9:15 AM - 3:30 PM IST are rejected
7. **Rate limiting**: Exceeding 3 req/sec causes temporary ban
