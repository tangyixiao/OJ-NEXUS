# OJ NEXUS — Training Engine

Deterministic, unit-tested domain policies. Pure Kotlin, no Android dependencies. The UI never
re-implements these rules — it calls the engines.

## Review Scheduler (`core/domain/ReviewScheduler.kt`)

Spaced-repetition ladder over stages 0..5 with intervals **1 / 3 / 7 / 21 / 45 / 90 days**.

| Outcome | Effect |
| --- | --- |
| First schedule | stage 0, due +1 day |
| **PASS** | stage + 1 (capped at 5; the 90-day interval repeats forever) |
| **HARD** | stage unchanged, due +⌊interval / 2⌋ days (min 1) |
| **FAIL** | stage − 1 (min 0), **re-test the next day** regardless of the new stage's interval |
| **SKIP** | stage unchanged, due + interval; **not counted as a completion** for activity |
| **RESET** | back to stage 0, due +1 day |

Time rules: `dueAt` is stored as UTC epoch millis; `dueDayIndex` (local epoch day) is computed
once at write time with the user's zone, so queue bucketing (OVERDUE / DUE TODAY / UPCOMING)
never depends on query-time timezone handling. Every completion is appended to `review_log`
(SKIP included for history, excluded from activity aggregates).

## Streak Policy (`core/domain/StreakCalculator.kt`, `ActivityPolicy`)

A local calendar day is **active** when any of:
- ≥ 1 problem solved (`attempts` with verdict AC that day),
- ≥ 1 review completed (review_log rows, excluding SKIP),
- ≥ 20 minutes of finished training time.

`currentStreak` counts consecutive active days ending today — or yesterday, if today is not yet
active (a streak is never broken by the current day still being in progress). `longestStreak`
scans the full window. Thresholds live in `ActivityPolicy`, not in UI code.

## Activity Score (`core/domain/ActivityScorer.kt`)

Explainable heatmap intensity (0–4), deliberately simple:

```
score = 3×solved + (attempts − solved) + 2×reviewsCompleted + trainingMinutes
intensity: 0 | 1–2 | 3–5 | 6–9 | 10+
```

AC-only metrics distort behaviour; attempts, reviews and training time all count.

## Session State Machine (`core/domain/SessionStateMachine.kt`, `SessionClock`)

```
PLANNED ─start→ RUNNING ─pause→ PAUSED
   │              │  ↑resume       │
   └─cancel→      ├─finish→ FINISHED ←finish─┘
   CANCELLED ←─cancel──────────────┘
```

FINISHED and CANCELLED are terminal — any event on them throws (tested). All transitions go
through the machine; Composables never branch on lifecycle inline.

**Timing is derived, never accumulated.** The session row persists `startedAt`, `pausedAt`,
`totalPausedMs`, `finishedAt`; elapsed = `now − startedAt − totalPausedMs − (open pause window)`.
A 1 Hz ticker feeds ONLY the elapsed-time text — nothing else on the screen recomposes per
second, and no timer writes to the database. Because every snapshot is persisted, backgrounding,
rotation and process death recover to the correct elapsed value for free.

**Known trade-off (accepted for this phase):** elapsed time is computed from wall-clock
snapshots, so a user manually changing the system clock shifts a running session's elapsed
value. A monotonic/process-death dual-clock design would fix that at the cost of real
complexity; Phase 1 deliberately keeps the single simple source of truth. The session-creation
guard (`at most one live session`) is enforced inside the create transaction itself, so
concurrent creators cannot race past it.

## Data Flow

Repositories (`core/data/repository`) own transactions and derived-field maintenance
(`applyAttempt`, aggregates via `AnalyticsDao` SQL GROUP BY on precomputed `day_index` keys).
UI states are `Loadable<T>` (Loading / Ready / Failed) exposed by ViewModels through
`StateFlow`; `SessionActionError` / `DataError` are the only error surfaces.
