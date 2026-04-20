# Facts (reference view)

This directory is a **reference view** into the canonical facts ledger that
lives in the consumer repo:

    /Users/vishnushankar/workspace/OptionsAnalyzerV2/docs/facts/

Files `domain.md`, `cache-and-state.md`, `performance.md`, `external.md`
and the `adr/` tree (one level up in this repo) are **symlinks** to those
canonical copies. Editing them here edits the canonical files.

If any symlink is broken, your sibling checkout is missing:

    git clone <url> ../OptionsAnalyzerV2

## Repo-local files (NOT symlinked)

- `sdk-public-api.md` — enumerates FnOSdk's publicly-consumed surface with
  `@since` tags and stability markers. Per-repo because each SDK has its
  own public API.

## Why this split

Consumer-side facts (what the app needs to know about the world) belong
with the consumer; the SDK is one of many such facts. SDK-side facts (what
the SDK promises to the world) belong here.

## Entry rules

Consult the canonical README (`../../OptionsAnalyzerV2/docs/facts/README.md`
or via the `domain.md` symlink). Entries ≤4 lines; never silently delete;
superseded entries use ~~strikethrough~~ with dated replacement.

## Cross-repo update

When a canonical fact changes, this repo inherits it via symlink
automatically. No `CHANGELOG` entry needed on the FnOSdk side — but when
`sdk-public-api.md` changes, log the change in `CHANGELOG.md` *and* open
an ADR in the canonical repo per the public-API change rule (see
`../../CLAUDE.md` §"Public-API change rule").
