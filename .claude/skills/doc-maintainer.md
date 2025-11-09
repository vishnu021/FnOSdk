---
name: doc-maintainer
description: Maintain synchronization between Java public APIs and module guide documentation for FnOSdk
---

# Documentation Maintainer Skill

## Purpose
Ensure `docs/module-guides/*.md` files accurately reflect all public APIs in FnOSdk Java source code.

## When to Use
- After modifying public classes, methods, or constructors in `*/src/main/java/**`
- Before committing code changes
- When explicitly requested via `/update-docs` command
- During release preparation to verify completeness

## Scope
**Include:**
- All files in `*/src/main/java/**/*.java`
- Public classes, interfaces, enums
- Public constructors and methods
- Public static methods
- Spring/Lombok annotations affecting API usage

**Exclude:**
- Files in `*/src/test/**`
- Private/protected/package-private members
- Internal implementation packages (`**/internal/**`, `**/impl/**`)
- Comments-only changes

## Module Mapping
Map Java source files to their corresponding documentation:

| Source Path Pattern | Documentation File |
|---------------------|-------------------|
| `fno-models/src/main/java/**` | `docs/module-guides/fno-models.md` |
| `fno-utils/src/main/java/**` | `docs/module-guides/fno-utils.md` |
| `fno-technicals/src/main/java/**` | `docs/module-guides/fno-technicals.md` |
| `fno-kite-reader/src/main/java/**` | `docs/module-guides/fno-kite-reader.md` |

## Documentation Format

For each public API element, generate documentation in this format:

```markdown
#### `ClassName` - Brief Description

```java
public class ClassName {
    public ClassName(ParamType param1, ParamType param2)
    public ReturnType methodName(ParamType param) throws ExceptionType
    public static ReturnType staticMethod(ParamType param)
}
```

**Parameters:**
- `param1`: Description with type information
- `param2`: Description with type information

**Returns:** Description of return value and type

**Throws:**
- `ExceptionType`: When this exception occurs

**Example:**
```java
// Working, compilable example showing typical usage
ClassName instance = new ClassName(value1, value2);
ReturnType result = instance.methodName(param);

// Show integration with other FnOSdk components if applicable
```

**Edge Cases:**
- Null handling: What happens with null inputs?
- Empty collections: Behavior with empty lists/maps
- Thread safety: Is this thread-safe?
- Performance: Any important performance considerations
```

## Quality Requirements

Every documented API must include:
1. ✅ **Exact signature** - Character-for-character match with source code
2. ✅ **All parameters** - Type and description for each parameter
3. ✅ **Return value** - Type and description of what's returned
4. ✅ **Exceptions** - Document all checked exceptions
5. ✅ **Working example** - Copy-pasteable code that compiles
6. ✅ **Edge cases** - Document null handling, empty inputs, thread safety
7. ✅ **Integration patterns** - Show cross-module usage when applicable

## Process

### 1. Detection
```bash
# Identify changed files
git diff --name-only --staged  # Or use --since HEAD~1
# Filter for Java files in src/main/java/
# Exclude test files and internal packages
```

### 2. Extraction
For each changed Java file:
- Parse package declaration
- Extract public class/interface/enum declarations
- Extract public constructors with parameter lists
- Extract public methods with signatures
- Note Spring annotations (@Service, @Component, etc.)
- Note Lombok annotations (@Data, @Builder, etc.)

### 3. Update Documentation
For the corresponding module guide:
- Locate or create section for the package
- Update or add class documentation using format above
- Ensure alphabetical ordering within sections
- Maintain existing examples and enhance if needed
- Add cross-references to related classes in other modules

### 4. Validation
Before finishing:
- ✓ Verify all changed public APIs are documented
- ✓ Check signature accuracy (exact match)
- ✓ Ensure examples use correct imports
- ✓ Validate no broken cross-references
- ✓ Confirm consistent formatting

### 5. Report
Provide summary:
```
📝 Documentation Update Summary

Modules updated: [list]
Files modified:
  - docs/module-guides/MODULE.md (added X, updated Y methods)

Quality checks:
  ✓ X public classes documented
  ✓ Y methods documented
  ✓ Z examples provided
  ⚠ N items need review (if any)

Suggested commands:
  git add docs/module-guides/MODULE.md
```

## Special Patterns

### For Indicators (fno-technicals)
All technical indicators follow `AbstractIndicator` pattern:
```java
public class IndicatorName extends AbstractIndicator {
    public IndicatorName(int period)
    public List<Double> calculate(List<Candlestick> candles)
    public List<Double> calculateFromClosedPrice(List<Double> prices)
}
```

Document with:
- Standard periods (e.g., RSI typically uses 14)
- Interpretation guide (e.g., RSI < 30 = oversold)
- Multi-timeframe usage examples

### For Order Requests (fno-models)
Order models use Builder pattern:
```java
@Data
@Builder
public class OrderRequestType implements OrderRequest {
    private String field1;
    private Integer field2;
}
```

Document with:
- All builder fields and their purpose
- Required vs optional fields
- Example showing `.builder()...build()` pattern
- Integration with ActiveOrderFactory

### For Services (fno-kite-reader)
Services have error handling:
```java
@Service
public class ServiceName {
    public ReturnType method(Params...) throws KiteException, IOException
}
```

Document with:
- Exception handling examples
- Retry logic when applicable
- Rate limiting considerations

## Error Handling

### Parse Failures
If unable to parse a Java file:
```markdown
<!-- NEEDS REVIEW: Could not parse [ClassName] from [file path] -->
#### `ClassName` - [Stub documentation]
Please manually review and update this section.
```

### Incomplete Documentation
If existing documentation is incomplete:
- Add missing elements (signatures, examples)
- Mark with `<!-- Enhanced by doc-maintainer -->`
- Notify in summary report

### Conflicting Changes
If documentation was manually edited while code changed:
- Preserve manual enhancements
- Update signatures to match code
- Add comment: `<!-- Signature updated, manual content preserved -->`

## Integration with Git Workflow

This skill complements the Git workflow:
1. **Developer makes code changes**
2. **doc-maintainer skill runs** (auto or manual)
3. **Documentation updated** in same session
4. **Developer commits** code + docs together
5. **Pre-commit hook verifies** (backup check)
6. **CI/CD validates** (final enforcement)

## Code Standards for Examples

### Logging Convention
**ALWAYS use Lombok @Slf4j logging** instead of System.out.println:

```java
// ❌ WRONG - Never use System.out.println
System.out.println("Order placed: " + orderId);

// ✅ CORRECT - Use Lombok @Slf4j logger
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrderService {
    public void placeOrder() {
        log.info("Order placed: {}", orderId);
    }
}
```

**Logging levels:**
- `log.info()` - For informational messages (most common in examples)
- `log.debug()` - For detailed debugging info
- `log.warn()` - For warnings
- `log.error()` - For errors

**Benefits:**
- Professional logging framework
- Parameterized messages (safer and faster)
- Configurable log levels
- Consistent with SDK codebase

### Import Convention
**OMIT common Java utility imports** to reduce document weight:

```java
// ❌ DON'T INCLUDE these common imports in examples:
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;

// ✅ DO INCLUDE specialized imports:
import java.util.stream.IntStream;  // Specialized
import java.util.concurrent.TimeUnit;  // Specialized
import java.time.LocalDate;  // Date/time is not obvious
import java.time.format.DateTimeFormatter;  // Specialized
```

**Rationale:**
- List, Map, Set are universally known in Java
- Including them adds unnecessary weight to documentation
- AI agents understand these without explicit imports
- Specialized imports still need to be shown for clarity

**Always include:**
- SDK-specific imports (com.vish.fno.*)
- Lombok imports (lombok.extern.slf4j.Slf4j, lombok.Data, etc.)
- Third-party library imports (com.zerodhatech.*, org.springframework.*, etc.)
- Non-obvious Java imports (java.time.*, java.util.concurrent.*, etc.)

### Example Class Structure
Wrap standalone code examples in proper classes:

```java
// ❌ WRONG - Floating code snippets
double rsi = calculateRSI(candles);
if (rsi < 30) {
    System.out.println("Oversold");
}

// ✅ CORRECT - Proper class with @Slf4j
import com.vish.fno.technical.indicators.RelativeStrengthIndex;
import com.vish.fno.model.Candle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RSIAnalyzer {
    public void analyzeRSI(List<Candle> candles) {
        RelativeStrengthIndex rsi14 = new RelativeStrengthIndex(14);
        List<Double> rsiValues = rsi14.calculate(candles);

        double currentRSI = rsiValues.get(rsiValues.size() - 1);
        if (currentRSI < 30) {
            log.info("Oversold - potential buy signal");
        }
    }
}
```

**Benefits:**
- More realistic and professional examples
- Shows proper class structure
- Demonstrates best practices
- AI agents can copy-paste and run

## Advanced Features

### Bulk Update Mode
Update all modules regardless of recent changes:
```
/update-docs --full
```

### Single Module Mode
Update specific module only:
```
/update-docs fno-technicals
```

### Since Revision Mode
Update based on git range:
```
/update-docs --since HEAD~3
```

## Success Metrics

The skill is working correctly when:
- ✅ 100% of changed public APIs reflected in docs
- ✅ All signatures match source exactly
- ✅ All examples are valid and compile
- ✅ No manual documentation cleanup needed
- ✅ Downstream projects (OptionsAnalyzer) generate correct code

## See Also

- `.claude/agents/fnosdk-doc-watcher.md` - Agent that invokes this skill automatically
- `.claude/commands/update-docs.md` - Slash command interface
- `.claude/agent_policy.md` - When to trigger documentation updates
- `docs/DOCUMENTATION_MAINTENANCE.md` - Overall documentation strategy
