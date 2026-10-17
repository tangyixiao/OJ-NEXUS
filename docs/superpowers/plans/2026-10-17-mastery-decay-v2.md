# Plan — Mastery decay v2 (Phase 81)

Scope: mastery score decays with time-since-last-AC per knowledge area. Pure model + analytics
lens + doc. No schema change, no network.

## Steps

1. **`core/domain/MasteryEngine.kt`** — add a decay term driven by `KnowledgeEvidence`:
   `lastSolvedDaysAgo` per area. Formula, in `docs/TRAINING_ENGINE.md`:
   `decay = floor(score * daysSince / DECAY_FULL_DAYS)` clamped to `[0, MAX_DECAY]`, where
   `DECAY_FULL_DAYS = 30` and `MAX_DECAY = 30`. `daysSince` comes from the evidence row, never
   from a clock read inside the engine (the repository supplies the local-day delta), so the
   engine stays pure and testable at 0 / 30 / >30 days.
2. **`MasteryReason.DECAY`** — a new reason carrying the applied amount, so the UI can render
   `MASTERY 72 ▼ DECAY 8` from the same result the score came from. Reason text is data, not UI
   copy, so it does not need a string resource.
3. **`core/database/dao/KnowledgeDao.kt`** — add `daysSinceLastSolved(area)` per area to the
   existing performance query (COALESCE to a large sentinel for "never solved"), so one flow
   feeds both the score and the decay.
4. **`KnowledgeRepository.observeMastery()`** — pass the day delta into
   `MasteryEngine.evaluate(area, evidence)`; no signature change to the flow.
5. **`feature/analytics` focus lens** — render the decay under the score using the existing
   metric row, with `NexusTone` severity. No new section, no new layout constant.
6. **`docs/TRAINING_ENGINE.md`** — the formula, the two constants, and the boundary table.
7. **Tests** — `MasteryEngineTest` decay cases (0 days, exactly the cap, past the cap, floor
   rounding, never-solved) and a repository-level test that the day delta reaches the engine.

## Gates

- `.\tools\gradlew-local.bat assembleDebug` → BUILD SUCCESSFUL
- `.\tools\gradlew-local.bat test` → BUILD SUCCESSFUL
- `values-zh-rCN` mirrors any new keys (LocalizationResourceTest).

## Exit criteria

The score drops by the documented amount at 0 / 30 / >30 days, the reason travels with the
result, no clock is read inside the engine, and the doc states the formula.
