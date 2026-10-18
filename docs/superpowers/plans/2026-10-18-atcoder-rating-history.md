# Plan: AtCoder contest results & rating sync (Phase 93)

Date: 2026-10-18 · Spec: docs/superpowers/specs/2026-10-18-atcoder-rating-history.md

## Steps

1. `AtCoderUrls.kt`: add `SITE_BASE_URL = "https://atcoder.jp/"`.
2. `AtCoderDtos.kt`: `@Serializable AtCoderRatingChangeDto` (PascalCase SerialNames,
   `contestNameEn: String? = null`).
3. NEW `api/AtCoderHistoryApi.kt`: Retrofit `@GET("users/{handle}/history/json")`.
4. `AtCoderProblemsClient.kt`: generalize client `call` to take a `suspend C.() -> T`
   receiver so one class serves both api interfaces; add `AtCoderHistoryClient` typealias
   construction in app wiring (same gate, same retry policy).
5. `AtCoderAdapter.kt`: `fetchRatingHistory(handle)`; capabilities += RATING, RATING_HISTORY;
   `RetrofitAtCoderAdapter` gains the history client parameter.
6. `AtCoderMappers.kt`: `toRatingChangeEntity(dto, handle)` — contestId from
   ContestScreenName, epoch from OffsetDateTime EndTime, rank=Place; caller filters IsRated.
7. `AtCoderPolicies.kt`: `RATING_FRESH_MS = 6h`.
8. `AtCoderSyncRepository.kt`: `syncRating(account, force)` via `runStage` (RATING arms in
   freshness map + `stamp()`); filter `IsRated`, `ratingChangeDao().upsertAll`.
9. `AtCoderSyncCoordinator.kt`: stage order SUBMISSIONS → RATING → CONTESTS → PROBLEMS.
10. `OjNexusApplication.kt`: history Retrofit + shared gate; pass both clients to adapter.
11. Tests: `AtCoderHistoryClientTest` (fixture decode), `AtCoderMappersTest` extensions,
    `AtCoderSyncRepositoryTest` extensions (upsert idempotent, unrated filtered, empty ok,
    failed refresh retains cache, coordinator 4 stages).
12. Docs: `ATCODER.md` source boundary + capability sentence; `OJ_ADAPTERS.md` AtCoder note.
13. Gates: full `test assembleDebug`; versionCode 90→91 / 0.3.91; ROADMAP prepend;
    docs/LONG_RANGE_PLAN.md PHASE 93 ✅; commit `feat:`.

## Risks / notes

- Generalizing the client must not break the existing 6 `AtCoderClientTest` cases — the
  receiver type becomes generic but semantics stay identical.
- `runStage` freshness `when` is exhaustive-checked by the compiler; adding the RATING arm
  before use keeps it total.
- `--tests` filters go on `:app:testDebugUnitTest` with package names.
