# Shoonya vs Zerodha: API Comparison for Algo Trading

**Date:** 2026-03-31

---

## Cost Comparison

### Brokerage

| | Zerodha (Kite Connect) | Shoonya (Finvasia) |
|--|----------------------|-------------------|
| **Options brokerage** | Rs 20 per executed order | Rs 5 + GST (~Rs 5.90) per executed order |
| **Equity delivery** | Rs 0 | Rs 5 + GST |
| **Equity intraday** | Rs 20 per order | Rs 5 + GST |
| **Charging model** | Flat per order (not per lot) | Flat per order (not per lot) |

### Statutory Charges (Identical Across Brokers)

| Charge | Rate | Applies To |
|--------|------|-----------|
| STT | 0.1% (rising to 0.15% from Apr 2026) | Sell-side option premium |
| Exchange transaction | ~0.035% (NSE) | Both sides |
| Stamp duty | 0.003% | Buy side |
| SEBI charges | Rs 10/crore | Both sides |
| GST | 18% on brokerage + txn charges | Both sides |

### Cost Per Round Trip (1 lot Nifty options, Rs 200 premium)

| Component | Zerodha | Shoonya | Savings |
|-----------|---------|---------|---------|
| Brokerage (buy + sell) | Rs 40.00 | Rs 11.80 | Rs 28.20 |
| STT | Rs 6.25 | Rs 6.25 | Rs 0 |
| Exchange txn | Rs 3.94 | Rs 3.94 | Rs 0 |
| Stamp duty | Rs 0.15 | Rs 0.15 | Rs 0 |
| GST | Rs 8.03 | Rs 2.88 | Rs 5.15 |
| **Total** | **~Rs 58** | **~Rs 25** | **~Rs 33** |

### Monthly Cost (100 round trips)

| | Zerodha | Shoonya |
|--|---------|---------|
| Brokerage | Rs 4,000 | Rs 1,180 |
| API subscription | Rs 500/month (with market data) | Rs 0 (free) |
| AMC | Rs 0 | Rs 0 |
| **Total fixed costs** | **Rs 4,500** | **Rs 1,180** |
| **Annual savings** | -- | **Rs 39,840** |

### Multi-Lot Impact

Brokerage is per order, not per lot. Placing 1 order for 5 lots:
- Zerodha: Rs 20 brokerage (same as 1 lot)
- Shoonya: Rs 5.90 brokerage (same as 1 lot)

The per-lot saving is most impactful for strategies with many entries/exits.

---

## API Performance

### Rate Limits

| Metric | Zerodha | Shoonya |
|--------|---------|---------|
| **Orders per second** | 3 OPS (strictest) | 20 OPS (most liberal) |
| **General requests/sec** | 10 req/sec | 20 req/sec |
| **Orders per minute** | ~180 | 200 |
| **Daily order limit** | Not published | Not published |

Shoonya's 20 OPS limit is 6.7x more liberal than Zerodha's 3 OPS for orders. This matters for multi-strategy setups placing orders simultaneously.

### Latency

| Metric | Zerodha | Shoonya |
|--------|---------|---------|
| **Order placement** | ~50-100ms (reported) | ~50-150ms (reported) |
| **WebSocket tick delivery** | Sub-100ms | Sub-100ms |
| **Historical data** | Fast (dedicated infrastructure) | Slower (less infrastructure) |

Both are adequate for options algo trading where tick-level latency (<1ms) is not critical.

### Uptime and Reliability

| Metric | Zerodha | Shoonya |
|--------|---------|---------|
| **Infrastructure** | Largest Indian broker, dedicated data centers | Smaller broker, adequate infrastructure |
| **Known outages** | Rare, but documented incidents during high volatility | Fewer users = fewer reports, but incidents exist |
| **WebSocket stability** | Very stable, auto-reconnect | Stable, single connection limit |
| **Session expiry** | End of day | End of day |

---

## API Design and Developer Experience

### Authentication

| Aspect | Zerodha | Shoonya |
|--------|---------|---------|
| **Auth type** | OAuth 2.0 (redirect-based) | Direct login (SHA-256 password + TOTP) |
| **Token acquisition** | Browser redirect → requestToken → API call → accessToken | Single API call with credentials |
| **2FA** | Not required for API | TOTP required (factor2 field) |
| **Session restore** | Re-login daily | Can restore with stored susertoken |
| **Automation ease** | Harder (needs browser for initial token) | Easier (fully programmatic with TOTP secret) |

Shoonya's auth is simpler for fully automated systems -- no browser redirect needed. Zerodha requires a manual login step each day to get the requestToken.

### Request/Response Format

| Aspect | Zerodha | Shoonya |
|--------|---------|---------|
| **Request format** | Standard REST (JSON body / query params) | `jData={json}&jKey={token}` (form-encoded) |
| **Response format** | JSON | JSON |
| **HTTP methods** | GET/POST/PUT/DELETE | POST only (all endpoints) |
| **Error format** | HTTP status codes + JSON body | Always 200 OK, check `stat: "Not_Ok"` in body |
| **Error detail** | Structured error codes | Free-text `emsg` field |

Zerodha follows REST conventions more closely. Shoonya uses POST-only with a custom `jData/jKey` encoding -- non-standard but simple to implement.

### SDKs and Language Support

| Language | Zerodha | Shoonya |
|----------|---------|---------|
| **Python** | Official (`kiteconnect`) | Official (`NorenRestApiPy`) |
| **Java** | Official (`javakiteconnect`) | None (REST API only) |
| **Node.js** | Community | Official |
| **.NET/C#** | Official | Official |
| **Go** | Official | None |
| **PHP** | Official | None |

Zerodha has a mature official Java SDK. Shoonya has no Java SDK -- requires building an HTTP client wrapper.

### Documentation Quality

| Aspect | Zerodha | Shoonya |
|--------|---------|---------|
| **API docs** | Comprehensive, well-structured (kite.trade/docs) | Adequate, some gaps (GitHub README-based) |
| **Code examples** | Extensive, per-endpoint | Python-focused, fewer examples |
| **Community** | Large (forums, Stack Overflow) | Smaller but active |
| **Changelog** | Versioned, documented | Less formal |

### Instrument Data

| Aspect | Zerodha | Shoonya |
|--------|---------|---------|
| **Source** | API call `getInstruments()` (authenticated) | CSV download (no auth needed) |
| **Format** | JSON array | Zipped CSV files per exchange |
| **Update frequency** | Daily | Daily |
| **Symbol format** | `NIFTY2542217000CE` | `NIFTY22APR25C17000` |
| **Lot size** | In instrument data | In CSV (LotSize column) |
| **Expiry** | In instrument data | In CSV (Expiry column, DD-MMM-YYYY) |

Different symbol formats -- a key mapping concern when switching brokers.

---

## User Grievances and Known Issues

### Zerodha

| Issue | Severity | Details |
|-------|----------|---------|
| **3 OPS order rate limit** | Medium | Strictest among brokers; bottleneck for multi-strategy systems |
| **Rs 500/month API fee** | Low | Required for market data access; adds up for small accounts |
| **OAuth complexity** | Medium | Daily browser login required; hard to fully automate |
| **No bracket orders via API** | Low | BO/CO discontinued for API users |
| **GTT limitations** | Low | GTT orders not available via API |
| **Contract note discrepancies** | Rare | Occasional reports of charge mismatches |

### Shoonya

| Issue | Severity | Details |
|-------|----------|---------|
| **Brokerage increase (Dec 2024)** | High | Changed from Rs 0 to Rs 5/order; broke trust with zero-brokerage users |
| **Single WebSocket connection** | Medium | Only 1 WebSocket allowed; limits multi-process architectures |
| **No official Java SDK** | Medium | Must build own HTTP client; maintenance burden |
| **Thinner documentation** | Medium | Some endpoints underdocumented; Python SDK is the de facto reference |
| **Smaller user base** | Low | Fewer community resources, Stack Overflow answers |
| **Proposed API fee (cancelled)** | Resolved | Rs 1,999/month API fee was proposed then cancelled after backlash |
| **Symbol encoding issues** | Low | Symbols with `&` (e.g., M&M) require careful URL encoding |
| **Session management** | Low | No explicit token refresh; must re-login on session expiry |

### Common to Both

| Issue | Details |
|-------|---------|
| **SEBI static IP requirement (Apr 2026)** | All API orders must come from whitelisted static IPs |
| **10 OPS unregistered algo limit** | SEBI mandates formal algo registration above 10 OPS |
| **STT increase (Apr 2026)** | Options STT rising from 0.1% to 0.15% on sell side |

---

## Feature Comparison Summary

| Feature | Zerodha | Shoonya | Winner |
|---------|---------|---------|--------|
| **Brokerage cost** | Rs 20/order | Rs 5.90/order | Shoonya |
| **API fee** | Rs 500/month | Free | Shoonya |
| **Order rate limit** | 3 OPS | 20 OPS | Shoonya |
| **Java SDK** | Official | None | Zerodha |
| **Documentation** | Excellent | Adequate | Zerodha |
| **Auth simplicity** | OAuth (browser needed) | TOTP (fully programmatic) | Shoonya |
| **REST API design** | Standard REST | POST-only, jData/jKey | Zerodha |
| **WebSocket** | Multiple connections | Single connection | Zerodha |
| **Infrastructure reliability** | Tier 1 | Tier 2 | Zerodha |
| **Community support** | Large | Small | Zerodha |
| **Instrument data** | JSON API | CSV download | Tie |
| **Historical data** | Comprehensive | Adequate | Zerodha |

---

## Recommendation

**Run both brokers in parallel:**
- Use Zerodha for WebSocket (tick data) and historical data (better infrastructure)
- Use Shoonya for order execution (lower cost, higher rate limits, simpler auth)
- The `ITMResolver` interface in `fno-models` makes this broker-agnostic at the strategy level

**Cost savings with Shoonya order execution:**
- At 100 trades/month: Rs 3,320/month saved (Rs 39,840/year)
- At 200 trades/month: Rs 6,640/month saved (Rs 79,680/year)
- Plus Rs 500/month API fee savings from Zerodha data access (if not needed)

**Risk mitigation:**
- Keep Zerodha as fallback for order execution
- Shoonya's brokerage has changed before (0 → 5); may change again
- Dual-broker setup provides resilience against either broker's API outages
