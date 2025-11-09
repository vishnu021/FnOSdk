# FnOSdk

A comprehensive Java SDK for Futures and Options (F&O) trading and simulation on the National Stock Exchange of India (NSE).

---

## 📘 Overview

FnOSdk is a modular, extensible SDK built to simplify development of **algorithmic trading systems**, **backtesting engines**, and **live simulation frameworks** for Indian markets. It provides ready-to-use models, utilities, and indicators that can integrate into trading or analytics pipelines.

Each module is independently versioned and comes with its own automatically maintained documentation using **Claude subagents**.

---

## 🧩 Modules

### **fno-models**

Contains all foundational data models and POJOs representing instruments, trades, and orders.

**Key Features:**

* Unified market data model (OHLCV, tick, candle)
* Order and position tracking classes
* Instrument metadata, symbol mappings
* JSON-mappable data objects for easy serialization

**Maven Dependency:**

```xml
<dependency>
  <groupId>com.vish.fno</groupId>
  <artifactId>fno-models</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

### **fno-utils**

Provides essential helper utilities and reusable patterns for trading operations.

**Key Features:**

* Date/time handling, file utilities, and compression
* Order management helpers (target/stop-loss strategies)
* Heikin-Ashi transformations and candle conversions
* Thread-safe caching utilities

**Example Usage:**

```java
HeikinAshiTransformer ha = new HeikinAshiTransformer();
List<Candle> haSeries = ha.transform(candles);
```

**Maven Dependency:**

```xml
<dependency>
  <groupId>com.vish.fno</groupId>
  <artifactId>fno-utils</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

### **fno-technicals**

A lightweight and extensible indicator library with Greeks computation support.

**Key Features:**

* Indicators: SMA, EMA, RSI, Bollinger Bands, ATR
* Greeks: Delta, Gamma, Theta, Vega, Rho
* Pluggable indicator architecture (add new metrics easily)
* Thread-safe and backtest-friendly

**Example:**

```java
RelativeStrengthIndex rsi = new RelativeStrengthIndex(14);
double rsiValue = rsi.calculate(closePrices);
```

**Maven Dependency:**

```xml
<dependency>
  <groupId>com.vish.fno</groupId>
  <artifactId>fno-technicals</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

### **fno-kite-reader**

Handles data access and order management using the Zerodha Kite Connect API.

**Key Features:**

* WebSocket streaming for live data
* Historical candle retrieval
* Order placement, cancellation, modification
* PCR and open interest utilities

**Example:**

```java
HistoricalDataService service = new HistoricalDataService(kiteConnect);
List<HistoricalData> data = service.getHistoricalData(
    instrumentToken, startDate, endDate, "day");
```

---

## ⚙️ Requirements

* Java 17 or later
* Maven 3.6+
* Optional: Spring Boot 3.2+ for dependency management

---

## 🏗️ Installation & Build

### Install Locally

```bash
git clone <repository-url>
cd FnOSdk
mvn clean install
```

### Build All Modules

```bash
mvn clean install
```

### Build Single Module

```bash
cd fno-technicals && mvn clean install
```

### Run Tests

```bash
mvn test
```

---

## 📖 Documentation Automation

FnOSdk integrates **Claude’s `doc-watch` subagent** to maintain and update documentation automatically whenever code changes.

### 🔄 Automatic Trigger (via Subagent: `doc-watch`)

**Triggered When:**

* Public Java APIs change in `src/main/java/**`
* Module `pom.xml` files are modified

**Performs:**

* Executes `/update-docs` command automatically.
* Updates corresponding `docs/module-guides/*.md` and project docs.

**Files Auto-Updated:**

* `docs/module-guides/fno-models.md`
* `docs/module-guides/fno-utils.md`
* `docs/module-guides/fno-technicals.md`
* `docs/module-guides/fno-kite-reader.md`
* `docs/SDK_USAGE.md`
* `docs/DOCUMENTATION_MAINTENANCE.md`
* `docs/AI_AGENT_GUIDE.md`

**Auto Trigger YAML:**

```yaml
triggers:
  paths_include:
    - "**/src/main/java/**/*.java"
    - "**/pom.xml"
  paths_exclude:
    - "**/src/test/**"
    - "**/internal/**"
```

---

## 🧭 Manual Triggers

Run these anytime to manually refresh documentation:

```bash
/update-docs                 # Rebuild docs since last commit
/update-docs fno-technicals  # Regenerate docs for a single module
/update-docs --staged        # Only update staged changes
/update-docs --since HEAD~2  # Compare with specific revision
/update-docs --full          # Full reindex (slower)
```

**Run Subagent Directly:**

```bash
claude run subagent doc-watch
```

---

## 💡 Architecture & Design

### Dependency Graph

```
fno-kite-reader
  └── fno-utils
      └── fno-models
fno-technicals
  ├── fno-utils
  │   └── fno-models
  └── fno-models
```

### Module Responsibilities

| Module          | Role                                        |
| --------------- | ------------------------------------------- |
| fno-models      | Core domain entities for trading operations |
| fno-utils       | Helper and strategy utilities               |
| fno-technicals  | Indicators and Greeks computations          |
| fno-kite-reader | API and market data integration             |

### Coding Conventions

* **Clean Code & PMD enforcement**
* **No circular dependencies**
* **Module isolation** for testability

---

## 🧪 Code Quality & Testing

* Static analysis: PMD, SpotBugs, Checkstyle
* Unit and integration tests for all modules
* 80%+ coverage goal for core classes

### Run Quality Checks

```bash
mvn verify -Pcode-quality
```

---

## 💼 Use Cases

1. **Algorithmic Trading** – use Kite Reader + Technicals
2. **Backtesting Engine** – use Models + Utils + Technicals
3. **Market Analysis Tools** – integrate Technicals only
4. **Risk Management Dashboards** – combine Models + Utils

---

## 🔧 Configuration Example (Kite Connect)

```yaml
kite:
  api-key: your-api-key
  access-token: your-access-token
```

---

## 🧩 Integration With Other Projects

FnOSdk is compatible with trading platforms and frameworks like:

* Spring Boot-based analytics services
* Flink or Kafka pipelines for streaming data
* Local Java Swing dashboards or React frontends

---

## 🤖 AI Documentation Workflow

* **Subagent:** `doc-watch` (auto detection)
* **Skill:** `doc-maintainer` (writes module guides)
* **Command:** `/update-docs` (manual trigger)
* **Hooks:** Optional pre-commit validation

This ensures documentation never goes stale as code evolves.

---

## 📜 License

Specify your license here.

---

## 🤝 Contributing

Pull requests are welcome. Always ensure docs are current by running:

```bash
/update-docs --full
```

For feature modules, include example snippets and update their corresponding module guide.
