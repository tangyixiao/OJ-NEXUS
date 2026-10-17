# 2026-10-17 — Mastery decay v2

## Context

Phase 81 of `docs/LONG_RANGE_PLAN.md` is "Mastery decay v2": an area that was solved two months
ago and never touched again still reported its old mastery score, so the weak-area ranking could
not tell a fresh strength from a forgotten one. The phase adds a time term to the existing score
and documents the formula.

## What exists today

- `core/domain/MasteryEngine.kt` — `evaluate(area, evidence)` → `MasteryResult(area, score,
  reasons)` from coverage (70 points), efficiency (30) and failures (−5).
- `core/data/repository/KnowledgeRepository.observeMastery()` — maps
  `KnowledgeDao.observePerformance()` rows into `KnowledgeAreaState`.
- `feature/training/TrainingScreen.KnowledgeSection` — one row per area with reason tags and the
  score.
- `core/database/entity/ProblemEntity` — `firstSolvedAt: Long?` (epoch millis, nullable) already
  exists on the local problem row.

## Decisions

1. **Decay is a subtraction from the existing score, not a separate axis.**
   `decay = floor(base * daysSinceLastSolved / 30)` capped at 30, then
   `score = max(0, base - decay)`. The bonus is that the UI renders `MASTERY 72 ▼ DECAY 8` from
   two numbers that always agree, instead of a score plus a "freshness" percentage that can
   contradict it.
2. **The engine reads no clock.** `KnowledgeEvidence.daysSinceLastSolved: Int?` is supplied by
   the repository from a `LocalDaySource`-driven DAO query, so every boundary (0 days, exactly the
   cap, past the cap, never solved) is a direct function call in a unit test. `null` means "no
   linked problem solved", which is the one case decay must skip.
3. **The cap prevents a fully stale area from reading as a grading.**
   `MAX_DECAY = 30` is smaller than the 70-point coverage term on purpose: a year-old perfect area
   still scores above a fresh, barely-working one, which matches how a solver's retention actually
   behaves. The cap is what makes the 0/30/365 columns of the boundary table equal for base ≥ 100.
4. **`DECAY` is a reason, and the reason travels with the score.**
   `MasteryReason.DECAY` (UI label `STALE`) is emitted only when decay is non-zero, so the tag
   row cannot claim staleness for a fresh area. `MasteryResult.decay` carries the amount.
5. **The SQL computes the day delta, not the repository.**
   The `daysSinceLastSolved` expression is a correlated subquery over solved problems in the area
   (`today - first_solved_at / 86400000`), so it re-evaluates on every emission of the existing
   performance flow. No new flow, no polling loop.
6. **`observeMastery()` now takes the day from `LocalDaySource.day`.**
   This is the same midnight-aware flow the review queue already uses, so mastery decays on its
   own across a midnight boundary and refreshes on resume. It required threading
   `localDaySource` into `KnowledgeRepository`, which is a container change in `OjNexusApplication`
   only.

## Files

- `core/domain/MasteryEngine.kt` — `KnowledgeEvidence.daysSinceLastSolved`,
  `MasteryReason.DECAY`, `MasteryResult.decay`, `DECAY_FULL_DAYS = 30`, `MAX_DECAY = 30`.
- `core/database/dao/KnowledgeDao.kt` — `observePerformance(todayEpochDay)` with the
  `daysSinceLastSolved` column; `KnowledgePerformanceRow` gained the nullable field.
- `core/data/repository/KnowledgeRepository.kt` — constructor takes `LocalDaySource`;
  `observeMastery()` flat-maps the day into the performance query; `KnowledgeAreaState.decay`.
- `feature/training/TrainingScreen.kt` — `▼ DECAY n` beside the score when non-zero.
- `core/ui/Labels.kt` + `strings.xml` / `values-zh-rCN/strings.xml` — `mastery_reason_decay`,
  `knowledge_decay`.
- `docs/TRAINING_ENGINE.md` — the Mastery & Decay v2 section with the boundary table.
- Tests — `core/domain/MasteryEngineDecayTest.kt` (13 cases) and three new `KnowledgeDaoTest`
  cases for the SQL expression.

## Tests

`MasteryEngineDecayTest` pins: no decay for a never-solved area, 0 days → 0, proportional decay
below the cap, floor-not-round, the cap at 15/30/365 days, the score never going negative, the
decay amount travelling with the result, negative day counts treated as zero, `NO_EVIDENCE`
unchanged, the documented constants, and determinism across repeated calls.
`KnowledgeDaoTest` pins the SQL: `null` when the area has no solved problem, the exact day delta
when it does, and `null` for an attempted-but-unsolved area.

## Out of scope

Per-goal decay rates, goal-aware dampening and decay-driven notifications are later phases.
Nothing here writes to a network surface.

The feature adds no network request, no Room migration and no credential flow; it only reshapes
locally stored mastery evidence into a fresher score.
