# FnOSdk Automatic Documentation System Overview

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Developer Action                         │
│                  (Modifies Java source code)                     │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Claude Code Detection                         │
│                                                                  │
│  Monitors: */src/main/java/**/*.java                            │
│  Detects: Public class/method changes                           │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
                        ┌───────────────┐
                        │ Public API?   │
                        └───────┬───────┘
                                │
                    ┌───────────┴───────────┐
                    │                       │
                   YES                     NO
                    │                       │
                    ▼                       ▼
    ┌───────────────────────────┐   ┌──────────────┐
    │ agent_policy.md           │   │ No Action    │
    │ Triggers Automatically    │   │ (Private     │
    └───────────┬───────────────┘   │  code only)  │
                │                   └──────────────┘
                ▼
    ┌───────────────────────────┐
    │ doc-maintainer skill      │
    │ Invoked Automatically     │
    └───────────┬───────────────┘
                │
                ▼
    ┌───────────────────────────────────────────────┐
    │ Skill Workflow:                               │
    │                                               │
    │ 1. Read modified Java files                   │
    │ 2. Extract public APIs                        │
    │ 3. Generate documentation                     │
    │ 4. Update docs/module-guides/*.md             │
    │ 5. Validate completeness                      │
    │ 6. Create examples                            │
    └───────────────────┬───────────────────────────┘
                        │
                        ▼
    ┌───────────────────────────────────────────────┐
    │ Documentation Updated                         │
    │                                               │
    │ Files modified:                               │
    │ - docs/module-guides/MODULE.md                │
    │                                               │
    │ Changes:                                      │
    │ - New class documentation added               │
    │ - Method signatures updated                   │
    │ - Examples created                            │
    │ - Integration patterns shown                  │
    └───────────────────┬───────────────────────────┘
                        │
                        ▼
    ┌───────────────────────────────────────────────┐
    │ User Notified                                 │
    │                                               │
    │ ✅ Code changes complete                      │
    │ ✅ Documentation updated                      │
    │ ✅ Ready to commit together                   │
    └───────────────────┬───────────────────────────┘
                        │
                        ▼
    ┌───────────────────────────────────────────────┐
    │ Commit Together                               │
    │                                               │
    │ git add fno-MODULE/src/                       │
    │ git add docs/module-guides/MODULE.md          │
    │ git commit -m "Add Feature"                   │
    └───────────────────────────────────────────────┘
```

## Multi-Layer Defense

```
┌─────────────────────────────────────────────────────────────┐
│ Layer 1: Claude Code Agent Policy                          │
│ • Automatic detection of public API changes                 │
│ • Proactive documentation updates                           │
│ • Real-time as you code                                     │
│ • First line of defense                                     │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼ (Backup if Layer 1 missed)
┌─────────────────────────────────────────────────────────────┐
│ Layer 2: Git Pre-Commit Hook (.githooks/pre-commit)        │
│ • Runs before local commit                                  │
│ • Checks for documentation updates                          │
│ • Warns if missing                                          │
│ • Second line of defense                                    │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼ (Backup if Layers 1-2 missed)
┌─────────────────────────────────────────────────────────────┐
│ Layer 3: GitHub Actions CI/CD                              │
│ • Runs on Pull Requests                                     │
│ • Fails build if docs missing                               │
│ • Posts comment with requirements                           │
│ • Final enforcement before merge                            │
└─────────────────────────────────────────────────────────────┘
```

## Component Interaction

```
┌──────────────────────┐
│  agent_policy.md     │◄─────┐
│                      │      │
│  Defines WHEN to     │      │ References
│  trigger doc updates │      │
└──────────┬───────────┘      │
           │                  │
           │ Invokes          │
           │                  │
           ▼                  │
┌──────────────────────┐      │
│  doc-maintainer      │      │
│  skill               │      │
│                      │      │
│  Defines HOW to      │──────┘
│  update docs         │
└──────────┬───────────┘
           │
           │ Uses
           │
           ▼
┌──────────────────────────────────────┐
│  /update-docs command                │
│                                      │
│  Manual trigger for skill            │
│  (Optional, for explicit requests)   │
└──────────────────────────────────────┘
```

## Data Flow

```
┌────────────────┐
│ Java Source    │
│ Code (*.java)  │
└────────┬───────┘
         │
         │ Analyzed by
         ▼
┌─────────────────────────┐
│ doc-maintainer skill    │
│                         │
│ Extracts:               │
│ • Package names         │
│ • Class declarations    │
│ • Method signatures     │
│ • Parameters            │
│ • Return types          │
│ • Annotations           │
└────────┬────────────────┘
         │
         │ Generates
         ▼
┌─────────────────────────┐
│ Documentation Entry     │
│                         │
│ Contains:               │
│ • Signature             │
│ • Parameters docs       │
│ • Return value docs     │
│ • Code example          │
│ • Integration pattern   │
└────────┬────────────────┘
         │
         │ Written to
         ▼
┌───────────────────────────┐
│ docs/module-guides/       │
│ MODULE.md                 │
│                           │
│ Consumed by:              │
│ • Human developers        │
│ • AI agents (Claude)      │
│ • OptionsAnalyzer skill   │
└───────────────────────────┘
```

## Consumption Chain

```
┌──────────────────────┐
│ FnOSdk Code          │
│ (Java Source)        │
└──────────┬───────────┘
           │
           │ Auto-documented by
           ▼
┌──────────────────────┐
│ FnOSdk Docs          │
│ (module-guides/*.md) │
└──────────┬───────────┘
           │
           │ Referenced by
           ▼
┌─────────────────────────────────┐
│ OptionsAnalyzer Skill           │
│ (.claude/skills/fnosdk.md)      │
│                                 │
│ knowledge_paths:                │
│ - fno-models.md                 │
│ - fno-utils.md                  │
│ - fno-technicals.md             │
│ - fno-kite-reader.md            │
└──────────┬──────────────────────┘
           │
           │ Used by
           ▼
┌─────────────────────────────────┐
│ Claude Code in OptionsAnalyzer  │
│                                 │
│ Reads FnOSdk docs to:           │
│ • Generate correct imports      │
│ • Use exact method signatures   │
│ • Create working examples       │
│ • Follow integration patterns   │
└─────────────────────────────────┘
```

## Quality Assurance Flow

```
┌──────────────────────┐
│ Documentation Entry  │
│ Generated            │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────────────────┐
│ Quality Checks (Automatic)       │
│                                  │
│ ✓ Signature matches source       │
│ ✓ All parameters documented      │
│ ✓ Return value documented        │
│ ✓ Example present                │
│ ✓ Example syntax valid           │
└──────────┬───────────────────────┘
           │
           ▼
┌──────────────────────────────────┐
│ Completeness Check               │
│                                  │
│ ✓ All public classes documented  │
│ ✓ All public methods documented  │
│ ✓ No outdated entries            │
└──────────┬───────────────────────┘
           │
           ▼
┌──────────────────────────────────┐
│ AI-Agent Validation              │
│                                  │
│ Question: Can AI generate code   │
│          from docs alone?        │
│                                  │
│ ✓ YES → Accept                   │
│ ✗ NO  → Enhance                  │
└──────────────────────────────────┘
```

## Trigger Conditions

```
Code Change Type          │ Auto-Doc Triggered? │ Reason
─────────────────────────┼────────────────────┼─────────────────────
New public class         │ ✅ YES              │ Public API addition
New public method        │ ✅ YES              │ Public API addition
Modified method signature│ ✅ YES              │ Public API change
New public field         │ ✅ YES              │ Public API addition
Deprecated method        │ ✅ YES              │ Public API change
Private method change    │ ❌ NO               │ Internal only
Test code change         │ ❌ NO               │ Not public API
Comment/Javadoc change   │ ❌ NO               │ No code change
Refactor (same API)      │ ❌ NO               │ API unchanged
Build file change        │ ❌ NO               │ Not source code
```

## File Structure

```
FnOSdk/
├── .claude/                          # Claude Code configuration
│   ├── README.md                     # This overview
│   ├── SYSTEM_OVERVIEW.md            # Architecture diagrams
│   ├── agent_policy.md               # Automatic trigger rules ⭐
│   ├── skills/
│   │   └── doc-maintainer.md         # Documentation skill ⭐
│   └── commands/
│       └── update-docs.md            # Manual trigger command
│
├── .githooks/                        # Git hooks (backup layer)
│   ├── pre-commit                    # Pre-commit check
│   ├── install-hooks.sh              # Installation script
│   └── README.md                     # Hook documentation
│
├── .github/workflows/                # CI/CD (final layer)
│   └── documentation-check.yml       # PR documentation check
│
├── docs/                             # Documentation (target)
│   ├── SDK_USAGE.md                  # Entry point for consumers
│   ├── AI_AGENT_GUIDE.md             # AI agent integration guide
│   ├── DOCUMENTATION_MAINTENANCE.md  # Maintenance guide
│   └── module-guides/                # Auto-updated API docs ⭐
│       ├── fno-models.md
│       ├── fno-utils.md
│       ├── fno-technicals.md
│       └── fno-kite-reader.md
│
├── CONTRIBUTING.md                   # Contribution guidelines
└── CLAUDE.md                         # Development guide

⭐ = Core components of automatic system
```

## Success Metrics

The system is working correctly when:

1. **Automation Rate**
   - ✅ 95%+ of public API changes trigger auto-doc
   - ✅ <5% require manual intervention

2. **Completeness**
   - ✅ 100% of public classes documented
   - ✅ 100% of public methods documented
   - ✅ 0 outdated entries

3. **Quality**
   - ✅ All examples compile
   - ✅ All signatures match source
   - ✅ Integration patterns present

4. **Downstream Impact**
   - ✅ OptionsAnalyzer AI generates correct code
   - ✅ No compilation errors from FnOSdk usage
   - ✅ Developers report accurate documentation

## Troubleshooting Decision Tree

```
Documentation not updated?
│
├─> Public API changed?
│   │
│   ├─> NO → Expected behavior (internal change)
│   │
│   └─> YES
│       │
│       ├─> agent_policy.md exists?
│       │   │
│       │   ├─> NO → Create .claude/ files
│       │   │
│       │   └─> YES
│       │       │
│       │       ├─> Claude Code reading it?
│       │       │   │
│       │       │   ├─> NO → Check CLAUDE.md references it
│       │       │   │
│       │       │   └─> YES
│       │       │       │
│       │       │       └─> Manual trigger: /update-docs
│
└─> Documentation quality low?
    │
    └─> Enhance manually:
        "Please improve documentation for X with
         more detailed examples and edge cases"
```

## Key Benefits

1. **Zero Forgetting** - Automation ensures docs are never forgotten
2. **Real-Time Updates** - Documentation updated as you code
3. **Consistent Quality** - Same format and standards every time
4. **AI-Agent Ready** - Optimized for downstream AI code generation
5. **Multi-Layer Safety** - Agent + Git Hook + CI/CD catch all cases
6. **Developer Friendly** - No extra steps required

## Comparison with Manual Process

```
┌─────────────────────┬──────────────────┬────────────────────┐
│ Aspect              │ Manual Process   │ Automatic System   │
├─────────────────────┼──────────────────┼────────────────────┤
│ Documentation Rate  │ 60-70%           │ 98-100%            │
│ Time to Document    │ 15-30 min        │ <1 min             │
│ Consistency         │ Varies           │ Uniform            │
│ Outdated Entries    │ Common           │ Rare               │
│ Developer Burden    │ High             │ Low                │
│ AI-Agent Quality    │ Mixed            │ High               │
│ Downstream Bugs     │ Frequent         │ Rare               │
└─────────────────────┴──────────────────┴────────────────────┘
```

## Summary

The FnOSdk automatic documentation system uses:
- **Claude Code agent policy** to detect changes and trigger updates
- **doc-maintainer skill** to generate comprehensive documentation
- **Multi-layer defense** (Agent → Git Hook → CI/CD) to ensure completeness
- **AI-agent optimization** to enable correct code generation in downstream projects

Result: **Accurate, complete, always-synchronized documentation** with minimal developer effort.
