# FnOSdk Public API

Enumerates FnOSdk's publicly-consumed surface. A "public" type/method is
one imported by any external consumer (currently `OptionsAnalyzerV2` in
the sibling repo).

## Stability markers

| Marker | Meaning |
|--------|---------|
| `stable` | Supported. Breaking changes require a major version bump + ADR. |
| `experimental` | API may change without a major version bump. Consumers should pin a version or wrap. |
| `deprecated` | Scheduled for removal. Not for new code; existing callers migrate. |

Default is `stable` unless otherwise noted.

## Versioning

Current SDK version: **`1.0.0-SNAPSHOT`** (declared in root `pom.xml`).
When the version changes, re-verify every `@since` entry below.

## Public surface — best-effort inventory (2026-04-20)

### fno-models (`com.vish.fno.model.*`)

| Type | @since | Stability | Notes |
|------|--------|-----------|-------|
| `Ticker` | 1.0 | stable | Hot-path tick record |
| `Candle` | 1.0 | stable | OHLC bar |
| `Task` | 1.0 | stable | Strategy task binding |
| `SymbolData` | 1.0 | stable | Per-symbol candle container |
| `Exchange`, `InstrumentType`, `PositionType` | 1.0 | stable | Order classification enums |
| `order.activeorder.ActiveOrder` / `AbstractActiveOrder` / `OptionBasedActiveOrder` | 1.0 | stable | ⚠ `extraData : Map<String,String>` is `HashMap` — **not thread-safe under concurrent writes**. Documented in consumer `sdk-surface.md`. |
| `order.orderrequest.*` (`OrderRequest`, `TickBasedOrderRequest`, `IndexOrderRequest`, `OptionBasedOrderRequest`, `MultiTargetOrderRequest`, `MultiTargetTickOrderRequest`) | 1.0 | stable | Strategy-emitted intent records |
| `order.Target` | 1.0 | stable | Multi-target split record |
| `cache.OrderCache` | 1.0 | stable | Thread-safe; hot-path ~520/sec. See canonical `cache-and-state.md`. |
| `helper.OrderFlowHandler` | 1.0 | stable | Tick→strategy→order interface |
| `strategy.TickBasedStrategy` | 1.0 | stable | Strategy contract |

### fno-utils (`com.vish.fno.util.*`)

| Type | @since | Stability | Notes |
|------|--------|-----------|-------|
| `PriceUtils`, `CandleUtils`, `FileUtils`, `JsonUtils`, `TimeUtils`, `TimeFrameUtils` | 1.0 | stable | Static utility facades |
| `FnoConstants` | 1.0 | stable | `TOTAL_TRADING_MINUTES` etc. |
| `time.TimeSource` | 1.0 | stable | Clock abstraction (prod/backtest) |
| `candle.store.CandleStore` / `CandleStoreImpl` | 1.0 | stable | Dual-path cache — see canonical `cache-and-state.md` |
| `position.LotSizeProvider` | 1.0 | stable | `Integer getLotSize(String symbol)` — null if not found |
| `position.PositionSizingService` | 1.0 | stable | Capital + lot size → qty |
| `tick.TickStore` | 1.0 | stable | Latest-ticker store (thread-safe) |

### fno-technicals (`com.vish.fno.technical.*`)

| Type | @since | Stability | Notes |
|------|--------|-----------|-------|
| `SimpleMovingAverage`, `ExponentialMovingAverage`, `RSI`, `BollingerBands` | 1.0 | stable | Indicator calcs (stateful per-symbol) |
| `BlackScholes` / `Greeks` | 1.0 | stable | Expiry-day Greeks |
| `AbstractIndicator` | 1.0 | stable | Extension point — override `calculateFromClosedPrice(List<Double>)` |

### fno-kite-reader (`com.vish.fno.reader.*`)

| Type | @since | Stability | Notes |
|------|--------|-----------|-------|
| `core.KiteService` | 1.0 | stable | Facade — orders, historical, lot size |
| `core.KiteSession`, `KiteOrderExecutor`, `KiteWebSocket` | 1.0 | stable | Used internally by `KiteService`; direct use is [VERIFY] |
| `core.InstrumentCache` / `InstrumentSummary` | 1.0 | stable | Daily Kite instrument dump |
| `core.HistoricalDataProvider` | 1.0 | stable | 3 req/sec rate-limited; 30s dedup |
| `core.HolidayCalendar` | 1.0 | stable | NSE market holiday dates |
| `util.TickMapper` | 1.0 | stable | Kite `Tick` → `Ticker` conversion |

### fno-shoonya-reader (`com.vish.fno.reader.shoonya.*`)

| Type | @since | Stability | Notes |
|------|--------|-----------|-------|
| `core.ShoonyaService`, `ShoonyaITMResolver`, `ShoonyaSession`, `ShoonyaOrderExecutor`, `ShoonyaInstrumentCache` | 1.0 | **experimental** | **Not wired into prod** as of 2026-04-20 (confirmed via `OptionsAnalyzerV2` `external.md`). Pending work: market-price protection at application level (see `docs/plans/2026-04-01-shoonya-market-price-protection.md`). |

### fno-strategy-utils (`com.vish.fno.strategy.*`)

| Type | @since | Stability | Notes |
|------|--------|-----------|-------|
| `cpr.CPRUtils` | 1.0 | stable | CPR-level calculations |
| `pcr.PCRUtils` | 1.0 | stable | PCR-signal utilities |
| `orderflow.TargetAndStopLossStrategy` (and subclasses) | 1.0 | stable | TP/SL policies |
| `trend.*`, `priceaction.*` | 1.0 | stable | Pre-built strategy helpers |

### fno-phase-analyzer (`com.vish.fno.phase.*`)

| Type | @since | Stability | Notes |
|------|--------|-----------|-------|
| `WyckoffPhaseIdentifier`, `CompositeWyckoffPhaseIdentifier` | 1.0 | stable | Wyckoff regime classifier |

## Deprecated

*(none yet — log entries here when public types are deprecated)*

## Change protocol

Every addition / removal / rename / contract change above requires:

1. A new ADR in `../../OptionsAnalyzerV2/docs/adr/` describing motivation
   and consumer impact.
2. A version bump in the affected module's `pom.xml`.
3. Updating this file — add / mark deprecated / remove the entry.

## Automation recommendation

Consider adding a Java public-API diff tool to the build to catch surface
changes automatically:

- **`japicmp-maven-plugin`**: compares compiled JARs between versions.
  Integrates into the `verify` phase.
- **`revapi`** (RedHat): richer semantic checks, larger footprint.

Neither is installed this session — propose a PR if desired. A lightweight
first step is committing a Javadoc-jar under `docs/api/` per release and
diffing manually.

## ⚠ Needs completion

This is a **best-effort first pass**. Before using this file for
authoritative release decisions, fill:

- Exact `@since` versions per type (all currently `1.0`; verify via
  `git log --follow <file>` or release notes).
- Per-method signatures for `KiteService`, `InstrumentCache`,
  `HistoricalDataProvider` (currently only type-level).
- Flag any `com.vish.fno.*.internal` packages incorrectly exposed — those
  should not appear in this document.
- `KiteSession` / `KiteOrderExecutor` / `KiteWebSocket` — confirm whether
  direct use by consumers is supported or if they are implementation
  details of `KiteService`.
