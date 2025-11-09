---

name: update-docs
description: Regenerate FnOSdk docs from public Java API changes.
example: /update-docs [module] [--since <rev>] [--staged] [--full]
------------------------------------------------------------------

# Update Documentation Command

Analyzes recent code changes and updates API documentation in `docs/module-guides/` and relevant top-level guides.

## Steps

1. Detect scope (prefer staged, else `HEAD~1`).
2. Extract public APIs from changed Java files (classes, constructors, methods).
3. Update target docs based on module mapping.
4. Validate completeness & enforce quality checklist.
5. Print a **Documentation Update Summary** with recommended `git add` commands.

## Target Docs

* `docs/module-guides/fno-models.md`
* `docs/module-guides/fno-utils.md`
* `docs/module-guides/fno-technicals.md`
* `docs/module-guides/fno-kite-reader.md`
* `docs/SDK_USAGE.md`
* `docs/DOCUMENTATION_MAINTENANCE.md`
* `docs/AI_AGENT_GUIDE.md`

## Usage Examples

```
/update-docs                 # since last commit
/update-docs --staged        # only staged changes
/update-docs fno-technicals  # update one module
/update-docs --since HEAD~3  # compare from earlier rev
/update-docs --full          # full reindex (slow)
```

## Quality Checklist

* [ ] Exact signatures (character-for-character)
* [ ] Params / Returns / Throws documented
* [ ] One realistic, compilable example
* [ ] Edge cases (null/time/precision/threading) noted
* [ ] Integration pattern for cross-module logic

## Output Summary

```
## Documentation Update Summary
- Modules: fno-technicals (1)
- Updated: docs/module-guides/fno-technicals.md
- Missing: none
Next: git add docs/module-guides/fno-technicals.md
```
