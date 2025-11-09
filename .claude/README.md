# FnOSdk Documentation Automation – Comprehensive README

This README combines the **existing documentation** with the **latest updates** to explain how the FnOSdk auto-documentation system works, how to use it, and how to troubleshoot or extend it.

---

## 🧩 Overview

FnOSdk uses **Claude Code** and an internal **FnOSdk-Doc-Watcher agent** to automatically maintain documentation for all Java modules. Whenever a public API changes, the system updates corresponding Markdown documentation files under `docs/module-guides/`.

This automation ensures downstream projects (like *OptionsAnalyzer* or *TradeSimulator*) always have accurate, current API references.

---

## 📂 Components and Responsibilities

| File                                       | Role                                                                           |
| ------------------------------------------ | ------------------------------------------------------------------------------ |
| `fnosdk-doc-watcher.md`                    | Agent definition. Watches code changes and triggers doc updates automatically. |
| `.claude/agent_policy.md`                  | Defines automation rules (when to run, what paths to include/exclude, safety). |
| `.claude/skills/doc-maintainer.md`         | Implements the logic for generating documentation from public APIs.            |
| `.claude/commands/update-docs.md`          | Manual command to regenerate docs on demand.                                   |
| `.claude/templates/module-doc-template.md` | Optional shared structure template for consistent doc formatting.              |

---

## ⚙️ How It Works

1. **Detection** – The `fnosdk-doc-watcher` agent monitors Java source paths (`src/main/java/**`) and module `pom.xml` files.
2. **Scope** – It determines whether to update based on staged changes or last commit differences.
3. **Trigger** – On change, it executes `/update-docs`, which orchestrates a regeneration.
4. **Doc Generation** – The `doc-maintainer` skill extracts class/method signatures, parameters, return values, and examples, updating the relevant `docs/module-guides/*.md` files.
5. **Verification** – A **Documentation Update Summary** is printed, listing updated guides and any missing information.

---

## 🧠 Manual Commands

Use the `/update-docs` command manually at any time to force documentation refresh:

```
/update-docs                 # since last commit
/update-docs --staged        # only staged changes
/update-docs <module>        # single module
/update-docs --full          # full reindex (slow)
/update-docs --dry-run       # checks completeness, no writes
```

These commands ensure that even when the automation is disabled or uncertain, you can manually re-generate documentation.

---

## 🔍 File-Level Details

### **fnosdk-doc-watcher.md**

* Defines how the agent detects source changes and when to trigger.
* Auto-triggers `/update-docs` based on recent diffs.
* Restricts write operations to `docs/**`.
* Generates summaries and telemetry logs.

**Recommended Improvements:**

* Add YAML header for metadata (version, name, auto_trigger).
* Add a quick-action table at the top summarizing triggers → scope → command.
* Add telemetry at `.claude/cache/doc-update.log`.

### **.claude/agent_policy.md**

* Defines *when* and *how* automation runs.
* Enforces file safety: writes limited to documentation directories.
* Calls `/update-docs` whenever public API changes.

**Recommended Improvements:**

* Include severity levels for CI (`warn` vs `fail`).
* Add bypass instructions (e.g., using `--no-verify`).
* Optionally allow manual override for large merges.

### **.claude/skills/doc-maintainer.md**

* Core logic for extracting class, constructor, and method info.
* Maps each module to its respective guide (e.g., `fno-technicals` → `docs/module-guides/fno-technicals.md`).
* Generates examples and validation sections.

**Recommended Improvements:**

* Introduce `templates/module-doc-template.md` for consistent formatting.
* Cache parsed symbols for multi-file edits.
* Add *Deprecation/Migration* section for signature changes.

### **.claude/commands/update-docs.md**

* Manual trigger command for regeneration.
* Wraps around `doc-maintainer` and applies the same validation rules.

**Recommended Improvements:**

* Add `--dry-run` option for CI completeness checks.
* Add exit codes (0 = success, 8 = missing entries) for automation pipelines.

---

## 🧪 Testing the Automation

### Test 1 – New Public Class

1. Add a class under `fno-technicals/src/main/java/…/TestIndicator.java`.
2. Save or stage the file.
3. The watcher detects it and regenerates `docs/module-guides/fno-technicals.md`.
4. Verify the new class and method appear with examples.

### Test 2 – Modified Method

1. Change a method signature in `SimpleMovingAverage.java`.
2. Observe auto-update in `docs/module-guides/fno-technicals.md`.
3. Confirm updated signature and example.

### Test 3 – Manual Rebuild

Run `/update-docs --full` to rebuild all guides.

### Test 4 – Completeness Check

Run `/update-docs --dry-run` to confirm 100% of APIs are documented.

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
    └── fno-kite-reader.md
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
