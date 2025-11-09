# Contributing to FnOSdk

Thank you for contributing to FnOSdk! This guide will help you make contributions that maintain code quality and documentation accuracy.

## Code of Conduct

- Write clean, well-documented Java code following PMD conventions
- Follow Spring Boot best practices
- Maintain backward compatibility when possible
- Ensure all tests pass before submitting changes

## Development Workflow

### 1. Setting Up Your Environment

```bash
# Clone the repository
git clone https://github.com/yourusername/FnOSdk.git
cd FnOSdk

# Build all modules
mvn clean install

# Run tests
mvn test
```

### 2. Making Changes

1. Create a feature branch: `git checkout -b feature/your-feature-name`
2. Make your code changes
3. **Update documentation** (see Documentation Requirements below)
4. Write or update unit tests
5. Ensure PMD compliance: `mvn clean package`
6. Commit your changes with descriptive messages

### 3. Submitting Changes

1. Push your branch to the repository
2. Create a Pull Request with a clear description
3. Ensure all CI checks pass
4. Address review feedback

## Documentation Requirements

**CRITICAL**: FnOSdk documentation is consumed by AI agents in downstream projects (like OptionsAnalyzer). Outdated documentation leads to incorrect code generation.

### When to Update Documentation

You **MUST** update documentation when:

1. **Adding new public classes or methods** to any module
2. **Changing method signatures** (parameters, return types)
3. **Modifying behavior** of existing public APIs
4. **Deprecating** or removing APIs
5. **Adding new modules** or packages
6. **Changing dependencies** that affect API usage

### What Documentation to Update

#### Module Guide Files (`docs/module-guides/*.md`)

For each change, update the corresponding module guide:

| Module Changed | Update File |
|----------------|-------------|
| fno-models | `docs/module-guides/fno-models.md` |
| fno-utils | `docs/module-guides/fno-utils.md` |
| fno-technicals | `docs/module-guides/fno-technicals.md` |
| fno-kite-reader | `docs/module-guides/fno-kite-reader.md` |

#### What to Include in Documentation

For each public class/method, document:

1. **Class/Method signature** with full package path
2. **Purpose** and use cases
3. **Parameters** with types and descriptions
4. **Return values** with types
5. **Exceptions** that may be thrown
6. **Code examples** showing typical usage
7. **Integration patterns** for common scenarios

#### Example: Adding a New Indicator

If you add `StochasticOscillator` to fno-technicals:

**Code:**
```java
package com.vish.fno.technical.indicators;

public class StochasticOscillator extends AbstractIndicator {
    public StochasticOscillator(int kPeriod, int dPeriod) { ... }
    public Map<String, List<Double>> calculate(List<Candlestick> candles) { ... }
}
```

**Required Documentation Update in `docs/module-guides/fno-technicals.md`:**

```markdown
#### `StochasticOscillator` - Momentum Indicator
```java
public class StochasticOscillator extends AbstractIndicator {
    public StochasticOscillator(int kPeriod, int dPeriod)

    public Map<String, List<Double>> calculate(List<Candlestick> candles)
}
```

**Parameters:**
- `kPeriod`: Lookback period for %K line (typically 14)
- `dPeriod`: Smoothing period for %D line (typically 3)

**Returns Map with:**
- "k": %K line values
- "d": %D line (signal) values

**Example:**
```java
StochasticOscillator stoch = new StochasticOscillator(14, 3);
Map<String, List<Double>> result = stoch.calculate(candles);

List<Double> kLine = result.get("k");
List<Double> dLine = result.get("d");

double currentK = kLine.get(kLine.size() - 1);
if (currentK < 20) {
    System.out.println("Oversold signal");
}
```
```

### Documentation Checklist

Before committing, verify:

- [ ] All new public classes are documented in the relevant module guide
- [ ] All new public methods have signatures and examples
- [ ] Changed method signatures are updated in documentation
- [ ] Code examples compile and run correctly
- [ ] Integration patterns are provided for complex features
- [ ] `docs/SDK_USAGE.md` is updated if new use cases are added
- [ ] Changelog or version notes mention API changes

## Git Pre-Commit Hook (Recommended)

To automate documentation checks, install the pre-commit hook:

```bash
# From FnOSdk root directory
cp .githooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

This hook will:
- Detect changes to Java files in `src/main/java/`
- Check if corresponding module guide in `docs/module-guides/` was updated
- Warn you if documentation appears outdated
- Allow bypass with `git commit --no-verify` (use sparingly!)

## Code Quality Standards

### PMD Compliance

All code must pass PMD static analysis:

```bash
mvn clean package  # PMD runs during package phase
```

Common PMD rules enforced:
- No unused imports or variables
- Proper exception handling (no empty catch blocks)
- No System.out.println() (use logging)
- Cyclomatic complexity limits
- Method length limits

### Testing Requirements

1. **Unit tests required** for all new features
2. Test coverage should be maintained or improved
3. Tests should follow naming pattern: `*Test.java`
4. Mock external dependencies (Kite API) using Mockito

Example test structure:
```java
public class SimpleMovingAverageTest {
    @Test
    public void testCalculate_withValidData_returnsCorrectSMA() {
        // Arrange
        SimpleMovingAverage sma = new SimpleMovingAverage(3);
        List<Candlestick> candles = createTestCandles();

        // Act
        List<Double> result = sma.calculate(candles);

        // Assert
        assertEquals(expectedValue, result.get(0), 0.01);
    }
}
```

## Breaking Changes

If your change breaks backward compatibility:

1. **Mark old APIs as @Deprecated** first
2. Provide migration path in Javadoc
3. Update `docs/MIGRATION.md` with upgrade instructions
4. Increment version appropriately (major version for breaking changes)
5. Add prominent notes in documentation

Example:
```java
/**
 * @deprecated Use {@link #newMethod(String, int)} instead.
 * This method will be removed in version 2.0.0.
 */
@Deprecated
public void oldMethod(String param) {
    // ...
}
```

## Documentation Review Process

For Pull Requests involving public API changes:

1. Reviewer will check that documentation is updated
2. Examples in documentation should be tested
3. AI agent perspective: "Can an AI generate correct code from this documentation?"
4. Documentation quality is as important as code quality

## Questions?

- Open an issue for clarification
- Check existing documentation in `docs/`
- Review `CLAUDE.md` for development guidelines
- See `docs/AI_AGENT_GUIDE.md` for how documentation is consumed

## Thank You!

Your contributions make FnOSdk better for everyone. Accurate documentation ensures that both humans and AI agents can use FnOSdk effectively.
