# Plan — Phase 91: Codeforces problemset incremental sync

Date: 2026-10-18
Spec: docs/superpowers/specs/2026-10-18-cf-problemset-delta.md

1. NEW `app/src/main/java/com/ojnexus/judge/codeforces/ProblemsetDeltaPlanner.kt`:
   - `enum ProblemsetSyncDecision { SKIP_FRESH, SKIP_UNCHANGED, WRITE }`
   - `object ProblemsetDeltaPlanner`:
     - `decide(force, lastSyncedAt, now, freshnessMs, liveCount, fetchedCount)`:
       force → WRITE candidate; fresh → SKIP_FRESH; fetched == liveCount > 0 →
       SKIP_UNCHANGED; else WRITE.
     - `fingerprint(merged: List<Pair<CfProblemDto, CfProblemStatisticsDto?>>): String` —
       SHA-256 over `contestId:index:rating:solvedCount` lines (problems without
       externalId skipped, matching mergeProblemset).
     - `contentMatches(a, b)`.
2. `RemoteProblemDao`: add `@Query("SELECT COALESCE(SUM(solved_count), 0) FROM remote_problems WHERE judge = :judge") suspend fun sumSolvedByJudge(judge: String): Long`.
3. `CodeforcesSyncRepository.syncProblemset`: after merge compute `fingerprint`; fetch
   `countByJudge` + `sumSolvedByJudge` before writing; if planner says SKIP_UNCHANGED
   (same count, same sum-of-solved fingerprint proxy per spec) return itemsProcessed = 0
   without the chunked transaction; else write (unchanged chunking) and stamp.
   - Keep `stampModule`/freshness behavior identical.
4. Tests: NEW `app/src/test/java/com/ojnexus/judge/codeforces/ProblemsetDeltaPlannerTest.kt`
   (pure: decide matrix + fingerprint stability/change); extend `CodeforcesSyncTest`
   with 2 cases: second sync with identical problemset skips write (verify via
   `sumSolvedByJudge` unchanged + outcome itemsProcessed == 0 while forced), changed
   solvedCount writes.
5. Gates: `tools/gradlew-local.bat test assembleDebug`; versionCode 88→89
   (`0.3.89`) in `app/build.gradle.kts:18-19`; ROADMAP prepend; plan mark ✅; commit.
