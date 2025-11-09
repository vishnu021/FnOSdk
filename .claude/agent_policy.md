---

policy_version: 1
auto_trigger: true
manual_commands:

* /update-docs

---

# FnOSdk Agent Policy – Automatic Documentation Maintenance

**Principle:** Documentation is part of the codebase. All public API changes must reflect in module guides.

## Trigger Rules

* Include: `fno-*/src/main/java/**/*.java`, module `pom.xml`.
* Exclude: `**/src/test/**`, `**/internal/**`, `**/impl/**`, comments-only diffs.

## Automatic Actions

1. Detect changed files (prefer staged → else last commit).
2. Run `/update-docs` command.
3. Invoke **doc-maintainer** skill to regenerate relevant docs.
4. Perform completeness and quality validation.
5. Summarize affected modules and updated files.

## Quality Requirements

* Exact signatures.
* Document params, returns, throws.
* Provide one realistic example.
* Mention integration patterns and edge cases.

## Safety Controls

* Writes limited to `docs/**`.
* If Java changes occur without doc updates, suggest `/update-docs --since HEAD~1`.

## Local-Only Failsafe

Prompt the user if missing docs before commit (optional pre-commit hook support).
