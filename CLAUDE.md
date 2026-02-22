# CLAUDE.md

FnOSdk development guide for Claude Code. Multi-module Maven SDK for F&O trading on NSE India.

**For SDK usage**: See `docs/SDK_USAGE.md` and `docs/module-guides/*.md`

---

## Tech Stack

- **Java 21** (pattern matching switch, records, `.toList()`)
- **Spring Boot 3.4.1** (parent POM)
- **Lombok** (boilerplate reduction)
- **PMD 7.4.0** (static analysis, fails build on violations)
- **Kite Connect 3.3.2** (fno-kite-reader only)
- **Apache Commons Math3** (fno-technicals only)

---

## Build Commands

```bash
mvn clean install                           # Build all modules
mvn clean install -Dpmd.skip=true           # Skip PMD checks
mvn test                                    # Run all tests
mvn test -Dtest=SimpleMovingAverageTest     # Single test class
mvn test -Dtest=SMATest#testCalculate       # Single test method
mvn clean package                           # Build + PMD analysis
```

---

## Module Architecture

```
fno-models      (foundation - POJOs, interfaces)
    ^
fno-utils       (business utilities, time/candle/file utils)
    ^
fno-technicals  (indicators, Greeks - also depends on fno-models)
    ^
fno-kite-reader (Kite API - depends only on fno-utils)
    ^
fno-strategy-utils (CPR/PCR, order flow, stop-loss strategies)
    ^
fno-phase-analyzer (Wyckoff analysis, market regimes)
```

### Module Quick Reference

| Module | Key Classes | Purpose |
|--------|------------|---------|
| fno-models | `OrderRequest`, `ActiveOrder`, `Candle`, `Ticker` | Core POJOs |
| fno-utils | `TimeUtils`, `CandleUtils`, `PriceUtils`, `FileUtils` | Utilities |
| fno-technicals | `SimpleMovingAverage`, `RSI`, `BlackScholes` | Indicators/Greeks |
| fno-kite-reader | `KiteService` (facade), `KiteSession`, `KiteOrderExecutor`, `HistoricalDataProvider`, `InstrumentCache`, `KiteWebSocket` | Kite API |
| fno-strategy-utils | `CPRUtils`, `PCRUtils`, `TargetAndStopLossStrategy` | Strategy tools |
| fno-phase-analyzer | `WyckoffPhaseIdentifier`, `CompositeWyckoffPhaseIdentifier` | Wyckoff analysis |

---

## Code Standards

### Critical Rules

1. **NO wildcard imports** - ever
   ```java
   // NEVER: import java.util.*;
   // ALWAYS: import java.util.List;
   ```

2. **Optional over null** for public methods that may fail
   ```java
   public static Optional<String> getTime(Date ts) { ... }
   // Use: .map()/.flatMap() chains, NEVER .orElse(null)
   ```

3. **PMD enforced** - build fails on violations
   - No `System.out.println()` or `printStackTrace()`
   - Use `@Slf4j` with `log.info()`, `log.debug()`
   - Preserve stack traces in exception handling

### Java 21 Patterns (Required)

```java
// Pattern matching switch
return switch (orderRequest) {
    case IndexOrderRequest r -> new ActiveIndexOrder(r);
    case OptionBasedOrderRequest r -> new OptionBasedActiveOrder(r);
    default -> throw new IllegalArgumentException("Unknown: " + orderRequest.getClass());
};

// Stream .toList() not .collect(Collectors.toList())
list.stream().filter(...).toList();
```

### Thread Safety

- `ConcurrentHashMap` for shared maps
- `CopyOnWriteArrayList` for read-heavy shared lists
- `volatile` for shared boolean flags
- `Collections.unmodifiableList()` for immutable lists
- Widen parameters: `List` not `ArrayList`

### Extension Patterns

**New Indicator**: Extend `AbstractIndicator`, implement `calculateFromClosedPrice(List<Double>)`

**New Order Type**: Implement `OrderRequest`, add case to `ActiveOrderFactory` pattern matching switch

**toString() in subclasses**: Override `appendToStringFields()` hook, not `toString()`

---

## Common Pitfalls

| Pitfall | Fix |
|---------|-----|
| Wildcard imports | Use explicit imports only |
| `.orElse(null)` on Optional | Use `.map()/.flatMap()` chains |
| `System.out.println()` | Use `@Slf4j` + `log.info()` |
| Catching generic `Exception` | Catch specific exceptions |
| `ArrayList` in parameters | Use `List` interface |
| Forgetting PMD | Always run `mvn clean package` before commit |

---

## Testing

- Test naming: `*Test.java` (e.g., `SimpleMovingAverageTest`)
- Structure mirrors source: `src/test/java/com/vish/fno/`
- Mockito for external dependencies (fno-kite-reader)
- All indicators/utilities require unit tests

---

## Documentation Automation

**After ANY public API change**, invoke the doc-watcher agent:
```
Task(subagent_type="fnosdk-doc-watcher", ...)
```

Manual: `/update-docs`, `/update-docs --staged`, `/update-docs fno-technicals`

See `.claude/agent_policy.md` for automation rules.

---

## Memory Bank Update Trigger

Update this CLAUDE.md when:
- Adding new modules or major classes
- Changing build commands or dependencies
- Discovering new gotchas or patterns
- Updating Java/Spring Boot versions
- Adding new code standards or PMD rules

Use `#` key shortcut during session to auto-incorporate learnings.

---

## Compaction Preservation

**Always preserve in context during long sessions:**
- Module dependency order (fno-models -> fno-utils -> ...)
- Critical rules: no wildcards, Optional over null, PMD enforcement
- Java 21 patterns: pattern matching switch, `.toList()`
- Thread safety conventions
- Current branch and uncommitted changes

**Safe to drop:**
- Detailed module class listings (re-read from code)
- Full code examples (reference CLAUDE.md)
- Documentation automation details (see `.claude/`)

---

## Session Checkpoints

### Starting a Session
1. Check `git status` for uncommitted work
2. Identify current branch context
3. Note any failing tests: `mvn test`

### Before Major Changes
1. Ensure clean build: `mvn clean install`
2. Create checkpoint commit if needed
3. Note files being modified

### Ending a Session
1. Run `mvn clean install` (includes PMD)
2. Update docs if API changed: `/update-docs`
3. Commit with descriptive message or note state for next session

### Resuming After Break
```bash
git status                    # Check state
git log --oneline -5          # Recent commits
mvn test                      # Verify build health
```

---

## Key Files

| Purpose | Location |
|---------|----------|
| PMD rules | `ruleset/pmd-custom-ruleset.xml` |
| Parent POM | `pom.xml` |
| SDK docs | `docs/SDK_USAGE.md` |
| Module guides | `docs/module-guides/*.md` |
| Automation config | `.claude/agent_policy.md` |
