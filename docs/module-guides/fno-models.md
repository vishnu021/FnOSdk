# fno-models - Core Data Models

Foundation module providing core POJOs and interfaces for F&O trading operations.

## Maven Dependency
```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-models</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

## Order Request Package

### OrderRequest Interface

Core interface for all order requests.

```java
public interface OrderRequest
```

**Methods:**

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `getIndex()` | - | `String` | Trading index/symbol (e.g., "NIFTY", "BANKNIFTY") |
| `getBuyThreshold()` | - | `double` | Price threshold at which to place order |
| `getTarget()` | - | `double` | Target price for the trade |
| `getExpirationTimestamp()` | - | `int` | Timestamp when order request expires |
| `getTag()` | - | `String` | Unique tag identifying the order request |
| `getTask()` | - | `Task` | Task associated with this order request |
| `verifyBuyThreshold(Ticker tick)` | `tick` | `Optional<OrderRequest>` | Returns order if threshold crossed, empty otherwise |

---

### IndexOrderRequest

Order request for index-based futures/options trading.

```java
@Slf4j
@Getter
@Builder
public class IndexOrderRequest implements OrderRequest
```

**Key Fields:** task, tag, index, optionSymbol, date, timestamp, expirationTimestamp, buyThreshold, target, stopLoss, callOrder, extraData

**Builder:**
```java
public static IndexOrderRequestBuilder builder(String tag, String index, Task task)
```

---

### OptionBasedOrderRequest

Order request for option premium-based trading.

```java
@Slf4j
@Getter
@Builder
public class OptionBasedOrderRequest implements OrderRequest
```

**Key Fields:** task, tag, index, date, timestamp, expirationTimestamp, buyThreshold, target, stopLoss, extraData

**Builder:**
```java
public static OptionBasedOrderRequestBuilder builder(String tag, String index, Task task)
```

---

### TickBasedOrderRequest

Order request for tick-based high-frequency trading.

```java
@Slf4j
@Getter
@Builder
public class TickBasedOrderRequest implements OrderRequest
```

**Key Fields:** Similar to `IndexOrderRequest`.

**Key Differences:**
- `verifyBuyThreshold()` always returns `Optional.of(this)` (no threshold verification needed)

**Builder:**
```java
public static TickBasedOrderRequestBuilder builder(String tag, String index, Task task)
```

**Usage Example:**
```java
import com.vish.fno.model.order.orderrequest.IndexOrderRequest;
import com.vish.fno.model.order.orderrequest.OptionBasedOrderRequest;
import com.vish.fno.model.order.orderrequest.TickBasedOrderRequest;
import com.vish.fno.model.Task;
import lombok.extern.slf4j.Slf4j;
import java.util.Date;
import java.util.HashMap;

@Slf4j
public class OrderRequestExample {
    public void createOrders() {
        Task task = Task.builder().enabled(true).expiryDayOrders(true).build();

        // Index-based order (NIFTY Call)
        IndexOrderRequest callOrder = IndexOrderRequest.builder("STRATEGY_1", "NIFTY", task)
            .optionSymbol("NIFTY24OCT19500CE")
            .date(new Date())
            .timestamp(915)
            .expirationTimestamp(1530)
            .buyThreshold(19500.0)
            .target(19600.0)
            .stopLoss(19450.0)
            .callOrder(true)
            .extraData(new HashMap<>())
            .build();

        // Option premium order
        OptionBasedOrderRequest premiumOrder = OptionBasedOrderRequest.builder("PREMIUM_1", "NIFTY50", task)
            .date(new Date())
            .timestamp(915)
            .expirationTimestamp(1530)
            .buyThreshold(150.0)
            .target(180.0)
            .stopLoss(140.0)
            .extraData(new HashMap<>())
            .build();

        // Tick-based order (immediate execution)
        TickBasedOrderRequest tickOrder = TickBasedOrderRequest.builder("TICK_1", "NIFTY", task)
            .optionSymbol("NIFTY24OCT19500CE")
            .date(new Date())
            .timestamp(915)
            .expirationTimestamp(1530)
            .buyThreshold(19500.0)
            .target(19520.0)
            .stopLoss(19490.0)
            .callOrder(true)
            .extraData(new HashMap<>())
            .build();

        log.info("Created call order: {}", callOrder);
        log.info("Created premium order: {}", premiumOrder);
        log.info("Created tick order: {}", tickOrder);
    }
}
```

---

## Active Order Package

### ActiveOrder Interface

Interface for an active (executed) order.

```java
public interface ActiveOrder
```

**Methods:**

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `getIndex()` | - | `String` | Trading index |
| `getDate()` | - | `Date` | Order date |
| `getTarget()` | - | `double` | Target price |
| `getStopLoss()` | - | `double` | Stop loss price |
| `getBuyPrice()` | - | `double` | Buy price |
| `getSellPrice()` | - | `double` | Sell price |
| `setStopLoss(double stopLoss)` | `stopLoss` | `void` | Updates stop loss |
| `setSellPrice(double sellPrice)` | `sellPrice` | `void` | Sets sell price |
| `setExitTimeStamp(int exitTimeStamp)` | `exitTimeStamp` | `void` | Sets exit timestamp |
| `getBuyQuantity()` | - | `int` | Quantity bought |
| `getLotSize()` | - | `int` | Lot size |
| `getSoldQuantity()` | - | `int` | Quantity sold |
| `incrementSoldQuantity(int soldQuantity, double sellOptionPrice)` | `soldQuantity`, `sellOptionPrice` | `void` | Tracks partial exits |
| `getTag()` | - | `String` | Order tag |
| `isActive()` | - | `boolean` | Checks if order is active |
| `setActive(boolean active)` | `active` | `void` | Sets active status |
| `closeOrder(double closePrice, int timeIndex, String timestamp)` | `closePrice`, `timeIndex`, `timestamp` | `void` | Closes the order |
| `getExtraData()` | - | `Map<String, String>` | Extra metadata |
| `appendExtraData(String key, String value)` | `key`, `value` | `void` | Adds metadata |
| `getTradingSymbol()` | - | `String` | Trading symbol |
| `getProfit()` | - | `double` | Calculates profit |

**Note:** For CSV export and order logging, use `ActiveOrderFormatter` utility class from fno-utils module (see fno-utils documentation).

---

### AbstractActiveOrder

Abstract base class providing common functionality for all active orders.

```java
@Getter
public abstract class AbstractActiveOrder implements ActiveOrder
```

**Protected Fields:** tag, date, entryTimeStamp, exitTimeStamp, buyThreshold, buyPrice, buyQuantity, soldQuantity, sellPrice, target, stopLoss, extraData, stopLossRevisionCount, stopLossRevision

**Constructor:**
```java
public AbstractActiveOrder(String tag, Date date, int entryTimeStamp, double buyThreshold,
                          double buyPrice, int buyQuantity, double target, double stopLoss,
                          Map<String, String> extraData)
```

**Protected Methods:**
- `updateStopLoss(double stopLoss)`: Updates stop loss and maintains revision history
- `appendExtraData(String key, String value)`: Adds or updates metadata
- `getTag()`: Returns tag with lowercase letters removed

---

### ActiveIndexOrder

Active order implementation for index-based trades.

```java
@Slf4j
@Getter
public class ActiveIndexOrder extends AbstractActiveOrder
```

**Additional Fields:** task, index, optionSymbol, lotSize, callOrder, buyOptionPrice, sellOptionPrice, isActive, realisedProfit

**Constructor:**
```java
public ActiveIndexOrder(IndexOrderRequest openOrder, double buyPrice, int timestampIndex, String timestamp,
                        int quantity, int lotSize)
```

**Key Methods:**
- `setStopLoss(double stopLoss)`: For Call orders, only updates if new stop loss is higher; for Put orders, only if lower (trailing stop loss logic)
- `setBuyOptionPrice(double buyOptionPrice)`: Sets option buy price and initializes realized profit
- `incrementSoldQuantity(int soldQuantity, double sellOptionPrice)`: Increments sold quantity and updates realized profit
- `isTargetAchieved(double ltp)`: For Call: `ltp > target`, for Put: `ltp < target`
- `isStopLossHit(double ltp)`: For Call: `ltp < stopLoss`, for Put: `ltp > stopLoss`
- `getProfit()`: For Call: `(sellPrice - buyPrice) * buyQuantity`, for Put: `(buyPrice - sellPrice) * buyQuantity`

---

### OptionBasedActiveOrder

Active order for option premium-based trades.

```java
@Slf4j
@Getter
public class OptionBasedActiveOrder extends AbstractActiveOrder
```

**Additional Fields:** task, index, lotSize, isActive, realisedProfit

**Constructor:**
```java
public OptionBasedActiveOrder(OptionBasedOrderRequest openOrder, double buyPrice, int timestampIndex, String timestamp,
                              int quantity, int lotSize)
```

**Key Differences:**
- Profit calculation: Always `(sellPrice - buyPrice) * buyQuantity` (long premium)
- Stop loss: Only updates if new value is higher (always long positions)
- Realized profit: Initialized as negative buy cost, incremented with sales

---

### TickBasedActiveOrder

Active order for tick-based trades.

```java
@Slf4j
@Getter
public class TickBasedActiveOrder extends AbstractActiveOrder
```

**Behavior:** Similar to `ActiveIndexOrder` (supports both call and put orders).

---

### ActiveOrderFactory

Factory for creating active orders from order requests.

```java
public class ActiveOrderFactory
```

**Method:**
```java
public static ActiveOrder createOrder(OrderRequest orderRequest, double ltp, int timestamp, String orderEntryTimestamp,
                                      int quantity, int lotSize)
```

**Mapping:**
- `IndexOrderRequest` → `ActiveIndexOrder`
- `OptionBasedOrderRequest` → `OptionBasedActiveOrder`
- `TickBasedOrderRequest` → `TickBasedActiveOrder`

**Usage Example:**
```java
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.activeorder.ActiveOrderFactory;
import com.vish.fno.model.order.orderrequest.OrderRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrderExecutionExample {
    public void executeOrder(OrderRequest orderRequest) {
        double currentLTP = 19505.0;
        int currentTimestamp = 920;
        String timestampString = "2024-10-28 09:20:00";

        int quantity = 50;
        int lotSize = 50;

        ActiveOrder activeOrder = ActiveOrderFactory.createOrder(
            orderRequest, currentLTP, currentTimestamp, timestampString, quantity, lotSize
        );

        log.info("Active order created: {}", activeOrder);

        // Manage order lifecycle
        if (activeOrder.isTargetAchieved(19550.0)) {
            log.info("Target achieved!");
            activeOrder.closeOrder(19550.0, 1030, "2024-10-28 10:30:00");
        }

        // Trail stop loss (for call order)
        activeOrder.setStopLoss(19510.0);

        // Partial exit
        if (activeOrder instanceof ActiveIndexOrder indexOrder) {
            indexOrder.incrementSoldQuantity(25, 170.0);
        }

        log.info("Current profit: {}", activeOrder.getProfit());
    }
}
```

---

### OrderSellDetailModel

Java record containing order sell decision details.

```java
public record OrderSellDetailModel(
    boolean sellOrder,
    int quantity,
    OrderSellReason reason,
    ActiveOrder order
)
```

**Constructor:**
```java
public OrderSellDetailModel(boolean sellOrder)
```
Creates a "don't sell" instance. Throws `IllegalStateException` if `sellOrder` is `true`.

**Usage Example:**
```java
import com.vish.fno.model.order.OrderSellDetailModel;
import com.vish.fno.model.order.OrderSellReason;
import com.vish.fno.model.order.activeorder.ActiveOrder;

public class SellDecisionExample {
    public OrderSellDetailModel evaluateSell(ActiveOrder activeOrder, double currentLTP) {
        if (!activeOrder.isActive()) {
            return new OrderSellDetailModel(false);
        }

        if (activeOrder.isTargetAchieved(currentLTP)) {
            return new OrderSellDetailModel(true, activeOrder.getBuyQuantity(),
                                           OrderSellReason.TARGET_HIT, activeOrder);
        }

        if (activeOrder.isStopLossHit(currentLTP)) {
            return new OrderSellDetailModel(true, activeOrder.getBuyQuantity(),
                                           OrderSellReason.STOP_LOSS_HIT, activeOrder);
        }

        return new OrderSellDetailModel(false);
    }
}
```

---

### ExitDetail

Immutable record for storing order exit details when an order is sold (partially or fully).

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExitDetail(
    Integer quantity,
    Double sellPrice,
    Double sellOptionPrice
)
```

**Fields:**
- `quantity`: Number of contracts/lots sold in this exit
- `sellPrice`: Index price at which the order was sold
- `sellOptionPrice`: Option price at which the order was sold (null for non-index orders)

**Factory Methods:**
```java
public static ExitDetail forRegularOrder(Integer quantity, Double sellPrice)
public static ExitDetail forIndexOrder(Integer quantity, Double sellPrice, Double sellOptionPrice)
```

**Usage Example:**
```java
import com.vish.fno.model.order.ExitDetail;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExitDetailsExample {
    public void recordOrderExit() {
        // Regular order exit (non-index)
        ExitDetail regularExit = ExitDetail.forRegularOrder(50, 19550.0);
        log.info("Regular exit: {} lots at {}", regularExit.quantity(), regularExit.sellPrice());

        // Index order exit with option price
        ExitDetail indexExit = ExitDetail.forIndexOrder(25, 19500.0, 175.0);
        log.info("Index exit: {} lots, index={}, option={}",
            indexExit.quantity(), indexExit.sellPrice(), indexExit.sellOptionPrice());
    }
}
```

---

## Helper Package

### OrderFlowHandler Interface

Interface for handling order flow (buy and sell decisions).

```java
public interface OrderFlowHandler
```

**Methods:**
- `verifyAndSellOrder(String tickSymbol, Ticker tick)`: Evaluates sell conditions for active orders
- `verifyAndBuyOrder(Ticker tick, OrderRequest order)`: Evaluates buy conditions for pending orders

---

### ITMResolver Interface

Interface for resolving ITM (In-The-Money) and OTM (Out-of-The-Money) option symbols.

```java
public interface ITMResolver
```

**Methods:**

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `resolveITMSymbol(String index, double price, boolean isCall)` | `index`, `price`, `isCall` | `String` | Resolves ITM option symbol for given index at current price |
| `resolveOTMSymbol(String index, double price, boolean isCall)` | `index`, `price`, `isCall` | `String` | Resolves OTM option symbol for given index at current price |
| `prepareSymbols()` | - | `void` | Prepares/refreshes option symbols (e.g., fetches from broker API) |

**Implementations:**
- **KiteITMResolver** (in `fno-kite-reader`): Production implementation using Kite Connect API
- **BacktestITMResolver** (in consumer projects): Backtest implementation with pre-configured symbol mappings

**Usage Example:**
```java
import com.vish.fno.model.helper.ITMResolver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OptionSymbolResolver {
    private final ITMResolver itmResolver;

    public OptionSymbolResolver(ITMResolver itmResolver) {
        this.itmResolver = itmResolver;
    }

    public void resolveOptionSymbols(String index, double currentPrice) {
        // Prepare symbols (e.g., fetch latest from broker)
        itmResolver.prepareSymbols();

        // Resolve ITM CALL
        String itmCall = itmResolver.resolveITMSymbol(index, currentPrice, true);
        log.info("ITM CALL for {} at {}: {}", index, currentPrice, itmCall);

        // Resolve ITM PUT
        String itmPut = itmResolver.resolveITMSymbol(index, currentPrice, false);
        log.info("ITM PUT for {} at {}: {}", index, currentPrice, itmPut);

        // Resolve OTM CALL
        String otmCall = itmResolver.resolveOTMSymbol(index, currentPrice, true);
        log.info("OTM CALL for {} at {}: {}", index, currentPrice, otmCall);

        // Resolve OTM PUT
        String otmPut = itmResolver.resolveOTMSymbol(index, currentPrice, false);
        log.info("OTM PUT for {} at {}: {}", index, currentPrice, otmPut);
    }
}
```

---

## Cache Package

### LimitedCache

Generic cache with limited entries per key (FIFO eviction). Thread-safe implementation using `ConcurrentHashMap` and `ReentrantReadWriteLock`.

```java
@Slf4j
@SuppressWarnings("PMD.LooseCoupling")
public class LimitedCache<K, V>
```

**Constructor:**
```java
public LimitedCache(int maxEntriesPerKey)
```

**Thread-Safety:** All public methods are thread-safe for concurrent access.

**Methods:**

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `put(K key, V value)` | `key`, `value` | `void` | Thread-safe add with FIFO eviction (uses write lock) |
| `get(K key)` | `key` | `List<V>` | Returns defensive copy of values (uses read lock) |
| `keySet()` | - | `Set<K>` | Returns unmodifiable key set (thread-safe) |
| `size(K key)` | `key` | `int` | Returns entry count for key (uses read lock) |

**Usage Example:**
```java
import com.vish.fno.model.cache.LimitedCache;
import com.vish.fno.model.Ticker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TickerCacheExample {
    private final LimitedCache<String, Ticker> tickerCache = new LimitedCache<>(100);

    public void cacheTickerData(String symbol, Ticker ticker) {
        tickerCache.put(symbol, ticker);
        log.info("Cached {} tickers for {}", tickerCache.size(symbol), symbol);
    }

    public List<Ticker> getRecentTickers(String symbol) {
        return tickerCache.get(symbol);
    }
}
```

---

### OrderCache

Specialized cache for managing order requests and active orders with thread-safe cash management. Uses `CopyOnWriteArrayList` for order collections.

```java
@Slf4j
public class OrderCache
```

**Constructor:**
```java
public OrderCache(double availableCash)
```

**Fields:**
- `orderRequests`: `CopyOnWriteArrayList<OrderRequest>` - Thread-safe pending orders
- `activeOrders`: `CopyOnWriteArrayList<ActiveOrder>` - Thread-safe active orders
- `availableCash`: `volatile double` with `@Getter` - Read-only access via getter

**Key Methods:**

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `getAvailableCash()` | - | `double` | Thread-safe read of cash balance (volatile field) |
| `deductCash(double amount)` | `amount` | `void` | Atomically deduct cash for buy orders (synchronized) |
| `addCash(double amount)` | `amount` | `void` | Atomically add cash for sell orders (synchronized) |
| `checkEntryInOpenOrders(Ticker tick, String tickSymbol)` | `tick`, `tickSymbol` | `Optional<OrderRequest>` | Checks if any pending order should be triggered |
| `isNotInActiveOrders(OrderRequest tickOrderRequest)` | `tickOrderRequest` | `boolean` | Validates no duplicate active order exists |
| `addOrderRequest(OrderRequest order)` | `order` | `void` | Adds order request (removes duplicates first) |
| `appendActiveOrder(ActiveOrder activeOrder)` | `activeOrder` | `void` | Adds active order to tracking list |
| `removeExpiredOpenOrders(int timestamp)` | `timestamp` | `void` | Removes orders past their expiration time |
| `getActiveOrderForSymbol(String symbol)` | `symbol` | `List<ActiveOrder>` | Returns all active orders for a symbol |

**Thread-Safety Guarantees:**
- **Cash operations**: `deductCash()` and `addCash()` are synchronized for atomic updates
- **Cash reading**: `getAvailableCash()` uses volatile field for thread-safe reads
- **Collections**: `CopyOnWriteArrayList` ensures thread-safe iteration and modification
- **No setter**: `availableCash` field has no setter - enforces use of synchronized methods only

**Usage Example:**
```java
import com.vish.fno.model.cache.OrderCache;
import com.vish.fno.model.order.orderrequest.OrderRequest;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import com.vish.fno.model.order.activeorder.ActiveOrderFactory;
import com.vish.fno.model.Ticker;
import lombok.extern.slf4j.Slf4j;
import java.util.Optional;

@Slf4j
public class OrderCacheExample {
    private final OrderCache orderCache = new OrderCache(100000.0);

    public void manageOrders(OrderRequest request, Ticker ticker) {
        // Check available cash (read-only access)
        log.info("Available cash: {}", orderCache.getAvailableCash());

        orderCache.addOrderRequest(request);
        Optional<OrderRequest> triggerOrder = orderCache.checkEntryInOpenOrders(ticker, "NIFTY");

        if (triggerOrder.isPresent()) {
            int quantity = 50;
            int lotSize = 50;
            double orderCost = ticker.lastTradedPrice() * quantity;

            // Deduct cash for buy order (thread-safe)
            orderCache.deductCash(orderCost);

            ActiveOrder activeOrder = ActiveOrderFactory.createOrder(
                triggerOrder.get(), ticker.lastTradedPrice(), 915, "2024-10-28 09:15:00",
                quantity, lotSize
            );

            orderCache.appendActiveOrder(activeOrder);
            orderCache.removeOrderRequest(triggerOrder.get());
            log.info("Order activated, remaining cash: {}", orderCache.getAvailableCash());
        }

        // Release capital on exit
        for (ActiveOrder order : orderCache.getActiveOrders()) {
            if (order.isTargetAchieved(ticker.lastTradedPrice())) {
                double sellValue = ticker.lastTradedPrice() * order.getBuyQuantity();
                orderCache.addCash(sellValue); // Thread-safe cash addition
                orderCache.removeActiveOrder(order);
            }
        }

        orderCache.removeExpiredOpenOrders(1530);
    }
}
```

---

## Util Package

### ModelUtils

Utility class for common model operations.

```java
@SuppressWarnings("PMD.AvoidDecimalLiteralsInBigDecimalConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ModelUtils
```

**Constants:** `public static final String INDENTED_TAB = "\n\t\t\t\t\t\t\t\t";`

**Methods:**

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `round(double price)` | `price` | `double` | Price rounded to nearest 0.05 (5 paise) |
| `roundTo5Paise(double price)` | `price` | `BigDecimal` | BigDecimal rounded to 5 paise with 2 decimal places |
| `roundToNearest(final BigDecimal value, final BigDecimal increment)` | `value`, `increment` | `BigDecimal` | Value rounded to nearest increment |
| `getStringDate(Date date)` | `date` | `String` | Date formatted as "yyyy-MM-dd" |
| `getStringTime(Date date)` | `date` | `String` | Time formatted as "HH:mm:ss.SSS" |
| `getStringDateTime(Date date)` | `date` | `String` | DateTime formatted as "yyyy-MM-dd HH:mm" |

**Usage Example:**
```java
import com.vish.fno.model.util.ModelUtils;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.util.Date;

@Slf4j
public class ModelUtilsExample {
    public void formatPrices() {
        double price = 19502.37;
        double rounded = ModelUtils.round(price);
        log.info("Rounded: {}", rounded); // 19502.35

        BigDecimal precise = ModelUtils.roundTo5Paise(price);
        log.info("Precise: {}", precise); // 19502.35

        Date now = new Date();
        String dateStr = ModelUtils.getStringDate(now);
        String timeStr = ModelUtils.getStringTime(now);
        String dateTimeStr = ModelUtils.getStringDateTime(now);

        log.info("Date: {}, Time: {}, DateTime: {}", dateStr, timeStr, dateTimeStr);
    }
}
```

---

## Core Data Models

### Candle - OHLCV Data (Java Record)

```java
public record Candle(
    String time,
    double open,
    double high,
    double low,
    double close,
    Long volume,
    Long oi
)
```

**Usage:**
```java
import com.vish.fno.model.Candle;

Candle candle = new Candle("2024-09-28 09:15:00", 19500.0, 19550.0, 19480.0, 19520.0, 1000000L, 5000000L);
double closePrice = candle.close();
Long volume = candle.volume();
```

---

### Ticker - Real-time Market Data (Java Record)

```java
public record Ticker(
    String mode,
    boolean tradable,
    long instrumentToken,
    String instrumentSymbol,
    double lastTradedPrice,
    double highPrice,
    double lowPrice,
    double openPrice,
    double closePrice,
    double change,
    double lastTradedQuantity,
    double averageTradePrice,
    long volumeTradedToday,
    double totalBuyQuantity,
    double totalSellQuantity,
    Date lastTradedTime,
    double oi,
    double openInterestDayHigh,
    double openInterestDayLow,
    Date tickTimestamp,
    Map<String, List<Depth>> depth
) implements Comparable<Ticker>
```

**Key Fields:** instrumentToken, instrumentSymbol, lastTradedPrice, oi, tickTimestamp, depth

**Usage:**
```java
import com.vish.fno.model.Ticker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TickerProcessor {
    public void processTicker(Ticker ticker, double previousLTP) {
        if (ticker.lastTradedPrice() > previousLTP) {
            log.info("Price up: {}", ticker.lastTradedPrice());
        }

        List<Ticker> tickers = /* ... */;
        tickers.sort(Ticker::compareTo); // Sorts by tickTimestamp
    }
}
```

---

### CandleMetaData, OptionMetaData, SymbolData - Storage Records

```java
public record CandleMetaData(String symbol, String date)

public record OptionMetaData(String symbol, String date, String expiryDate)

@Document(collection = "minute_history_data")
public record SymbolData(
    @Id CandleMetaData record,
    List<Candle> data
)
```

**Usage:**
```java
import com.vish.fno.model.SymbolData;
import com.vish.fno.model.Candle;
import com.vish.fno.model.candle.CandleMetaData;

CandleMetaData metadata = new CandleMetaData("NIFTY24OCTFUT", "2024-10-28");
SymbolData symbolData = new SymbolData(metadata, candles);
symbolDataRepository.save(symbolData);
```

---

## Strategy Interfaces

### Strategy - Base Strategy Interface

```java
public interface Strategy {
    void initialise(Task task);
    Task getTask();
    String getTag();
}
```

**Subinterfaces:**
- `MinuteStrategy extends Strategy`: Minute-level trading
- `IndexBasedStrategy extends MinuteStrategy`: Index futures trading
- `OptionBasedStrategy extends MinuteStrategy`: Options trading
- `TickBasedStrategy extends Strategy`: Real-time tick trading

**Implementing a Custom Strategy:**
```java
import com.vish.fno.model.strategy.Strategy;
import com.vish.fno.model.Task;

public class MyTradingStrategy implements Strategy {
    private Task task;
    private String tag;

    @Override
    public void initialise(Task task) {
        this.task = task;
        this.tag = "MY_CUSTOM_STRATEGY";
        setupIndicators();
        loadHistoricalData();
    }

    @Override
    public Task getTask() {
        return task;
    }

    @Override
    public String getTag() {
        return tag;
    }

    private void setupIndicators() {
        // Setup technical indicators
    }

    private void loadHistoricalData() {
        // Load required data
    }
}
```

---

## Wyckoff Models

### WyckoffPhase - Market Phase Enumeration

```java
public enum WyckoffPhase
```

**Enum Constants:**

| Phase | Name | Description |
|-------|------|-------------|
| `ACCUMULATION_PHASE_A` | Stopping the Prior Downtrend | PS, SC, AR, ST |
| `ACCUMULATION_PHASE_B` | Building a Cause | Testing supply and demand |
| `ACCUMULATION_PHASE_C` | Spring/Shakeout | Testing support levels |
| `ACCUMULATION_PHASE_D` | Sign of Strength | Breaking resistance |
| `DISTRIBUTION_PHASE_A` | Stopping the Prior Uptrend | PSY, BC, AR, ST |
| `DISTRIBUTION_PHASE_B` | Building a Cause | Testing demand and supply |
| `DISTRIBUTION_PHASE_C` | Upthrust | Testing resistance levels |
| `DISTRIBUTION_PHASE_D` | Sign of Weakness | Breaking support |
| `MARKUP` | Uptrend | Higher highs and higher lows |
| `MARKDOWN` | Downtrend | Lower highs and lower lows |
| `REACCUMULATION` | Continuation in Uptrend | - |
| `REDISTRIBUTION` | Continuation in Downtrend | - |
| `CONSOLIDATION` | Sideways Movement | Range bound |
| `UNKNOWN` | Unable to Determine | - |

**Methods:** `getPhaseName()`, `getDescription()`, `isAccumulation()`, `isDistribution()`, `isMarkup()`, `isMarkdown()`

---

### IWyckoffPhaseIdentifier - Phase Identification Interface

```java
public interface IWyckoffPhaseIdentifier
```

**Core Methods:**
- `WyckoffPhase identifyPhase(List<Candle> data, int currentIndex)`
- `String getIdentifierType()`
- `String getDescription()`

**Optional Methods (with defaults):**
- `double getPhaseConfidence(List<Candle> data, int currentIndex)`
- `int getMinimumDataPoints()`
- `boolean supportsRealTimeAnalysis()`
- `void reset()`

---

### WyckoffIndicators - Technical Indicators Record

```java
public record WyckoffIndicators(
    double pricePosition,
    double volumeAnalysis,
    double trendStrength,
    double rangePosition,
    double supplyDemandBalance,
    double volatility,
    double momentum,
    double relativeStrength,
    boolean hasSpring,
    boolean hasUpthrust,
    boolean hasSignOfStrength,
    boolean hasSignOfWeakness,
    double supportLevel,
    double resistanceLevel,
    double averageVolume,
    double currentVolume,
    int daysInRange,
    double rangeWidth
)
```

---

## Order Enums

### OrderSellReason - Order Exit Reasons

```java
public enum OrderSellReason {
    TARGET_HIT,
    STOP_LOSS_HIT,
    EXPIRY_TIME_REACHED
}
```

**Usage:**
```java
import com.vish.fno.model.order.OrderSellReason;

public void exitOrder(ActiveOrder order, double currentPrice) {
    OrderSellReason reason;

    if (currentPrice >= order.getTarget()) {
        reason = OrderSellReason.TARGET_HIT;
    } else if (currentPrice <= order.getStopLoss()) {
        reason = OrderSellReason.STOP_LOSS_HIT;
    } else if (isExpiryTime()) {
        reason = OrderSellReason.EXPIRY_TIME_REACHED;
    }

    sellOrder(order, reason);
}
```

---

## Edge Cases

**OrderRequest:**
- `tag` field strips lowercase letters; null becomes empty string
- Equality based on tag, index, and callOrder (not all fields)

**ActiveOrder:**
- Stop loss trailing only updates in beneficial direction (up for calls, down for puts)
- Partial exits use `incrementSoldQuantity()` for tracking
- Realized profit calculated incrementally as quantities sold

**OrderCache:**
- `addOrderRequest()` removes duplicates before adding
- Use `removeExpiredOpenOrders()` regularly to clean up
- Cash management: ONLY via `deductCash()` and `addCash()` (no setter provided)
- Both cash methods are synchronized to prevent race conditions

**LimitedCache:**
- FIFO eviction when limit reached
- Returns empty list for non-existent keys

---

## Dependencies

- **Spring Boot Data MongoDB**: For persistence (optional)
- **Jackson**: For JSON serialization
- **Lombok**: For reducing boilerplate

---

## Thread Safety

Most model classes are POJOs and not thread-safe by default. If sharing across threads, use proper synchronization or immutable copies.

**Thread-Safe Components:**
- **OrderCache**:
  - `getAvailableCash()` provides thread-safe reads via volatile field
  - `deductCash()` and `addCash()` are synchronized for atomic modifications
  - No setter provided - enforces thread-safe cash management
- **Wyckoff Records**: `WyckoffIndicators` and all phase-related records are immutable and thread-safe

---

## See Also

- **fno-utils**: Utilities for working with these models
- **fno-kite-reader**: Integration with Kite Connect API
- **fno-technicals**: Technical indicators using Candle data
- **fno-phase-analyzer**: Wyckoff phase identification implementations
