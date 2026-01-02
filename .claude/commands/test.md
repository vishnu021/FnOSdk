---
name: test
description: Run tests for changed modules with intelligent selection
usage: |
  /test                      # Test changed modules
  /test --all               # Test all modules
  /test fno-technicals      # Test specific module
  /test --coverage          # Include coverage report
  /test SimpleMovingAverageTest  # Run specific test class
---

# /test Command

Manually trigger the test-runner agent to execute tests with smart module selection.

## Usage

```
/test [options] [module|test-class]
```

## Options

- `--all` - Run tests for all modules
- `--coverage` - Include Jacoco coverage report
- `--fast` - Skip integration tests for quick feedback
- `MODULE_NAME` - Test specific module (e.g., `fno-technicals`)
- `TestClassName` - Run specific test class

## Examples

### Test only changed modules (smart, recommended)
```
/test
```

### Test all modules (comprehensive)
```
/test --all
```

### Test specific module
```
/test fno-technicals
```

### Test specific module with coverage
```
/test --coverage fno-technicals
```

### Test specific test class
```
/test SimpleMovingAverageTest
```

### Quick test (skip integration tests)
```
/test --fast
```

## What It Does

1. **Detects changed modules** based on git diff
2. **Analyzes dependencies** to determine test scope
3. **Executes tests** for affected modules
4. **Parses results** and identifies failures
5. **Analyzes failures** with fix suggestions
6. **Reports coverage** (if requested)

## Output Format

### Success:
```
✅ All Tests Passed

Module: fno-technicals
Tests: 45 passed, 0 failed, 0 skipped
Time: 12.3s
Coverage: 87%

Total: 45 tests passed in 12.3s
Ready to commit ✓
```

### Failures:
```
❌ Test Failures Detected

Module: fno-technicals
Tests: 43 passed, 2 failed, 0 skipped
Time: 14.7s

Failed Tests:

1. SimpleMovingAverageTest.testCalculate_withNullInput
   Expected: IllegalArgumentException
   Actual: NullPointerException at SimpleMovingAverage.java:42

   Fix suggestion:
     Add null check at method start

2. BollingerBandsTest.testCalculate_withInsufficientData
   Expected: result.size() >= 20
   Actual: result.size() = 15

   Fix suggestion:
     Update test data or adjust expectations

Action required:
  Fix 2 failing tests before committing
  Re-run: /test fno-technicals
```

## Smart Module Selection

The agent understands FnOSdk's module dependency structure:

```
fno-models (changed) → Test all modules (everything depends on it)
fno-utils (changed) → Test utils + technicals + kite-reader + strategy-utils + phase-analyzer
fno-technicals (changed) → Test technicals + strategy-utils + phase-analyzer
fno-kite-reader (changed) → Test kite-reader only
fno-strategy-utils (changed) → Test strategy-utils + phase-analyzer
fno-phase-analyzer (changed) → Test phase-analyzer only
```

## When to Use

- **After code changes** - Verify functionality still works
- **Before committing** - Ensure tests pass
- **After fixing bugs** - Validate fix
- **After code review** - Run after addressing feedback
- **Before opening PR** - Comprehensive validation

## Performance

- **Single module**: ~5-15 seconds
- **Multiple modules (parallel)**: ~15-30 seconds
- **All modules**: ~30-60 seconds
- **With coverage**: +20-30% time overhead

## Coverage Reporting

When using `--coverage`, you get:

```
📊 Test Coverage Report

Module: fno-technicals
  Overall: 87% (target: 80%) ✓
  Classes: 42/45 covered (93%)
  Lines: 1,847/2,123 covered (87%)

Uncovered classes:
  - StochasticOscillator (0% - no tests)
  - MACD (0% - no tests)

Suggested additions:
  Add tests for StochasticOscillator and MACD
```

## Integration

This command invokes the **test-runner** agent defined in `.claude/agents/test-runner.md`.

## Safety

This is a **mostly read-only operation**. It will:
- ✅ Read source and test files
- ✅ Execute tests (may write test reports to `target/`)
- ✅ Generate coverage reports

It will NOT:
- ❌ Modify source or test code
- ❌ Commit changes
- ❌ Clean or delete files
- ❌ Modify pom.xml

## Troubleshooting

### Tests fail to compile
```
Fix: Run mvn clean compile first
Check: Syntax errors in source code
```

### Tests fail unexpectedly
```
Fix: Review failure analysis in report
Check: Recent code changes
Run: /review to check code quality
```

### Coverage below target
```
Fix: Add tests for uncovered classes
Check: Coverage report for specific gaps
Focus: High-value, complex logic first
```

## Related Commands

- `/review` - Check code quality before testing
- `/update-docs` - Update docs if tests revealed API changes
- `/build` - Full build verification after tests pass
