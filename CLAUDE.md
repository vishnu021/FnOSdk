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
- `docs/module-guides/fno-strategy-utils.md` - Strategy utilities for price action, CPR/PCR analysis
- `docs/module-guides/fno-phase-analyzer.md` - Wyckoff phase analysis and market regime identification

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
cd fno-models  # or fno-utils, fno-technicals, fno-kite-reader, fno-strategy-utils, fno-phase-analyzer
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

fno-strategy-utils (depends on fno-technicals, fno-utils, and fno-models)
    ├── fno-technicals
    │   ├── fno-utils
    │   │   └── fno-models
    │   └── fno-models
    ├── fno-utils
    │   └── fno-models
    └── fno-models

fno-phase-analyzer (depends on fno-strategy-utils, fno-technicals, fno-utils, and fno-models)
    ├── fno-strategy-utils
    │   ├── fno-technicals
    │   │   ├── fno-utils
    │   │   │   └── fno-models
    │   │   └── fno-models
    │   ├── fno-utils
    │   │   └── fno-models
    │   └── fno-models
    ├── fno-technicals
    │   ├── fno-utils
    │   │   └── fno-models
    │   └── fno-models
    ├── fno-utils
    │   └── fno-models
    └── fno-models
```

### Module Responsibilities

**fno-models** - Foundation layer with core POJOs and interfaces:
- Order models: `OrderRequest` interface with implementations (`IndexOrderRequest`, `OptionBasedOrderRequest`, `TickBasedOrderRequest`)
- Active order tracking: `ActiveOrder` interface and `AbstractActiveOrder` base class (core order state only)
- Market data structures: `Candle`, `Ticker`, `CompressedTicker`
- Trading instruments: `SymbolData`, `OptionSymbolData`, `OptionMetaData`
- Caching utilities: `LimitedCache` (thread-safe cache with size limits)
- Uses MongoDB integration (spring-boot-starter-data-mongodb)

**fno-utils** - Business logic utilities (depends on fno-models):
- Candlestick utilities: `CandleUtils`, `CandlePatternUtils`, `HeikinAshi` transformations
- Time utilities: `TimeUtils`, `TimeFrameUtils`
- File operations: `FileUtils`, compression via `CompressionUtils`
- Order formatting: `ActiveOrderFormatter` (CSV export, logging utilities)
- JSON serialization: `JsonUtils`
- Data caching: `AbstractDataCache` (thread-safe tick caching)

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

**fno-strategy-utils** - Advanced strategy utilities (depends on fno-technicals, fno-utils, fno-models):
- Price action analysis: `Point`, `ChartPoint`, `Vector2`, `DataAnalyser`, `Line`
- CPR utilities: `CPRUtils` (Central Pivot Range calculations)
- PCR utilities: `PCRUtils` (Put-Call Ratio analysis)
- Trend analysis: `HATrendUtils` (Heikin Ashi trend detection)
- Order flow management: `TargetAndStopLossStrategy` interface, `AbstractTargetAndStopLossStrategy` base class
- Strategy implementations: `FixedTargetAndStopLossStrategy`, `PartialRevisingStopLoss` (dynamic stop-loss strategies)
- Order utilities: `OrderManagerUtils`

**fno-phase-analyzer** - Wyckoff phase analysis and market regime identification (depends on fno-strategy-utils, fno-technicals, fno-utils, fno-models):
- Wyckoff phase models: `WyckoffPhase` (enum), `IWyckoffPhaseIdentifier` (interface), `WyckoffIndicators`
- Classical Wyckoff: `ClassicalWyckoffPhaseIdentifier` (traditional Wyckoff methodology)
- Volume-based analysis: `VolumeBasedWyckoffPhaseIdentifier` (volume profile analysis)
- Heikin Ashi integration: `HeikinAshiWyckoffPhaseIdentifier` (smoothed trend analysis)
- Renko analysis: `RenkoWyckoffPhaseIdentifier` (noise-filtered analysis)
- Structure & swing: `StructureSwingWyckoffPhaseIdentifier` (market structure detection)
- Market Profile: `MarketProfileTPOWyckoffPhaseIdentifier` (Time Price Opportunity analysis)
- Derivatives/Futures: `DerivativesFuturesOIWyckoffPhaseIdentifier` (Open Interest analysis)
- Composite strategy: `CompositeWyckoffPhaseIdentifier` (combines multiple strategies)
- Factory pattern: `WyckoffPhaseIdentifierFactory` (creates appropriate identifiers)

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
- Target/stop-loss logic extracted to `TargetAndStopLossStrategy` (Strategy pattern in fno-strategy-utils)
- Order formatting separated into `ActiveOrderFormatter` utility (fno-utils)

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

### Import Standards

**CRITICAL RULE**: NEVER use wildcard imports for ANY package or class.

- ✅ **DO**: Use explicit imports for everything
  ```java
  // Static imports
  import static com.vish.fno.util.FnoConstants.NIFTY_50;
  import static com.vish.fno.util.FnoConstants.NIFTY_BANK;
  import static com.vish.fno.util.FnoConstants.EQUITY;

  // Standard imports
  import java.util.List;
  import java.util.Map;
  import java.util.ArrayList;
  import java.util.HashMap;
  import java.util.Date;
  ```

- ❌ **DON'T**: Use wildcard imports (applies to ALL packages)
  ```java
  import java.util.*;                              // NEVER
  import static com.vish.fno.util.FnoConstants.*;  // NEVER
  import com.zerodhatech.models.*;                 // NEVER
  ```

**Rationale**:
- Explicit imports improve code readability
- Makes dependencies clear at a glance
- Prevents naming conflicts
- Easier to track which classes are actually used
- Better IDE support for refactoring and unused import detection
- Industry best practice for maintainable code

**Exceptions**: None. This rule applies to all Java code in the project.

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
- Review `docs/CODEBASE_IMPROVEMENT_RECOMMENDATIONS.md` for code quality analysis and enhancement suggestions
- Read `docs/DOCUMENTATION_MAINTENANCE.md` for documentation strategy and maintenance guidelines

---

## Automatic Documentation Maintenance

**🚨 CRITICAL REQUIREMENT - READ THIS FIRST 🚨**

Documentation updates are **MANDATORY** for all public API changes. This is **NON-NEGOTIABLE**.

### STRICT DOCUMENTATION ENFORCEMENT

**⚠️ EVERY TIME you modify ANY public API in `*/src/main/java/**`, you MUST:**

1. ✅ Make your code changes
2. ✅ Fix PMD violations and ensure compilation succeeds
3. ✅ **IMMEDIATELY invoke the `fnosdk-doc-watcher` agent** using the Task tool:
   ```
   Task(subagent_type="fnosdk-doc-watcher", ...)
   ```
4. ✅ Verify documentation was updated in `docs/module-guides/`
5. ✅ Only then mark tasks as complete or suggest commits

**❌ FAILURE TO UPDATE DOCUMENTATION IS A BLOCKING ERROR**

**Rules for Claude Code assistants:**
- ❌ NEVER mark tasks complete without updating docs
- ❌ NEVER suggest commits without verifying docs are synchronized
- ❌ NEVER skip the doc-watcher agent after API changes
- ✅ ALWAYS treat missing documentation as a critical failure
- ✅ ALWAYS launch the agent proactively, not reactively

---

**IMPORTANT**: FnOSdk uses AI-powered automatic documentation maintenance via Claude Code agents.

### Agent-Based Automation

FnOSdk has a `.claude/` directory containing agents, skills, and policies that automate documentation maintenance:

```
.claude/
├── README.md                         # Overview of automation system
├── agent_policy.md                   # Automatic trigger rules
├── agents/
│   └── fnosdk-doc-watcher.md        # Documentation maintenance agent
├── skills/
│   └── doc-maintainer.md            # Documentation maintenance skill
└── commands/
    └── update-docs.md               # Manual documentation update command
```

### How It Works

**Automatic Process:**
1. You modify a Java file with public API changes
2. Agent policy (`.claude/agent_policy.md`) detects the change
3. `fnosdk-doc-watcher` agent is **automatically invoked**
4. Agent uses `doc-maintainer` skill to update documentation
5. Documentation in `docs/module-guides/` is **automatically** updated
6. You commit code + documentation together

**Manual Triggers:**
```bash
/update-docs                    # Update docs for recent changes
/update-docs --staged           # Update docs for staged files only
/update-docs --full             # Full documentation rebuild
/update-docs fno-technicals     # Update specific module only
```

### Documentation Agent

**Agent:** `.claude/agents/fnosdk-doc-watcher.md`

**Responsibilities:**
- Detect public API changes in Java source files
- Extract exact method signatures and parameters
- Update corresponding module guides in `docs/module-guides/`
- Validate documentation completeness and quality
- Ensure downstream AI agents can generate correct code

**Automatic triggers:**
- After modifying files in `*/src/main/java/**`
- Before committing changes
- Before opening pull requests
- On explicit user request

### Documentation Skill

**Skill:** `.claude/skills/doc-maintainer.md`

**Purpose:**
- Core documentation generation logic
- Maps Java source files to module guides
- Enforces documentation quality standards
- Generates working code examples
- Validates completeness

**Quality requirements:**
- ✅ Exact method signatures (character-for-character match)
- ✅ All parameters documented with types
- ✅ Return values documented
- ✅ Working, compilable code examples
- ✅ Edge cases noted (null handling, thread safety)
- ✅ Integration patterns for cross-module features

See `.claude/agent_policy.md` for complete automation rules and `.claude/SUGGESTED_AGENTS_AND_SKILLS.md` for additional automation capabilities.

---

## Available Automation Tools

### Current Agents

**fnosdk-doc-watcher** (`.claude/agents/fnosdk-doc-watcher.md`)
- **Purpose:** Automatic documentation maintenance
- **Triggers:** Public API changes, commit preparation, PR creation
- **Status:** ✅ Active
- **Usage:** Automatic (triggered by agent policy)

### Current Skills

**doc-maintainer** (`.claude/skills/doc-maintainer.md`)
- **Purpose:** Core documentation generation and validation
- **Usage:** Invoked by fnosdk-doc-watcher agent
- **Features:** API extraction, signature validation, example generation

### Current Commands

**Slash Commands:**
- `/update-docs` - Manually trigger documentation update
- `/update-docs --staged` - Update docs for staged files only
- `/update-docs --full` - Rebuild all documentation
- `/update-docs [module]` - Update specific module only

### Suggested Future Agents

See `.claude/SUGGESTED_AGENTS_AND_SKILLS.md` for recommended additional automation:

**High Priority:**
- **code-reviewer** - Automatic code quality and PMD checks
- **test-runner** - Automated testing with smart test selection

**Medium Priority:**
- **build-verifier** - Complete Maven build validation
- **dependency-updater** - Dependency management and updates
- **release-preparer** - Release automation (versioning, changelog, tagging)

**Lower Priority:**
- **example-generator** - Generate realistic code examples
- **migration-helper** - Assist with API migrations

---

## Documentation Organization

### Production Documentation Structure

```
docs/
├── SDK_USAGE.md                       # Main entry point for SDK users
├── AI_AGENT_GUIDE.md                  # Guide for AI agents using the SDK
├── DOCUMENTATION_MAINTENANCE.md       # Documentation maintenance strategy
├── module-guides/                     # Module-specific API documentation
│   ├── fno-models.md                 # Core data models
│   ├── fno-utils.md                  # Utility functions
│   ├── fno-technicals.md             # Technical indicators & Greeks
│   ├── fno-kite-reader.md            # Kite Connect integration
│   ├── fno-strategy-utils.md         # Strategy utilities
│   └── fno-phase-analyzer.md         # Wyckoff phase analysis
└── work-progress/                     # Temporary work-in-progress docs
    ├── DOCUMENTATION_AUDIT_REPORT.md
    ├── DOCUMENTATION_FIX_SUMMARY.md
    ├── PHASE_2A_COMPLETION_SUMMARY.md
    ├── PHASE_2B_COMPLETION_SUMMARY.md
    └── COMPLETE_DOCUMENTATION_OVERHAUL_SUMMARY.md
```

### Documentation Guidelines

**Production Documentation** (commit to git):
- `docs/SDK_USAGE.md` - Main SDK usage guide
- `docs/AI_AGENT_GUIDE.md` - AI agent integration guide
- `docs/module-guides/*.md` - Module API references
- `docs/DOCUMENTATION_MAINTENANCE.md` - Documentation strategy

**Work-Progress Documentation** (temporary, gitignored):
- `docs/work-progress/*` - Session summaries, audit reports, completion notes
- These files document the documentation improvement process
- **Should be in `.gitignore`** - not committed to repository
- Used for tracking progress during documentation overhauls

**When to use `docs/work-progress/`:**
- Creating audit reports of documentation gaps
- Writing session summaries of documentation fixes
- Tracking multi-phase documentation improvements
- Recording completion status for documentation tasks
- Any temporary markdown files that document the documentation process itself

**File Naming Convention:**
- Production: Descriptive names like `SDK_USAGE.md`, `fno-models.md`
- Work-progress: Action-oriented names like `PHASE_2A_COMPLETION_SUMMARY.md`, `DOCUMENTATION_AUDIT_REPORT.md`

### Code Example Standards

**CRITICAL: Documentation must be CONCISE and AI-agent optimized**

**Documentation Style:**
- ❌ NO verbose explanations - use direct technical language
- ❌ NO repetitive examples - one example per class showing 2-3 methods
- ❌ NO redundant descriptions - if method name is clear, minimal description needed
- ❌ NO marketing language - pure technical documentation
- ✅ USE tables for listing >5 methods in a class
- ✅ Group related methods, document collectively
- ✅ Maximum 30-50 lines per class (exceptions: complex service classes)
- ✅ Remove "Introduction", "Overview", "Best Practices" unless essential

**Logging:**
- ✅ **USE** Lombok @Slf4j with `log.info()`, `log.debug()`, etc.
- ❌ **NEVER** use `System.out.println()`

**Imports:**
- ✅ **INCLUDE** SDK imports (com.vish.fno.*), Lombok imports, third-party imports
- ✅ **INCLUDE** specialized Java imports (java.time.*, java.util.concurrent.*, etc.)
- ❌ **OMIT** common Java utility imports (java.util.List, java.util.Map, java.util.Set, java.util.ArrayList, java.util.HashMap)

**Example Structure:**
- ✅ Keep under 20 lines - show USAGE, not implementation
- ✅ ONE example per class demonstrating 2-3 key methods together
- ✅ Use parameterized logging: `log.info("Order: {}", orderId)`
- ✅ Follow Java 17+ patterns (records, .toList(), etc.)
- ✅ Avoid repetitive setup code - show variations inline

See `.claude/agents/fnosdk-doc-watcher.md` for complete standards.

---

## Contributing

When contributing to FnOSdk, please:
1. Follow the coding standards enforced by PMD
2. Write unit tests for new features
3. **Documentation is auto-maintained** - Claude Code will update `docs/module-guides/` automatically when you change public APIs
4. Run `mvn clean install` to ensure all tests pass and PMD checks succeed
5. See `CONTRIBUTING.md` for detailed contribution guidelines including documentation requirements