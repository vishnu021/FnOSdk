---
policy_version: 1.1
auto_trigger: true
applies_to:
  - fnosdk-doc-watcher
  - code-reviewer
  - test-runner
manual_commands:
  - /update-docs
  - /review
  - /test
---

# FnOSdk Agent Policy - Automated Quality Assurance

## Overview

This policy ensures **code quality, test coverage, and documentation** stay synchronized with code changes by automatically triggering specialized agents for FnOSdk.

**Principles:**
- **Quality first:** Code must pass quality checks before committing
- **Test early:** Run tests as soon as code changes
- **Document always:** Public API changes must update module guides

## Agents Covered

1. **fnosdk-doc-watcher** - Documentation synchronization
2. **code-reviewer** - Code quality and PMD analysis
3. **test-runner** - Automated test execution

## Automatic Trigger Conditions

### Documentation Agent: `fnosdk-doc-watcher`

The `fnosdk-doc-watcher` agent should be **automatically invoked** when:

### 1. File Modifications
Any changes to files matching these patterns:
- **Include:** `fno-*/src/main/java/**/*.java`
- **Include:** `fno-*/pom.xml` (dependency or version changes)
- **Exclude:** `**/src/test/**` (test code)
- **Exclude:** `**/internal/**`, `**/impl/**` (internal packages)
- **Exclude:** Comment-only changes (no code modifications)

### 2. Commit Preparation
When the user:
- Stages files with `git add`
- Explicitly mentions committing changes
- Requests review before commit
- Uses `/update-docs` command

### 3. Pull Request Preparation
Before:
- Creating a pull request
- Releasing a new version
- Merging to main branch

### 4. Explicit User Request
When the user asks to:
- "Update documentation"
- "Sync docs with code"
- "Check documentation completeness"
- "Review API documentation"

## Detection Strategy

### Priority Order
1. **Staged files first** (if any exist)
   ```bash
   git diff --name-only --staged
   ```

2. **Recent commits** (if no staged files)
   ```bash
   git diff --name-only HEAD~1
   ```

3. **Full scan** (on explicit request or before release)
   ```bash
   # Scan all public Java files
   ```

### Change Classification
Classify detected changes:
- **Public API** → **MUST** trigger documentation update
  - New public classes, interfaces, enums
  - New public methods or constructors
  - Modified method signatures
  - Added/removed/renamed public fields
  - Deprecated APIs

- **Internal changes** → **SKIP** documentation update
  - Private/protected method changes
  - Internal refactoring
  - Test code modifications
  - Comment/Javadoc updates only

## Invocation Process

When trigger conditions are met:

### 1. Notify User
```
🔍 Detected public API changes in [module(s)]
📝 Updating documentation automatically...
```

### 2. Invoke Agent
Launch `fnosdk-doc-watcher` agent with appropriate scope:
- **Staged changes:** `--staged` flag
- **Recent commit:** `--since HEAD~1` flag
- **Full scan:** `--full` flag

### 3. Execute Documentation Update
The agent will:
1. Identify affected modules
2. Extract public API signatures
3. Update corresponding module guides
4. Validate completeness and quality
5. Report summary

### 4. Quality Validation
Ensure documentation meets standards:
- ✅ Exact method signatures (character-for-character match)
- ✅ All parameters documented with types
- ✅ Return values documented
- ✅ Exceptions documented (if applicable)
- ✅ Working code example provided
- ✅ Edge cases noted (null handling, thread safety)
- ✅ Integration patterns shown (for cross-module features)

### 5. User Notification
Report results:
```
✅ Documentation updated successfully

Modified files:
  - docs/module-guides/fno-technicals.md (added SimpleMovingAverage example)
  - docs/module-guides/fno-models.md (updated OrderRequest signature)

Quality checks passed:
  ✓ 5 classes documented
  ✓ 12 methods documented
  ✓ 8 examples provided

Ready to commit:
  git add docs/module-guides/fno-technicals.md
  git add docs/module-guides/fno-models.md
```

## Safety Controls

### Write Restrictions
- ✅ **ALLOWED:** Write to `docs/**` only
- ❌ **FORBIDDEN:** Modify `src/**` (Java source code)
- ❌ **FORBIDDEN:** Modify `pom.xml` or build files
- ❌ **FORBIDDEN:** Execute `git push` or remote operations
- ❌ **FORBIDDEN:** Network calls or external API access

### Failure Handling
If documentation update fails:
1. **Log the error** with details
2. **Mark affected files** with `<!-- NEEDS REVIEW -->`
3. **Notify user** with suggested manual fix
4. **Create minimal stub** documentation (if possible)
5. **List unmatched APIs** for user review

Example failure notification:
```
⚠️ Documentation update incomplete

Could not parse:
  - SimpleMovingAverage.java (parse error on line 42)

Manual review needed:
  - docs/module-guides/fno-technicals.md

Suggested action:
  Review and manually update SimpleMovingAverage documentation
```

---

### Code Quality Agent: `code-reviewer`

The `code-reviewer` agent should be **automatically invoked** when:

#### Trigger Conditions:

1. **Before Committing**
   - User stages Java files with `git add`
   - User explicitly mentions "review code" or "check quality"
   - Before running `/update-docs` (quality gate)

2. **File Modifications**
   Any changes to files matching:
   - **Include:** `fno-*/src/main/java/**/*.java`
   - **Exclude:** `**/src/test/**` (unless explicitly requested)

3. **Pull Request Preparation**
   - Before creating a pull request
   - After addressing review feedback
   - Before merging to main branch

4. **Explicit User Request**
   When the user asks to:
   - "Review code quality"
   - "Check for PMD violations"
   - "Analyze code issues"
   - Uses `/review` command

#### Execution Order:

**Recommended workflow:**
1. ✅ Code changes made
2. ✅ `/review` - Check code quality
3. ✅ Fix any critical issues
4. ✅ `/test` - Run tests
5. ✅ `/update-docs` - Update documentation
6. ✅ `git commit` - Commit changes

#### Success Criteria:

- Zero critical PMD violations (Priority 1)
- All FnOSdk patterns validated
- No security issues detected

**Note:** Warnings (Priority 2-3) should be fixed but won't block commits.

---

### Test Automation Agent: `test-runner`

The `test-runner` agent should be **automatically invoked** when:

#### Trigger Conditions:

1. **After Code Changes**
   - Java source files modified in `fno-*/src/main/java/**`
   - After fixing code review issues
   - After modifying public APIs

2. **Before Committing**
   - User stages Java files
   - Before documentation updates (verify examples work)
   - Before creating pull requests

3. **Dependency Changes**
   - `pom.xml` files modified
   - Module dependencies updated
   - Transitive dependency changes

4. **Explicit User Request**
   When the user asks to:
   - "Run tests"
   - "Check if tests pass"
   - "Validate my changes"
   - Uses `/test` command

#### Smart Execution:

**Dependency-aware testing:**
- `fno-models` changed → Test all modules
- `fno-utils` changed → Test utils + dependent modules
- `fno-technicals` changed → Test technicals + strategy-utils + phase-analyzer
- Other modules → Test only that module

#### Success Criteria:

- All tests pass (0 failures)
- No compilation errors
- Coverage meets threshold (if configured)

**Note:** Failed tests will block commits. Fix failures before proceeding.

---

## Integration with Git Workflow

This policy integrates with the three-layer defense system:

### Layer 1: Proactive (This Policy)
- **Real-time** quality checks during development
- **Automatic** invocation of code-reviewer, test-runner, and doc-watcher
- **First line of defense** before commits

### Layer 2: Pre-Commit Hook
- **Local verification** before commit
- **Warns** if documentation missing
- **Backup check** (in `.githooks/pre-commit`)

### Layer 3: CI/CD
- **Remote enforcement** on pull requests
- **Fails build** if docs incomplete
- **Final gate** before merge (in `.github/workflows/documentation-check.yml`)

Together, these layers ensure **zero documentation drift** and **high code quality**.

## Special Handling

### Multi-Module Changes
When multiple modules are modified:
1. Process **all affected modules** in single update
2. Maintain **cross-references** between modules
3. Update **SDK_USAGE.md** if integration patterns change

### Breaking Changes
When API signatures change incompatibly:
1. Mark old signatures as **@Deprecated** in docs
2. Provide **migration guide** with before/after examples
3. Note **version** when deprecation occurred
4. Link to **new API** recommendation

### New Modules
When a new module is added:
1. Create new module guide: `docs/module-guides/MODULE.md`
2. Follow **existing template** structure
3. Add entry to `docs/SDK_USAGE.md` quick reference table
4. Update `CLAUDE.md` with module architecture info

## Proactive Behavior Guidelines

The agent should be **proactive** but **not intrusive**:

### ✅ DO
- Automatically detect and update documentation
- Provide clear summary of changes
- Suggest next steps (git commands)
- Preserve manually-added content
- Enhance incomplete documentation

### ❌ DON'T
- Interrupt user workflow unnecessarily
- Update documentation for private/internal changes
- Modify user's Java source code
- Auto-commit changes without user consent
- Remove manually-added examples or explanations

## Performance Considerations

### Optimization Strategies
- **Cache** parsed Java files to avoid re-parsing
- **Skip** files with no public API changes
- **Batch** updates for multiple files in same module
- **Limit** scope to changed files (avoid full scans unless requested)

### Timeout Handling
If documentation update takes too long:
1. Process **high-priority modules first** (fno-models, fno-utils)
2. **Time-box** each module (max 30 seconds)
3. **Skip** non-critical enhancements (preserve essentials)
4. **Report** what was completed vs. skipped

## Success Criteria

The policy is effective when:
1. **100% automation rate** - All code changes trigger appropriate agents
2. **Zero critical issues** - Code passes quality checks before commit
3. **All tests pass** - No broken functionality after changes
4. **Always synchronized** - Docs match code at commit time
5. **Downstream success** - OptionsAnalyzer AI generates correct code
6. **Developer satisfaction** - Minimal friction, fast feedback

## Manual Override

Users can bypass or customize behavior:

### Skip All Checks (Emergency Only)
```bash
# Bypass pre-commit hooks - USE SPARINGLY
git commit --no-verify -m "Emergency fix"
```

### Manual Agent Invocation

**Code Review:**
```
/review                    # Review changed files
/review --staged          # Review staged files only
/review fno-technicals    # Review specific module
/review --full            # Full codebase review
```

**Testing:**
```
/test                      # Test changed modules
/test --all               # Test all modules
/test fno-technicals      # Test specific module
/test --coverage          # Include coverage report
```

**Documentation:**
```
/update-docs              # Update docs for changes
/update-docs --staged     # Update for staged files
/update-docs --full       # Full doc rebuild
/update-docs fno-technicals  # Specific module
```

## Maintenance and Evolution

### Policy Updates
This policy should be reviewed and updated when:
- Adding new modules to FnOSdk
- Changing documentation structure
- Improving automation based on feedback
- Anthropic releases new agent policy guidelines

### Version History
- **v1.1** (Current) - Added code-reviewer and test-runner agents
- **v1.0** - Initial documentation policy with fnosdk-doc-watcher

## See Also

### Agent Implementations
- `.claude/agents/code-reviewer.md` - Code quality agent
- `.claude/agents/test-runner.md` - Test automation agent
- `.claude/agents/fnosdk-doc-watcher.md` - Documentation agent

### Manual Commands
- `.claude/commands/review.md` - `/review` command
- `.claude/commands/test.md` - `/test` command
- `.claude/commands/update-docs.md` - `/update-docs` command

### Documentation
- `docs/DOCUMENTATION_MAINTENANCE.md` - Documentation strategy
- `.claude/README.md` - Automation system overview
- `.githooks/pre-commit` - Git pre-commit hook
- `.github/workflows/documentation-check.yml` - CI/CD check
