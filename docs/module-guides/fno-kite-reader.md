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

This automatically includes `fno-utils` (and transitively `fno-models`).

## Prerequisites

1. **Zerodha Account**: Active trading account
2. **Kite Connect App**: Create app at https://developers.kite.trade/
3. **API Credentials**:
   - API Key
   - API Secret
   - Access Token (generated daily via login flow)

## Configuration

### Spring Boot Configuration

```yaml
# application.yml
kite:
  api-key: ${KITE_API_KEY}
  api-secret: ${KITE_API_SECRET}
  access-token: ${KITE_ACCESS_TOKEN}
```

### Bean Configuration

```java
@Configuration
public class KiteConfiguration {

    @Value("${kite.api-key}")
    private String apiKey;

    @Value("${kite.access-token}")
    private String accessToken;

    @Bean
    public KiteConnect kiteConnect() {
        KiteConnect kc = new KiteConnect(apiKey);
        kc.setAccessToken(accessToken);
        return kc;
    }

    @Bean
    public KiteService kiteService(KiteConnect kiteConnect) {
        return new KiteService(kiteConnect);
    }

    @Bean
    public HistoricalDataService historicalDataService(KiteConnect kiteConnect) {
        return new HistoricalDataService(kiteConnect);
    }

    @Bean
    public InstrumentCache instrumentCache(KiteService kiteService) {
        return new InstrumentCache(kiteService);
    }
}
```

## Core Services

### KiteService - Order Management & Market Data

```java
import com.vish.fno.reader.service.KiteService;
import com.zerodhatech.kiteconnect.KiteConnect;

@Service
public class TradingService {
    private final KiteService kiteService;

    public TradingService(KiteConnect kiteConnect) {
        this.kiteService = new KiteService(kiteConnect);
    }
}
```

#### Place Orders

```java
// Market order
IndexOrderRequest marketOrder = IndexOrderRequest.builder()
    .symbol("NIFTY24SEPFUT")
    .quantity(50)
    .orderType("MARKET")
    .transactionType("BUY")
    .product("MIS")
    .build();

try {
    String orderId = kiteService.placeOrder(marketOrder);
    logger.info("Order placed: {}", orderId);
} catch (KiteException e) {
    logger.error("Order failed: {} - {}", e.code, e.message);
} catch (IOException e) {
    logger.error("Network error", e);
}
```

#### Modify Orders

```java
// Modify existing order
IndexOrderRequest modifiedOrder = IndexOrderRequest.builder()
    .symbol("NIFTY24SEPFUT")
    .quantity(50)
    .orderType("LIMIT")
    .price(19600.0)
    .transactionType("BUY")
    .product("MIS")
    .build();

try {
    kiteService.modifyOrder("240928000123456", modifiedOrder);
} catch (KiteException | IOException e) {
    logger.error("Modify failed", e);
}
```

#### Cancel Orders

```java
try {
    kiteService.cancelOrder("240928000123456");
    logger.info("Order cancelled");
} catch (KiteException | IOException e) {
    logger.error("Cancel failed", e);
}
```

#### Get Positions

```java
try {
    Positions positions = kiteService.getPositions();

    // Net positions
    positions.net.forEach(pos -> {
        logger.info("Symbol: {}, Qty: {}, PnL: {}",
            pos.tradingSymbol, pos.quantity, pos.pnl);
    });

    // Day positions
    positions.day.forEach(pos -> {
        logger.info("Intraday: {}, Qty: {}", pos.tradingSymbol, pos.quantity);
    });
} catch (KiteException | IOException e) {
    logger.error("Failed to fetch positions", e);
}
```

#### Get Holdings

```java
try {
    List<Holding> holdings = kiteService.getHoldings();
    holdings.forEach(holding -> {
        logger.info("Stock: {}, Qty: {}, Avg Price: {}, Current: {}",
            holding.tradingSymbol,
            holding.quantity,
            holding.averagePrice,
            holding.lastPrice);
    });
} catch (KiteException | IOException e) {
    logger.error("Failed to fetch holdings", e);
}
```

#### Get Quote

```java
try {
    Map<String, Quote> quotes = kiteService.getQuote("NSE:NIFTY 50", "NFO:NIFTY24SEPFUT");

    Quote niftyQuote = quotes.get("NSE:NIFTY 50");
    logger.info("NIFTY: LTP={}, Change={}%",
        niftyQuote.lastPrice,
        niftyQuote.netChange);
} catch (KiteException | IOException e) {
    logger.error("Failed to fetch quotes", e);
}
```

### HistoricalDataService - Historical Candlestick Data

```java
import com.vish.fno.reader.service.HistoricalDataService;
import com.zerodhatech.models.HistoricalData;

@Service
public class DataService {
    private final HistoricalDataService histService;

    public DataService(KiteConnect kiteConnect) {
        this.histService = new HistoricalDataService(kiteConnect);
    }
}
```

#### Fetch Historical Data

```java
import com.vish.fno.model.Candle;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

Long instrumentToken = 256265L; // NIFTY token
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

try {
    List<HistoricalData> data = histService.getHistoricalData(
        instrumentToken,
        LocalDate.of(2024, 9, 1),
        LocalDate.of(2024, 9, 30),
        "day"  // Intervals: "minute", "5minute", "15minute", "day"
    );

    // Convert to Candle objects
    List<Candle> candles = data.stream()
        .map(hd -> new Candle(
            hd.timeStamp.toInstant()
                .atZone(ZoneId.of("Asia/Kolkata"))
                .toLocalDateTime()
                .format(formatter),
            hd.open,
            hd.high,
            hd.low,
            hd.close,
            hd.volume,
            hd.oi
        ))
        .toList();

} catch (KiteException | IOException e) {
    logger.error("Failed to fetch historical data", e);
}
```

**Available Intervals:**
- `"minute"` - 1-minute candles (max 60 days)
- `"5minute"` - 5-minute candles
- `"15minute"` - 15-minute candles
- `"day"` - Daily candles (max 2000 days)

### InstrumentCache - Fast Instrument Lookup

```java
import com.vish.fno.reader.service.InstrumentCache;

@Service
public class InstrumentService {
    private final InstrumentCache cache;

    public InstrumentService(KiteService kiteService) {
        this.cache = new InstrumentCache(kiteService);
    }

    @PostConstruct
    public void init() throws KiteException, IOException {
        cache.initialize(); // Fetch and cache all instruments
        logger.info("Instrument cache initialized");
    }
}
```

#### Lookup Instruments

```java
// Get instrument by trading symbol
Instrument niftyFuture = cache.getInstrument("NIFTY24SEPFUT");
logger.info("Token: {}, Lot Size: {}",
    niftyFuture.instrumentToken,
    niftyFuture.lotSize);

// Get instrument token directly
Long token = cache.getInstrumentToken("NIFTY24SEPFUT");

// Get all instruments by exchange
List<Instrument> nfoInstruments = cache.getInstrumentsByExchange("NFO");
logger.info("Total NFO instruments: {}", nfoInstruments.size());
```

### KiteWebSocket - Real-time Market Data

```java
import com.vish.fno.reader.service.KiteWebSocket;
import com.zerodhatech.ticker.KiteTicker;
import com.zerodhatech.models.Tick;

@Service
public class RealtimeDataService {
    private KiteWebSocket webSocket;

    public void startWebSocket(String accessToken, String apiKey) {
        KiteTicker ticker = new KiteTicker(accessToken, apiKey);
        webSocket = new KiteWebSocket(ticker);

        // Set up callbacks
        ticker.setOnConnectedListener(() -> {
            logger.info("WebSocket connected");
        });

        ticker.setOnDisconnectedListener(() -> {
            logger.warn("WebSocket disconnected");
        });

        ticker.setOnTickerArrivalListener(this::onTick);

        ticker.setOnErrorListener(exception -> {
            logger.error("WebSocket error", exception);
        });

        // Connect
        webSocket.connect();

        // Subscribe to instruments
        ArrayList<Long> tokens = new ArrayList<>();
        tokens.add(256265L);  // NIFTY
        tokens.add(260105L);  // BANKNIFTY
        webSocket.subscribe(tokens);
    }

    private void onTick(ArrayList<Tick> ticks) {
        for (Tick tick : ticks) {
            logger.info("Token: {}, LTP: {}, Volume: {}, OI: {}",
                tick.getInstrumentToken(),
                tick.getLastTradedPrice(),
                tick.getVolumeTradedToday(),
                tick.getOI());

            // Process tick data
            processMarketData(tick);
        }
    }

    public void stopWebSocket() {
        if (webSocket != null) {
            webSocket.disconnect();
        }
    }
}
```

## Utility Classes

### OrderUtils - Order Parameter Builder

```java
import com.vish.fno.reader.util.OrderUtils;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.kiteconnect.utils.Constants;

public class OrderService {
    public OrderParams createOrder(String symbol, int quantity, String transactionType) {
        // Creates market order with NFO exchange and MIS product
        OrderParams params = OrderUtils.createMarketOrderWithParameters(
            symbol,           // e.g., "NIFTY24SEPFUT"
            quantity,         // e.g., 50
            transactionType,  // "BUY" or "SELL"
            "MY_STRATEGY"     // Tag (max 20 chars)
        );

        // Pre-configured with:
        // - orderType: MARKET
        // - product: MIS (intraday)
        // - exchange: NFO
        // - validity: DAY
        // - triggerPrice: 0.0

        return params;
    }
}
```

**Method Signature:**
```java
public static OrderParams createMarketOrderWithParameters(
    String symbol,
    int orderSize,
    String transactionType,  // "BUY" or "SELL"
    String tag               // Strategy tag (max 20 chars, auto-truncated)
)
```

**Returns:** `OrderParams` ready for use with `KiteConnect.placeOrder()`

**Use Case:** Quickly create standardized market orders without manually setting all parameters.

**Thread Safety:** Safe (no shared state)

---

### InstrumentFileUtils - Instrument Cache Persistence

```java
import com.vish.fno.reader.util.InstrumentFileUtils;
import com.zerodhatech.models.Instrument;

public class CacheService {
    public void saveAndLoadInstruments() {
        // 1. Fetch instruments from Kite
        List<Instrument> instruments = kiteConnect.getInstruments();

        // 2. Save to local file (instrument_cache/instruments_<date>.json)
        InstrumentFileUtils.saveInstrumentCache(instruments);

        // 3. Save filtered version with pretty print
        List<Instrument> niftyOptions = instruments.stream()
            .filter(i -> i.getName().equals("NIFTY"))
            .toList();
        InstrumentFileUtils.saveFilteredInstrumentCache(niftyOptions);

        // 4. Load cached instruments (avoids API call)
        List<Instrument> cachedToday = InstrumentFileUtils.loadInstrumentCache(0);  // Today
        List<Instrument> cachedYesterday = InstrumentFileUtils.loadInstrumentCache(1);  // 1 day ago

        if (cachedToday == null) {
            // Cache file doesn't exist, fetch from API
            instruments = kiteConnect.getInstruments();
            InstrumentFileUtils.saveInstrumentCache(instruments);
        }
    }
}
```

**Methods:**

```java
// Save all instruments to date-stamped file
public static void saveInstrumentCache(List<Instrument> instruments)

// Save filtered instruments with pretty formatting
public static void saveFilteredInstrumentCache(Object instruments)

// Load instruments from N days ago (0 = today, 1 = yesterday, etc.)
public static List<Instrument> loadInstrumentCache(int days)
```

**File Location:** `./instrument_cache/instruments_<yyyy-MM-dd>.json`

**Use Case:**
- Reduce API calls by caching instrument list
- Instrument list changes daily, so cache by date
- Avoid hitting rate limits during startup

**Thread Safety:** Not thread-safe for concurrent writes

---

### OptionPriceUtils - Option Strike Selection

```java
import com.vish.fno.reader.service.OptionPriceUtils;
import com.zerodhatech.models.Instrument;
import lombok.extern.slf4j.Slf4j;
import java.util.Optional;

@Slf4j
public class OptionSelector {
    private final List<Instrument> instruments;

    public void selectOptions(double niftySpot) {
        // 1. Get next expiry future symbol
        Optional<String> futureSymbol = OptionPriceUtils.getNextExpiryFutureSymbol(
            "NIFTY 50",
            instruments
        );
        futureSymbol.ifPresent(symbol ->
            log.info("Next expiry future: {}", symbol)  // e.g., "NIFTY24SEPFUT"
        );

        // 2. Get ITM call option
        String itmCall = OptionPriceUtils.getITMStock(
            "NIFTY 50",
            niftySpot,        // e.g., 19500.0
            true,             // true = Call, false = Put
            instruments
        );
        log.info("ITM Call: {}", itmCall);  // e.g., "NIFTY24SEP19400CE"

        // 3. Get ITM put option
        String itmPut = OptionPriceUtils.getITMStock(
            "NIFTY 50",
            niftySpot,
            false,            // Put option
            instruments
        );
        log.info("ITM Put: {}", itmPut);    // e.g., "NIFTY24SEP19500PE"

        // 4. Get OTM call option
        String otmCall = OptionPriceUtils.getOTMStock(
            "NIFTY 50",
            niftySpot,
            true,
            instruments
        );
        log.info("OTM Call: {}", otmCall);  // e.g., "NIFTY24SEP19550CE"

        // 5. Get OTM put option
        String otmPut = OptionPriceUtils.getOTMStock(
            "NIFTY 50",
            niftySpot,
            false,
            instruments
        );
        log.info("OTM Put: {}", otmPut);    // e.g., "NIFTY24SEP19450PE"
    }
}
```

**Methods:**

```java
// Get nearest expiry future symbol
public static Optional<String> getNextExpiryFutureSymbol(
    String symbol,          // "NIFTY 50", "NIFTY BANK", "NIFTY FIN SERVICE"
    List<Instrument> instruments
)

// Get in-the-money option symbol
public static String getITMStock(
    String indexSymbol,     // "NIFTY 50", "NIFTY BANK", "NIFTY FIN SERVICE"
    double price,           // Current spot price
    boolean isCall,         // true = Call, false = Put
    List<Instrument> instruments
)

// Get out-of-the-money option symbol
public static String getOTMStock(
    String indexSymbol,
    double price,
    boolean isCall,
    List<Instrument> instruments
)
```

**Returns:**
- `getNextExpiryFutureSymbol()`: `Optional<String>` (empty if not found)
- `getITMStock()`: Trading symbol string (empty if not found)
- `getOTMStock()`: Trading symbol string (empty if not found)

**Supported Indices:**
- `"NIFTY 50"` → Maps to "NIFTY"
- `"NIFTY BANK"` → Maps to "BANKNIFTY"
- `"NIFTY FIN SERVICE"` → Maps to "FINNIFTY"

**Use Cases:**
- Build option strategies (straddles, strangles, spreads)
- Dynamically select strikes based on spot price
- Find nearest expiry contracts for rolling positions

**Thread Safety:** Safe (no shared state)

**Edge Cases:**
- Returns empty string if no matching strike found
- Logs warnings when symbols not found
- Always selects nearest expiry date

---

## Common Integration Patterns

### Pattern 1: Live Trading with Historical Analysis

```java
import com.vish.fno.reader.service.KiteService;
import com.vish.fno.reader.service.HistoricalDataService;
import com.vish.fno.reader.service.InstrumentCache;
import com.vish.fno.technical.indicators.ma.SimpleMovingAverage;
import com.vish.fno.technical.indicators.RelativeStrengthIndex;
import com.vish.fno.model.Candle;
import com.vish.fno.model.order.orderrequest.IndexOrderRequest;
import java.time.LocalDate;

@Service
public class LiveTradingService {
    private final KiteService kiteService;
    private final HistoricalDataService histService;
    private final InstrumentCache cache;
    private final SimpleMovingAverage sma50;
    private final RelativeStrengthIndex rsi14;

    public void executeStrategy(String symbol) {
        try {
            // 1. Get instrument token
            Long token = cache.getInstrumentToken(symbol);

            // 2. Fetch recent historical data
            List<HistoricalData> recentData = histService.getHistoricalData(
                token,
                LocalDate.now().minusDays(60),
                LocalDate.now(),
                "day"
            );

            // 3. Convert to candles
            List<Candle> candles = convertToCandles(recentData);

            // 4. Calculate indicators
            List<Double> smaValues = sma50.calculate(candles);
            List<Double> rsiValues = rsi14.calculate(candles);

            // 5. Generate signal
            double currentRSI = rsiValues.get(rsiValues.size() - 1);
            double currentPrice = candles.get(candles.size() - 1).getClose();
            double currentSMA = smaValues.get(smaValues.size() - 1);

            if (currentRSI < 30 && currentPrice > currentSMA) {
                // Buy signal - place order
                placeOrder(symbol, "BUY");
            }

        } catch (Exception e) {
            logger.error("Strategy execution failed", e);
        }
    }

    private void placeOrder(String symbol, String transactionType) throws Exception {
        IndexOrderRequest order = IndexOrderRequest.builder()
            .symbol(symbol)
            .quantity(50)
            .orderType("MARKET")
            .transactionType(transactionType)
            .product("MIS")
            .build();

        String orderId = kiteService.placeOrder(order);
        logger.info("Order placed: {}", orderId);
    }
}
```

### Pattern 2: Position Management

```java
@Service
public class PositionManager {
    private final KiteService kiteService;
    private final FixedTargetAndStopLossStrategy tsl;

    public void managePositions() {
        try {
            Positions positions = kiteService.getPositions();

            for (Position pos : positions.net) {
                if (pos.quantity == 0) continue;

                double entryPrice = pos.averagePrice;
                double currentPrice = pos.lastPrice;
                double pnl = pos.pnl;

                String transactionType = pos.quantity > 0 ? "BUY" : "SELL";

                // Calculate exit levels
                double target = tsl.calculateTarget(entryPrice, transactionType);
                double stopLoss = tsl.calculateStopLoss(entryPrice, transactionType);

                // Check if target or stop-loss hit
                if ((transactionType.equals("BUY") && currentPrice >= target) ||
                    (transactionType.equals("SELL") && currentPrice <= target)) {
                    // Target hit - exit position
                    exitPosition(pos);
                    logger.info("Target hit for {}, PnL: {}", pos.tradingSymbol, pnl);
                }

                if ((transactionType.equals("BUY") && currentPrice <= stopLoss) ||
                    (transactionType.equals("SELL") && currentPrice >= stopLoss)) {
                    // Stop-loss hit - exit position
                    exitPosition(pos);
                    logger.info("Stop-loss hit for {}, PnL: {}", pos.tradingSymbol, pnl);
                }
            }

        } catch (Exception e) {
            logger.error("Position management failed", e);
        }
    }

    private void exitPosition(Position pos) throws Exception {
        IndexOrderRequest exitOrder = IndexOrderRequest.builder()
            .symbol(pos.tradingSymbol)
            .quantity(Math.abs(pos.quantity))
            .orderType("MARKET")
            .transactionType(pos.quantity > 0 ? "SELL" : "BUY")
            .product(pos.product)
            .build();

        kiteService.placeOrder(exitOrder);
    }
}
```

### Pattern 3: Real-time Strategy Execution

```java
@Service
public class TickBasedStrategy {
    private final KiteWebSocket webSocket;
    private final KiteService kiteService;
    private Map<Long, Double> emaValues = new ConcurrentHashMap<>();

    public void start() {
        KiteTicker ticker = new KiteTicker(accessToken, apiKey);
        webSocket = new KiteWebSocket(ticker);

        ticker.setOnTickerArrivalListener(ticks -> {
            for (Tick tick : ticks) {
                processTickStrategy(tick);
            }
        });

        webSocket.connect();
        webSocket.subscribe(Arrays.asList(256265L)); // NIFTY
    }

    private void processTickStrategy(Tick tick) {
        Long token = tick.getInstrumentToken();
        double ltp = tick.getLastTradedPrice();

        // Update EMA (simplified - use proper EMA calculation)
        double previousEMA = emaValues.getOrDefault(token, ltp);
        double newEMA = (ltp * 0.1) + (previousEMA * 0.9);
        emaValues.put(token, newEMA);

        // Generate signal
        if (ltp > newEMA * 1.001) { // 0.1% above EMA
            // Buy signal
            placeTick Order("BUY");
        }
    }
}
```

## Error Handling

### Kite API Exceptions

```java
try {
    kiteService.placeOrder(order);
} catch (KiteException e) {
    switch (e.code) {
        case 400:
            logger.error("Bad request: {}", e.message);
            break;
        case 403:
            logger.error("Forbidden: Invalid credentials");
            break;
        case 429:
            logger.error("Rate limit exceeded");
            // Implement retry with backoff
            break;
        case 500:
            logger.error("Kite server error");
            break;
        default:
            logger.error("Kite error {}: {}", e.code, e.message);
    }
} catch (IOException e) {
    logger.error("Network error", e);
    // Retry logic
}
```

### Retry Logic

```java
@Retryable(
    value = {IOException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000)
)
public String placeOrderWithRetry(OrderRequest order) throws Exception {
    return kiteService.placeOrder(order);
}
```

## Best Practices

1. **Access Token Management**:
   - Tokens expire daily
   - Implement automatic token refresh
   - Securely store credentials

2. **Rate Limiting**:
   - Respect API rate limits (3 requests/second)
   - Implement request throttling
   - Use WebSocket for real-time data (not polling)

3. **Error Handling**:
   - Always catch `KiteException` and `IOException`
   - Log error codes for debugging
   - Implement retry logic for transient failures

4. **Instrument Cache**:
   - Initialize once at startup
   - Refresh daily (instruments change)
   - Use for all symbol-to-token conversions

5. **WebSocket Management**:
   - Implement reconnection logic
   - Handle disconnect gracefully
   - Unsubscribe before disconnect

6. **Order Validation**:
   - Verify market hours before placing orders
   - Validate lot sizes (NIFTY=50, BANKNIFTY=15)
   - Check margin availability

## Thread Safety

- **KiteService**: Not thread-safe, use synchronization or separate instances
- **InstrumentCache**: Thread-safe after initialization
- **WebSocket**: Use single instance, callbacks execute on ticker thread

## Security

- **Never commit** API keys/secrets to version control
- Use **environment variables** or secure vaults
- **Rotate access tokens** regularly
- **Monitor** API usage for anomalies
