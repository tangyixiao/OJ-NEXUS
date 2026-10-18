# Spec — Unified Submission Detail Sheet (Phase 94)

## Goal

One shared bottom-sheet design for any judge's submission: verdict, time/memory, problem link,
note shortcut. Attempt rows (ProblemDetailScreen) and Luogu submission cards
(SubmissionCenterScreen) render the same component.

## Constraints

- No schema change — the detail view is a pure UI mapping over existing entities.
- Strings via resources (EN + zh-rCN); telemetry tone, uppercase labels.
- Verdict mapping stays honest: unknown statuses surface raw status text, never a guessed tag.
- Links open through the existing custom-tab opener (`UrlOpener` in core/ui).
- Judge-agnostic: mappers never construct URLs; callers precompute judge-specific links.

## Design

- `core/ui/submission/SubmissionDetailUiModel.kt` — data class with judgeId, verdict (unified
  `Verdict?`), rawVerdict, language, timestampEpochMs, executionTimeMs?, memoryKiB?, score?,
  problemTitle?, problemId?, submissionUrl?, problemUrl?, extraRows.
- `core/ui/submission/SubmissionDetailSheet.kt` — ModalBottomSheet rendering the model:
  judge tag + verdict tag header, metrics row (TIME/MEMORY/SCORE, honest "not reported" state),
  language + submitted-at lines, problem row with OPEN PROBLEM link, OPEN SUBMISSION link,
  extra rows, ADD NOTE shortcut (optional callback).
- `feature/problems/AttemptDetailMapping.kt` — top-level `toSubmissionDetailModel(attempt,
  submissionUrl)`: local judge id when sourceJudge is null, no metrics, no links beyond the
  precomputed submission URL.
- `feature/submissions/SubmissionDetailMapping.kt` — `SubmissionJobEntity.toSubmissionDetailModel()`:
  verdict from `LuoguJudgeStatus.verdict(status)`, problem URL from `LuoguUrls.problem(pid)`,
  extra rows for SCORE / JUDGE STATUS / COMPILE / COMPILE MESSAGE / OUTPUT / EXIT CODE / ERROR /
  KIND, and `submissionDetailSheetVisible(status)` = READY or FAILED.
- ProblemDetailScreen AttemptRow: whole row clickable opens sheet (label
  `attempt_open_details`); OPEN ON CODEFORCES stays a separate clickable.
- SubmissionCenterScreen: DETAILS tag on terminal jobs toggles a per-card sheet.

## DoD

- Same component renders CF / Luogu / local rows; strings via resources.
- Unit tests: format helpers honest ranges, attempt mapping (manual → local, remote → carries
  precomputed URL), Luogu job mapping (verdicts, extra rows, run kind, no pid → no link),
  sheet visibility for terminal statuses.
- Gates: full `test assembleDebug` BUILD SUCCESSFUL.
