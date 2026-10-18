# Spec: AtCoder contest results & rating sync (Phase 93)

Date: 2026-10-18 · Plan: docs/LONG_RANGE_PLAN.md PHASE 93 (ARC C — Multi-OJ depth)

## Goal

Pull the AtCoder rating history through a public endpoint, cache it locally in the shared
`rating_changes` table, and render it offline through the existing judge-agnostic surfaces.

## Endpoint (probed live 2026-10-18)

`GET https://atcoder.jp/users/<handle>/history/json?contestType=algo` — official site JSON,
no auth, not scraping. Fields (PascalCase): `IsRated, Place, OldRating, NewRating,
Performance, InnerPerformance, ContestScreenName, ContestName, ContestNameEn, EndTime`
(EndTime carries `+09:00`-style offsets). Unknown handle answers `200 []` — never a 404 —
which keeps `UNVERIFIED` binding semantics. kenkoooo exposes no user-rating endpoint
(probed: v3/user/rating_history et al. are 404).

## Constraints

- docs/ATCODER.md: no passwords/cookies/scraping — the official JSON endpoint satisfies this.
- Unrated rows (`IsRated=false`, `NewRating=0`) are filtered BEFORE mapping: the UI never
  renders a missing rating as zero.
- Profile capability stays unavailable (no profile source).
- The kenkoooo request gate is shared across both hosts (one limiter, consistent pacing).

## Design

1. `AtCoderRatingChangeDto` (@Serializable, SerialName PascalCase) + `AtCoderHistoryApi`
   (Retrofit, base `https://atcoder.jp/`, `@GET("users/{handle}/history/json")`).
2. `AtCoderProblemsClient` generalized to `AtCoderProblemsClient<C>` so one client class wraps
   both Retrofit interfaces with identical retry/error semantics.
3. Adapter: `fetchRatingHistory(handle)` + capabilities gain RATING, RATING_HISTORY.
4. Mapper: contestId = `ContestScreenName.substringBefore(".")`, rank=Place,
   ratingUpdateTimeSeconds = `OffsetDateTime.parse(endTime).toInstant().epochSecond`.
5. Repository `syncRating` via the existing runStage freshness pattern (RATING_FRESH_MS=6h,
   ratingSyncedAt stamp); coordinator order SUBMISSIONS → RATING → CONTESTS → PROBLEMS.
6. Persistence: full-history replace — `deleteByJudge` + `insertAll` in one
   `database.withTransaction`. Room's generated `@Upsert` resolves conflicts on the primary
   key only; a `(judge, contest_id)` unique-index collision would silently DROP changed rows
   (verified in generated RatingChangeDao_Impl.kt), so upsert is wrong for full-history
   refresh semantics.

## DoD

- Fixtures tests: MockWebServer path/query exactness + fixture decode (rated+unrated rows);
  mapper tests (contest id extraction, offset conversion, EN-name preference);
  repository tests (rated-only upsert, idempotent second run, empty-history no-op,
  failed refresh retains cache); coordinator emits 4 stage receipts.
- Full `test assembleDebug` green.

## Out of scope

- Profile fetch (no source), heuristic-contest ratings (filtered), per-contest result pages
  beyond what history/json carries.
