# Focus Sprint difficulty ladder — plan (2026-10-18)

Design: docs/superpowers/specs/2026-10-18-focus-sprint-ladder-design.md
Phase: docs/LONG_RANGE_PLAN.md:106 (PHASE 83).

## Steps

1. `app/src/main/java/com/ojnexus/feature/training/FocusSprint.kt`
   - Add `enum class FocusSprintLadderBand { EASIER, FIT, STRETCH, ANY }` with
     `fun bandFor(difficulty: Int?, targetDifficulty: Int?): FocusSprintLadderBand` returning
     `ANY` when either side is null, and constants `EASIER_STEP = 200`, `FIT_STEP = 200`.
   - Add `enum class FocusSprintReasonCode { REVIEW_DUE, WEAK_AREA, FAILURE_HISTORY, REVIEW_GAP,
     DIFFICULTY_FIT, UNSOLVED, UNRATED }`.
   - Add `data class FocusSprintLadderItem(item, difficulty, targetDifficulty, band, reason,
     reviewGapDays: Int?)`.
   - Add `data class FocusSprintLadderPlan(items, band, targetDifficulty)` with `ids`,
     `dueCount`, `targetCount`, `unratedCount`.
   - Add `fun buildFocusSprintLadder(buckets, recommendations, targetDifficulty: Int?, band,
     todayEpochDay, limit = DEFAULT_FOCUS_SPRINT_LIMIT): FocusSprintLadderPlan`.
   - Keep `buildFocusSprintPlan` exactly as it is (Phase 75 contract, existing tests must pass
     unchanged); the ladder reuses its pool builders via shared private helpers.
2. `app/src/main/res/values/strings.xml` — new keys `training_focus_sprint_band`,
   `training_focus_sprint_band_easier`, `_fit`, `_stretch`, `_any`,
   `training_focus_sprint_band_empty`, `training_focus_sprint_reason_review_due`, `_weak_area`,
   `_failure_history`, `_review_gap` ("REVIEW GAP %1$dD"), `_difficulty_fit`, `_unsolved`,
   `_unrated`, `training_focus_sprint_rating_unknown`, `training_focus_sprint_rating_separator`.
3. `app/src/main/res/values-zh-rCN/strings.xml` — mirror.
4. `app/src/main/java/com/ojnexus/feature/training/TrainingScreen.kt`
   - `rememberSaveable { mutableStateOf(FocusSprintLadderBand.FIT) }` band state next to
     `reviewFilter`.
   - Pass `band` into `FocusSprintPanel`; the panel builds the ladder from
     `buildFocusSprintLadder(uiState.reviews, uiState.recommendations, target, band, todayEpochDay)`.
     The target comes from `uiState.trainingTargets.firstOrNull()?.targetDifficulty`.
   - Band chips row reusing the selected/border pattern of `ReviewFilterButton`.
   - Preview rows: judge line appends ` · rating` (or `—`), source tag replaced by the reason tag
     with tone by code.
   - Empty ladder shows `training_focus_sprint_band_empty` instead of `_ready`.
5. `app/src/test/java/com/ojnexus/feature/training/FocusSprintLadderTest.kt` — the 9 cases from the
   design's Tests section.
6. Gates: `.\tools\gradlew-local.bat test` then `assembleDebug`; bump
   `versionCode = 81` / `versionName = "0.3.81"`; prepend the ROADMAP entry; mark Phase 83 ✅;
   commit `feat(android): focus sprint difficulty ladder`.

## Notes / risks

- `TrainingRecommendation` has no difficulty field, so the ladder needs ratings. The recommendation
  is built from `TrainingPlanner.rank(candidate)` where `candidate.difficulty` exists but is not
  carried into `TrainingRecommendation`. Rather than change the planner (out of scope), the ladder
  takes ratings from the **due review** items (which carry `difficulty`) and, for recommendations,
  from a new optional `difficulty: Int? = null` field added to `TrainingRecommendation`. The
  producer site must be updated to pass it; if it is unavailable the row is simply unrated and the
  ladder still works.
- No Turbine/MockK available; tests stay pure JVM + Robolectric where Room is needed.
