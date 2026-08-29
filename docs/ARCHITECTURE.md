# OJ NEXUS — Architecture

## Module Layout

Phase 0–2: single `app` module with strict package boundaries. Real Gradle modularization only
when the codebase justifies it (no premature 20-module split).

```
com.ojnexus
├── MainActivity            # single activity, edge-to-edge, NexusTheme
├── app/                    # shell: NexusApp, NexusDestination, NexusBottomBar
├── core/
│   ├── designsystem/       # tokens (colors/typography/spacing/motion) + components
│   ├── model/              # domain enums/models (JudgeId, Verdict, KnowledgeArea, TrainingType)
│   ├── sample/             # DEVELOPMENT SAMPLE data for UI preview only
│   └── ui/                 # presentation glue: verdict tone/label mapping, number formatting
└── feature/
    ├── dashboard/  problems/  training/  analytics/  profile/
    └── (later: problem detail, review, session, contests, arena, knowledge, achievements,
         settings, command)
```

Planned (post-Phase 2) layering inside each feature:

```
UI (Compose) → ViewModel (StateFlow<UiState>) → UseCase/Repository → Data Source → Room/Network
```

## Rules

- Composables render state; they never perform I/O or business logic.
- One `UiState` type per screen covering Loading / Success / Empty / Error / Offline.
- `Flow` collection is lifecycle-aware; `StateFlow` is the default UI-state holder.
- Repositories cache locally (Room) and treat the network as a sync mechanism, not the source
  of truth. A failed request must never blank a screen that has local data.
- Network errors are mapped to domain errors
  (`NetworkUnavailable, RateLimited, Unauthorized, UserNotFound, ParseError, ServerError, Unknown`).
- Judge adapters are isolated: `judge/<name>/` owns endpoints, DTOs, parsing, quirks. A broken
  adapter degrades only its own judge (see OJ_ADAPTERS.md).
- Deterministic engines (Mastery, Training, Review scheduling, Sync) are pure Kotlin where
  possible — unit-testable without Android.

## Phase 0 State

- Shell: `MainActivity` → `NexusApp` (NavHost + flat bottom bar, fade transitions).
- Design system implemented (see DESIGN_SYSTEM.md).
- Five skeleton screens render `core/sample` data through default parameters and are labeled
  DEV SAMPLE on the dashboard. No ViewModels, no Room, no network yet — by design.
- Unit tests cover the domain enums (`Verdict.fromRaw`, `JudgeId.fromId`) and will cover
  formatting + engines as they land.
