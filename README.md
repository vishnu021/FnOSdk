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
* Order formatting utilities (CSV export, logging)
* Heikin-Ashi transformations and candle conversions
* Thread-safe caching utilities
* Candle pattern detection utilities

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

**Maven Dependency:**

```xml
<dependency>
  <groupId>com.vish.fno</groupId>
  <artifactId>fno-kite-reader</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

### **fno-strategy-utils**

Advanced strategy utilities for trend analysis, price action, and order flow management.

**Key Features:**

* Target/stop-loss strategies (Fixed, Partial Revising)
* Heikin-Ashi trend detection with weighted scoring
* Central Pivot Range (CPR) calculations
* Support/Resistance level detection
* Price action analysis (maxima/minima, trendlines)
* Order flow management utilities

**Example:**

```java
// Detect trend using Heikin-Ashi
Trend currentTrend = HATrendUtils.getTrend(candles);

// Calculate CPR levels
Map<String, Float> pivots = CPRUtils.getFloorPivots(previousDayCandle);

// Dynamic stop-loss management
PartialRevisingStopLoss strategy = new PartialRevisingStopLoss(dataCache);
```

**Maven Dependency:**

```xml
<dependency>
  <groupId>com.vish.fno</groupId>
  <artifactId>fno-strategy-utils</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
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
* `docs/module-guides/fno-strategy-utils.md`

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

fno-strategy-utils
  ├── fno-technicals
  │   ├── fno-utils
  │   │   └── fno-models
  │   └── fno-models
  ├── fno-utils
  │   └── fno-models
  └── fno-models
```

### Module Responsibilities

| Module            | Role                                          |
| ----------------- | --------------------------------------------- |
| fno-models        | Core domain entities for trading operations   |
| fno-utils         | Helper and strategy utilities                 |
| fno-technicals    | Indicators and Greeks computations            |
| fno-kite-reader   | API and market data integration               |
| fno-strategy-utils| Advanced strategy utilities and order flow    |

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

1. **Algorithmic Trading** – use Kite Reader + Technicals + Strategy Utils
2. **Backtesting Engine** – use Models + Utils + Technicals + Strategy Utils
3. **Market Analysis Tools** – integrate Technicals + Strategy Utils
4. **Risk Management Dashboards** – combine Models + Utils + Strategy Utils
5. **Trend Following Systems** – use Strategy Utils for HA trend analysis
6. **CPR Breakout Trading** – use Strategy Utils for pivot calculations
7. **Partial Profit Booking** – use Strategy Utils order flow management

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

## 🤖 Using FnOSdk Documentation with AI Agents

FnOSdk documentation is **AI-optimized** and designed to be used by AI agents in downstream projects (like Claude Code, GitHub Copilot, or custom AI assistants).

### 📚 For Projects Using FnOSdk

If your project depends on FnOSdk, you can configure your AI agents to use our documentation for accurate code generation.

#### Option 1: Direct Documentation Access

Point your AI agent to the module guides:

```
docs/module-guides/fno-models.md        - Core data models
docs/module-guides/fno-utils.md         - Utility functions
docs/module-guides/fno-technicals.md    - Technical indicators
docs/module-guides/fno-kite-reader.md   - Kite API integration
docs/module-guides/fno-strategy-utils.md - Strategy utilities
```

#### Option 2: Claude Code Integration (Recommended)

For projects using Claude Code, add this to your project's `CLAUDE.md` or `.claude/` configuration:

```markdown
## FnOSdk Integration

This project uses FnOSdk for F&O trading operations. When generating code using FnOSdk:

### Available Documentation
- **Location:** `path/to/FnOSdk/docs/module-guides/`
- **Entry Point:** `path/to/FnOSdk/docs/SDK_USAGE.md`

### Module Guides
- **fno-models:** Data models (Candle, Ticker, OrderRequest, Strategy interfaces)
- **fno-utils:** Utilities (CandleUtils, TimeFrameUtils, JsonUtils, Trend enum)
- **fno-technicals:** Indicators (SMA, EMA, RSI, Bollinger Bands) & Greeks (Delta, Gamma, Theta, Vega, Rho)
- **fno-kite-reader:** Kite Connect integration (KiteService, HistoricalDataService, WebSocket)
- **fno-strategy-utils:** Advanced strategies (HATrendUtils, CPRUtils, DataAnalyser, PartialRevisingStopLoss)

### Important Notes
- All examples use Lombok @Slf4j for logging (never System.out.println)
- Common imports (java.util.List, java.util.Map) are omitted in docs
- All class names are exact (e.g., "Candle" not "Candlestick")
- All examples compile without modification
```

#### Option 3: Git Submodule Approach

Add FnOSdk docs as a git submodule in your project:

```bash
# In your project root
git submodule add <FnOSdk-repo-url> vendor/FnOSdk
git submodule update --init --recursive

# Create symbolic link to docs (optional)
ln -s vendor/FnOSdk/docs docs/fnosdk
```

Then reference in your AI configuration:
```markdown
External SDK documentation available at: `vendor/FnOSdk/docs/module-guides/`
```

### 🎯 AI Prompt Examples

When asking AI agents to generate code using FnOSdk:

**✅ Good Prompts:**
```
"Generate code to calculate 20-period SMA using fno-technicals"
"Create an IndexOrderRequest for buying NIFTY futures"
"Fetch historical data using HistoricalDataService and convert to Candle objects"
"Implement a strategy using the Strategy interface from fno-models"
```

**❌ Avoid These:**
```
"Calculate moving average" (too vague - agent might not know to use FnOSdk)
"Get market data" (doesn't specify which FnOSdk service)
```

### 📖 Documentation Quality Guarantees

FnOSdk documentation is maintained to ensure:

- ✅ **100% Accuracy** - All class names, method signatures match source code exactly
- ✅ **Complete Imports** - All necessary imports included (excluding common Java utils)
- ✅ **Compilable Examples** - Every example can be copy-pasted and runs
- ✅ **Professional Standards** - Uses Lombok @Slf4j logging, Java 17 patterns
- ✅ **Thread Safety Notes** - Documented for all classes
- ✅ **Real-world Examples** - Realistic use cases, not toy examples

### 🔧 Setting Up AI Agent Context

**For Claude Code Users:**

Create a `.claude/context/fnosdk.md` file in your project:

```markdown
# FnOSdk Context

## Module Dependencies
<dependency>
  <groupId>com.vish.fno</groupId>
  <artifactId>fno-utils</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>

## Key Classes Reference
- Candle: `com.vish.fno.model.Candle` (Java record with 7 fields)
- Ticker: `com.vish.fno.model.Ticker` (21 fields, Comparable)
- Strategy: `com.vish.fno.model.strategy.Strategy` (base interface)
- CandleUtils: `com.vish.fno.util.CandleUtils` (static utilities)
- TimeFrameUtils: `com.vish.fno.util.TimeFrameUtils` (candle merging)
- SimpleMovingAverage: `com.vish.fno.technical.indicators.ma.SimpleMovingAverage`
- KiteService: `com.vish.fno.reader.service.KiteService`
- HATrendUtils: `com.vish.fno.strategy.HATrendUtils` (Heikin-Ashi trend analysis)
- CPRUtils: `com.vish.fno.strategy.util.CPRUtils` (Central Pivot Range calculations)
- DataAnalyser: `com.vish.fno.strategy.priceaction.DataAnalyser` (Price action analysis)
- PartialRevisingStopLoss: `com.vish.fno.strategy.orderflow.PartialRevisingStopLoss` (Dynamic stop-loss)

For complete API documentation, see: [path/to/FnOSdk/docs/module-guides/]
```

**For GitHub Copilot Users:**

Add comments at the top of your Java files:

```java
/**
 * This file uses FnOSdk for F&O trading operations.
 *
 * Documentation:
 * - fno-models: Core data models (Candle, Ticker, OrderRequest)
 * - fno-utils: Utilities (CandleUtils, TimeFrameUtils, JsonUtils)
 * - fno-technicals: Indicators (SMA, EMA, RSI) and Greeks
 * - fno-kite-reader: Kite Connect integration
 * - fno-strategy-utils: Strategy utilities (HATrendUtils, CPRUtils, price action)
 *
 * See: docs/module-guides/ for complete API reference
 */
```

### 🚀 Success Metrics

Your AI agent is properly configured when it:
- ✅ Uses correct class names (e.g., `Candle` not `Candlestick`)
- ✅ Includes proper imports (`com.vish.fno.*`, Lombok)
- ✅ Uses Lombok @Slf4j logging instead of System.out.println
- ✅ Generates compilable code without manual fixes
- ✅ Follows Java 17 patterns (records, .toList(), etc.)

### 📞 Support

If AI agents generate incorrect code:
1. Check that class names match docs exactly
2. Verify imports are complete
3. Ensure you're referencing the latest module guides
4. Report issues at: [repository-issues-url]

---

## 🤖 AI Automation Details (For FnOSdk Maintainers)

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
