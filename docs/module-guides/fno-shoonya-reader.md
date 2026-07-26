# fno-shoonya-reader Module Guide

**Package:** `com.vish.fno.reader.shoonya`
**Dependencies:** fno-utils (transitively includes fno-models)
**External Dependencies:** None (uses `java.net.http.HttpClient`)
**Status:** Disabled by default (`placeOrders=false`)

---

## Overview

Shoonya (Finvasia) broker integration using raw HTTP client against the NorenRestApi. Provides order execution, instrument caching, and ITM/OTM resolution -- parallel to fno-kite-reader (Zerodha).

No WebSocket or historical data support (can be added later).

---

## Package Overview

| Package | Key Classes |
|---------|-------------|
| `com.vish.fno.reader.shoonya.core` | ShoonyaService (facade), ShoonyaITMResolver, ShoonyaSession, ShoonyaOrderExecutor, ShoonyaInstrumentCache |
| `com.vish.fno.reader.shoonya.model` | ShoonyaOpenOrder, ShoonyaInstrument, ShoonyaOrder |
| `com.vish.fno.reader.shoonya.util` | ShoonyaHttpClient, ShoonyaOptionPriceUtils, ShoonyaInstrumentFileUtils |
| `com.vish.fno.reader.shoonya.exception` | ShoonyaApiException |

---

## Public API

### ShoonyaService (Public Facade)

Main entry point. Composes all internal components.

```java
import com.vish.fno.reader.shoonya.core.ShoonyaService;

ShoonyaService shoonya = new ShoonyaService(
    "FA12345",              // userId
    "password",             // password (plain text, SHA-256 hashed internally)
    "FNV123",               // vendorCode
    "api_secret",           // apiSecret
    "TOTP_BASE32_SECRET",   // totpSecret (for RFC 6238 TOTP generation)
    "unique-device-id",     // imei (unique device identifier)
    nifty100Symbols,        // List<String> of tracked symbols
    false                   // placeOrders (disabled by default)
);
shoonya.authenticate();
```

**Auth Methods:**

| Method | Return | Description |
|--------|--------|-------------|
| `authenticate()` | `void` | Login via `/QuickAuth` with SHA-256 pwd + TOTP |
| `isInitialised()` | `boolean` | Check if session is active |

**Order Methods:**

| Method | Return | Description |
|--------|--------|-------------|
| `buyOrder(symbol, size, tag, isPlace)` | `Optional<ShoonyaOpenOrder>` | Place market buy order |
| `sellOrder(symbol, size, tag, isPlace)` | `Optional<ShoonyaOpenOrder>` | Place market sell order |
| `getOrders()` | `List<ShoonyaOrder>` | Fetch order book |

**Instrument Methods:**

| Method | Return | Description |
|--------|--------|-------------|
| `getITMStock(index, price, isCall)` | `String` | **Nearest ITM** symbol — floor/ceiling relative to spot, takes no offset |
| `getOTMStock(index, price, isCall)` | `String` | **Nearest OTM** symbol — floor/ceiling relative to spot, takes no offset |
| `getOptionStock(index, price, isCall, policy)` | `String` | ⚠️ **Does NOT honour `StrikePolicy`** — same collapse as Kite (ADR 0062): `ITM_1, ITM_2 → getITMStock` · `OTM_1, OTM_2 → getOTMStock` · `ATM → getITMStock`. So `ATM` resolves one strike in-the-money and `ITM_2`/`OTM_2` are unreachable. Unlike `KiteService`, there is **no shadow logging and no offset-aware resolver** in this module yet |
| `getInstrument(symbol)` | `Optional<Long>` | Symbol to token lookup |
| `getSymbol(token)` | `String` | Token to symbol lookup |
| `getAllOptionSymbols(indexSymbol)` | `List<String>` | All CE+PE for nearest expiry |
| `getLotSizeFromFuture(indexName)` | `Optional<Integer>` | Lot size from future contract |
| `getAllFutureLotSizeInfo()` | `Map<String,Integer>` | All indices lot sizes |
| `isExpiryDayForOption(symbol, date)` | `boolean` | Check option expiry |
| `isExpiryDayForIndex(name, date)` | `boolean` | Check index expiry |

**Nearest-expiry selection (fixed 2026-06-16):** `ShoonyaOptionPriceUtils` selects the nearest
expiry **chronologically**, by parsing the contract-master expiry string as a `LocalDate`
(`dd-MMM-yyyy`, case-insensitive). It previously sorted the raw strings lexicographically, which
across month boundaries could pick a far expiry as "nearest" (`"28-APR-2026"` sorts before
`"28-DEC-2025"`) on the live trading path. Unparseable expiry strings sort as `LocalDate.MAX`, so
a malformed contract-master row can never be selected as nearest. Affects `getITMStock`,
`getOTMStock`, `getOptionStock`, and `getAllOptionSymbols`. (Kite was never affected — it groups
on parsed `java.util.Date`.)

### ShoonyaITMResolver

Implements `ITMResolver` from `fno-models`. Enables broker-agnostic strategy execution.

```java
import com.vish.fno.reader.shoonya.core.ShoonyaITMResolver;
import com.vish.fno.model.helper.ITMResolver;

// Switching brokers is one line:
ITMResolver resolver = new ShoonyaITMResolver(shoonyaService);
// vs: ITMResolver resolver = new KiteITMResolver(kiteService);

// Strategy code unchanged
resolver.resolveITMSymbol("NIFTY 50", 22500.0, true);
```

---

## Authentication Flow

```
ShoonyaService.authenticate()
  └─ ShoonyaSession.authenticate()
       ├─ Compute appKey = SHA-256(userId + "|" + apiSecret)
       ├─ Compute pwd = SHA-256(password) (done at construction)
       ├─ Generate TOTP from base32 secret (RFC 6238, HMAC-SHA1, 30s step, 6 digits)
       ├─ POST /QuickAuth with jData={uid, pwd, factor2, vc, appkey, imei, source="API"}
       ├─ Store susertoken as sessionToken
       └─ Set initialised = true
```

No browser redirect needed (unlike Zerodha OAuth). Fully programmatic with TOTP secret.

---

## Order Execution Flow

```
ShoonyaService.buyOrder(symbol, size, tag, isPlaceOrder)
  └─ ShoonyaOrderExecutor.buyOrder(...)
       └─ placeOrder(..., "B", ...)
            ├─ Check isPlaceOrder flag → return test order if false
            ├─ Check session.isPlaceOrders() → return test order if false
            ├─ Check session.isInitialised() → return error if false
            ├─ Resolve exchange via instrumentCache.getExchangeForSymbol(symbol)
            ├─ Build payload: trantype=B, prd=I (MIS), prctyp=MKT, prc=0, ret=DAY
            ├─ Execute with lock: POST /PlaceOrder with jData + jKey
            └─ Return ShoonyaOpenOrder(orderId, true, null) on success
```

---

## Instrument Caching

Downloads contract master CSVs (no auth required):
- `https://api.shoonya.com/NFO_symbols.txt.zip` -- NSE F&O
- `https://api.shoonya.com/NSE_symbols.txt.zip` -- NSE Cash
- `https://api.shoonya.com/BFO_symbols.txt.zip` -- BSE F&O
- `https://api.shoonya.com/BSE_symbols.txt.zip` -- BSE Cash

Parses CSV columns:
- NFO/BFO (10 columns): Exchange, Token, LotSize, Symbol, TradingSymbol, Expiry, Instrument, OptionType, StrikePrice, TickSize
- NSE/BSE (7 columns): Exchange, Token, LotSize, Symbol, TradingSymbol, Instrument, TickSize

Filters by Nifty 100 symbols list (same as fno-kite-reader). Lazy initialization with double-checked locking.

---

## Zerodha vs Shoonya Mapping

| Concept | Zerodha (Kite) | Shoonya (Noren) |
|---------|---------------|-----------------|
| Product (intraday) | `MIS` | `I` |
| Transaction buy | `BUY` | `B` |
| Transaction sell | `SELL` | `S` |
| Order type market | `MARKET` | `MKT` |
| Request format | Standard REST | `jData={json}&jKey={token}` |
| Auth | OAuth (browser redirect) | SHA-256 pwd + TOTP (programmatic) |
| Session token | accessToken | susertoken (jKey) |
| Instruments | API call (authenticated) | CSV download (no auth) |
| Symbol format | `NIFTY2542217000CE` | `NIFTY22APR25C17000` |

---

## Model Records

### ShoonyaOpenOrder

```java
public record ShoonyaOpenOrder(
    String orderId,        // norenordno, null if failed
    boolean isOrderPlaced, // true on success or test mode
    String errorMessage    // emsg from API, null on success
) {}
```

### ShoonyaInstrument

```java
public record ShoonyaInstrument(
    String exchange, long token, int lotSize, String symbol,
    String tradingSymbol, String expiry, String instrumentType,
    String optionType, double strikePrice, double tickSize
) {}
```

### ShoonyaOrder

```java
public record ShoonyaOrder(
    String orderId, String exchange, String tradingSymbol,
    String status, String transactionType, int quantity,
    int filledQuantity, double averagePrice, String remarks
) {}
```

---

## Thread Safety

| Component | Thread-Safe | Notes |
|-----------|-------------|-------|
| ShoonyaHttpClient | Yes | `HttpClient` is thread-safe; stateless |
| ShoonyaSession | Yes | `volatile sessionToken/initialised` + fair `ReentrantLock` (12s timeout) |
| ShoonyaInstrumentCache | Yes | `volatile CacheData` + `ReentrantLock` (double-checked locking) |
| ShoonyaOrderExecutor | Yes | Stateless; delegates locking to session |
| ShoonyaService | Yes | Stateless facade; delegates to thread-safe components |
| ShoonyaITMResolver | Yes | Stateless; delegates to ShoonyaService |
| All model records | Yes | Immutable |
| ShoonyaOptionPriceUtils | Yes | Static, stateless |
| ShoonyaInstrumentFileUtils | Yes | Static, stateless |

---

## Scope Limitations (Current)

| Feature | Status | Notes |
|---------|--------|-------|
| Order execution (market) | Implemented | Buy/sell via `/PlaceOrder` |
| Instrument caching | Implemented | CSV download + parse |
| ITM/OTM resolution | Implemented | Via `ShoonyaOptionPriceUtils` |
| WebSocket (live ticks) | Not implemented | Can be added later |
| Historical data | Not implemented | Can be added later |
| Bracket/cover orders | Not implemented | Only market orders |
| Order modification | Not implemented | Can be added later |
| Order cancellation | Not implemented | Can be added later |
