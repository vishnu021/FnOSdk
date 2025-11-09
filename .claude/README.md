# Claude Code Configuration for FnOSdk

This directory contains AI agent configuration for automatic documentation maintenance in FnOSdk.

## Overview

FnOSdk uses Claude Code to **automatically maintain API documentation** when code changes. This ensures downstream projects (like OptionsAnalyzer) always have accurate API references.

## Files in This Directory

```
.claude/
├── README.md                    # This file
├── agent_policy.md              # Automatic behavior rules
├── skills/
│   └── doc-maintainer.md        # Documentation maintenance skill
└── commands/
    └── update-docs.md           # Manual documentation update command
```

## How Automatic Documentation Works

### 1. Agent Policy (`.claude/agent_policy.md`)

**What it does:**
- Defines automatic behaviors for Claude Code
- Triggers documentation updates when public APIs change
- Enforces quality standards
- Ensures completeness

**Key rules:**
- **ALWAYS** update documentation with code changes
- **PROACTIVELY** invoke doc-maintainer skill
- **VALIDATE** completeness before finishing
- **COMMIT** code and docs together

### 2. Documentation Skill (`.claude/skills/doc-maintainer.md`)

**What it does:**
- Scans Java source files for public APIs
- Extracts exact method signatures
- Generates documentation entries
- Creates working code examples
- Validates completeness

**Covers:**
- All public classes, interfaces, enums
- All public methods and constructors
- Builder patterns and factories
- Spring service annotations

### 3. Update Command (`.claude/commands/update-docs.md`)

**Manual trigger:**
```
/update-docs
```

**Use when:**
- Reviewing documentation completeness
- After major refactoring
- Before releasing a version
- When automatic update needs manual review

## Setup and Activation

### Prerequisites

1. **Claude Code installed** and configured
2. **Working in FnOSdk repository**
3. **Git initialized** (for change detection)

### Verification

The system is active by default. To verify:

1. **Check files exist:**
   ```bash
   ls -la .claude/agent_policy.md
   ls -la .claude/skills/doc-maintainer.md
   ls -la .claude/commands/update-docs.md
   ```

2. **Claude Code should acknowledge policy:**
   When opening FnOSdk in Claude Code, it should read:
   - `CLAUDE.md` (mentions automatic documentation)
   - `.claude/agent_policy.md` (behavioral rules)

3. **Test automatic behavior:**
   See "Testing the System" below

## Testing the System

### Test 1: Add a New Class

**Objective:** Verify automatic documentation when adding a new public class.

**Steps:**

1. **Create a new test indicator:**
   ```bash
   # Create file: fno-technicals/src/main/java/com/vish/fno/technical/indicators/TestIndicator.java
   ```

   ```java
   package com.vish.fno.technical.indicators;

   import com.vish.fno.model.Candlestick;
   import java.util.List;

   public class TestIndicator extends AbstractIndicator {
       private final int period;

       public TestIndicator(int period) {
           this.period = period;
       }

       public List<Double> calculate(List<Candlestick> candles) {
           // Test implementation
           return List.of();
       }
   }
   ```

2. **Ask Claude Code to help:**
   ```
   I just created TestIndicator.java. Can you help me complete it?
   ```

3. **Expected behavior:**
   - Claude Code completes the implementation
   - **Automatically** detects it's a new public class
   - **Automatically** invokes doc-maintainer skill
   - **Automatically** updates `docs/module-guides/fno-technicals.md`
   - Provides summary: "Updated documentation for TestIndicator"

4. **Verify documentation:**
   ```bash
   # Check docs/module-guides/fno-technicals.md contains:
   # - TestIndicator class documentation
   # - Constructor documentation
   # - calculate() method documentation
   # - Code example
   ```

5. **Clean up:**
   ```bash
   git checkout -- fno-technicals/src/
   git checkout -- docs/module-guides/fno-technicals.md
   ```

### Test 2: Modify Existing Method

**Objective:** Verify automatic documentation when modifying a method signature.

**Steps:**

1. **Request a change:**
   ```
   Modify SimpleMovingAverage to add a 'weighted' parameter to the calculate method
   ```

2. **Expected behavior:**
   - Claude Code modifies `SimpleMovingAverage.java`
   - **Automatically** detects method signature change
   - **Automatically** updates `docs/module-guides/fno-technicals.md`
   - Updates method signature in docs
   - Updates or adds example showing new parameter

3. **Verify:**
   ```bash
   # Check docs show new signature:
   # public List<Double> calculate(List<Candlestick> candles, boolean weighted)
   ```

4. **Clean up:**
   ```bash
   git checkout -- fno-technicals/src/
   git checkout -- docs/module-guides/fno-technicals.md
   ```

### Test 3: Manual Command

**Objective:** Verify `/update-docs` command works.

**Steps:**

1. **Make a change manually:**
   ```bash
   # Edit fno-models/src/.../IndexOrderRequest.java
   # Add a new field: private String customTag;
   ```

2. **Run command:**
   ```
   /update-docs
   ```

3. **Expected behavior:**
   - Scans fno-models for changes
   - Detects new field in IndexOrderRequest
   - Updates `docs/module-guides/fno-models.md`
   - Provides summary of changes

4. **Clean up:**
   ```bash
   git checkout -- fno-models/src/
   git checkout -- docs/module-guides/fno-models.md
   ```

### Test 4: Completeness Check

**Objective:** Verify documentation covers all public APIs.

**Steps:**

1. **Ask Claude Code:**
   ```
   Check if all public APIs in fno-technicals are documented
   ```

2. **Expected behavior:**
   - Invokes doc-maintainer skill
   - Scans all Java files in fno-technicals
   - Compares with documentation
   - Reports:
     - X/X public classes documented
     - Y/Y public methods documented
     - Lists any missing entries

## Usage Examples

### Example 1: Adding a New Feature

**User request:**
```
Add a MACD indicator to fno-technicals
```

**Claude Code behavior:**
```
1. Creating MACDIndicator.java...
2. Creating MACDIndicatorTest.java...
3. [Auto-detecting public API]
4. [Invoking doc-maintainer skill]
5. Updating docs/module-guides/fno-technicals.md...

✅ MACD indicator implementation complete
✅ Unit tests added
✅ Documentation updated with:
   - MACD class documentation
   - Constructor and method signatures
   - Usage example showing signal calculation
   - Integration pattern with price crossovers

Files ready to commit:
- fno-technicals/src/main/java/.../MACDIndicator.java
- fno-technicals/src/test/java/.../MACDIndicatorTest.java
- docs/module-guides/fno-technicals.md
```

### Example 2: Refactoring

**User request:**
```
Refactor CandleUtils to use streams instead of loops
```

**Claude Code behavior:**
```
Refactoring CandleUtils.java...

[Auto-checking for public API changes]
Public API unchanged (internal implementation only).
No documentation update needed.

✅ Refactoring complete
- Internal loops converted to streams
- Public API signatures unchanged
- Tests still passing
```

### Example 3: Before Release

**User request:**
```
Review and ensure all documentation is complete before releasing v1.1
```

**Claude Code behavior:**
```
[Invoking doc-maintainer skill for comprehensive review]

Scanning all modules:
- fno-models: 12/12 classes documented ✅
- fno-utils: 18/18 classes documented ✅
- fno-technicals: 15/15 classes documented ✅
- fno-kite-reader: 8/8 classes documented ✅

Examples validation:
- All examples validated ✅
- No compilation issues found ✅

Integration patterns:
- Backtesting patterns documented ✅
- Live trading patterns documented ✅
- Order flow patterns documented ✅

Documentation is complete and ready for v1.1 release.
```

## Troubleshooting

### "Documentation not updating automatically"

**Check:**

1. **Agent policy is active:**
   ```bash
   # Verify file exists and is readable
   cat .claude/agent_policy.md | head -20
   ```

2. **Claude Code is reading CLAUDE.md:**
   - CLAUDE.md should reference the agent policy
   - Look for "Automatic Documentation Maintenance" section

3. **Public API changes are detectable:**
   - Changes must be in `src/main/java/` (not test code)
   - Classes/methods must be `public`
   - Files must be modified (git can detect changes)

**Solution:**
```
Manually trigger: /update-docs
```

### "Documentation quality is low"

**Issue:** Auto-generated docs lack detail

**Solution:**

1. **Review generated docs:**
   ```bash
   git diff docs/module-guides/
   ```

2. **Enhance with context:**
   ```
   Please improve the documentation for ClassName with:
   - More detailed parameter explanations
   - Performance considerations
   - Edge case handling
   - More realistic examples
   ```

3. **Add integration patterns:**
   ```
   Add an integration pattern showing how to use ClassName in a trading strategy
   ```

### "Skill not found"

**Error:** Claude Code can't find doc-maintainer skill

**Check:**
```bash
# Verify skill file exists
ls -la .claude/skills/doc-maintainer.md

# Check file permissions
chmod 644 .claude/skills/doc-maintainer.md
```

## Best Practices

### For Developers

1. **Trust the automation** - Let Claude Code update docs automatically
2. **Review the changes** - Always `git diff docs/` before committing
3. **Enhance when needed** - Auto-generated docs are a starting point
4. **Commit together** - Code + docs in same commit

### For Reviewers

1. **Check documentation completeness** in PRs
2. **Validate examples** - Do they make sense?
3. **Verify AI-agent usability** - Can code be generated from docs alone?
4. **Ensure integration patterns** - Complex features need patterns

## Maintenance

### Updating the Agent Policy

**File:** `.claude/agent_policy.md`

**When to update:**
- Changing documentation standards
- Adding new behavioral rules
- Adjusting quality thresholds

**After updating:**
- Test with sample changes
- Verify Claude Code acknowledges new rules

### Updating the Skill

**File:** `.claude/skills/doc-maintainer.md`

**When to update:**
- Adding new modules to FnOSdk
- Changing documentation structure
- Improving extraction logic

**After updating:**
- Run `/update-docs` to test
- Verify documentation quality

## Integration with Git Hooks

This automatic system **complements** the Git pre-commit hooks:

**Git Hooks** (`.githooks/pre-commit`):
- Last line of defense
- Catches cases where auto-update didn't run
- Enforces human review

**Claude Code Automation** (`.claude/`):
- First line of defense
- Proactive documentation updates
- Integrated into development workflow

**Together:** Ensure documentation is never forgotten.

## See Also

- `CONTRIBUTING.md` - Contribution guidelines
- `docs/DOCUMENTATION_MAINTENANCE.md` - Documentation maintenance guide
- `docs/AI_AGENT_GUIDE.md` - How AI agents consume this documentation
- `CLAUDE.md` - Development guide with auto-doc references

## Questions?

- Check existing documentation in `.claude/` files
- Review examples in this README
- Test with small changes first
- Open an issue if behavior is unexpected

---

**Summary:** FnOSdk automatically maintains documentation using Claude Code's agent policy and doc-maintainer skill. Public API changes trigger automatic documentation updates, ensuring downstream projects (like OptionsAnalyzer) always have accurate references.
