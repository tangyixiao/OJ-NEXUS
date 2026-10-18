# Focus Sprint difficulty ladder — design (2026-10-18)

## Intent

Phase 75's `buildFocusSprintPlan` (app/src/main/java/com/ojnexus/feature/training/FocusSprint.kt)
picks a sprint from two local pools: reviews due now, then ranked training recommendations. Both
pools are ordered by *urgency* — due day / due time / priority. Nothing about difficulty is
visible to the user, and the preview row cannot say why a problem is in the list.

Phase 83 makes the plan a **difficulty ladder**: the user picks a starting difficulty band and the
sprint walks upward, and every row carries a deterministic reason code so the preview explains
itself.

## Scope

- `app/src/main/java/com/ojnexus/feature/training/FocusSprint.kt` — pure ordering + reason codes.
- `app/src/main/java/com/ojnexus/feature/training/TrainingScreen.kt` — preview rows + band picker.
- Strings in both `values/` and `values-zh-rCN/`.
- Unit tests only (pure functions + a source-text layout test).

Out of scope: the recommendation generator (`TrainingPlanner`), Room, network, scheduling.

## Model

### `enum class FocusSprintLadderBand`

| band | meaning | selection rule for a candidate with rating `r` and target `t` |
| --- | --- | --- |
| `EASIER` | below target | `r <= t - EASIER_STEP` |
| `FIT` | at target | `t - EASIER_STEP < r <= t + FIT_STEP` |
| `STRETCH` | above target | `r > t + FIT_STEP` |
| `ANY` | no band | always true |

Constants: `EASIER_STEP = 200`, `FIT_STEP = 200`.

A candidate with **no rating** or **no target** is never banded: it falls back to `ANY`, and it is
placed in a final "unknown" group rather than mixed into `EASIER`/`STRETCH`. This satisfies
"never infer target from missing rating".

### `FocusSprintLadderItem`

```kotlin
data class FocusSprintLadderItem(
    val item: FocusSprintItem,
    val difficulty: Int?,
    val targetDifficulty: Int?,
    val band: FocusSprintLadderBand,
    val reason: FocusSprintReasonCode,
)
```

`item` reuses the existing `FocusSprintItem` (problemId / judge / externalId / title / source) so
the launch path (`FocusSprintPlan.ids`) stays unchanged.

### `enum class FocusSprintReasonCode`

Deterministic, data-backed codes only. Displayed via `FocusSprintReasonCode.labelRes()`:

| code | when |
| --- | --- |
| `REVIEW_DUE` | source == DUE |
| `WEAK_AREA` | TARGET with `TrainingReason.LOW_MASTERY` in reasons |
| `FAILURE_HISTORY` | TARGET with `TrainingReason.FAILURE_HISTORY` in reasons |
| `REVIEW_GAP` | TARGET with `TrainingReason.REVIEW_DUE` in reasons |
| `DIFFICULTY_FIT` | TARGET with `TrainingReason.DIFFICULTY_FIT` in reasons |
| `UNSOLVED` | TARGET with `TrainingReason.UNSOLVED` in reasons (last resort) |
| `UNRATED` | any item with `difficulty == null` |

Precedence: `UNRATED` (only when rating is missing) → `REVIEW_DUE` (source DUE) → then the
recommendation's reasons in the fixed order WEAK_AREA, FAILURE_HISTORY, REVIEW_GAP,
DIFFICULTY_FIT, UNSOLVED. The reason set from `TrainingPlanner` is a `Set<TrainingReason>` with no
declared order, so the code fixes the order explicitly. A TARGET with an empty reason set falls
back to `UNSOLVED`, which is always true for a recommendation.

The plan spec mentions `REVIEW GAP 21D`. The gap in days is available on due reviews
(`dueDayIndex - todayEpochDay`), so `REVIEW_GAP` renders as a two-part string:
`REVIEW GAP` + the day count when the item is a due review with a known day index. The pure
function returns the code plus a `reviewGapDays: Int?`; the UI composes the label.

### Ordering

`buildFocusSprintLadder(buckets, recommendations, targetDifficulty, band, todayEpochDay, limit)`:

1. Split candidates into due (overdue + dueToday) and target pools, exactly as Phase 75 does
   (same comparators, same `distinctBy { problemId }`, same due-wins-over-duplicate rule).
2. Band each candidate. `band == ANY` keeps every candidate; otherwise only matching candidates
   survive, except that unrated/unknown candidates are kept in a trailing group.
3. Order within a band: `EASIER` → `FIT` → `STRETCH` → unknown, and inside a band ascending
   difficulty (unrated last), then by the pool's own urgency order (due day, due time, priority,
   problemId) so the ladder is fully deterministic.
4. `take(limit)` (default 5, unchanged).

`FocusSprintLadderPlan` exposes `items`, `ids`, `dueCount`, `targetCount`, `unratedCount`,
`band`, and `targetDifficulty` so the panel can render "NO PROBLEMS IN THIS BAND" when empty.

## UI

`FocusSprintPanel` gains a band row under the metrics row: four chips
(`EASIER` / `FIT` / `STRETCH` / `ANY`) using the existing selected/border pattern from
`ReviewFilterButton` (app/src/main/java/com/ojnexus/feature/training/TrainingScreen.kt:721).
Default is `FIT` so an un-configured user sees the previous behaviour's spirit (target-centred
candidates) without any new setup step.

Each preview row keeps judge + title, and the source tag is replaced by a reason tag:
`FocusSprintReasonCode.labelRes()` text, `NexusTone.Warning` for `REVIEW_DUE`/`FAILURE_HISTORY`,
`NexusTone.Accent` for `WEAK_AREA`/`DIFFICULTY_FIT`, `NexusTone.Neutral` otherwise. The rating is
appended to the judge line when known (`CODEFORCES 1234A · 1600`) so the ladder direction is
visible; unrated rows show `—`.

Band state is `rememberSaveable` in `TrainingScreen` (like `reviewFilter`), so a rotation keeps
the picker but the plan is recomposed from the same `uiState`.

## Non-goals

- No change to `TrainingPlanner.rank`, `TrainingCandidate`, or how recommendations are produced.
- No new preference, no persistence of the band.
- No network, no Room migration, no credential flow — the ladder only re-orders local data.

## Tests

`app/src/test/java/com/ojnexus/feature/training/FocusSprintLadderTest.kt` (pure JVM):

1. `ANY` band keeps the Phase 75 order and ids.
2. `FIT` band keeps only in-band candidates, ascending difficulty.
3. `EASIER` / `STRETCH` split around the target with the 200/200 steps.
4. A missing rating never matches `EASIER`/`FIT`/`STRETCH`; it trails in an unknown group.
5. A missing target with `ANY` keeps everything; with a concrete band everything is unknown.
6. Reason codes: precedence over a fixed reason set, `UNRATED` wins, due source → `REVIEW_DUE`.
7. `REVIEW_GAP` carries `reviewGapDays` = `dueDayIndex - todayEpochDay` for due items and null
   for targets.
8. Limit is respected; duplicates and due-vs-target conflicts behave as in Phase 75.
9. `TrainingScreenUiLayoutTest`-style source assertions: the new chips, the reason tag switch and
   the rating suffix exist in `TrainingScreen.kt`, and `reduceMotion` still gates the panel.
