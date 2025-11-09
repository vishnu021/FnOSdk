# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when **developing** FnOSdk itself.

---

**🔍 Looking to USE FnOSdk in your project?**

You're in the wrong place! This file is for FnOSdk **internal development** only.

For **using** FnOSdk modules in your application, see:
- **Entry Point**: `docs/SDK_USAGE.md`
- **Module Guides**: `docs/module-guides/*.md`

Each module has a dedicated guide with API reference, examples, and integration patterns:
- `docs/module-guides/fno-models.md` - Core data models
- `docs/module-guides/fno-utils.md` - Utility functions
- `docs/module-guides/fno-technicals.md` - Technical indicators and Greeks
- `docs/module-guides/fno-kite-reader.md` - Kite Connect API integration

---

## Project Overview (for FnOSdk developers)

FnOSdk is a multi-module Maven SDK for Futures and Options (F&O) trading and simulation on the National Stock Exchange of India (NSE). It provides reusable components for building trading applications, backtesting engines, and simulation tools.

**This document covers**: Building, testing, contributing to, and maintaining FnOSdk.

## Build & Development Commands

### Build All Modules
```bash
mvn clean install
```

### Build Individual Module
```bash
cd fno-models  # or fno-utils, fno-technicals, fno-kite-reader
mvn clean install
```

### Run All Tests
```bash
mvn test
```

### Run Tests for Single Module
```bash
cd fno-models
mvn test
```

### Run Single Test Class
```bash
mvn test -Dtest=SimpleMovingAverageTest
```

### Run Specific Test Method
```bash
mvn test -Dtest=SimpleMovingAverageTest#testCalculate
```

### Run PMD Static Analysis
```bash
mvn clean package  # PMD runs automatically during package phase
```

### Skip PMD During Build
```bash
mvn clean install -Dpmd.skip=true
```

## Architecture & Module Dependencies

### Dependency Hierarchy
```
fno-kite-reader (depends on fno-utils)
    └── fno-utils (depends on fno-models)
        └── fno-models (foundation, no dependencies)

fno-technicals (depends on fno-utils and fno-models)
    ├── fno-utils
    │   └── fno-models
    └── fno-models
```

### Module Responsibilities

**fno-models** - Foundation layer with core POJOs and interfaces:
- Order models: `OrderRequest` interface with implementations (`IndexOrderRequest`, `OptionBasedOrderRequest`, `TickBasedOrderRequest`)
- Active order tracking: `ActiveOrder` interface and `AbstractActiveOrder` base class
- Market data structures: `Candle`, `Ticker`, `CompressedTicker`
- Trading instruments: `SymbolData`, `OptionSymbolData`, `OptionMetaData`
- Uses MongoDB integration (spring-boot-starter-data-mongodb)

**fno-utils** - Business logic utilities (depends on fno-models):
- Candlestick utilities: `CandleUtils`, `HeikinAshi` transformations
- Time utilities: `TimeUtils`, `TimeFrameUtils`
- File operations: `FileUtils`, compression via `CompressionUtils`
- Order flow strategies: `TargetAndStopLossStrategy` (target/stop-loss management)
- Options utilities: `OptionsMetaDataUtils`
- JSON serialization: `JsonUtils`

**fno-technicals** - Technical analysis and mathematical calculations (depends on fno-utils, fno-models):
- Base indicator framework: `Indicator` interface → `AbstractIndicator` base class
- Moving averages: `MovingAverage` (abstract) → `SimpleMovingAverage`, `ExponentialMovingAverage`, `SmoothedMovingAverage`
- Other indicators: `RelativeStrengthIndex`, `BollingerBands`
- Options Greeks: `BlackScholes`, `Delta`, `Gamma`, `Theta`, `Vega`, `Rho` (all implement `OptionGreek`)
- Uses Apache Commons Math3 for mathematical operations

**fno-kite-reader** - External API integration for live trading (depends on fno-utils):
- Zerodha Kite Connect API integration (version 3.3.2)
- Services: `KiteService`, `HistoricalDataService`, `KiteWebSocket`, `InstrumentCache`
- Market data retrieval and real-time WebSocket streaming
- Order placement and management: `OrderUtils`
- Utilities: `InstrumentFileUtils`, `OptionPriceUtils`

## Key Architecture Patterns

### Indicator Extensibility
All technical indicators extend `AbstractIndicator` which implements `Indicator`. The framework provides:
- Standard `calculate()` methods accepting `List<Candle>`
- Helper methods like `getClosedPrices()` for extracting price data
- Support for calculations with or without previous day data

To add a new indicator:
1. Extend `AbstractIndicator`
2. Implement `calculateFromClosedPrice(List<Double>)` and overloaded versions
3. Add unit tests following existing patterns (e.g., `SimpleMovingAverageTest`)

### Order Request Pattern
Orders follow an interface-based design:
- `OrderRequest` interface defines contract
- Concrete implementations: `IndexOrderRequest`, `OptionBasedOrderRequest`, `TickBasedOrderRequest`
- Factory pattern: `ActiveOrderFactory` converts requests to active orders
- Active orders use inheritance: `AbstractActiveOrder` → specific implementations

### Configuration & Integration
- Spring Boot 3.2.2 as parent POM
- Java 17 required (uses modern features like `stream().toList()`)
- Lombok for reducing boilerplate
- Kite Connect credentials needed for fno-kite-reader (API key + access token)

## Code Quality Standards

PMD static analysis enforces strict rules (configured in `ruleset/pmd-custom-ruleset.xml`):
- Best practices: avoid parameter reassignment, use collection.isEmpty(), prefer varargs
- Code simplification: use foreach loops, avoid unnecessary locals
- Exception handling: preserve stack traces, avoid catching generic exceptions
- Performance: optimize string operations, avoid array loops where applicable
- Multithreading: proper synchronization, avoid deprecated thread methods
- Logging: no printStackTrace() or System.out.println()

PMD runs automatically during `mvn package` phase and will fail the build on violations.

## Testing Conventions

- Tests follow naming pattern: `*Test.java` (e.g., `SimpleMovingAverageTest`)
- Unit tests for all indicators and utilities
- Mock-based testing with Mockito for external dependencies (fno-kite-reader)
- Test files mirror source structure: `src/test/java/com/vish/fno/`

## Common Use Cases

1. **Live Trading**: Use fno-kite-reader for real-time data and execution
2. **Backtesting**: Use fno-models + fno-utils + fno-technicals without external API
3. **Technical Analysis**: Import fno-technicals for indicator calculations
4. **Strategy Development**: Combine all modules, implement custom `OrderRequest` types

---

## Documentation

All API documentation, usage examples, and integration patterns are maintained in `docs/`:
- Start with `docs/SDK_USAGE.md` for SDK consumer guide
- See `docs/module-guides/*.md` for detailed API references
- Check `docs/AI_AGENT_GUIDE.md` for AI agent integration instructions

---

## Automatic Documentation Maintenance

**IMPORTANT**: FnOSdk uses AI-powered automatic documentation maintenance.

### Agent Policy

This repository has `.claude/agent_policy.md` that **automatically triggers documentation updates** when you modify public APIs.

**How it works:**
1. You modify a Java file with public API changes
2. Claude Code **automatically** detects the change
3. Claude Code **automatically** invokes the `doc-maintainer` skill
4. Documentation in `docs/module-guides/` is **automatically** updated
5. You commit code + documentation together

**Manual trigger:**
```
/update-docs
```

Or use the skill directly:
```
Use the doc-maintainer skill to update documentation
```

### Documentation Skill

**Skill location:** `.claude/skills/doc-maintainer.md`

**Purpose:**
- Scan Java source files for public API changes
- Extract exact method signatures
- Update module guides with complete API documentation
- Validate completeness and quality
- Ensure AI agents in downstream projects (like OptionsAnalyzer) can generate correct code

**When it runs:**
- Automatically after modifying public classes/methods
- Before completing tasks that changed public APIs
- On explicit user request (`/update-docs`)

### Quality Standards

All documentation updates must include:
- Exact method signatures (copy from source)
- All parameters documented with types
- Return values documented
- Working code examples
- Integration patterns for complex APIs

See `.claude/agent_policy.md` for complete behavioral rules.

---

## Contributing

When contributing to FnOSdk, please:
1. Follow the coding standards enforced by PMD
2. Write unit tests for new features
3. **Documentation is auto-maintained** - Claude Code will update `docs/module-guides/` automatically when you change public APIs
4. Run `mvn clean install` to ensure all tests pass and PMD checks succeed
5. See `CONTRIBUTING.md` for detailed contribution guidelines including documentation requirements