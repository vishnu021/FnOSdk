# fno-shoonya-reader Module Design

**Date:** 2026-03-31
**Status:** Approved
**Scope:** Orders + Instruments + ITMResolver (no WebSocket, no historical data)

---

## Goal

Add a Shoonya (Finvasia) broker integration module parallel to `fno-kite-reader`, using raw HTTP client against the NorenRestApi. Disabled by default. Enables switching brokers without changing strategy code.

---

## Architecture

### Module Structure

```
fno-shoonya-reader/
├── pom.xml
└── src/main/java/com/vish/fno/reader/shoonya/
    ├── core/
    │   ├── ShoonyaService.java              (public facade)
    │   ├── ShoonyaSession.java              (auth + rate-limited execution)
    │   ├── ShoonyaOrderExecutor.java        (buy/sell market orders)
    │   ├── ShoonyaInstrumentCache.java      (CSV download, symbol/token lookup)
    │   └── ShoonyaITMResolver.java          (implements ITMResolver)
    ├── model/
    │   ├── ShoonyaOpenOrder.java            (record: order result)
    │   ├── ShoonyaInstrument.java           (record: parsed CSV row)
    │   └── ShoonyaOrder.java               (record: order book entry)
    ├── util/
    │   ├── ShoonyaHttpClient.java           (HTTP wrapper for jData/jKey format)
    │   ├── ShoonyaOptionPriceUtils.java     (strike resolution)
    │   └── ShoonyaInstrumentFileUtils.java  (CSV download + cache)
    └── exception/
        └── ShoonyaApiException.java         (API error wrapper)
```

### Dependencies

```xml
<dependencies>
    <dependency>
        <groupId>com.vish.fno</groupId>
        <artifactId>fno-utils</artifactId>
        <version>${project.version}</version>
    </dependency>
    <!-- No external broker SDK - uses java.net.http.HttpClient -->
</dependencies>
```

No new external dependencies. Uses only JDK 21 `java.net.http.HttpClient`.

---

## API Integration Details

### Base URL

`https://api.shoonya.com/NorenWClientTP/`

### Request Format

All endpoints are POST with `application/x-www-form-urlencoded`:
```
jData={"field":"value"}&jKey=<session_token>
```

Login uses only `jData` (no `jKey`).

### Authentication

**Endpoint:** `POST /QuickAuth`

| Field | Value |
|-------|-------|
| `uid` | User ID |
| `pwd` | SHA-256 of password |
| `factor2` | TOTP (RFC 6238) from secret |
| `vc` | Vendor code |
| `appkey` | SHA-256 of `"{userid}\|{api_secret}"` |
| `imei` | Unique device identifier |
| `source` | `"API"` |

Returns `susertoken` used as `jKey` for all subsequent calls.

### Place Order

**Endpoint:** `POST /PlaceOrder`

| Field | Value |
|-------|-------|
| `uid` | User ID |
| `actid` | Account ID (same as uid) |
| `trantype` | `"B"` (buy) / `"S"` (sell) |
| `prd` | `"I"` (MIS/intraday) |
| `exch` | `"NFO"` / `"BFO"` / `"NSE"` / `"BSE"` |
| `tsym` | URL-encoded trading symbol |
| `qty` | Quantity as string |
| `prctyp` | `"MKT"` for market orders |
| `prc` | `"0"` for market orders |
| `ret` | `"DAY"` |
| `remarks` | Order tag |
| `ordersource` | `"API"` |

Success response: `{"stat":"Ok","norenordno":"20052000000017"}`

### Order Book

**Endpoint:** `POST /OrderBook`

Returns JSON array of orders with fields: `norenordno`, `exch`, `tsym`, `qty`, `prc`, `status`, `trantype`, `fillshares`, `avgprc`, `remarks`.

### Position Book

**Endpoint:** `POST /PositionBook`

Returns JSON array with: `exch`, `tsym`, `netqty`, `daybuyqty`, `daysellqty`, `rpnl`, `urmtom`.

### Instrument Master

Downloaded as CSV (no auth required):
- `https://api.shoonya.com/NFO_symbols.txt.zip`
- `https://api.shoonya.com/NSE_symbols.txt.zip`
- `https://api.shoonya.com/BFO_symbols.txt.zip`
- `https://api.shoonya.com/BSE_symbols.txt.zip`

**NFO CSV columns:** Exchange, Token, LotSize, Symbol, TradingSymbol, Expiry, Instrument, OptionType, StrikePrice, TickSize

**Symbol format:** `NIFTY28APR26C17200` (different from Zerodha's format)

---

## Component Design

### ShoonyaHttpClient (util)

Thin wrapper around `java.net.http.HttpClient`:

```java
public class ShoonyaHttpClient {
    private static final String BASE_URL = "https://api.shoonya.com/NorenWClientTP/";
    private final HttpClient httpClient;

    // POST with jData only (login)
    public String post(String endpoint, Map<String, Object> payload);

    // POST with jData + jKey (authenticated)
    public String postAuthenticated(String endpoint, Map<String, Object> payload, String sessionToken);

    // GET for file downloads (instruments)
    public byte[] download(String url);
}
```

- Uses `JsonUtils.createObjectMapper()` for serialization (VT-safe).
- URL-encodes `tsym` values that contain special characters.
- Throws `ShoonyaApiException` on `stat: "Not_Ok"` responses.

### ShoonyaSession (core, package-private)

Mirrors `KiteSession`. Manages auth state and rate-limited execution.

```java
class ShoonyaSession {
    private final String userId;
    private final String passwordHash;      // SHA-256 of password
    private final String vendorCode;
    private final String apiSecret;
    private final String totpSecret;        // RFC 6238 TOTP secret
    private final String imei;
    private final boolean placeOrders;

    private final ShoonyaHttpClient httpClient;
    private final ReentrantLock apiLock;     // Fair mode, 12s timeout
    private volatile String sessionToken;    // susertoken
    private volatile boolean initialised;

    void authenticate();                     // Computes appkey, pwd, TOTP, calls /QuickAuth
    boolean isInitialised();
    boolean isPlaceOrders();
    String getSessionToken();

    <T> T executeWithLock(Supplier<T> action, String operationName);
    <T> T executeWithLockSafe(Supplier<T> action, String operationName, T fallback);
}
```

TOTP generation uses `javax.crypto.Mac` with `HmacSHA1` (RFC 6238 implementation, no external library).

### ShoonyaOrderExecutor (core, package-private)

Mirrors `KiteOrderExecutor`.

```java
class ShoonyaOrderExecutor {
    private final ShoonyaSession session;
    private final ShoonyaInstrumentCache instrumentCache;

    Optional<ShoonyaOpenOrder> buyOrder(String symbol, int orderSize, String tag, boolean isPlaceOrder);
    Optional<ShoonyaOpenOrder> sellOrder(String symbol, int orderSize, String tag, boolean isPlaceOrder);
    List<ShoonyaOrder> getOrders();
    // getPositions() not needed for initial scope
}
```

Order flow:
1. Check `session.isInitialised()` and `session.isPlaceOrders()`
2. Resolve exchange via `instrumentCache.getExchangeForSymbol(symbol)`
3. Build order payload: `trantype=B/S`, `prd=I`, `prctyp=MKT`, `prc=0`, `ret=DAY`
4. Execute via `session.executeWithLock()` calling `httpClient.postAuthenticated("/PlaceOrder", ...)`
5. Parse response, return `ShoonyaOpenOrder`

### ShoonyaInstrumentCache (core, package-private)

Mirrors `InstrumentCache`. Downloads and parses CSV contract master files.

```java
class ShoonyaInstrumentCache {
    private final List<String> nifty100Symbols;
    private final ShoonyaHttpClient httpClient;
    private volatile CacheData cache;
    private final ReentrantLock cacheLock;

    Optional<Long> getInstrument(String symbol);        // symbol -> token
    String getSymbol(long token);                       // token -> symbol
    String getExchangeForSymbol(String symbol);         // symbol -> NFO/BFO/NSE/BSE
    List<ShoonyaInstrument> getInstruments();
    Optional<List<ShoonyaInstrument>> getEarliestExpiryInstruments(String name, String type);
    Optional<Integer> getLotSizeFromFuture(String indexName);
    Map<String, Integer> getAllFutureLotSizeInfo();
    boolean isExpiryDayForOption(String symbol, Date date);
    boolean isExpiryDayForIndex(String indexName, Date date);
    List<String> getAllOptionSymbols(String indexSymbol);

    record CacheData(
        List<ShoonyaInstrument> filteredInstruments,
        Map<String, SymbolInfo> symbolInfoMap,
        Map<Long, String> tokenToSymbolMap
    ) {}

    record SymbolInfo(long token, String exchange) {}
}
```

Downloads `NFO_symbols.txt.zip` and `NSE_symbols.txt.zip` (and BFO/BSE), unzips, parses CSV.
Filters by Nifty 100 + index derivatives (same logic as kite-reader).
Caches to disk via `ShoonyaInstrumentFileUtils`. Double-checked locking with volatile + ReentrantLock.

### ShoonyaService (core, public facade)

Mirrors `KiteService` but scoped to orders + instruments.

```java
public class ShoonyaService {
    private final ShoonyaSession session;
    private final ShoonyaOrderExecutor orderExecutor;
    private final ShoonyaInstrumentCache instrumentCache;

    public ShoonyaService(String userId, String password, String vendorCode,
                          String apiSecret, String totpSecret, String imei,
                          List<String> nifty100Symbols, boolean placeOrders);

    // Auth
    public void authenticate();
    public boolean isInitialised();

    // Orders
    public Optional<ShoonyaOpenOrder> buyOrder(String symbol, int size, String tag, boolean isPlace);
    public Optional<ShoonyaOpenOrder> sellOrder(String symbol, int size, String tag, boolean isPlace);
    public List<ShoonyaOrder> getOrders();

    // Instruments
    public String getITMStock(String index, double price, boolean isCall);
    public String getOTMStock(String index, double price, boolean isCall);
    public String getOptionStock(String index, double price, boolean isCall, StrikePolicy policy);
    public List<String> getAllOptionSymbols(String indexSymbol);
    public Optional<Long> getInstrument(String symbol);
    public String getSymbol(long token);
    public Optional<Integer> getLotSizeFromFuture(String indexName);
    public Map<String, Integer> getAllFutureLotSizeInfo();
    public boolean isExpiryDayForOption(String symbol, Date date);
    public boolean isExpiryDayForIndex(String name, Date date);
}
```

### ShoonyaITMResolver (core, public)

Implements `ITMResolver` from `fno-models`. Wraps `ShoonyaService`.

```java
@RequiredArgsConstructor
public class ShoonyaITMResolver implements ITMResolver {
    private final ShoonyaService shoonyaService;

    @Override
    public String resolveITMSymbol(String index, double price, boolean isCall);

    @Override
    public String resolveOTMSymbol(String index, double price, boolean isCall);

    @Override
    public void prepareSymbols();
}
```

### Model Records

```java
// Order execution result
public record ShoonyaOpenOrder(
    String orderId,             // norenordno
    boolean isOrderPlaced,
    String errorMessage
) {}

// Parsed from CSV contract master
public record ShoonyaInstrument(
    String exchange,
    long token,
    int lotSize,
    String symbol,              // Underlying (e.g., "NIFTY")
    String tradingSymbol,       // Full symbol (e.g., "NIFTY28APR26C17200")
    String expiry,              // "28-APR-2026"
    String instrumentType,      // OPTIDX, FUTIDX, FUTSTK, OPTSTK, EQ, INDEX
    String optionType,          // CE, PE, XX
    double strikePrice,
    double tickSize
) {}

// Order book entry
public record ShoonyaOrder(
    String orderId,             // norenordno
    String exchange,
    String tradingSymbol,
    String status,              // OPEN, COMPLETE, CANCELED, REJECTED
    String transactionType,     // B, S
    int quantity,
    int filledQuantity,
    double averagePrice,
    String remarks
) {}
```

### ShoonyaApiException

```java
public class ShoonyaApiException extends RuntimeException {
    private final String errorMessage;    // from emsg field
}
```

---

## Mapping: Zerodha vs Shoonya

| Concept | Zerodha (Kite) | Shoonya (Noren) |
|---------|---------------|-----------------|
| Product (intraday) | `MIS` | `I` |
| Product (overnight F&O) | `NRML` | `M` |
| Transaction buy | `BUY` | `B` |
| Transaction sell | `SELL` | `S` |
| Order type market | `MARKET` | `MKT` |
| Retention | `DAY` | `DAY` |
| Order variety | `regular` | N/A (no variety concept) |
| Symbol: Nifty CE | `NIFTY2542217000CE` | `NIFTY22APR25C17000` |
| Auth | OAuth (requestToken) | SHA-256 pwd + TOTP |
| Session token | accessToken | susertoken (jKey) |
| Instruments | API call `getInstruments()` | CSV download from URL |

---

## Integration with Existing Code

### Broker-Agnostic Interface

Strategies depend on `ITMResolver` (from `fno-models`), not on any broker-specific class. Switching brokers:

```java
// Zerodha
ITMResolver resolver = new KiteITMResolver(kiteService);

// Shoonya
ITMResolver resolver = new ShoonyaITMResolver(shoonyaService);

// Strategy code unchanged
resolver.resolveITMSymbol("NIFTY", 22500.0, true);
```

### Module Dependency Chain

```
fno-models
    ^
fno-utils
    ^
fno-shoonya-reader  (new, parallel to fno-kite-reader)
```

Both `fno-kite-reader` and `fno-shoonya-reader` depend on `fno-utils` (which transitively includes `fno-models`). Neither depends on the other.

### Parent POM Update

Add `<module>fno-shoonya-reader</module>` to the parent POM's modules list, after `fno-kite-reader`.

---

## Thread Safety

| Component | Strategy |
|-----------|----------|
| ShoonyaSession | `volatile sessionToken` + `volatile initialised` + `ReentrantLock apiLock` (fair, 12s timeout) |
| ShoonyaInstrumentCache | `volatile CacheData` + `ReentrantLock cacheLock` (double-checked locking) |
| ShoonyaHttpClient | Stateless (HttpClient is thread-safe) |
| ShoonyaOrderExecutor | Stateless (delegates locking to session) |
| All records | Immutable |

---

## Disabled by Default

`ShoonyaService` constructor takes `boolean placeOrders`. When `false`:
- Auth still works (for instrument loading)
- `buyOrder()`/`sellOrder()` return test orders without hitting the API
- Same pattern as `KiteService`

---

## Out of Scope

- WebSocket (live tick streaming) -- add later
- Historical data (OHLC candles) -- add later
- Bracket/cover orders -- only market orders for now
- Product conversion -- not needed for intraday options
- Order modification/cancellation -- can be added later

---

## Files to Create

1. `fno-shoonya-reader/pom.xml`
2. `ShoonyaHttpClient.java` (util)
3. `ShoonyaInstrumentFileUtils.java` (util)
4. `ShoonyaOptionPriceUtils.java` (util)
5. `ShoonyaApiException.java` (exception)
6. `ShoonyaOpenOrder.java` (model)
7. `ShoonyaInstrument.java` (model)
8. `ShoonyaOrder.java` (model)
9. `ShoonyaSession.java` (core)
10. `ShoonyaInstrumentCache.java` (core)
11. `ShoonyaOrderExecutor.java` (core)
12. `ShoonyaService.java` (core)
13. `ShoonyaITMResolver.java` (core)
14. Parent `pom.xml` update (add module)
