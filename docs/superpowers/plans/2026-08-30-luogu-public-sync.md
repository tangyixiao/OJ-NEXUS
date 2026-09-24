# Luogu public sync implementation plan

> Execute this plan in the current branch. Every implementation task starts
> with a focused failing test, then the smallest production change, then the
> focused test and full verification. Commit each logical task separately.

## Task 1: Extend the Luogu transport boundary

**Files**

- Modify `app/src/main/java/com/ojnexus/judge/luogu/api/LuoguApi.kt`
- Modify `app/src/main/java/com/ojnexus/judge/luogu/api/dto/LuoguDtos.kt`
- Modify `app/src/main/java/com/ojnexus/judge/luogu/LuoguClient.kt`
- Add `app/src/test/java/com/ojnexus/judge/luogu/LuoguPublicTransportTest.kt`

**Red**

- Add fixture-backed tests for profile/practice/problem/contest envelopes,
  record auth envelope, and page metadata.
- Add tests that public calls route through the existing gate and that malformed
  or auth-gated responses map to typed errors.
- Run the focused test and observe compilation/test failure because the calls
  and DTOs do not yet exist.

**Green**

- Define serializable typed envelopes and nullable payload DTOs tolerant of
  unknown Luogu fields.
- Add content-only Retrofit calls and client methods with existing bounded
  retry/cancellation behavior.
- Detect `auth/login` record envelopes as
  `LuoguApiError.AuthenticationRequired`.

**Verify and commit**

```powershell
tools\gradlew-local.bat test --tests '*LuoguPublicTransportTest'
git diff --check
git add app/src/main app/src/test docs/superpowers
git commit -m "feat: add Luogu public sync transport"
```

## Task 2: Add Luogu mapping and Room schema support

**Files**

- Add `app/src/main/java/com/ojnexus/judge/luogu/LuoguMappers.kt`
- Modify `app/src/main/java/com/ojnexus/core/database/entity/JudgeProfileEntity.kt`
- Modify `app/src/main/java/com/ojnexus/core/database/entity/RatingChangeEntity.kt`
- Modify `app/src/main/java/com/ojnexus/core/database/OjNexusDatabase.kt`
- Add migration tests/fixtures under `app/src/test/java/com/ojnexus/core/database`
- Add `app/src/test/java/com/ojnexus/judge/luogu/LuoguMappersTest.kt`

**Red**

- Test profile metadata and current-rating fallback.
- Test chronological ELO mapping, nullable old rating/rank, and missing contest
  ID filtering.
- Test problem tags serialization, official difficulty provenance, contest
  phase derivation, and migration 5→6.
- Run focused tests and observe failure before mapper/entity implementation.

**Green**

- Add nullable Luogu profile metadata fields and nullable rating fields without
  changing existing semantics for other judges.
- Add a complete explicit Room migration and register it in database construction.
- Implement deterministic mappers with no network or UI dependencies.

**Verify and commit**

```powershell
tools\gradlew-local.bat test --tests '*LuoguMappersTest' --tests '*MigrationTest'
git diff --check
git add app/src/main app/src/test
git commit -m "feat: map Luogu public data into local models"
```

## Task 3: Implement the local-first Luogu sync repository

**Files**

- Add `app/src/main/java/com/ojnexus/judge/luogu/LuoguSyncRepository.kt`
- Add `app/src/test/java/com/ojnexus/judge/luogu/LuoguSyncRepositoryTest.kt`
- Modify DAOs only when a missing transactional/upsert query is proven by a
  failing test.

**Red**

- Test page-by-page upserts, idempotent repeat sync, page-budget termination,
  per-stage timestamps, and preservation of prior rows after a later failure.
- Test records produce `AUTH_REQUIRED` with zero imported submissions.

**Green**

- Resolve canonical handle through public search on each run.
- Persist profile/rating, then contest/problem pages, immediately per page.
- Keep public stage failure typed and preserve existing cache.
- Keep submissions read-only/auth-gated; never write fake attempts.

**Verify and commit**

```powershell
tools\gradlew-local.bat test --tests '*LuoguSyncRepositoryTest'
git diff --check
git add app/src/main app/src/test
git commit -m "feat: persist Luogu public sync stages"
```

## Task 4: Wire the coordinator, registry, and settings sync

**Files**

- Add `app/src/main/java/com/ojnexus/judge/luogu/LuoguSyncCoordinator.kt`
- Modify `LuoguAdapter.kt` capabilities and public methods
- Modify `OjNexusApplication.kt` registration
- Modify only required strings/UI state for typed auth limitation
- Add `app/src/test/java/com/ojnexus/judge/luogu/LuoguSyncCoordinatorTest.kt`

**Red**

- Test public-stage order, active-account guard, and partial report when
  submissions require authentication.
- Test capability exposure makes the existing manual/background sync path
  available without advertising private submission support.

**Green**

- Register the Luogu coordinator with the existing dispatcher/worker path.
- Expose only profile, rating, catalog, contests, and background capabilities;
  do not advertise incremental submission support or unauthenticated submissions.
- Keep account disconnect behavior and stale-cache semantics unchanged.

**Verify and commit**

```powershell
tools\gradlew-local.bat test --tests '*LuoguSyncCoordinatorTest'
git diff --check
git add app/src/main app/src/test
git commit -m "feat: wire Luogu background synchronization"
```

## Task 5: Documentation, full verification, and release readiness

**Files**

- Modify `README.md`
- Modify `docs/MULTI_OJ.md`
- Modify `docs/OJ_ADAPTERS.md`
- Modify `docs/ROADMAP.md`
- Add/update any user-facing English and Simplified Chinese strings.

**Work**

- Document the actual Stage 1 boundary and the typed submission limitation.
- Run the complete required verification:

```powershell
tools\gradlew-local.bat clean test assembleDebug lintDebug
git diff --check
git status --short
git diff --stat
```

- Inspect the final diff for secrets, credentials, cookies, tokens, machine
  paths, fake API fixtures in production, and accidental WebView/scraping code.
- Do not push or create a GitHub release in this plan unless the user gives a
  separate explicit release instruction after reviewing the completed build.
