# Plan: Codeforces submission detail deep-link (Phase 92)

Date: 2026-10-18 · Spec: docs/superpowers/specs/2026-10-18-cf-submission-deeplink.md

## Steps

1. `CodeforcesUrls.kt`: add `submission(contestId, submissionId)` next to `contest()`.
2. `app/src/test/java/com/ojnexus/judge/codeforces/CodeforcesUrlsTest.kt`: 7 exactness tests
   (problem join, multi-char index "C1", space→%20, submission join, minimal ids, contest,
   profile). Run first — pure JVM.
3. `core/model/TrainingModels.kt`: `Attempt` gains `sourceJudge/externalSubmissionId/contestId`
   (nullable, default null, doc'd as remote-sync origin).
4. `core/database/mapper/Mappers.kt`: `AttemptEntity.toDomain()` passes the three fields.
5. `ProblemDetailScreen.kt` `AttemptRow`: compute `submissionUrl` (CODEFORCES + both ids parse),
   clickable Row with Role.Button + onClickLabel + semantics contentDescription, accent
   "OPEN ON CODEFORCES" label; hoist `stringResource` before lambdas (no static var hack).
6. Strings EN + zh-rCN: `attempt_open_remote` after `attempt_delete_cd` in both files.
7. Gates: full `test assembleDebug` (684 tests expected), aggregate 0 failures.
8. Bookkeeping: versionCode 89→90 / versionName 0.3.90 (app/build.gradle.kts:18-19);
   ROADMAP.md "CODEFORCES SUBMISSION DEEP-LINK" bilingual section prepend;
   docs/LONG_RANGE_PLAN.md PHASE 92 ✅ (v0.3.90, 2026-10-18); commit `feat:`.

## Risks / notes

- Attempt domain change ripples to any positional constructions — all call sites use named
  args or entity-backed mappers; full-suite run is the net.
- `--tests` filter must go on `:app:testDebugUnitTest`; package paths, not source dirs.
