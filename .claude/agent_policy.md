---
policy_version: 1.0
auto_trigger: true
applies_to:
  - fnosdk-doc-watcher
manual_commands:
  - /update-docs
---

# FnOSdk Agent Policy - Automatic Documentation Maintenance

## Overview

This policy ensures that **documentation stays synchronized with code** by automatically triggering the `fnosdk-doc-watcher` agent whenever public Java APIs are modified in FnOSdk.

**Principle:** Documentation is not an afterthought—it's part of the code. All public API changes must be reflected in module guides before committing.

## Automatic Trigger Conditions

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

## Integration with Git Workflow

This policy integrates with the three-layer defense system:

### Layer 1: Proactive (This Policy)
- **Real-time** documentation updates during development
- **Automatic** invocation when code changes
- **First line of defense**

### Layer 2: Pre-Commit Hook
- **Local verification** before commit
- **Warns** if documentation missing
- **Backup check** (in `.githooks/pre-commit`)

### Layer 3: CI/CD
- **Remote enforcement** on pull requests
- **Fails build** if docs incomplete
- **Final gate** before merge (in `.github/workflows/documentation-check.yml`)

Together, these layers ensure **zero documentation drift**.

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
1. **100% automation rate** - All public API changes trigger doc update
2. **Zero manual cleanup** - Generated docs are production-ready
3. **Always synchronized** - Docs match code at commit time
4. **Downstream success** - OptionsAnalyzer AI generates correct code
5. **Developer satisfaction** - Minimal friction in development workflow

## Manual Override

Users can bypass or customize behavior:

### Skip Documentation Update
```bash
# For emergency fixes only
git commit --no-verify -m "Emergency fix"
```

### Force Full Documentation Rebuild
```
/update-docs --full
```

### Update Specific Module
```
/update-docs fno-technicals
```

### Review Without Writing
```
/update-docs --dry-run
```

## Maintenance and Evolution

### Policy Updates
This policy should be reviewed and updated when:
- Adding new modules to FnOSdk
- Changing documentation structure
- Improving automation based on feedback
- Anthropic releases new agent policy guidelines

### Version History
- **v1.0** (Current) - Initial comprehensive policy

## See Also

- `.claude/agents/fnosdk-doc-watcher.md` - Agent implementation
- `.claude/skills/doc-maintainer.md` - Documentation skill
- `.claude/commands/update-docs.md` - Manual command interface
- `docs/DOCUMENTATION_MAINTENANCE.md` - Overall documentation strategy
- `.githooks/pre-commit` - Git pre-commit hook
- `.github/workflows/documentation-check.yml` - CI/CD check
