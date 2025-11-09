# Skill: doc-maintainer (FnOSdk)

Purpose: Keep `docs/module-guides/*.md` in sync with **public APIs**.

## Inputs
- Changed paths (git diff)
- Optional flags: `--module`, `--since <rev>`, `--staged`, `--full`

## Source Scan (public API only)
For each changed `src/main/java/**.java`:
- Capture `package`, `public class|interface|enum`, public constructors, public/static methods.
- Note Spring/Lombok annotations that affect usage patterns.

Ignore private/protected, tests, and impl/internal packages.

## Module Routing
- Paths starting with `fno-models/`       → `docs/module-guides/fno-models.md`
- Paths starting with `fno-utils/`        → `docs/module-guides/fno-utils.md`
- Paths starting with `fno-technicals/`   → `docs/module-guides/fno-technicals.md`
- Paths starting with `fno-kite-reader/`  → `docs/module-guides/fno-kite-reader.md`

## Doc Update Rules
- Create/refresh a **per-package** section; alphabetize classes.
- For each public type, emit a block:

```md
#### `ClassName` — One-line purpose
```java
public class ClassName {
  public ClassName(Arg1 a1, Arg2 a2)
  public Return method(Arg a) throws X
  public static Return util(Arg a)
}
