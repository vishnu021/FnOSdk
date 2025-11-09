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

FnOSdk uses **Claude Code agents** to automatically maintain documentation whenever code changes.

### 🤖 .claude Folder - AI Automation Hub

The `.claude/` directory contains all AI-powered automation for maintaining this repository:

```
.claude/
├── README.md                      # Overview and testing guide
├── agent_policy.md                # When to trigger automation
├── agents/
│   └── fnosdk-doc-watcher.md     # Documentation maintenance agent
├── skills/
│   └── doc-maintainer.md         # Core documentation generation logic
├── commands/
│   └── update-docs.md            # Manual documentation update command
└── SUGGESTED_AGENTS_AND_SKILLS.md # Future automation roadmap
```

### 📋 .claude File Purpose Guide

| File | Purpose | How It Helps |
|------|---------|--------------|
| **agent_policy.md** | Defines WHEN to trigger automation | Automatically detects public API changes and triggers doc updates |
| **agents/fnosdk-doc-watcher.md** | Documentation maintenance agent | Monitors code changes, extracts API signatures, updates docs |
| **skills/doc-maintainer.md** | Core documentation skill | Maps Java files to docs, generates examples, validates quality |
| **commands/update-docs.md** | Manual trigger command | Allows explicit documentation updates via `/update-docs` |
| **README.md** | Setup and testing guide | Explains how to use and test the automation |
| **SUGGESTED_AGENTS_AND_SKILLS.md** | Future automation ideas | Roadmap for code review, testing, build verification agents |

### 🔄 How Automatic Documentation Works

**When you modify a Java file with public API changes:**

1. **Detection** - `agent_policy.md` automatically detects the change
2. **Invocation** - `fnosdk-doc-watcher` agent is triggered
3. **Analysis** - Agent reads Java source, extracts public API signatures
4. **Generation** - `doc-maintainer` skill generates documentation with examples
5. **Update** - Module guides in `docs/module-guides/` are updated
6. **Notification** - You're notified: "✅ Documentation updated"
7. **Commit** - You commit code + docs together

**Zero manual work required!**

### 📝 Files Auto-Updated

* `docs/module-guides/fno-models.md`
* `docs/module-guides/fno-utils.md`
* `docs/module-guides/fno-technicals.md`
* `docs/module-guides/fno-kite-reader.md`

### 🎯 Quality Standards

Every auto-generated documentation includes:
- ✅ Exact method signatures (character-for-character match)
- ✅ All parameters with types and descriptions
- ✅ Return values documented
- ✅ Working, compilable code examples
- ✅ Edge cases (null handling, thread safety)
- ✅ Integration patterns

### 🧭 Manual Triggers

Run these commands in Claude Code to manually update documentation:

```bash
/update-docs                 # Update docs for recent changes
/update-docs --staged        # Only staged files
/update-docs --full          # Rebuild all documentation
/update-docs fno-technicals  # Update specific module only
```

### 🛡️ Multi-Layer Defense

Documentation maintenance has three layers:

1. **Claude Code Agent** (Primary) - Auto-updates as you code
2. **Git Pre-Commit Hook** (Backup) - Warns if docs missing (`.githooks/`)
3. **GitHub Actions CI/CD** (Enforcement) - Fails build if docs incomplete (`.github/workflows/`)

Together, these ensure **zero documentation drift**.

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

## 🤖 AI Automation Details

For comprehensive information about the automation system:

* **Setup & Testing:** `.claude/README.md` - Complete setup guide and examples
* **Agent Policy:** `.claude/agent_policy.md` - Trigger rules and behavior
* **Agent Details:** `.claude/agents/fnosdk-doc-watcher.md` - Documentation agent implementation
* **Skill Logic:** `.claude/skills/doc-maintainer.md` - Core documentation generation
* **Future Plans:** `.claude/SUGGESTED_AGENTS_AND_SKILLS.md` - Roadmap for code review, testing, build agents
* **Development Guide:** `CLAUDE.md` - For FnOSdk internal development
* **Maintenance Guide:** `docs/DOCUMENTATION_MAINTENANCE.md` - Documentation strategy

This automation ensures documentation never goes stale as code evolves.

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
