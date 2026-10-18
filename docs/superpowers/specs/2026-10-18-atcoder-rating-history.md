# Spec: AtCoder contest results & rating sync (Phase 93)

Date: 2026-10-18 · Plan: docs/LONG_RANGE_PLAN.md PHASE 93 (ARC C — Multi-OJ depth)

## Goal

Pull the AtCoder rating history (per-contest results: place, old/new rating, performance,
contest) through a public endpoint and cache it locally in the existing `rating_changes`
table so the Profile/Connector surfaces render AtCoder rating offline, exactly like
Codeforces today.

## Endpoint (probed live from this host, 2026-10-18)

- `GET https://atcoder.jp/users/<handle>/history/json?contestType=algo` — official public
  JSON, no auth, no HTML scraping. Array of
  `{IsRated, Place, OldRating, NewRating, Performance, InnerPerformance, ContestScreenName,
  ContestName, ContestNameEn, EndTime}`; `EndTime` carries `+09:00`-style offsets
  (`OffsetDateTime`, never `Instant.parse`).
- Nonexistent user returns HTTP 200 with `[]` — keep UNVERIFIED binding semantics; empty
  history is a successful sync that writes nothing.
- `contestType=algo` is the algorithm ladder (tourist: 142 algo vs 17 heuristic entries).
  We sync the algo ladder only; heuristic (AHC) ratings are a separate ladder that would
  corrupt the old→new rating chain.
- kenkoooo has no user rating endpoint (probed: all v3 candidates 404).

This changes the docs/ATCODER.md boundary: rating history is now available via the official
public JSON endpoint. It stays public-API only; no password, cookie, session, or scraping.

## Constraints

- Reuse the shared `RateLimitedRequestGate` + `AtCoderRetryPolicy` bounded-retry semantics;
  same error taxonomy (`AtCoderApiError.*`).
- RATED rows only (`IsRated == true`) — `ATCODER.md` rule: never render a missing rating as
  zero; unrated rows have `NewRating = 0`.
- No schema change: `rating_changes` (unique `(judge, contest_id)`) + `sync_states.rating_synced_at`
  already exist.
- `contestId = ContestScreenName.substringBefore(".")` (e.g. `agc004`); upsert idempotency
  matches CF precedent.

## Design

1. `AtCoderUrls.SITE_BASE_URL = "https://atcoder.jp/"` + Retrofit `AtCoderHistoryApi`
   (`@GET("users/{handle}/history/json")`, `@Query("contestType") contestType: String`).
2. `@Serializable AtCoderRatingChangeDto` with PascalCase `@SerialName`s; `ContestNameEn`
   nullable default null (payload has it, defensive).
3. `AtCoderHistoryClient` mirrors `AtCoderProblemsClient` retry semantics against the new
   api, sharing the same gate instance.
4. `AtCoderAdapter.fetchRatingHistory(handle): List<AtCoderRatingChangeDto>`;
   capabilities += `RATING`, `RATING_HISTORY` (not PROFILE — no profile source).
5. `AtCoderMappers.toRatingChangeEntity(dto, handle, now)` — repo filters `IsRated` first;
   `rank = Place`, `ratingUpdateTimeSeconds = OffsetDateTime(endTime).toEpochSecond()`.
6. `AtCoderSyncPolicy.RATING_FRESH_MS = 6h`; `syncRating(account, force)` via `runStage`
   with new `RATING` arms in freshness map + `stamp()`; coordinator order
   SUBMISSIONS → RATING → CONTESTS → PROBLEMS (submissions bootstrap first).
7. Wiring: second Retrofit instance for `SITE_BASE_URL`; `RetrofitAtCoderAdapter` takes both
   clients. Offline rendering: shared judge-agnostic `rating_changes` surfaces need no UI code.

## DoD

- Fixture-derived unit tests: DTO decode (probe-shaped JSON), mapper (contestId extraction,
  offset→epoch), repository (upsert idempotent, unrated filtered, empty history ok, failed
  refresh retains cache, coordinator records 4 stages).
- Full `test assembleDebug` green; docs updated (`ATCODER.md`, `OJ_ADAPTERS.md`).

## Out of scope

- Heuristic/AHC rating ladder, AtCoder profile (rank/handle card data), any authenticated path.
