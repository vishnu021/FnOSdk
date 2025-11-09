# Git Hooks for FnOSdk

This directory contains Git hooks to maintain documentation quality.

## Pre-Commit Hook

The pre-commit hook checks that documentation is updated when public APIs change.

### What It Does

1. Detects changes to Java source files in `src/main/java/`
2. Identifies which modules (fno-models, fno-utils, fno-technicals, fno-kite-reader) were modified
3. Checks if public classes or methods were added/changed
4. Verifies that corresponding documentation in `docs/module-guides/` was updated
5. Warns if documentation appears outdated
6. Allows bypass with confirmation (not recommended)

### Installation

**Option 1: Manual Installation**
```bash
# From FnOSdk root directory
cp .githooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

**Option 2: Automated Installation**
```bash
# From FnOSdk root directory
./.githooks/install-hooks.sh
```

### Testing the Hook

1. Modify a public class in any module (e.g., add a new method to `SimpleMovingAverage`)
2. Stage the change: `git add fno-technicals/src/main/java/...`
3. Try to commit: `git commit -m "Add new method"`
4. Hook will warn you to update `docs/module-guides/fno-technicals.md`

### Bypassing the Hook

In rare cases where you need to commit without documentation updates:

```bash
git commit --no-verify -m "Your message"
```

**⚠ Warning**: Only bypass when absolutely necessary. Outdated documentation causes issues for SDK consumers and AI agents.

### Uninstalling

```bash
rm .git/hooks/pre-commit
```

## Why This Matters

FnOSdk documentation is consumed by:
- Human developers using the SDK
- AI agents (like Claude) generating code in downstream projects
- Build pipelines and automation tools

Outdated documentation leads to:
- Incorrect code generation by AI agents
- Developer confusion and bugs
- Wasted time debugging SDK usage issues

By keeping documentation in sync with code, we ensure FnOSdk remains reliable and easy to use.
