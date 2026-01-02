---
name: review
description: Run code quality review on changed files
usage: |
  /review                    # Review all changed files
  /review --staged          # Review only staged files
  /review fno-technicals    # Review specific module
  /review --full            # Review entire codebase
---

# /review Command

Manually trigger the code-reviewer agent to analyze code quality.

## Usage

```
/review [options] [module]
```

## Options

- `--staged` - Review only files in git staging area
- `--full` - Review entire codebase (slow, ~3-5 minutes)
- `MODULE_NAME` - Review specific module only (e.g., `fno-technicals`)

## Examples

### Review all changed files (staged or recent commit)
```
/review
```

### Review only staged files (fastest during development)
```
/review --staged
```

### Review specific module
```
/review fno-technicals
```

### Review entire codebase (comprehensive, slow)
```
/review --full
```

## What It Does

1. **Detects changed files** in the specified scope
2. **Runs PMD static analysis** on Java source files
3. **Checks FnOSdk patterns** and conventions
4. **Identifies security issues** (hardcoded credentials, null checks)
5. **Generates detailed report** with fix suggestions

## Output Format

```
🔍 Code Review Summary

Files Reviewed: 5
Modules: fno-utils, fno-technicals

PMD Violations:
  ❌ 2 Critical - MUST FIX
  ⚠️  3 Warnings - SHOULD FIX
  ℹ️  1 Info - CONSIDER

Critical Issues:
  [Detailed list with file:line references and fix suggestions]

FnOSdk Pattern Issues:
  [Validation results for SDK patterns]

Security Findings:
  [Any security concerns found]

Recommendations:
  [Action items]
```

## When to Use

- **Before committing** - Catch quality issues early
- **After major changes** - Comprehensive review
- **Before opening PR** - Ensure code meets standards
- **During code review** - Address reviewer feedback

## Integration

This command invokes the **code-reviewer** agent defined in `.claude/agents/code-reviewer.md`.

## Safety

This is a **read-only operation**. It will:
- ✅ Read source files
- ✅ Run PMD analysis
- ✅ Generate reports

It will NOT:
- ❌ Modify any files
- ❌ Commit changes
- ❌ Run builds or tests

## Performance

- **Single module**: ~10-30 seconds
- **Multiple modules**: ~1-2 minutes
- **Full codebase (`--full`)**: ~3-5 minutes

Use `--staged` for fastest feedback during active development.

## Related Commands

- `/test` - Run tests after fixing code issues
- `/update-docs` - Update documentation if APIs changed
- `/build` - Verify full build after fixes
