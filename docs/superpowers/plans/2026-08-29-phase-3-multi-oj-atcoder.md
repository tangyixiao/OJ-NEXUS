# Phase 3 Multi-OJ Foundation + AtCoder Implementation Plan

> Execute on `phase/3-multi-oj-atcoder`. Use test-first cycles for each behavior, make
> logical commits, push, open the requested PR, wait for CI, and do not merge.

## Task 1: Shared judge contracts and request gate

Files:

- Create `app/src/main/java/com/ojnexus/judge/JudgeAdapter.kt`
- Create `app/src/main/java/com/ojnexus/judge/JudgeRegistry.kt`
- Create `app/src/main/java/com/ojnexus/core/network/RateLimitedRequestGate.kt`
- Modify `app/src/main/java/com/ojnexus/judge/codeforces/CodeforcesAdapter.kt`
- Modify `app/src/main/java/com/ojnexus/judge/codeforces/CodeforcesRequestGate.kt`
- Create tests under `app/src/test/java/com/ojnexus/judge/`

Steps:

1. Write failing tests for capability declarations, registry lookup, unsupported lookup,
   and independent gate state.
2. Add `JudgeCapability`, `DataSourceReliability`, `AdapterStatus`, `JudgeAdapter`,
   connector/coordinator contracts, and registry.
3. Extract the gate mechanism while retaining the Codeforces facade and 2100 ms policy.
4. Run the focused gate/registry/Codeforces tests.

## Task 2: Room v3 generalization

Files:

- Modify database entities and DAOs under `core/database`
- Modify `OjNexusDatabase.kt`
- Modify mappers and tests that construct numeric contest IDs
- Modify `MigrationTest.kt`
- Generate `app/schemas/com.ojnexus.core.database.OjNexusDatabase/3.json`

Steps:

1. Extend migration tests to express 2 to 3 and 1 to 3 preservation requirements; confirm
   they fail before the migration exists.
2. Generalize contest IDs to strings, add account verification/reliability, add remote
   difficulty source/last-seen, and add account/timestamp sync metadata.
3. Implement `MIGRATION_2_3` with table rebuilds and `CAST(... AS TEXT)` where SQLite type
   changes are required. Preserve indices and foreign-key behavior.
4. Update Codeforces mappers with `.toString()` identities and official difficulty source.
5. Run migration, DAO, mapper, and Codeforces sync tests; generate and inspect schema v3.

## Task 3: AtCoder transport, DTOs, mapping, and policies

Files:

- Create `judge/atcoder/api/AtCoderProblemsApi.kt`
- Create `judge/atcoder/api/dto/AtCoderDtos.kt`
- Create `judge/atcoder/AtCoderProblemsClient.kt`
- Create `judge/atcoder/AtCoderAdapter.kt`
- Create `judge/atcoder/AtCoderPolicies.kt`
- Create `judge/atcoder/AtCoderUrls.kt`
- Create `judge/atcoder/mapper/AtCoderMappers.kt`
- Create focused DTO/client/mapper tests

Steps:

1. Write serialization samples for submissions, contests, merged problems, and problem
   models, including nullable/unknown fields.
2. Write verdict, URL encoding, contest boundary, and difficulty-source tests (negative,
   floating, very high, missing, AHC/Marathon).
3. Implement the current AtCoder Problems endpoints through the independent 1100 ms gate,
   bounded transient retry, cancellation propagation, and typed errors.
4. Implement source-native mapping; omit rating/profile capability and HTML scraping.

## Task 4: Soft binding and generic account lifecycle

Files:

- Modify `JudgeAccountRepository.kt`
- Add connector implementations in `judge/codeforces` and `judge/atcoder`
- Add account lifecycle tests

Steps:

1. Write failing tests for Codeforces canonical binding, AtCoder case preservation,
   confirmed binding, valid-but-unverified binding, source-unavailable binding, and invalid
   formats.
2. Route connect through the registry connector for the selected judge.
3. Persist verification state/reliability; reset only the replaced judge's cursor.
4. Verify disconnect keeps attempts/local problem/user-owned data and can optionally purge
   only the selected judge's remote cache.

## Task 5: AtCoder cursor planner and submission persistence

Files:

- Create `judge/atcoder/AtCoderSubmissionCursorPlanner.kt`
- Create `judge/atcoder/AtCoderSyncRepository.kt`
- Create planner and repository tests

Steps:

1. Write pure planner tests for empty/short/full pages, duplicate boundary rows, forward
   timestamp progress, same-second new IDs, identical repeated page, and 500-row same-second
   saturation.
2. Write repository tests for initial multi-page sync, 120-second incremental overlap,
   idempotency, rejudge updates, minimal problem materialization, metadata enrichment,
   process-restart replay, and no cursor advancement on failed page persistence.
3. Implement page-at-a-time transactions and atomic cursor updates. Keep a bounded page
   signature/no-progress guard and return a typed partial outcome on stall.
4. Confirm no complete-history list is retained and requests remain sequential.

## Task 6: AtCoder catalog and contest sync

Files:

- Extend `AtCoderSyncRepository.kt`
- Create `core/domain/ContestTimeStateCalculator.kt`
- Add catalog/contest/cache preservation tests

Steps:

1. Test joins by stable problem/contest IDs, estimated difficulty labels, AHC unknowns,
   points/solver counts, and remote/local separation.
2. Test that malformed/failed resources retain old cache and produce a stage failure.
3. Test string contest IDs and all four time boundaries.
4. Implement validated fetch/parse then chunked upsert; do not delete historical remote
   problems on refresh.

## Task 7: Multi-judge sync dispatch and WorkManager

Files:

- Create `judge/JudgeSyncDispatcher.kt`
- Move/generalize `judge/codeforces/sync/JudgeSyncWorker.kt`
- Add `AtCoderSyncCoordinator.kt`
- Modify `CodeforcesSyncCoordinator.kt` to implement the shared contract
- Modify `OjNexusApplication.kt`
- Add dispatcher/work-name/coordinator tests

Steps:

1. Test registry dispatch, judge/account mismatch rejection, per-judge stage lists, partial
   outcomes, and work names containing judge plus account ID.
2. Register two fully constructed adapter stacks with independent gates.
3. Route the single worker through the dispatcher and cancel only exact judge/account work.
4. Preserve Codeforces retry/result behavior and verify existing sync tests.

## Task 8: Multi-OJ repositories and UI

Files:

- Add a judge-agnostic remote data repository/read facade
- Modify Settings, Dashboard, Profile, Problems, Contest Center, and Analytics ViewModels
- Modify their Compose screens and `strings.xml`
- Add pure ViewModel/filter tests where practical

Steps:

1. Change screen state from CF-only fields to lists/maps keyed by judge.
2. Add two account connection panels, capability-aware labels, AtCoder unverified/source
   status, per-judge sync controls, and independent offline/partial display.
3. Add remote problem judge filter, correct materialization key/URL, contest judge filter,
   dashboard connection summary, and analytics judge breakdown.
4. Keep every UI string in resources and use only design-system tokens/components.
5. Compile after each screen family and run focused tests.

## Task 9: Documentation and audits

Files:

- Update `README.md`, `docs/ARCHITECTURE.md`, `docs/DATABASE.md`,
  `docs/OJ_ADAPTERS.md`, `docs/ROADMAP.md`, and `docs/SYNC_ENGINE.md`
- Add `docs/ATCODER.md` and `docs/MULTI_OJ.md`

Steps:

1. Document community-source reliability, request interval, allowed/deprecated endpoints,
   no-scraping security boundary, capabilities, soft binding, and rating limitation.
2. Document Room v3 and exact migration behavior.
3. Document same-second planner and safe stalled-page degradation.
4. Audit for plaintext credentials, cleartext hosts, direct network calls bypassing gates,
   global sync locks, numeric-only contest IDs, stale Phase 4 references, and raw UI strings.

## Task 10: Final verification and delivery

Steps:

1. Run focused tests after the final audit.
2. Run `tools/gradlew-local.bat clean test assembleDebug` and record the real test count.
3. Inspect XML failures/errors, schema v3, APK output, `git diff --check`, status, branch,
   commit range, and secrets/host scans.
4. Commit logical final changes using project commit prefixes.
5. Push `phase/3-multi-oj-atcoder` without force.
6. Create PR titled `PHASE 3: Multi-OJ Foundation + AtCoder Integration` using a body file
   with architecture, migration, AtCoder sources, pagination safety, tests, limitations,
   security, and screenshots/visual notes if available.
7. Wait for GitHub Actions. On failure, inspect the actual log, fix root cause, rerun local
   verification, push, and wait again.
8. Perform the final review checklist from the Phase 3 specification. Leave the PR open;
   do not merge and do not start Phase 4.
