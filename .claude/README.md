# FnOSdk Automated Quality Assurance – Comprehensive README

This README explains how FnOSdk's **automated quality assurance system** works, including code review, testing, and documentation maintenance.

---

## 🧩 Overview

FnOSdk uses **Claude Code** with three specialized agents to maintain code quality, test coverage, and documentation:

1. **code-reviewer** - Automated PMD analysis and code quality checks
2. **test-runner** - Smart test execution with dependency awareness
3. **fnosdk-doc-watcher** - Documentation synchronization

This automation ensures:
- ✅ **High code quality** through automated PMD checks
- ✅ **Comprehensive testing** with smart module selection
- ✅ **Synchronized documentation** for downstream projects (OptionsAnalyzer, TradeSimulator)
- ✅ **Fast feedback** during development

---

## 📂 Components and Responsibilities

| File/Directory                             | Role                                                                           |
| ------------------------------------------ | ------------------------------------------------------------------------------ |
| **Agents**                                  |                                                                                |
| `agents/code-reviewer.md`                  | Code quality agent. Runs PMD analysis and validates FnOSdk patterns.          |
| `agents/test-runner.md`                    | Test automation agent. Smart module selection and dependency-aware testing.    |
| `agents/fnosdk-doc-watcher.md`             | Documentation agent. Syncs docs with code changes automatically.               |
| **Policy**                                  |                                                                                |
| `agent_policy.md`                          | Automation rules (triggers, execution order, safety controls).                 |
| **Skills**                                  |                                                                                |
| `skills/doc-maintainer.md`                 | Documentation generation logic from public APIs.                               |
| **Commands**                                |                                                                                |
| `commands/review.md`                       | Manual `/review` command for code quality checks.                              |
| `commands/test.md`                         | Manual `/test` command for running tests.                                      |
| `commands/update-docs.md`                  | Manual `/update-docs` command for doc regeneration.                            |

---

## ⚙️ How It Works

### Automated Workflow

When you modify Java code, the system automatically:

1. **Code Review** (`code-reviewer`)
   - Detects changed Java files
   - Runs PMD static analysis
   - Validates FnOSdk patterns (Lombok usage, constants, logging)
   - Checks security issues (null handling, hardcoded credentials)
   - Reports violations with fix suggestions

2. **Testing** (`test-runner`)
   - Identifies affected modules based on dependencies
   - Runs tests only for changed code (smart selection)
   - Parses test results and analyzes failures
   - Generates coverage reports (optional)
   - Provides fix suggestions for failed tests

3. **Documentation** (`fnosdk-doc-watcher`)
   - Monitors public API changes
   - Extracts method signatures and parameters
   - Updates corresponding `docs/module-guides/*.md` files
   - Validates documentation completeness
   - Ensures examples compile

4. **Verification**
   - All agents report status and findings
   - Critical issues block commits
   - Actionable fix suggestions provided
   - Reports are concise and developer-friendly

---

## 🧠 Manual Commands

### Code Review
```
/review                    # Review changed files
/review --staged          # Review only staged files
/review fno-technicals    # Review specific module
/review --full            # Full codebase review
```

### Testing
```
/test                      # Test changed modules
/test --all               # Test all modules
/test fno-technicals      # Test specific module
/test --coverage          # Include coverage report
/test SimpleMovingAverageTest  # Run specific test class
```

### Documentation
```
/update-docs              # Update docs for changes
/update-docs --staged     # Only staged changes
/update-docs --full       # Full doc rebuild (slow)
/update-docs fno-technicals  # Specific module only
```

### Recommended Workflow
```
1. Make code changes
2. /review               # Check quality
3. Fix critical issues
4. /test                 # Run tests
5. Fix test failures
6. /update-docs          # Sync documentation
7. git commit            # Commit with confidence
```

---

## 🔍 Agent Details

### **code-reviewer** Agent

**Location:** `.claude/agents/code-reviewer.md`

**Purpose:** Automated code quality analysis

**What it does:**
- Runs PMD static analysis on changed Java files
- Validates FnOSdk-specific patterns (Lombok, constants, logging)
- Checks for security issues (null handling, hardcoded credentials)
- Categorizes violations by priority (Critical, Warning, Info)
- Provides fix suggestions with file:line references

**Safety:** Read-only. Cannot modify code or commit changes.

**Performance:** ~10-30 seconds per module

---

### **test-runner** Agent

**Location:** `.claude/agents/test-runner.md`

**Purpose:** Smart test execution with dependency awareness

**What it does:**
- Detects changed modules and their dependencies
- Runs tests only for affected code (intelligent selection)
- Parses test failures with root cause analysis
- Generates coverage reports (optional via `--coverage`)
- Provides actionable fix suggestions for failures

**Smart Selection Examples:**
- `fno-models` changed → Test all 6 modules (everything depends on it)
- `fno-technicals` changed → Test technicals + strategy-utils + phase-analyzer
- `fno-kite-reader` changed → Test kite-reader only

**Safety:** Mostly read-only. Writes test reports to `target/` but doesn't modify source.

**Performance:** ~5-15 seconds per module, ~30-60 seconds for full suite

---

### **fnosdk-doc-watcher** Agent

**Location:** `.claude/agents/fnosdk-doc-watcher.md`

**Purpose:** Documentation synchronization

**What it does:**
- Monitors public API changes in Java files
- Extracts method signatures, parameters, return types
- Updates corresponding `docs/module-guides/*.md` files
- Validates documentation completeness
- Ensures code examples compile

**Safety:** Writes only to `docs/` directory. Cannot modify source code.

**Performance:** ~1-3 minutes for full documentation rebuild

---

### **agent_policy.md**

**Location:** `.claude/agent_policy.md`

**Purpose:** Central automation policy (v1.1)

**What it defines:**
- When each agent triggers automatically
- Execution order and dependencies
- Safety controls and restrictions
- Success criteria for each agent
- Manual override options

**Key principle:** Quality first → Test early → Document always

---

## 🧪 Testing the Automation

### Test 1 – Code Quality Check

1. Modify a Java file with intentional issues:
   ```java
   public void badMethod() {
       System.out.println("Bad");  // Should use logging
   }
   ```
2. Run `/review`
3. Verify it reports PMD violations and suggests `log.info()` instead

### Test 2 – Smart Test Selection

1. Modify a class in `fno-models`
2. Run `/test`
3. Verify it tests all 6 modules (dependency-aware)
4. Modify a class in `fno-kite-reader`
5. Run `/test`
6. Verify it tests only fno-kite-reader (no dependencies on it)

### Test 3 – Documentation Sync

1. Add a new public method to `SimpleMovingAverage.java`
2. Run `/update-docs`
3. Verify `docs/module-guides/fno-technicals.md` is updated with the new method

### Test 4 – Full Workflow

1. Make code changes
2. Run `/review` → Fix critical PMD violations
3. Run `/test` → Verify all tests pass
4. Run `/update-docs` → Sync documentation
5. Verify all three agents report success

---

## 🧱 Project Layout

```
fnosdk-doc-watcher.md
.claude/
├── agent_policy.md
├── commands/
│   └── update-docs.md
├── skills/
│   └── doc-maintainer.md
└── templates/
    └── module-doc-template.md (optional)
docs/
└── module-guides/
    ├── fno-models.md
    ├── fno-utils.md
    ├── fno-technicals.md
    ├── fno-kite-reader.md
    ├── fno-strategy-utils.md
    └── fno-phase-analyzer.md
```

---

## ✅ Best Practices

* Always commit code and updated docs together.
* Run `/update-docs --full` before tagging a release.
* Use **stubs marked “needs review”** as placeholders for manual doc enhancement.
* Review **Documentation Update Summary** output before committing.
* For CI, configure `/update-docs --dry-run` to verify completeness.

---

## 🩵 Maintenance and Troubleshooting

### Common Issues

| Issue                        | Likely Cause                       | Solution                                                         |
| ---------------------------- | ---------------------------------- | ---------------------------------------------------------------- |
| Docs not updating            | Agent not triggered                | Run `/update-docs` manually                                      |
| Low detail in generated docs | Auto-generated examples too simple | Enhance manually or add template hints                           |
| Skill not found              | Wrong path or permissions          | Verify `.claude/skills/doc-maintainer.md` exists and is readable |

### When to Update the Policy

* New modules added.
* Documentation standards changed.
* Behavior or thresholds modified.

### When to Update the Skill

* Changing output format.
* Improving Java parser logic.
* Adding new annotations or code styles.

---

## 🚀 Future Enhancements

* **CI Integration:** Run `/update-docs --dry-run` on pull requests.
* **Multi-Language Docs:** Extend parser to Python/Kotlin.
* **Telemetry Subagent:** Track doc coverage percentage.
* **Interactive Diff Reporting:** Inline doc vs code delta in summary.

---

### Final Summary

FnOSdk’s documentation automation unites **fnosdk-doc-watcher**, **update-docs**, **doc-maintainer**, and **agent-policy** to form a self-maintaining system that guarantees synchronized source and documentation. Every public API edit automatically updates its Markdown references — ensuring clean, current, and auditable SDK documentation.
