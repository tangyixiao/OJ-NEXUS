# Plan — Unified Submission Detail Sheet (Phase 94)

Date: 2026-10-18 · Spec: docs/superpowers/specs/2026-10-18-submission-detail-sheet.md

1. `core/ui/submission/SubmissionDetailUiModel.kt` + `SubmissionDetailSheet.kt`
   (ModalBottomSheet, metrics row, links, extra rows, note shortcut).
2. `feature/problems/AttemptDetailMapping.kt` (top-level fn; local → judgeId LOCAL, no metrics)
   + ProblemDetailScreen AttemptRow clickable sheet host.
3. `feature/submissions/SubmissionDetailMapping.kt` (Luogu job mapping incl. verdict via
   `LuoguJudgeStatus.verdict`, extra rows, `submissionDetailSheetVisible`)
   + SubmissionCenterScreen DETAILS tag + per-card sheet.
4. `LuoguJudgeStatus` made public for the mapper; strings `submission_detail_*` +
   `attempt_open_details` EN/zh.
5. Tests: SubmissionDetailFormatTest (format helpers), AttemptDetailMapping tests
   (manual/remote), SubmissionDetailMappingTest (6 cases).
6. Gates `test assembleDebug`; versionCode 92 / 0.3.92; ROADMAP prepend; plan mark ✅; commit.

## Result

- Shipped as designed; two test bugs fixed en route (duplicate "1500 ms" assertion for an input
  whose formatted value is "1.50 s"; missing `.toSubmissionDetailModel()` call in the pending
  job test).
- Compile lessons: `SubmissionDetailMappingKt` is not a resolvable reference from tests — call
  top-level functions directly with the package prefix; `R` in core/ui tests is `com.ojnexus.R`.
