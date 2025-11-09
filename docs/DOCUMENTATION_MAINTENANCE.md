# Documentation Maintenance Guide

This document explains how FnOSdk ensures documentation stays synchronized with code changes.

## Why Documentation Matters

FnOSdk documentation serves three critical audiences:

1. **Human Developers** - Using FnOSdk in their projects
2. **AI Agents** (like Claude Code) - Generating code in downstream projects like OptionsAnalyzer
3. **Build Pipelines** - Automated integration and testing

**Outdated documentation causes:**
- Incorrect code generation by AI agents
- Bugs in consuming applications
- Developer confusion and wasted time
- Loss of trust in the SDK

## Documentation Structure

```
FnOSdk/
├── docs/
│   ├── SDK_USAGE.md              # Entry point for SDK consumers
│   ├── AI_AGENT_GUIDE.md         # Guide for AI agents
│   ├── DOCUMENTATION_MAINTENANCE.md  # This file
│   └── module-guides/
│       ├── fno-models.md         # Complete API reference for fno-models
│       ├── fno-utils.md          # Complete API reference for fno-utils
│       ├── fno-technicals.md     # Complete API reference for fno-technicals
│       └── fno-kite-reader.md    # Complete API reference for fno-kite-reader
├── CLAUDE.md                      # Development guide (internal use only)
└── CONTRIBUTING.md                # Contribution guidelines with doc requirements
```

## Automated Documentation Checks

FnOSdk uses a **three-layer approach** to ensure documentation stays updated:

### Layer 1: Git Pre-Commit Hook (Local)

**Location:** `.githooks/pre-commit`

**What it does:**
- Runs before every local commit
- Detects changes to Java source files
- Identifies which modules were modified
- Checks for public API changes (new classes, methods)
- Verifies corresponding documentation was updated
- Warns if documentation appears outdated

**Installation:**
```bash
# From FnOSdk root
./.githooks/install-hooks.sh
```

**Example workflow:**
```bash
# 1. Add a new indicator class
vim fno-technicals/src/main/java/.../StochasticOscillator.java

# 2. Stage the change
git add fno-technicals/

# 3. Try to commit
git commit -m "Add Stochastic Oscillator indicator"

# 4. Hook detects public API change and warns:
#    ⚠️  WARNING: Public API changed but documentation not updated
#    Please update: docs/module-guides/fno-technicals.md

# 5. Update documentation
vim docs/module-guides/fno-technicals.md
git add docs/module-guides/fno-technicals.md

# 6. Commit succeeds
git commit -m "Add Stochastic Oscillator indicator"
```

**Bypassing (not recommended):**
```bash
git commit --no-verify -m "Emergency fix"
```

### Layer 2: GitHub Actions (CI/CD)

**Location:** `.github/workflows/documentation-check.yml`

**What it does:**
- Runs automatically on every Pull Request
- Analyzes all changed files in the PR
- Checks for public API modifications
- Verifies documentation updates
- Fails the build if critical documentation is missing
- Posts a comment on PR with required actions

**Triggers:**
- Any changes to `fno-*/src/main/java/**`
- Any changes to `docs/module-guides/**`

**Example PR workflow:**
1. Developer creates PR with new feature
2. GitHub Actions runs documentation check
3. If documentation is missing:
   - Build fails ❌
   - Bot comments on PR with required updates
   - PR cannot be merged until documentation is added
4. Developer updates documentation
5. Build passes ✅

### Layer 3: Documentation Guidelines (Human Review)

**Location:** `CONTRIBUTING.md`

**What it provides:**
- Clear rules for when documentation must be updated
- Examples of good documentation
- Templates for documenting new APIs
- Checklist for documentation quality
- Best practices for API examples

**Used by:**
- Contributors writing new features
- Reviewers checking Pull Requests
- Maintainers enforcing quality standards

## Documentation Update Workflow

### When Adding a New Class

**Example:** Adding `StochasticOscillator` to `fno-technicals`

#### 1. Write the Code
```java
package com.vish.fno.technical.indicators;

public class StochasticOscillator extends AbstractIndicator {
    public StochasticOscillator(int kPeriod, int dPeriod) {
        // Implementation
    }

    public Map<String, List<Double>> calculate(List<Candlestick> candles) {
        // Implementation
    }
}
```

#### 2. Update `docs/module-guides/fno-technicals.md`

Add a new section:

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

**Returns:** Map with "k" and "d" keys containing respective line values

**Example:**
```java
StochasticOscillator stoch = new StochasticOscillator(14, 3);
Map<String, List<Double>> result = stoch.calculate(candles);

List<Double> kLine = result.get("k");
List<Double> dLine = result.get("d");

double currentK = kLine.get(kLine.size() - 1);
if (currentK < 20) {
    System.out.println("Oversold");
}
```
```

#### 3. Add Unit Tests
```java
public class StochasticOscillatorTest {
    @Test
    public void testCalculate_withValidData_returnsCorrectValues() {
        // Test implementation
    }
}
```

#### 4. Commit Together
```bash
git add fno-technicals/src/main/java/.../StochasticOscillator.java
git add fno-technicals/src/test/java/.../StochasticOscillatorTest.java
git add docs/module-guides/fno-technicals.md
git commit -m "Add Stochastic Oscillator indicator"
```

### When Modifying an Existing Method

**Example:** Changing `SimpleMovingAverage` to support weighted calculation

#### 1. Update the Code
```java
// Old signature
public List<Double> calculate(List<Candlestick> candles)

// New signature
public List<Double> calculate(List<Candlestick> candles, boolean weighted)
```

#### 2. Update Documentation in `docs/module-guides/fno-technicals.md`

**Before:**
```markdown
#### `SimpleMovingAverage` - SMA Indicator
```java
public SimpleMovingAverage(int period)
public List<Double> calculate(List<Candlestick> candles)
```
```

**After:**
```markdown
#### `SimpleMovingAverage` - SMA Indicator
```java
public SimpleMovingAverage(int period)
public List<Double> calculate(List<Candlestick> candles)
public List<Double> calculate(List<Candlestick> candles, boolean weighted)
```

**Parameters:**
- `candles`: Historical candlestick data
- `weighted` (optional): If true, applies linear weighting to recent prices

**Example:**
```java
SimpleMovingAverage sma = new SimpleMovingAverage(20);

// Standard SMA
List<Double> standardSMA = sma.calculate(candles);

// Weighted SMA (more weight to recent prices)
List<Double> weightedSMA = sma.calculate(candles, true);
```
```

#### 3. Mark Old Method as Deprecated (if removing)
```java
/**
 * @deprecated Use {@link #calculate(List, boolean)} instead.
 * Will be removed in version 2.0.0.
 */
@Deprecated
public List<Double> calculate(List<Candlestick> candles) {
    return calculate(candles, false);
}
```

## Documentation Quality Checklist

Before committing documentation updates, verify:

- [ ] **Complete API coverage** - All public classes and methods documented
- [ ] **Accurate signatures** - Match exactly with code (copy-paste from source)
- [ ] **Parameter descriptions** - Each parameter explained with types
- [ ] **Return value documentation** - What is returned and in what format
- [ ] **Exception documentation** - What exceptions may be thrown
- [ ] **Working examples** - Code examples that compile and run
- [ ] **Integration patterns** - Show how to use in real scenarios
- [ ] **Edge cases** - Document behavior for special inputs (null, empty, etc.)
- [ ] **Dependencies** - Mention required modules or setup
- [ ] **Version notes** - Mark deprecated APIs, breaking changes

## AI Agent Consumption

Documentation in `docs/module-guides/` is consumed by AI agents like Claude Code when working on projects that use FnOSdk.

**How it works:**

1. **OptionsAnalyzer** has `.claude/skills/fnosdk.md` skill
2. Skill references FnOSdk module guides by absolute path
3. When AI agent needs to use FnOSdk API, it reads the relevant guide
4. AI generates code using exact signatures and patterns from documentation
5. Code works correctly because documentation matches reality

**Example from OptionsAnalyzer:**
```markdown
<!-- In OptionsAnalyzer/.claude/skills/fnosdk.md -->

## Knowledge Sources

### Primary References (Read these files as needed)

1. **fno-technicals**: `/Users/vishnushankar/workspace/FnOSdk/docs/module-guides/fno-technicals.md`
   - `SimpleMovingAverage`, `RelativeStrengthIndex`
   - Options Greeks: `Delta`, `Gamma`, `Theta`
```

When Claude Code works on OptionsAnalyzer strategy code:
1. User: "Calculate 50-period SMA for NIFTY"
2. Claude reads `FnOSdk/docs/module-guides/fno-technicals.md`
3. Extracts exact API: `new SimpleMovingAverage(50).calculate(candles)`
4. Generates correct code with proper imports and error handling

**If documentation is outdated:**
- AI generates code using wrong signatures
- Compilation fails or runtime errors occur
- Developer wastes time debugging
- Trust in SDK decreases

## Troubleshooting

### "Pre-commit hook not working"

```bash
# Check if hook is installed
ls -la .git/hooks/pre-commit

# If missing, install:
./.githooks/install-hooks.sh

# Check if executable
chmod +x .git/hooks/pre-commit
```

### "GitHub Actions failing on documentation check"

1. Check the Actions tab on GitHub for specific error message
2. Compare changed Java files with changed documentation files
3. Ensure module guide matches the module that was changed
4. Read the bot comment on PR for specific required updates

### "How do I know what to document?"

1. Read `CONTRIBUTING.md` for detailed guidelines
2. Look at existing documentation in `docs/module-guides/` for examples
3. Ask yourself: "Can someone use this API from just the documentation?"
4. Include working code examples that you've tested

### "My change is internal, do I still need to update docs?"

**No documentation update needed if:**
- Changing private/protected methods
- Refactoring internal implementation
- Updating comments in code
- Fixing typos
- Changing test code only

**Documentation update required if:**
- Adding/modifying public classes, interfaces, enums
- Changing public method signatures
- Adding new parameters or return types
- Changing behavior of public methods
- Deprecating APIs

## Best Practices

### DO

✅ Update documentation in the same commit as code changes
✅ Include working, tested code examples
✅ Document edge cases and error conditions
✅ Use exact class/method signatures from source code
✅ Explain the "why" not just the "what"
✅ Add integration patterns for complex features
✅ Mark deprecated APIs clearly

### DON'T

❌ Commit code without updating corresponding documentation
❌ Use `--no-verify` to bypass pre-commit checks
❌ Write documentation that contradicts the code
❌ Include examples that don't compile
❌ Assume developers will "figure it out"
❌ Document internal implementation details in module guides
❌ Copy-paste documentation from other projects without adapting

## Questions?

- Check `CONTRIBUTING.md` for detailed contribution guidelines
- See existing module guides for documentation examples
- Review `docs/AI_AGENT_GUIDE.md` for AI consumption patterns
- Open an issue if you need clarification
- Ask in Pull Request comments for guidance

## Summary

FnOSdk uses **automated checks** (Git hooks, CI/CD) and **clear guidelines** (CONTRIBUTING.md) to ensure documentation stays synchronized with code.

**For developers:**
- Install Git hooks: `./.githooks/install-hooks.sh`
- Update docs in same commit as code
- Follow examples in existing module guides

**For reviewers:**
- Verify documentation quality in Pull Requests
- Check that examples are accurate and tested
- Ensure documentation serves both humans and AI agents

**Result:** Reliable, well-documented SDK that works correctly for all users.
