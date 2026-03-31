# fno-shoonya-reader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Shoonya (Finvasia) broker module for order execution + instrument caching + ITM resolution, parallel to fno-kite-reader, disabled by default.

**Architecture:** New Maven module `fno-shoonya-reader` using raw `java.net.http.HttpClient` against Shoonya's NorenRestApi. Mirrors fno-kite-reader's composition pattern (facade → executor → session) with package-private internals. Implements `ITMResolver` from fno-models for broker-agnostic strategy integration.

**Tech Stack:** Java 21, java.net.http.HttpClient, Jackson (via JsonUtils.createObjectMapper()), Lombok, SLF4J

---

## File Structure

```
fno-shoonya-reader/
├── pom.xml
└── src/
    ├── main/java/com/vish/fno/reader/shoonya/
    │   ├── core/
    │   │   ├── ShoonyaService.java              (public facade, composes all internals)
    │   │   ├── ShoonyaSession.java              (auth + rate-limited HTTP, package-private)
    │   │   ├── ShoonyaOrderExecutor.java         (buy/sell, package-private)
    │   │   ├── ShoonyaInstrumentCache.java       (CSV download+parse, package-private)
    │   │   └── ShoonyaITMResolver.java           (implements ITMResolver, public)
    │   ├── model/
    │   │   ├── ShoonyaOpenOrder.java             (record: order result)
    │   │   ├── ShoonyaInstrument.java            (record: parsed CSV row)
    │   │   └── ShoonyaOrder.java                 (record: order book entry)
    │   ├── util/
    │   │   ├── ShoonyaHttpClient.java            (HTTP wrapper, jData/jKey format)
    │   │   ├── ShoonyaOptionPriceUtils.java      (strike resolution from instruments)
    │   │   └── ShoonyaInstrumentFileUtils.java   (CSV download + local cache)
    │   └── exception/
    │       └── ShoonyaApiException.java          (API error wrapper)
    └── test/java/com/vish/fno/reader/shoonya/
        ├── model/
        │   └── ShoonyaInstrumentTest.java
        ├── util/
        │   ├── ShoonyaHttpClientTest.java
        │   └── ShoonyaOptionPriceUtilsTest.java
        └── core/
            ├── ShoonyaSessionTest.java
            ├── ShoonyaOrderExecutorTest.java
            └── ShoonyaInstrumentCacheTest.java
```

**Modify:**
- `pom.xml` (root) — add `<module>fno-shoonya-reader</module>`

---

### Task 1: Maven Module Scaffold

**Files:**
- Create: `fno-shoonya-reader/pom.xml`
- Modify: `pom.xml` (root, line 18 — add module after fno-kite-reader)

- [ ] **Step 1: Create fno-shoonya-reader/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <artifactId>FnOSdk</artifactId>
        <groupId>com.vish.fno</groupId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <modelVersion>4.0.0</modelVersion>
    <artifactId>fno-shoonya-reader</artifactId>
    <name>fno-shoonya-reader</name>
    <description>Shoonya (Finvasia) NorenRestApi integration for order execution</description>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.vish.fno</groupId>
            <artifactId>fno-utils</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Add module to parent pom.xml**

In `pom.xml` (root), add `<module>fno-shoonya-reader</module>` after the `fno-kite-reader` line:

```xml
<modules>
    <module>fno-models</module>
    <module>fno-utils</module>
    <module>fno-technicals</module>
    <module>fno-kite-reader</module>
    <module>fno-shoonya-reader</module>
    <module>fno-strategy-utils</module>
    <module>fno-phase-analyzer</module>
</modules>
```

- [ ] **Step 3: Create source directories**

Run:
```bash
mkdir -p fno-shoonya-reader/src/main/java/com/vish/fno/reader/shoonya/core
mkdir -p fno-shoonya-reader/src/main/java/com/vish/fno/reader/shoonya/model
mkdir -p fno-shoonya-reader/src/main/java/com/vish/fno/reader/shoonya/util
mkdir -p fno-shoonya-reader/src/main/java/com/vish/fno/reader/shoonya/exception
mkdir -p fno-shoonya-reader/src/test/java/com/vish/fno/reader/shoonya/model
mkdir -p fno-shoonya-reader/src/test/java/com/vish/fno/reader/shoonya/util
mkdir -p fno-shoonya-reader/src/test/java/com/vish/fno/reader/shoonya/core
```

- [ ] **Step 4: Verify build compiles**

Run: `mvn clean compile -pl fno-shoonya-reader -am`
Expected: BUILD SUCCESS (empty module compiles)

- [ ] **Step 5: Commit**

```bash
git add fno-shoonya-reader/pom.xml pom.xml
git commit -m "feat(shoonya): scaffold fno-shoonya-reader Maven module"
```

---

### Task 2: Model Records

**Files:**
- Create: `fno-shoonya-reader/src/main/java/com/vish/fno/reader/shoonya/model/ShoonyaOpenOrder.java`
- Create: `fno-shoonya-reader/src/main/java/com/vish/fno/reader/shoonya/model/ShoonyaInstrument.java`
- Create: `fno-shoonya-reader/src/main/java/com/vish/fno/reader/shoonya/model/ShoonyaOrder.java`
- Test: `fno-shoonya-reader/src/test/java/com/vish/fno/reader/shoonya/model/ShoonyaInstrumentTest.java`

- [ ] **Step 1: Create ShoonyaOpenOrder record**

```java
package com.vish.fno.reader.shoonya.model;

/**
 * Result of a Shoonya order placement attempt.
 * Mirrors {@code KiteOpenOrder} from fno-kite-reader.
 *
 * @param orderId        Shoonya order number (norenordno), null if failed
 * @param isOrderPlaced  true if order was placed (or test mode), false on error
 * @param errorMessage   error message from API (emsg field), null on success
 */
public record ShoonyaOpenOrder(
    String orderId,
    boolean isOrderPlaced,
    String errorMessage
) {
}
```

- [ ] **Step 2: Create ShoonyaInstrument record**

```java
package com.vish.fno.reader.shoonya.model;

/**
 * Instrument parsed from Shoonya contract master CSV.
 *
 * <p>CSV columns: Exchange, Token, LotSize, Symbol, TradingSymbol, Expiry, Instrument, OptionType, StrikePrice, TickSize
 *
 * <p>Example NFO row: {@code NFO,78900,65,NIFTY,NIFTY28APR26P17200,28-APR-2026,OPTIDX,PE,17200,0.05}
 * <p>Example NSE row: {@code NSE,26000,1,Nifty 50,NIFTY INDEX,INDEX,0} (fewer columns, no expiry/option fields)
 *
 * @param exchange        exchange code (NSE, NFO, BSE, BFO)
 * @param token           numeric instrument token
 * @param lotSize         contract lot size
 * @param symbol          underlying name (e.g., "NIFTY", "RELIANCE")
 * @param tradingSymbol   full trading symbol (e.g., "NIFTY28APR26C17200")
 * @param expiry          expiry date string (e.g., "28-APR-2026"), null for cash
 * @param instrumentType  instrument type (OPTIDX, FUTIDX, FUTSTK, OPTSTK, EQ, INDEX)
 * @param optionType      option type (CE, PE, XX), null for cash
 * @param strikePrice     strike price (-0.01 for futures), 0 for cash
 * @param tickSize        minimum price movement
 */
public record ShoonyaInstrument(
    String exchange,
    long token,
    int lotSize,
    String symbol,
    String tradingSymbol,
    String expiry,
    String instrumentType,
    String optionType,
    double strikePrice,
    double tickSize
) {
}
```

- [ ] **Step 3: Create ShoonyaOrder record**

```java
package com.vish.fno.reader.shoonya.model;

/**
 * Order book entry from Shoonya's OrderBook API response.
 *
 * @param orderId          Shoonya order number (norenordno)
 * @param exchange         exchange code (NSE, NFO, BSE, BFO)
 * @param tradingSymbol    trading symbol
 * @param status           order status (OPEN, COMPLETE, CANCELED, REJECTED)
 * @param transactionType  B (buy) or S (sell)
 * @param quantity         order quantity
 * @param filledQuantity   filled quantity (fillshares)
 * @param averagePrice     average fill price
 * @param remarks          order tag/remarks
 */
public record ShoonyaOrder(
    String orderId,
    String exchange,
    String tradingSymbol,
    String status,
    String transactionType,
    int quantity,
    int filledQuantity,
    double averagePrice,
    String remarks
) {
}
```

- [ ] **Step 4: Write ShoonyaInstrument CSV parsing test**

```java
package com.vish.fno.reader.shoonya.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShoonyaInstrumentTest {

    @Test
    void shouldCreateInstrumentFromNFOFields() {
        ShoonyaInstrument instrument = new ShoonyaInstrument(
            "NFO", 78900, 65, "NIFTY", "NIFTY28APR26P17200",
            "28-APR-2026", "OPTIDX", "PE", 17200.0, 0.05
        );

        assertEquals("NFO", instrument.exchange());
        assertEquals(78900L, instrument.token());
        assertEquals(65, instrument.lotSize());
        assertEquals("NIFTY", instrument.symbol());
        assertEquals("NIFTY28APR26P17200", instrument.tradingSymbol());
        assertEquals("28-APR-2026", instrument.expiry());
        assertEquals("OPTIDX", instrument.instrumentType());
        assertEquals("PE", instrument.optionType());
        assertEquals(17200.0, instrument.strikePrice());
        assertEquals(0.05, instrument.tickSize());
    }

    @Test
    void shouldCreateFutureInstrument() {
        ShoonyaInstrument future = new ShoonyaInstrument(
            "NFO", 66691, 65, "NIFTY", "NIFTY28APR26F",
            "28-APR-2026", "FUTIDX", "XX", -0.01, 0.1
        );

        assertEquals("FUTIDX", future.instrumentType());
        assertEquals("XX", future.optionType());
        assertEquals(-0.01, future.strikePrice());
    }
}
```

- [ ] **Step 5: Run tests**

Run: `mvn test -pl fno-shoonya-reader -Dtest=ShoonyaInstrumentTest`
Expected: 2 tests PASS

- [ ] **Step 6: Commit**

```bash
git add fno-shoonya-reader/src/
git commit -m "feat(shoonya): add model records (ShoonyaOpenOrder, ShoonyaInstrument, ShoonyaOrder)"
```

---

### Task 3: ShoonyaApiException

**Files:**
- Create: `fno-shoonya-reader/src/main/java/com/vish/fno/reader/shoonya/exception/ShoonyaApiException.java`

- [ ] **Step 1: Create ShoonyaApiException**

```java
package com.vish.fno.reader.shoonya.exception;

import java.io.Serial;

/**
 * Exception thrown when the Shoonya API returns an error response ({@code stat: "Not_Ok"}).
 */
public class ShoonyaApiException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ShoonyaApiException(String message) {
        super(message);
    }

    public ShoonyaApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add fno-shoonya-reader/src/main/java/com/vish/fno/reader/shoonya/exception/
git commit -m "feat(shoonya): add ShoonyaApiException"
```

---

### Task 4: ShoonyaHttpClient

**Files:**
- Create: `fno-shoonya-reader/src/main/java/com/vish/fno/reader/shoonya/util/ShoonyaHttpClient.java`
- Test: `fno-shoonya-reader/src/test/java/com/vish/fno/reader/shoonya/util/ShoonyaHttpClientTest.java`

- [ ] **Step 1: Write ShoonyaHttpClient**

```java
package com.vish.fno.reader.shoonya.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vish.fno.reader.shoonya.exception.ShoonyaApiException;
import com.vish.fno.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP client wrapper for Shoonya's NorenRestApi.
 *
 * <p>All Shoonya endpoints are POST with body format: {@code jData={json}&jKey={token}}.
 * Responses are always HTTP 200; errors indicated by {@code stat: "Not_Ok"} in JSON body.
 */
@Slf4j
public class ShoonyaHttpClient {

    private static final String BASE_URL = "https://api.shoonya.com/NorenWClientTP/";
    private static final String STAT_OK = "Ok";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ShoonyaHttpClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.objectMapper = JsonUtils.createObjectMapper();
    }

    /**
     * POST to a Shoonya endpoint without authentication (used for login).
     *
     * @param endpoint API path (e.g., "QuickAuth")
     * @param payload  request fields as a map
     * @return parsed JSON response
     * @throws ShoonyaApiException if API returns stat=Not_Ok
     * @throws IOException         on network/parsing errors
     */
    public JsonNode post(String endpoint, Map<String, Object> payload) throws IOException {
        String jData = objectMapper.writeValueAsString(payload);
        String body = "jData=" + URLEncoder.encode(jData, StandardCharsets.UTF_8);
        return executePost(endpoint, body);
    }

    /**
     * POST to a Shoonya endpoint with authentication token.
     *
     * @param endpoint     API path (e.g., "PlaceOrder")
     * @param payload      request fields as a map
     * @param sessionToken susertoken from login
     * @return parsed JSON response
     * @throws ShoonyaApiException if API returns stat=Not_Ok
     * @throws IOException         on network/parsing errors
     */
    public JsonNode postAuthenticated(String endpoint, Map<String, Object> payload,
                                      String sessionToken) throws IOException {
        String jData = objectMapper.writeValueAsString(payload);
        String body = "jData=" + URLEncoder.encode(jData, StandardCharsets.UTF_8)
                + "&jKey=" + URLEncoder.encode(sessionToken, StandardCharsets.UTF_8);
        return executePost(endpoint, body);
    }

    /**
     * POST to a Shoonya endpoint with authentication, returning the raw response body.
     * Used for endpoints that return JSON arrays (OrderBook, PositionBook) rather than objects.
     *
     * @param endpoint     API path (e.g., "OrderBook")
     * @param payload      request fields as a map
     * @param sessionToken susertoken from login
     * @return raw JSON response string
     * @throws IOException on network errors
     */
    public String postAuthenticatedRaw(String endpoint, Map<String, Object> payload,
                                       String sessionToken) throws IOException {
        String jData = objectMapper.writeValueAsString(payload);
        String body = "jData=" + URLEncoder.encode(jData, StandardCharsets.UTF_8)
                + "&jKey=" + URLEncoder.encode(sessionToken, StandardCharsets.UTF_8);
        return executePostRaw(endpoint, body);
    }

    /**
     * Download a file from the given URL (used for instrument CSV downloads, no auth required).
     *
     * @param url full URL to download
     * @return file contents as byte array
     * @throws IOException on network errors
     */
    public byte[] download(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IOException("Download failed with HTTP " + response.statusCode() + " for URL: " + url);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted for URL: " + url, e);
        }
    }

    private JsonNode executePost(String endpoint, String body) throws IOException {
        String responseBody = executePostRaw(endpoint, body);
        JsonNode node = objectMapper.readTree(responseBody);
        checkForError(node, endpoint);
        return node;
    }

    private String executePostRaw(String endpoint, String body) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("Shoonya {} response status: {}", endpoint, response.statusCode());
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted for endpoint: " + endpoint, e);
        }
    }

    private void checkForError(JsonNode node, String endpoint) {
        JsonNode statNode = node.get("stat");
        if (statNode != null && !STAT_OK.equals(statNode.asText())) {
            String errorMsg = node.has("emsg") ? node.get("emsg").asText() : "Unknown error";
            throw new ShoonyaApiException("Shoonya API error on " + endpoint + ": " + errorMsg);
        }
    }

    /**
     * Encodes a trading symbol for use in Shoonya API requests.
     * Symbols containing special characters (e.g., M&amp;M) must be URL-encoded in the tsym field.
     *
     * @param tradingSymbol raw trading symbol
     * @return URL-encoded symbol
     */
    public static String encodeSymbol(String tradingSymbol) {
        return URLEncoder.encode(tradingSymbol, StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 2: Write ShoonyaHttpClientTest (unit test for encodeSymbol and error checking)**

```java
package com.vish.fno.reader.shoonya.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShoonyaHttpClientTest {

    @Test
    void shouldEncodeSimpleSymbol() {
        assertEquals("NIFTY28APR26C17200", ShoonyaHttpClient.encodeSymbol("NIFTY28APR26C17200"));
    }

    @Test
    void shouldEncodeSymbolWithAmpersand() {
        String encoded = ShoonyaHttpClient.encodeSymbol("M&M-EQ");
        assertEquals("M%26M-EQ", encoded);
    }

    @Test
    void shouldEncodeSymbolWithSpaces() {
        String encoded = ShoonyaHttpClient.encodeSymbol("NIFTY INDEX");
        assertEquals("NIFTY+INDEX", encoded);
    }
}
```

- [ ] **Step 3: Run tests**

Run: `mvn test -pl fno-shoonya-reader -Dtest=ShoonyaHttpClientTest`
Expected: 3 tests PASS

- [ ] **Step 4: Commit**

```bash
git add fno-shoonya-reader/src/
git commit -m "feat(shoonya): add ShoonyaHttpClient with jData/jKey HTTP wrapper"
```

---

### Task 5: ShoonyaInstrumentFileUtils

**Files:**
- Create: `fno-shoonya-reader/src/main/java/com/vish/fno/reader/shoonya/util/ShoonyaInstrumentFileUtils.java`

- [ ] **Step 1: Create ShoonyaInstrumentFileUtils**

```java
package com.vish.fno.reader.shoonya.util;

import com.vish.fno.reader.shoonya.model.ShoonyaInstrument;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads and parses Shoonya contract master CSV files.
 *
 * <p>Shoonya publishes instrument data as zipped CSV files at:
 * <ul>
 *   <li>{@code https://api.shoonya.com/NFO_symbols.txt.zip} — NSE F&amp;O</li>
 *   <li>{@code https://api.shoonya.com/NSE_symbols.txt.zip} — NSE Cash</li>
 *   <li>{@code https://api.shoonya.com/BFO_symbols.txt.zip} — BSE F&amp;O</li>
 *   <li>{@code https://api.shoonya.com/BSE_symbols.txt.zip} — BSE Cash</li>
 * </ul>
 *
 * <p>NFO CSV columns (10): Exchange,Token,LotSize,Symbol,TradingSymbol,Expiry,Instrument,OptionType,StrikePrice,TickSize
 * <p>NSE CSV columns (7):  Exchange,Token,LotSize,Symbol,TradingSymbol,Instrument,TickSize
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ShoonyaInstrumentFileUtils {

    private static final String INSTRUMENT_BASE_URL = "https://api.shoonya.com/";

    private static final int NFO_COLUMN_COUNT = 10;
    private static final int NSE_COLUMN_COUNT = 7;

    /**
     * Downloads and parses instruments for the given exchanges.
     *
     * @param httpClient HTTP client for downloading
     * @param exchanges  exchange codes to download (e.g., "NFO", "NSE", "BFO", "BSE")
     * @return combined list of parsed instruments from all exchanges
     */
    public static List<ShoonyaInstrument> downloadAndParseInstruments(ShoonyaHttpClient httpClient,
                                                                      List<String> exchanges) {
        List<ShoonyaInstrument> allInstruments = new ArrayList<>();
        for (String exchange : exchanges) {
            try {
                String url = INSTRUMENT_BASE_URL + exchange + "_symbols.txt.zip";
                byte[] zipData = httpClient.download(url);
                List<ShoonyaInstrument> instruments = parseZippedCsv(zipData, exchange);
                allInstruments.addAll(instruments);
                log.info("Loaded {} instruments from {} contract master", instruments.size(), exchange);
            } catch (IOException e) {
                log.error("Failed to download {} instrument data", exchange, e);
            }
        }
        return allInstruments;
    }

    /**
     * Parses a zipped CSV file into a list of instruments.
     */
    static List<ShoonyaInstrument> parseZippedCsv(byte[] zipData, String exchange) throws IOException {
        List<ShoonyaInstrument> instruments = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry = zis.getNextEntry();
            if (entry == null) {
                log.warn("Empty zip file for exchange {}", exchange);
                return instruments;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    ShoonyaInstrument instrument = parseCsvLine(line, exchange);
                    if (instrument != null) {
                        instruments.add(instrument);
                    }
                } catch (NumberFormatException e) {
                    if (lineNum <= 2) {
                        log.debug("Skipping header/malformed line {} in {} CSV: {}", lineNum, exchange, line);
                    }
                }
            }
        }

        return instruments;
    }

    /**
     * Parses a single CSV line into a ShoonyaInstrument.
     * NFO/BFO have 10 columns; NSE/BSE have 7 columns.
     */
    private static ShoonyaInstrument parseCsvLine(String line, String exchange) {
        String[] parts = line.split(",", -1);

        if (parts.length >= NFO_COLUMN_COUNT && isDerivativeExchange(exchange)) {
            return new ShoonyaInstrument(
                parts[0].trim(),                               // exchange
                Long.parseLong(parts[1].trim()),                // token
                Integer.parseInt(parts[2].trim()),              // lotSize
                parts[3].trim(),                                // symbol (underlying)
                parts[4].trim(),                                // tradingSymbol
                parts[5].trim().isEmpty() ? null : parts[5].trim(), // expiry
                parts[6].trim(),                                // instrumentType
                parts[7].trim(),                                // optionType
                Double.parseDouble(parts[8].trim()),            // strikePrice
                Double.parseDouble(parts[9].trim())             // tickSize
            );
        } else if (parts.length >= NSE_COLUMN_COUNT) {
            return new ShoonyaInstrument(
                parts[0].trim(),                                // exchange
                Long.parseLong(parts[1].trim()),                // token
                Integer.parseInt(parts[2].trim()),              // lotSize
                parts[3].trim(),                                // symbol
                parts[4].trim(),                                // tradingSymbol
                null,                                           // expiry (cash has none)
                parts[5].trim(),                                // instrumentType
                null,                                           // optionType (cash has none)
                0.0,                                            // strikePrice
                Double.parseDouble(parts[6].trim())             // tickSize
            );
        }

        return null;
    }

    private static boolean isDerivativeExchange(String exchange) {
        return "NFO".equals(exchange) || "BFO".equals(exchange);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add fno-shoonya-reader/src/main/java/com/vish/fno/reader/shoonya/util/ShoonyaInstrumentFileUtils.java
git commit -m "feat(shoonya): add ShoonyaInstrumentFileUtils for CSV contract master parsing"
```

---

### Task 6: ShoonyaOptionPriceUtils

**Files:**
- Create: `fno-shoonya-reader/src/main/java/com/vish/fno/reader/shoonya/util/ShoonyaOptionPriceUtils.java`
- Test: `fno-shoonya-reader/src/test/java/com/vish/fno/reader/shoonya/util/ShoonyaOptionPriceUtilsTest.java`

- [ ] **Step 1: Create ShoonyaOptionPriceUtils**

Mirrors `OptionPriceUtils` from fno-kite-reader but operates on `ShoonyaInstrument` instead of Zerodha's `Instrument`.

```java
package com.vish.fno.reader.shoonya.util;

import com.vish.fno.model.Exchange;
import com.vish.fno.model.InstrumentType;
import com.vish.fno.reader.shoonya.model.ShoonyaInstrument;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.vish.fno.util.FnoConstants.INDEX_TO_DERIVATIVE;

/**
 * Option strike resolution utilities for Shoonya instruments.
 * Mirrors {@code OptionPriceUtils} from fno-kite-reader, adapted for {@link ShoonyaInstrument}.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ShoonyaOptionPriceUtils {

    public static String getITMStock(String indexSymbol, double price, boolean isCall,
                                     List<ShoonyaInstrument> instruments) {
        // ITM call = last strike <= price; ITM put = first strike >= price
        String itmSymbol = findStrike(indexSymbol, price, isCall, isCall, instruments);
        if (itmSymbol == null || itmSymbol.isBlank()) {
            log.error("Unable to find ITM symbol for index: {}, price: {}, call: {}", indexSymbol, price, isCall);
        }
        return itmSymbol;
    }

    public static String getOTMStock(String indexSymbol, double price, boolean isCall,
                                     List<ShoonyaInstrument> instruments) {
        // OTM call = first strike > price; OTM put = last strike < price
        String otmSymbol = findStrike(indexSymbol, price, isCall, !isCall, instruments);
        if (otmSymbol == null || otmSymbol.isBlank()) {
            log.error("Unable to find OTM symbol for index: {}, price: {}, call: {}", indexSymbol, price, isCall);
        }
        return otmSymbol;
    }

    public static List<String> getAllOptionSymbols(String indexSymbol, List<ShoonyaInstrument> instruments) {
        String symbolsName = getOptionPrefix(indexSymbol);

        Optional<List<ShoonyaInstrument>> calls = getEarliestExpiryInstruments(
                instruments, symbolsName, InstrumentType.CE.getCode());
        Optional<List<ShoonyaInstrument>> puts = getEarliestExpiryInstruments(
                instruments, symbolsName, InstrumentType.PE.getCode());

        List<String> allOptionSymbols = new ArrayList<>();
        calls.ifPresent(c -> c.forEach(i -> allOptionSymbols.add(i.tradingSymbol())));
        puts.ifPresent(p -> p.forEach(i -> allOptionSymbols.add(i.tradingSymbol())));

        log.info("Found {} option symbols for {}: {} CALLs, {} PUTs",
                allOptionSymbols.size(), indexSymbol,
                calls.map(List::size).orElse(0),
                puts.map(List::size).orElse(0));

        return allOptionSymbols;
    }

    private static String findStrike(String indexSymbol, double price, boolean isCall,
                                     boolean selectLastBelow, List<ShoonyaInstrument> instruments) {
        String symbolsName = getOptionPrefix(indexSymbol);
        String instrumentType = isCall ? InstrumentType.CE.getCode() : InstrumentType.PE.getCode();
        return getEarliestExpiryInstruments(instruments, symbolsName, instrumentType)
                .map(expiryInstruments -> resolveStrike(expiryInstruments, price, selectLastBelow))
                .orElse("");
    }

    private static String resolveStrike(List<ShoonyaInstrument> instruments, double price,
                                        boolean selectLastBelow) {
        NavigableMap<Long, String> strikeToSymbolMap = new TreeMap<>();
        for (ShoonyaInstrument instrument : instruments) {
            long strike = Math.round(instrument.strikePrice());
            strikeToSymbolMap.putIfAbsent(strike, instrument.tradingSymbol());
        }

        if (selectLastBelow) {
            String result = "";
            for (long strikePrice : strikeToSymbolMap.keySet()) {
                if (strikePrice > price) {
                    break;
                }
                result = strikeToSymbolMap.get(strikePrice);
            }
            return result;
        } else {
            for (long strikePrice : strikeToSymbolMap.keySet()) {
                if (strikePrice > price) {
                    return strikeToSymbolMap.get(strikePrice);
                }
            }
            return "";
        }
    }

    static Optional<List<ShoonyaInstrument>> getEarliestExpiryInstruments(
            List<ShoonyaInstrument> instruments, String symbolName, String optionType) {
        Map<String, List<ShoonyaInstrument>> byExpiry = instruments.stream()
                .filter(i -> symbolName.equalsIgnoreCase(i.symbol()))
                .filter(i -> Exchange.NFO.matches(i.exchange()) || Exchange.BFO.matches(i.exchange()))
                .filter(i -> optionType.equals(i.optionType()))
                .filter(i -> i.expiry() != null)
                .collect(Collectors.groupingBy(ShoonyaInstrument::expiry));

        return byExpiry.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private static String getOptionPrefix(String indexSymbol) {
        return INDEX_TO_DERIVATIVE.getOrDefault(indexSymbol, indexSymbol);
    }
}
```

- [ ] **Step 2: Write test**

```java
package com.vish.fno.reader.shoonya.util;

import com.vish.fno.reader.shoonya.model.ShoonyaInstrument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShoonyaOptionPriceUtilsTest {

    private static final List<ShoonyaInstrument> TEST_INSTRUMENTS = List.of(
        new ShoonyaInstrument("NFO", 100, 65, "NIFTY", "NIFTY28APR26C22000", "28-APR-2026", "OPTIDX", "CE", 22000, 0.05),
        new ShoonyaInstrument("NFO", 101, 65, "NIFTY", "NIFTY28APR26C22050", "28-APR-2026", "OPTIDX", "CE", 22050, 0.05),
        new ShoonyaInstrument("NFO", 102, 65, "NIFTY", "NIFTY28APR26C22100", "28-APR-2026", "OPTIDX", "CE", 22100, 0.05),
        new ShoonyaInstrument("NFO", 200, 65, "NIFTY", "NIFTY28APR26P22000", "28-APR-2026", "OPTIDX", "PE", 22000, 0.05),
        new ShoonyaInstrument("NFO", 201, 65, "NIFTY", "NIFTY28APR26P22050", "28-APR-2026", "OPTIDX", "PE", 22050, 0.05),
        new ShoonyaInstrument("NFO", 202, 65, "NIFTY", "NIFTY28APR26P22100", "28-APR-2026", "OPTIDX", "PE", 22100, 0.05)
    );

    @Test
    void shouldFindITMCallStrike() {
        // ITM call = last strike <= price (22030 → 22000 CE)
        String symbol = ShoonyaOptionPriceUtils.getITMStock("NIFTY", 22030, true, TEST_INSTRUMENTS);
        assertEquals("NIFTY28APR26C22000", symbol);
    }

    @Test
    void shouldFindOTMCallStrike() {
        // OTM call = first strike > price (22030 → 22050 CE)
        String symbol = ShoonyaOptionPriceUtils.getOTMStock("NIFTY", 22030, true, TEST_INSTRUMENTS);
        assertEquals("NIFTY28APR26C22050", symbol);
    }

    @Test
    void shouldFindITMPutStrike() {
        // ITM put = first strike >= price (22030 → 22050 PE)
        String symbol = ShoonyaOptionPriceUtils.getITMStock("NIFTY", 22030, false, TEST_INSTRUMENTS);
        assertEquals("NIFTY28APR26P22050", symbol);
    }

    @Test
    void shouldFindOTMPutStrike() {
        // OTM put = last strike < price (22030 → 22000 PE)
        String symbol = ShoonyaOptionPriceUtils.getOTMStock("NIFTY", 22030, false, TEST_INSTRUMENTS);
        assertEquals("NIFTY28APR26P22000", symbol);
    }

    @Test
    void shouldGetAllOptionSymbols() {
        List<String> symbols = ShoonyaOptionPriceUtils.getAllOptionSymbols("NIFTY", TEST_INSTRUMENTS);
        assertEquals(6, symbols.size());
    }
}
```

- [ ] **Step 3: Run tests**

Run: `mvn test -pl fno-shoonya-reader -Dtest=ShoonyaOptionPriceUtilsTest`
Expected: 5 tests PASS

- [ ] **Step 4: Commit**

```bash
git add fno-shoonya-reader/src/
git commit -m "feat(shoonya): add ShoonyaOptionPriceUtils for strike resolution"
```

---

### Task 7: ShoonyaSession

**Files:**
- Create: `fno-shoonya-reader/src/main/java/com/vish/fno/reader/shoonya/core/ShoonyaSession.java`
- Test: `fno-shoonya-reader/src/test/java/com/vish/fno/reader/shoonya/core/ShoonyaSessionTest.java`

- [ ] **Step 1: Create ShoonyaSession**

```java
package com.vish.fno.reader.shoonya.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.vish.fno.reader.shoonya.exception.ShoonyaApiException;
import com.vish.fno.reader.shoonya.util.ShoonyaHttpClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Manages Shoonya API session: authentication, token storage, and rate-limited execution.
 *
 * <p>Mirrors {@code KiteSession} from fno-kite-reader. Uses ReentrantLock (not synchronized)
 * for virtual thread compatibility.
 *
 * <p>Authentication uses SHA-256 password hashing and TOTP (RFC 6238) for 2FA.
 */
@Slf4j
class ShoonyaSession {

    private static final long LOCK_WAIT_LOG_THRESHOLD_MS = 100;
    static volatile long lockTimeoutSeconds = 12;

    private final String userId;
    private final String passwordHash;
    private final String vendorCode;
    private final String apiSecret;
    private final String totpSecret;
    private final String imei;
    @Getter
    private final boolean placeOrders;
    @Getter
    private final ShoonyaHttpClient httpClient;

    private final ReentrantLock apiLock = new ReentrantLock(true);
    @Getter
    private volatile String sessionToken;
    @Getter
    private volatile boolean initialised;

    ShoonyaSession(String userId, String password, String vendorCode,
                   String apiSecret, String totpSecret, String imei,
                   boolean placeOrders) {
        this.userId = userId;
        this.passwordHash = sha256Hex(password);
        this.vendorCode = vendorCode;
        this.apiSecret = apiSecret;
        this.totpSecret = totpSecret;
        this.imei = imei;
        this.placeOrders = placeOrders;
        this.httpClient = new ShoonyaHttpClient();
    }

    /**
     * Authenticate with Shoonya via /QuickAuth endpoint.
     * Computes appkey = SHA-256(userId|apiSecret), generates TOTP, and calls login.
     */
    void authenticate() {
        executeWithLockVoid(() -> {
            try {
                String appKey = sha256Hex(userId + "|" + apiSecret);
                String totp = generateTOTP(totpSecret);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("source", "API");
                payload.put("apkversion", "1.0.0");
                payload.put("uid", userId);
                payload.put("pwd", passwordHash);
                payload.put("factor2", totp);
                payload.put("vc", vendorCode);
                payload.put("appkey", appKey);
                payload.put("imei", imei);

                JsonNode response = httpClient.post("QuickAuth", payload);
                sessionToken = response.get("susertoken").asText();

                log.info("Shoonya authentication successful for user: {}", userId);
                if (response.has("exarr")) {
                    log.info("Enabled exchanges: {}", response.get("exarr"));
                }

                initialised = true;
            } catch (ShoonyaApiException e) {
                log.error("Shoonya authentication failed: {}", e.getMessage());
            } catch (IOException e) {
                log.error("Error during Shoonya authentication", e);
            }
        }, "authenticate");
    }

    <T> T executeWithLock(Supplier<T> action, String operationName) {
        long waitStart = System.nanoTime();
        if (!acquireLock(operationName)) {
            return null;
        }
        try {
            logWaitTime(waitStart, operationName);
            return action.get();
        } finally {
            apiLock.unlock();
        }
    }

    void executeWithLockVoid(Runnable action, String operationName) {
        long waitStart = System.nanoTime();
        if (!acquireLock(operationName)) {
            return;
        }
        try {
            logWaitTime(waitStart, operationName);
            action.run();
        } finally {
            apiLock.unlock();
        }
    }

    <T> T executeWithLockSafe(Supplier<T> action, String operationName, T fallback) {
        long waitStart = System.nanoTime();
        if (!acquireLock(operationName)) {
            return fallback;
        }
        try {
            logWaitTime(waitStart, operationName);
            return action.get();
        } catch (ShoonyaApiException e) {
            log.error("{} failed: {}", operationName, e.getMessage());
            return fallback;
        } finally {
            apiLock.unlock();
        }
    }

    String getUserId() {
        return userId;
    }

    private boolean acquireLock(String operationName) {
        try {
            if (!apiLock.tryLock(lockTimeoutSeconds, TimeUnit.SECONDS)) {
                log.error("API lock timeout after {}s for {}", lockTimeoutSeconds, operationName);
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for API lock for {}", operationName, e);
            return false;
        }
    }

    private void logWaitTime(long waitStart, String operationName) {
        long waitMs = (System.nanoTime() - waitStart) / 1_000_000;
        if (waitMs > LOCK_WAIT_LOG_THRESHOLD_MS) {
            log.info("API lock acquired for {} after {}ms wait", operationName, waitMs);
        }
    }

    /**
     * SHA-256 hex digest of the given input.
     */
    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Generate a TOTP (RFC 6238) code from the given base32-encoded secret.
     * Uses HMAC-SHA1 with 30-second time step and 6-digit output.
     */
    static String generateTOTP(String base32Secret) {
        long timeStep = System.currentTimeMillis() / 1000 / 30;
        byte[] key = base32Decode(base32Secret);
        byte[] timeBytes = ByteBuffer.allocate(8).putLong(timeStep).array();

        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hmac = mac.doFinal(timeBytes);

            int offset = hmac[hmac.length - 1] & 0x0F;
            int code = ((hmac[offset] & 0x7F) << 24)
                    | ((hmac[offset + 1] & 0xFF) << 16)
                    | ((hmac[offset + 2] & 0xFF) << 8)
                    | (hmac[offset + 3] & 0xFF);

            int otp = code % 1_000_000;
            return String.format("%06d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("TOTP generation failed", e);
        }
    }

    /**
     * Decode a base32-encoded string (RFC 4648) to bytes.
     */
    private static byte[] base32Decode(String encoded) {
        String base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        String upper = encoded.toUpperCase().replaceAll("[=\\s]", "");
        int bitBuffer = 0;
        int bitsInBuffer = 0;
        byte[] output = new byte[upper.length() * 5 / 8];
        int outputIndex = 0;

        for (char c : upper.toCharArray()) {
            int val = base32Chars.indexOf(c);
            if (val < 0) {
                continue;
            }
            bitBuffer = (bitBuffer << 5) | val;
            bitsInBuffer += 5;
            if (bitsInBuffer >= 8) {
                bitsInBuffer -= 8;
                output[outputIndex++] = (byte) ((bitBuffer >> bitsInBuffer) & 0xFF);
            }
        }
        byte[] result = new byte[outputIndex];
        System.arraycopy(output, 0, result, 0, outputIndex);
        return result;
    }
}
```

- [ ] **Step 2: Write ShoonyaSessionTest**

```java
package com.vish.fno.reader.shoonya.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ShoonyaSessionTest {

    @Test
    void shouldComputeSha256Hex() {
        // SHA-256 of "test" is well-known
        String hash = ShoonyaSession.sha256Hex("test");
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", hash);
    }

    @Test
    void shouldComputeAppKeyHash() {
        // appkey = SHA-256("FA12345|my_api_secret")
        String appKey = ShoonyaSession.sha256Hex("FA12345|my_api_secret");
        assertNotNull(appKey);
        assertEquals(64, appKey.length());
    }

    @Test
    void shouldGenerateSixDigitTOTP() {
        // Use a known base32 test secret
        String totp = ShoonyaSession.generateTOTP("JBSWY3DPEHPK3PXP");
        assertNotNull(totp);
        assertEquals(6, totp.length());
    }

    @Test
    void shouldNotBeInitialisedBeforeAuth() {
        ShoonyaSession session = new ShoonyaSession(
            "user", "pass", "vendor", "secret", "TOTP", "imei", false
        );
        assertFalse(session.isInitialised());
    }
}
```

- [ ] **Step 3: Run tests**

Run: `mvn test -pl fno-shoonya-reader -Dtest=ShoonyaSessionTest`
Expected: 4 tests PASS

- [ ] **Step 4: Commit**

```bash
git add fno-shoonya-reader/src/
git commit -m "feat(shoonya): add ShoonyaSession with SHA-256 auth and TOTP generation"
```

---

### Task 8: ShoonyaInstrumentCache

**Files:**
- Create: `fno-shoonya-reader/src/main/java/com/vish/fno/reader/shoonya/core/ShoonyaInstrumentCache.java`
- Test: `fno-shoonya-reader/src/test/java/com/vish/fno/reader/shoonya/core/ShoonyaInstrumentCacheTest.java`

- [ ] **Step 1: Create ShoonyaInstrumentCache**

```java
package com.vish.fno.reader.shoonya.core;

import com.vish.fno.model.Exchange;
import com.vish.fno.model.InstrumentType;
import com.vish.fno.reader.shoonya.model.ShoonyaInstrument;
import com.vish.fno.reader.shoonya.util.ShoonyaHttpClient;
import com.vish.fno.reader.shoonya.util.ShoonyaInstrumentFileUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.vish.fno.util.FnoConstants.INDEX_TO_DERIVATIVE;

/**
 * Cache for Shoonya instruments, mirroring {@code InstrumentCache} from fno-kite-reader.
 * Downloads CSV contract masters and provides symbol/token lookup.
 *
 * <p>Thread-safe lazy initialization using double-checked locking with volatile + ReentrantLock.
 */
@Slf4j
class ShoonyaInstrumentCache {

    private static final List<String> EXCHANGES_TO_DOWNLOAD = List.of("NFO", "NSE", "BFO", "BSE");

    private final ShoonyaHttpClient httpClient;
    private final Set<String> nifty100Symbols;
    private final ReentrantLock initLock = new ReentrantLock();
    private volatile CacheData cache;

    record SymbolInfo(long token, String exchange) {}

    private record CacheData(
        List<ShoonyaInstrument> filteredInstruments,
        Map<String, SymbolInfo> symbolInfoMap,
        Map<Long, String> tokenToSymbolMap
    ) {}

    ShoonyaInstrumentCache(List<String> nifty100Symbols, ShoonyaHttpClient httpClient) {
        this.nifty100Symbols = new HashSet<>(nifty100Symbols);
        this.httpClient = httpClient;
    }

    private void ensureInitialized() {
        if (cache != null) {
            return;
        }
        initLock.lock();
        try {
            if (cache == null) {
                initializeInstruments();
            }
        } finally {
            initLock.unlock();
        }
    }

    List<ShoonyaInstrument> getInstruments() {
        ensureInitialized();
        return Collections.unmodifiableList(cache.filteredInstruments());
    }

    Optional<Long> getInstrument(String symbol) {
        if (symbol == null) {
            return Optional.empty();
        }
        ensureInitialized();
        SymbolInfo info = cache.symbolInfoMap().get(symbol.toUpperCase(Locale.ENGLISH));
        return info != null ? Optional.of(info.token()) : Optional.empty();
    }

    String getExchangeForSymbol(String symbol) {
        if (symbol == null) {
            return Exchange.NFO.getCode();
        }
        ensureInitialized();
        SymbolInfo info = cache.symbolInfoMap().get(symbol.toUpperCase(Locale.ENGLISH));
        if (info == null) {
            log.warn("Exchange not found for symbol: {}, defaulting to NFO", symbol);
            return Exchange.NFO.getCode();
        }
        return info.exchange();
    }

    String getSymbol(long token) {
        ensureInitialized();
        return cache.tokenToSymbolMap().get(token);
    }

    Optional<Integer> getLotSizeFromFuture(String indexName) {
        if (indexName == null) {
            return Optional.empty();
        }
        String derivativeName = INDEX_TO_DERIVATIVE.getOrDefault(indexName, indexName);
        return getInstruments().stream()
                .filter(i -> isFutureType(i.instrumentType()))
                .filter(i -> derivativeName.equalsIgnoreCase(i.symbol()))
                .findFirst()
                .map(ShoonyaInstrument::lotSize);
    }

    Map<String, Integer> getAllFutureLotSizeInfo() {
        List<ShoonyaInstrument> instruments = getInstruments();
        Map<String, String> reverseMap = INDEX_TO_DERIVATIVE.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        return instruments.stream()
                .filter(i -> isFutureType(i.instrumentType()))
                .filter(i -> i.symbol() != null)
                .collect(Collectors.toMap(
                        i -> reverseMap.getOrDefault(i.symbol(), i.symbol()),
                        ShoonyaInstrument::lotSize,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));
    }

    boolean isExpiryDayForOption(String optionSymbol, java.util.Date currentDate) {
        List<ShoonyaInstrument> matching = getInstruments().stream()
                .filter(i -> i.tradingSymbol().equals(optionSymbol))
                .toList();

        if (matching.size() == 1 && matching.get(0).expiry() != null) {
            return isSameDayByExpiryString(matching.get(0).expiry(), currentDate);
        }
        log.error("Cannot find option: {} in Shoonya instrument cache. Found: {}", optionSymbol, matching.size());
        return false;
    }

    boolean isExpiryDayForIndex(String indexName, java.util.Date currentDate) {
        if (indexName == null || currentDate == null) {
            return false;
        }
        String derivativeName = INDEX_TO_DERIVATIVE.getOrDefault(indexName, indexName);
        return getInstruments().stream()
                .filter(i -> derivativeName.equalsIgnoreCase(i.symbol()))
                .filter(i -> InstrumentType.CE.getCode().equals(i.optionType())
                        || InstrumentType.PE.getCode().equals(i.optionType()))
                .anyMatch(i -> i.expiry() != null && isSameDayByExpiryString(i.expiry(), currentDate));
    }

    List<String> getAllOptionSymbols(String indexSymbol) {
        String symbolsName = INDEX_TO_DERIVATIVE.getOrDefault(indexSymbol, indexSymbol);
        List<ShoonyaInstrument> instruments = getInstruments();

        return instruments.stream()
                .filter(i -> symbolsName.equalsIgnoreCase(i.symbol()))
                .filter(i -> Exchange.NFO.matches(i.exchange()) || Exchange.BFO.matches(i.exchange()))
                .filter(i -> InstrumentType.CE.getCode().equals(i.optionType())
                        || InstrumentType.PE.getCode().equals(i.optionType()))
                .map(ShoonyaInstrument::tradingSymbol)
                .toList();
    }

    int getInstrumentMapSize() {
        CacheData data = cache;
        return data != null ? data.tokenToSymbolMap().size() : 0;
    }

    private void initializeInstruments() {
        List<ShoonyaInstrument> allInstruments = ShoonyaInstrumentFileUtils.downloadAndParseInstruments(
                httpClient, EXCHANGES_TO_DOWNLOAD);

        if (allInstruments.isEmpty()) {
            throw new IllegalStateException("Shoonya instrument cache initialization failed — no instruments loaded");
        }

        List<ShoonyaInstrument> filtered = filterInstruments(allInstruments);
        Map<String, SymbolInfo> symbolInfoMap = buildSymbolInfoMap(filtered);
        Map<Long, String> tokenToSymbolMap = buildTokenToSymbolMap(symbolInfoMap);

        log.info("Shoonya instrument cache: {} total, {} filtered", allInstruments.size(), filtered.size());

        this.cache = new CacheData(filtered, symbolInfoMap, tokenToSymbolMap);
    }

    private List<ShoonyaInstrument> filterInstruments(List<ShoonyaInstrument> allInstruments) {
        return allInstruments.stream()
                .filter(i -> i.symbol() != null)
                .filter(this::isRelevantExchange)
                .filter(this::isInTrackingList)
                .toList();
    }

    private boolean isInTrackingList(ShoonyaInstrument i) {
        return nifty100Symbols.contains(i.tradingSymbol())
                || nifty100Symbols.contains(i.symbol());
    }

    private boolean isRelevantExchange(ShoonyaInstrument i) {
        return Exchange.NSE.matches(i.exchange())
                || (Exchange.NFO.matches(i.exchange()) && i.expiry() != null)
                || Exchange.BSE.matches(i.exchange())
                || (Exchange.BFO.matches(i.exchange()) && i.expiry() != null);
    }

    private Map<String, SymbolInfo> buildSymbolInfoMap(List<ShoonyaInstrument> filtered) {
        return filtered.stream()
                .collect(Collectors.toMap(
                        i -> i.tradingSymbol().toUpperCase(Locale.ENGLISH),
                        i -> new SymbolInfo(i.token(), i.exchange()),
                        (existing, replacement) -> existing,
                        TreeMap::new));
    }

    private Map<Long, String> buildTokenToSymbolMap(Map<String, SymbolInfo> symbolInfoMap) {
        return symbolInfoMap.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getValue().token(), Map.Entry::getKey));
    }

    private static boolean isFutureType(String instrumentType) {
        return "FUTIDX".equals(instrumentType) || "FUTSTK".equals(instrumentType);
    }

    /**
     * Compare a Shoonya expiry date string (DD-MMM-YYYY) against a Java Date.
     * Uses simple string comparison to avoid complex date parsing.
     */
    private static boolean isSameDayByExpiryString(String expiryStr, java.util.Date date) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH);
        String dateStr = sdf.format(date).toUpperCase(Locale.ENGLISH);
        return expiryStr.toUpperCase(Locale.ENGLISH).equals(dateStr);
    }
}
```

- [ ] **Step 2: Write ShoonyaInstrumentCacheTest**

```java
package com.vish.fno.reader.shoonya.core;

import com.vish.fno.reader.shoonya.model.ShoonyaInstrument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoonyaInstrumentCacheTest {

    // Test the filtering and lookup logic using a pre-populated cache
    // (actual download is tested via integration tests)

    @Test
    void symbolInfoRecordShouldStoreTokenAndExchange() {
        ShoonyaInstrumentCache.SymbolInfo info = new ShoonyaInstrumentCache.SymbolInfo(78900L, "NFO");
        assertEquals(78900L, info.token());
        assertEquals("NFO", info.exchange());
    }
}
```

- [ ] **Step 3: Run tests**

Run: `mvn test -pl fno-shoonya-reader -Dtest=ShoonyaInstrumentCacheTest`
Expected: 1 test PASS

- [ ] **Step 4: Commit**

```bash
git add fno-shoonya-reader/src/
git commit -m "feat(shoonya): add ShoonyaInstrumentCache with CSV-based instrument loading"
```

---

### Task 9: ShoonyaOrderExecutor

**Files:**
- Create: `fno-shoonya-reader/src/main/java/com/vish/fno/reader/shoonya/core/ShoonyaOrderExecutor.java`
- Test: `fno-shoonya-reader/src/test/java/com/vish/fno/reader/shoonya/core/ShoonyaOrderExecutorTest.java`

- [ ] **Step 1: Create ShoonyaOrderExecutor**

```java
package com.vish.fno.reader.shoonya.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vish.fno.reader.shoonya.model.ShoonyaOpenOrder;
import com.vish.fno.reader.shoonya.model.ShoonyaOrder;
import com.vish.fno.reader.shoonya.exception.ShoonyaApiException;
import com.vish.fno.reader.shoonya.util.ShoonyaHttpClient;
import com.vish.fno.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles order placement and retrieval for Shoonya API.
 * Mirrors {@code KiteOrderExecutor} from fno-kite-reader.
 *
 * <p>All API calls are routed through {@link ShoonyaSession#executeWithLock} for rate limiting.
 */
@Slf4j
@RequiredArgsConstructor
class ShoonyaOrderExecutor {

    private static final int MAX_TAG_LENGTH = 20;
    private static final ObjectMapper MAPPER = JsonUtils.createObjectMapper();

    private final ShoonyaSession session;
    private final ShoonyaInstrumentCache instrumentCache;

    Optional<ShoonyaOpenOrder> buyOrder(String symbol, int orderSize, String tag, boolean isPlaceOrder) {
        log.info("Creating Shoonya buy order: qty={}, symbol={}, isPlaceOrder={}", orderSize, symbol, isPlaceOrder);
        return placeOrder(symbol, orderSize, tag, "B", isPlaceOrder);
    }

    Optional<ShoonyaOpenOrder> sellOrder(String symbol, int orderSize, String tag, boolean isPlaceOrder) {
        log.info("Creating Shoonya sell order: qty={}, symbol={}, tag={}, isPlaceOrder={}", orderSize, symbol, tag, isPlaceOrder);
        return placeOrder(symbol, orderSize, tag, "S", isPlaceOrder);
    }

    List<ShoonyaOrder> getOrders() {
        return session.executeWithLockSafe(() -> {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("uid", session.getUserId());
                payload.put("ordersource", "API");

                String rawResponse = session.getHttpClient().postAuthenticatedRaw(
                        "OrderBook", payload, session.getSessionToken());

                JsonNode arrayNode = MAPPER.readTree(rawResponse);
                if (!arrayNode.isArray()) {
                    log.warn("OrderBook returned non-array response, likely an error");
                    return List.<ShoonyaOrder>of();
                }

                List<ShoonyaOrder> orders = new ArrayList<>();
                for (JsonNode node : arrayNode) {
                    orders.add(new ShoonyaOrder(
                            getTextOrDefault(node, "norenordno", ""),
                            getTextOrDefault(node, "exch", ""),
                            getTextOrDefault(node, "tsym", ""),
                            getTextOrDefault(node, "status", ""),
                            getTextOrDefault(node, "trantype", ""),
                            getIntOrDefault(node, "qty", 0),
                            getIntOrDefault(node, "fillshares", 0),
                            getDoubleOrDefault(node, "avgprc", 0.0),
                            getTextOrDefault(node, "remarks", "")
                    ));
                }
                return orders;
            } catch (IOException e) {
                log.error("Failed to fetch order book", e);
                return List.<ShoonyaOrder>of();
            }
        }, "getOrders", List.of());
    }

    private Optional<ShoonyaOpenOrder> placeOrder(String symbol, int orderSize, String tag,
                                                   String transactionType, boolean isPlaceOrder) {
        if (!session.isInitialised()) {
            log.warn("Not placing order as Shoonya session is not initialized");
            return Optional.of(new ShoonyaOpenOrder(null, false, "Session not initialized"));
        }

        if (!isPlaceOrder) {
            log.warn("Not placing order as it is not enabled currently");
            return Optional.of(new ShoonyaOpenOrder(null, true, null));
        }

        if (!session.isPlaceOrders()) {
            log.warn("Not placing order as it is turned off by configuration");
            return Optional.of(new ShoonyaOpenOrder(null, true, null));
        }

        return session.executeWithLock(() -> {
            try {
                String exchange = instrumentCache.getExchangeForSymbol(symbol);
                String truncatedTag = tag.length() > MAX_TAG_LENGTH ? tag.substring(0, MAX_TAG_LENGTH) : tag;

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("ordersource", "API");
                payload.put("uid", session.getUserId());
                payload.put("actid", session.getUserId());
                payload.put("trantype", transactionType);
                payload.put("prd", "I");  // MIS equivalent
                payload.put("exch", exchange);
                payload.put("tsym", ShoonyaHttpClient.encodeSymbol(symbol));
                payload.put("qty", String.valueOf(orderSize));
                payload.put("prctyp", "MKT");
                payload.put("prc", "0");
                payload.put("ret", "DAY");
                payload.put("remarks", truncatedTag);

                JsonNode response = session.getHttpClient().postAuthenticated(
                        "PlaceOrder", payload, session.getSessionToken());

                String orderId = response.get("norenordno").asText();
                log.info("Shoonya order placed successfully: orderId={}, symbol={}, qty={}",
                        orderId, symbol, orderSize);
                return Optional.of(new ShoonyaOpenOrder(orderId, true, null));

            } catch (ShoonyaApiException e) {
                log.error("Shoonya API error placing order for {}: {}", symbol, e.getMessage());
                return Optional.of(new ShoonyaOpenOrder(null, false, e.getMessage()));
            } catch (IOException e) {
                log.error("Error placing Shoonya order for {}", symbol, e);
                return Optional.of(new ShoonyaOpenOrder(null, false, e.getMessage()));
            }
        }, "placeOrder");
    }

    private static String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        return node.has(field) ? node.get(field).asText() : defaultValue;
    }

    private static int getIntOrDefault(JsonNode node, String field, int defaultValue) {
        return node.has(field) ? node.get(field).asInt(defaultValue) : defaultValue;
    }

    private static double getDoubleOrDefault(JsonNode node, String field, double defaultValue) {
        return node.has(field) ? node.get(field).asDouble(defaultValue) : defaultValue;
    }
}
```

- [ ] **Step 2: Write ShoonyaOrderExecutorTest**

```java
package com.vish.fno.reader.shoonya.core;

import com.vish.fno.reader.shoonya.model.ShoonyaOpenOrder;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoonyaOrderExecutorTest {

    @Test
    void shouldReturnFailedOrderWhenSessionNotInitialised() {
        ShoonyaSession session = new ShoonyaSession(
            "user", "pass", "vendor", "secret", "TOTP", "imei", true
        );
        ShoonyaInstrumentCache cache = new ShoonyaInstrumentCache(java.util.List.of(), session.getHttpClient());
        ShoonyaOrderExecutor executor = new ShoonyaOrderExecutor(session, cache);

        Optional<ShoonyaOpenOrder> result = executor.buyOrder("NIFTY28APR26C22000", 65, "test", true);
        assertTrue(result.isPresent());
        assertFalse(result.get().isOrderPlaced());
        assertEquals("Session not initialized", result.get().errorMessage());
    }

    @Test
    void shouldReturnTestOrderWhenPlaceOrderDisabled() {
        ShoonyaSession session = new ShoonyaSession(
            "user", "pass", "vendor", "secret", "TOTP", "imei", false
        );
        ShoonyaInstrumentCache cache = new ShoonyaInstrumentCache(java.util.List.of(), session.getHttpClient());
        ShoonyaOrderExecutor executor = new ShoonyaOrderExecutor(session, cache);

        // isPlaceOrder=false → test order (success, no actual order)
        Optional<ShoonyaOpenOrder> result = executor.buyOrder("NIFTY28APR26C22000", 65, "test", false);
        assertTrue(result.isPresent());
        assertTrue(result.get().isOrderPlaced());
    }
}
```

- [ ] **Step 3: Run tests**

Run: `mvn test -pl fno-shoonya-reader -Dtest=ShoonyaOrderExecutorTest`
Expected: 2 tests PASS

- [ ] **Step 4: Commit**

```bash
git add fno-shoonya-reader/src/
git commit -m "feat(shoonya): add ShoonyaOrderExecutor for buy/sell market orders"
```

---

### Task 10: ShoonyaService (Public Facade)

**Files:**
- Create: `fno-shoonya-reader/src/main/java/com/vish/fno/reader/shoonya/core/ShoonyaService.java`

- [ ] **Step 1: Create ShoonyaService**

```java
package com.vish.fno.reader.shoonya.core;

import com.vish.fno.model.order.StrikePolicy;
import com.vish.fno.reader.shoonya.model.ShoonyaInstrument;
import com.vish.fno.reader.shoonya.model.ShoonyaOpenOrder;
import com.vish.fno.reader.shoonya.model.ShoonyaOrder;
import com.vish.fno.reader.shoonya.util.ShoonyaOptionPriceUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Public facade for the Shoonya broker integration.
 * Mirrors {@code KiteService} from fno-kite-reader, scoped to orders + instruments.
 *
 * <p>Usage:
 * <pre>
 * ShoonyaService shoonya = new ShoonyaService(
 *     "FA12345", "password", "FNV123", "api_secret",
 *     "TOTP_BASE32_SECRET", "unique-device-id",
 *     nifty100Symbols, false  // placeOrders=false (disabled)
 * );
 * shoonya.authenticate();
 * </pre>
 *
 * @see com.vish.fno.reader.shoonya.core.ShoonyaITMResolver for ITMResolver integration
 */
@Slf4j
public class ShoonyaService {

    private final ShoonyaSession session;
    private final ShoonyaOrderExecutor orderExecutor;
    private final ShoonyaInstrumentCache instrumentCache;

    public ShoonyaService(String userId, String password, String vendorCode,
                          String apiSecret, String totpSecret, String imei,
                          List<String> nifty100Symbols, boolean placeOrders) {
        this.session = new ShoonyaSession(userId, password, vendorCode, apiSecret,
                totpSecret, imei, placeOrders);
        this.instrumentCache = new ShoonyaInstrumentCache(nifty100Symbols, session.getHttpClient());
        this.orderExecutor = new ShoonyaOrderExecutor(session, instrumentCache);
    }

    // --- Auth ---

    public void authenticate() {
        session.authenticate();
    }

    public boolean isInitialised() {
        return session.isInitialised();
    }

    // --- Orders ---

    public Optional<ShoonyaOpenOrder> buyOrder(String symbol, int orderSize, String tag, boolean isPlaceOrder) {
        return orderExecutor.buyOrder(symbol, orderSize, tag, isPlaceOrder);
    }

    public Optional<ShoonyaOpenOrder> sellOrder(String symbol, int orderSize, String tag, boolean isPlaceOrder) {
        return orderExecutor.sellOrder(symbol, orderSize, tag, isPlaceOrder);
    }

    public List<ShoonyaOrder> getOrders() {
        return orderExecutor.getOrders();
    }

    // --- Instruments ---

    public String getITMStock(String indexSymbol, double price, boolean isCall) {
        return ShoonyaOptionPriceUtils.getITMStock(indexSymbol, price, isCall, instrumentCache.getInstruments());
    }

    public String getOTMStock(String indexSymbol, double price, boolean isCall) {
        return ShoonyaOptionPriceUtils.getOTMStock(indexSymbol, price, isCall, instrumentCache.getInstruments());
    }

    public String getOptionStock(String indexSymbol, double price, boolean isCall, StrikePolicy policy) {
        return switch (policy) {
            case ITM_1, ITM_2 -> getITMStock(indexSymbol, price, isCall);
            case OTM_1, OTM_2 -> getOTMStock(indexSymbol, price, isCall);
            case ATM -> getITMStock(indexSymbol, price, isCall);
        };
    }

    public Optional<Long> getInstrument(String symbol) {
        return instrumentCache.getInstrument(symbol);
    }

    public String getSymbol(long token) {
        return instrumentCache.getSymbol(token);
    }

    public List<String> getAllOptionSymbols(String indexSymbol) {
        return instrumentCache.getAllOptionSymbols(indexSymbol);
    }

    public Optional<Integer> getLotSizeFromFuture(String indexName) {
        return instrumentCache.getLotSizeFromFuture(indexName);
    }

    public Map<String, Integer> getAllFutureLotSizeInfo() {
        return instrumentCache.getAllFutureLotSizeInfo();
    }

    public boolean isExpiryDayForOption(String optionSymbol, Date date) {
        return instrumentCache.isExpiryDayForOption(optionSymbol, date);
    }

    public boolean isExpiryDayForIndex(String indexName, Date date) {
        return instrumentCache.isExpiryDayForIndex(indexName, date);
    }

    public List<ShoonyaInstrument> getInstruments() {
        return instrumentCache.getInstruments();
    }

    public int getInstrumentCacheSize() {
        return instrumentCache.getInstrumentMapSize();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add fno-shoonya-reader/src/main/java/com/vish/fno/reader/shoonya/core/ShoonyaService.java
git commit -m "feat(shoonya): add ShoonyaService public facade"
```

---

### Task 11: ShoonyaITMResolver

**Files:**
- Create: `fno-shoonya-reader/src/main/java/com/vish/fno/reader/shoonya/core/ShoonyaITMResolver.java`

- [ ] **Step 1: Create ShoonyaITMResolver**

```java
package com.vish.fno.reader.shoonya.core;

import com.vish.fno.model.helper.ITMResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Shoonya implementation of {@link ITMResolver}.
 * Enables broker-agnostic strategy execution -- strategies call
 * {@code ITMResolver.resolveITMSymbol()} regardless of which broker is active.
 *
 * <p>Usage:
 * <pre>
 * ITMResolver resolver = new ShoonyaITMResolver(shoonyaService);
 * // Same interface as KiteITMResolver -- strategies don't know the difference
 * </pre>
 *
 * @see com.vish.fno.reader.shoonya.core.ShoonyaService
 * @see ITMResolver
 */
@Slf4j
@RequiredArgsConstructor
public class ShoonyaITMResolver implements ITMResolver {

    private final ShoonyaService shoonyaService;

    @Override
    public String resolveITMSymbol(String index, double price, boolean isCall) {
        String symbol = shoonyaService.getITMStock(index, price, isCall);
        log.debug("Resolved Shoonya ITM {} for {} at price {}: {}",
                isCall ? "CALL" : "PUT", index, price, symbol);
        return symbol;
    }

    @Override
    public String resolveOTMSymbol(String index, double price, boolean isCall) {
        String symbol = shoonyaService.getOTMStock(index, price, isCall);
        log.debug("Resolved Shoonya OTM {} for {} at price {}: {}",
                isCall ? "CALL" : "PUT", index, price, symbol);
        return symbol;
    }

    @Override
    public void prepareSymbols() {
        // No WebSocket subscription needed for Shoonya (out of scope)
        // Instrument cache loads lazily on first access
        log.debug("Shoonya prepareSymbols called (no-op: instruments load lazily)");
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add fno-shoonya-reader/src/main/java/com/vish/fno/reader/shoonya/core/ShoonyaITMResolver.java
git commit -m "feat(shoonya): add ShoonyaITMResolver implementing ITMResolver interface"
```

---

### Task 12: Build Verification and PMD Check

- [ ] **Step 1: Compile full project**

Run: `mvn clean compile`
Expected: BUILD SUCCESS (all modules including fno-shoonya-reader)

- [ ] **Step 2: Run all tests in shoonya module**

Run: `mvn test -pl fno-shoonya-reader`
Expected: All tests PASS

- [ ] **Step 3: Run PMD check**

Run: `mvn clean package -pl fno-shoonya-reader`
Expected: BUILD SUCCESS with no PMD violations

If PMD violations occur, fix them (common issues: unused imports, long lines, missing @Override). Re-run until clean.

- [ ] **Step 4: Run full project build**

Run: `mvn clean install`
Expected: BUILD SUCCESS across all 7 modules

- [ ] **Step 5: Final commit with any PMD fixes**

```bash
git add -A fno-shoonya-reader/
git commit -m "fix(shoonya): resolve PMD violations and verify full build"
```

---

### Task 13: Update Module Documentation

**Files:**
- Modify: `CLAUDE.md` (add fno-shoonya-reader to module architecture and quick reference)
- Create: `docs/module-guides/fno-shoonya-reader.md`

- [ ] **Step 1: Update CLAUDE.md module architecture**

Add `fno-shoonya-reader` to the architecture diagram (parallel to fno-kite-reader, depends on fno-utils):

```
fno-models      (foundation - POJOs, interfaces)
    ^
fno-utils       (business utilities, time/candle/file utils)
    ^                   ^
fno-technicals  |    fno-shoonya-reader (Shoonya API - depends only on fno-utils)
    ^                   ^
fno-kite-reader (Kite API - depends only on fno-utils)
    ^
fno-strategy-utils (CPR/PCR, order flow, stop-loss strategies)
    ^
fno-phase-analyzer (Wyckoff analysis, market regimes)
```

Add to Module Quick Reference table:

```
| fno-shoonya-reader | `ShoonyaService` (facade), `ShoonyaITMResolver`, `ShoonyaSession`, `ShoonyaOrderExecutor`, `ShoonyaInstrumentCache` | Shoonya API |
```

- [ ] **Step 2: Create module guide**

Create `docs/module-guides/fno-shoonya-reader.md` with:
- Package overview
- Public API (ShoonyaService, ShoonyaITMResolver)
- Auth flow
- Order execution flow
- Instrument caching
- Thread safety table
- Comparison with fno-kite-reader

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md docs/module-guides/fno-shoonya-reader.md
git commit -m "docs: add fno-shoonya-reader module documentation"
```
