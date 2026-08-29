# OJ NEXUS — Architecture

## Module Layout

Phase 0–2: single `app` module with strict package boundaries. Real Gradle modularization only
when the codebase justifies it (no premature 20-module split).

```
com.ojnexus
├── OjNexusApplication      # manual DI container (AppContainer): db, clock, repositories
├── MainActivity            # single activity, edge-to-edge, NexusTheme
├── app/                    # shell: NexusApp (NavHost), NexusDestination, NexusBottomBar
├── core/
│   ├── database/           # Room v1: entities, DAOs, relations, mappers (schema exported)
│   ├── data/               # repositories (problem/review/training/analytics), DataResult
│   ├── designsystem/       # tokens (colors/typography/spacing/motion) + components
│   ├── domain/             # pure engines: ReviewScheduler, StreakCalculator,
│   │                       # SessionStateMachine/SessionClock, ActivityScorer/Policy
│   ├── model/              # domain enums + models (JudgeId, Verdict, Problem, ProblemKey, …)
│   └── ui/                 # Loadable state, enum label/tone mapping, formatting, DI local
└── feature/
    ├── dashboard/  problems/ (library, form, detail)  training/ (queue, tasks,
    │                          sessions, review session)  analytics/  profile/
    └── (later: contests, arena, knowledge, achievements, settings, command)
```

## Dependency Injection

Manual container (`AppContainer` in `OjNexusApplication`), provided to Compose via
`LocalAppContainer`. Chosen deliberately over Hilt for Phase 1: a handful of singletons on a
brand-new AGP 9 built-in-Kotlin toolchain — stability beats framework dogma. ViewModels are
created with `ContainerViewModelFactory`; composables never touch repositories or DAOs.

## Data Flow (Phase 1)

```
Room (Flow) → Repository (transactions, derived fields) → ViewModel (combine → Loadable<T>)
           → Composable (collectAsStateWithLifecycle)
```

- One `UiState` per screen; Loading/Empty/Failed handled at screen level, per-section empties
  inside Ready.
- Writes go through repositories and return `DataResult` / `DataError`
  (`DuplicateProblem`, `NotFound`, `Storage`) — SQLite exceptions never reach the UI.
- Session timing is derived from persisted snapshots (see TRAINING_ENGINE.md); the 1 Hz ticker
  recomposes only the elapsed-time text.

## Rules

- Composables render state; they never perform I/O or business logic.
- Local First: everything in Phase 1 works fully offline; the network layer arrives in Phase 2
  behind the JudgeAdapter boundary.
- Deterministic engines (Mastery, Training, Review scheduling, Sync) are pure Kotlin —
  unit-testable without Android.
- Day-key discipline: UTC epoch millis stored, local epoch days precomputed at write time.

## Testing

- Pure domain engines: JUnit (ReviewScheduler, StreakCalculator/ActivityPolicy,
  SessionStateMachine/SessionClock, ActivityScorer, ProblemStatus/ProblemKey, Verdict/JudgeId).
- Library filter/sort: pure functions, JUnit.
- Room schema guarantees (unique keys, cascades, counter queries): Robolectric DAO tests on the
  JVM — these run in CI like any unit test. No emulator-based instrumented tests yet.
