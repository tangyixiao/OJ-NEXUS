# Phase 3 Multi-OJ Foundation + AtCoder Design

## Outcome

Phase 3 proves that OJ NEXUS can support judges with materially different APIs without
copying the Codeforces stack. Codeforces keeps its stable ID/page pagination. AtCoder uses
the community-maintained AtCoder Problems resources, timestamp pagination, an independent
request gate, and explicit reliability/capability degradation.

Phase 3 does not add Luogu, Arena, achievements, AI features, HTML scraping, login sessions,
or Phase 4 product work.

## Architecture boundary

The shared `judge` layer owns only judge-independent contracts and orchestration:

- `JudgeAdapter`: identity, capabilities, reliability, and runtime status.
- `JudgeRegistry`: resolves adapters, account connectors, and sync coordinators by
  `JudgeId`; feature code does not branch on judge implementations.
- `JudgeCapability`: makes optional functionality explicit. Unsupported capabilities are
  absent, never represented by `NotImplementedException`.
- `JudgeSyncCoordinator`: one interface whose implementations supply their own ordered
  stage plans.
- `JudgeSyncDispatcher`: validates account/judge identity and routes WorkManager runs.
- `RateLimitedRequestGate`: reusable mechanism instantiated independently per data source.

Codeforces DTOs, verdict mapping, retry policy, and `from/count` planner stay in
`judge/codeforces`. AtCoder DTOs, community API error handling, difficulty semantics,
timestamp planner, and source policies stay in `judge/atcoder`.

## Adapter capabilities and reliability

Codeforces declares official account binding, profile, rating, rating history,
submissions, problem catalog, contests, background sync, and incremental sync.

AtCoder declares community-backed account binding, submissions, problem catalog,
estimated problem difficulty, contests, background sync, and incremental sync. Phase 3
does not declare profile/rating/rating-history support unless the fragile community proxy
is implemented and isolated. An absent rating is rendered as unavailable, never zero.

`AdapterStatus` is separate from persisted account sync state. A failed AtCoder resource
stage can degrade the adapter or produce a partial sync while previously cached resources
remain usable.

## Network policy

Every Codeforces request uses a Codeforces gate configured to 2100 ms. Every AtCoder
Problems request uses a distinct gate configured to at least 1100 ms. Each gate owns its
own mutex and monotonic request-start timestamp; no global mutex serializes judges.

AtCoder uses HTTPS only and only the documented/current community endpoints:

- `resources/contests.json`
- `resources/merged-problems.json`
- `resources/problem-models.json`
- `atcoder-api/v3/user/submissions?user=&from_second=`

Unknown JSON fields are ignored and nullable remote fields stay nullable. Retries are
bounded and limited to transient network/timeout/5xx/rate-limit failures. Cancellation is
always rethrown. No AtCoder HTML, password, cookie, session, CSRF token, or login flow is
used.

## Account binding

Codeforces continues strong binding through its official profile API and adopts the API's
canonical handle.

AtCoder uses soft binding:

1. Trim and validate the handle format.
2. Preserve the entered case; the lookup key is separate from display identity.
3. Probe the submissions source when available.
4. A matching submission confirms the account. An empty response cannot distinguish a
   new/no-submission account from a missing account, so it connects as `UNVERIFIED`.
5. Source unavailability also permits a valid handle to connect as `UNVERIFIED`; it is not
   converted to `USER NOT FOUND`.

The account row therefore stores a verification state and data-source reliability.

## Room v3

Room v3 preserves all v1/v2 data with `MIGRATION_2_3` and no destructive fallback.

- `judge_accounts` gains `verification_state` and `source_reliability`.
- `remote_problems.contest_id` becomes text so AtCoder contest keys such as `abc350` are
  lossless; it gains `difficulty_source` and `last_seen_at`.
- `contests.external_contest_id` becomes text; existing numeric Codeforces IDs are cast to
  decimal strings during migration.
- `rating_changes.contest_id` becomes text for domain consistency while preserving all CF
  values.
- `attempts.contest_id` becomes text so unified attempts retain both judges' contest IDs.
- `sync_states` gains `account_id` and `latest_submission_time_seconds`; the existing
  `latest_external_submission_id` remains the Codeforces ID cursor. Typed fields are used,
  not an opaque JSON cursor.

Migration tests cover 1 to 2, 2 to 3, and 1 to 3, including retained local notes/reviews,
Codeforces cursor values, and text contest identities. The exported `3.json` is committed.

## AtCoder data mapping

AtCoder submission IDs become `externalSubmissionId`; `problem_id` becomes the stable
problem external ID; `contest_id` remains a string. Verdicts map AC/WA/TLE/MLE/RE/CE to
the unified enum, all others to `OTHER`, while the raw result remains stored.

Submission sync materializes a local problem only for an observed submission (or explicit
Add to Training). If catalog metadata exists it is used; otherwise a minimal problem is
created with the official task URL and later enriched. Remote sync updates only remote
metadata and attempt facts; favorites, user tags, notes, failures, reviews, and training
history are never overwritten.

The remote catalog is built by joining merged problems, problem models, and contests in
memory after all resources parse successfully. It is then chunk-upserted. Existing cache
is retained on fetch/parse failure. AtCoder Problems difficulty is stored with source
`ESTIMATED`; AHC/Marathon problems and missing/invalid models remain `UNKNOWN`. The native
AtCoder value is not merged into a cross-judge normalized scale.

Contest rows use string identities and derive UPCOMING/LIVE/ENDED from start and duration
through a judge-independent pure function. Official Codeforces raw phase remains stored.

## Timestamp pagination and crash safety

AtCoder's endpoint returns at most 500 submissions from `from_second`. It cannot reuse the
Codeforces page planner.

- Initial sync starts at zero and persists each page transactionally.
- Incremental sync starts at `max(0, latestSubmissionTimeSeconds - 120)`.
- Every returned row is upserted by `(judge, submission id)`, including known IDs, so
  rejudge changes are refreshed.
- A full page advances the next request to the maximum timestamp itself, not timestamp + 1.
  The repeated boundary is deduplicated by ID.
- Progress means either a larger maximum timestamp or at least one newly observed ID.
- A short page terminates successfully.
- A repeated full-page signature with no new ID is a safe terminal degradation
  (`PAGINATION_STALLED`). In particular, an unpageable 500-row same-second saturation is
  reported partial and the durable cursor is not advanced past that second. This prefers
  an explicit incomplete sync over silent data loss or an infinite loop.
- The durable timestamp cursor is updated only in the same transaction as successfully
  persisted page rows. Process death therefore replays an overlap, which is idempotent.

## Sync and WorkManager

One `JudgeSyncWorker` receives `judgeId`, `accountId`, and `force`, then dispatches through
the registry. Unique manual and periodic work names include both judge and account ID.
Disconnect cancels only those names. Different judges may sync concurrently because gates
and work identities are independent; one judge never overwrites another judge's sync row.

Each coordinator owns its actual stages. Codeforces keeps profile, rating, submissions,
contests, and problems. AtCoder runs submissions, contests, and problems. A stage failure
does not delete cache or abort unrelated successful stages; the aggregate state becomes
PARTIAL when appropriate.

## UI and offline behavior

Settings presents separate Codeforces and AtCoder connection rows, source reliability,
verification state, per-judge sync state, connect/disconnect, and sync-now actions.
Dashboard/Profile expose connected judges without inventing AtCoder rating data. Problems
and Contest Center gain judge filters and use registry-provided URLs. Analytics exposes
per-judge activity breakdown and does not mix estimated AtCoder difficulty with official
Codeforces difficulty without a judge label.

All screens load Room data first. Offline or degraded network state leaves cached data
visible with a status label; it never turns an empty network response into destructive
cache replacement.

## Verification

Implementation is complete only after focused unit tests, migration tests, all existing
Codeforces regressions, `clean test assembleDebug`, exported schema review, secret/host
audit, branch diff review, PR creation, and successful GitHub Actions. The PR remains open
for user review and is not merged in Phase 3.
