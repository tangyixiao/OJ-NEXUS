# OJ NEXUS — Sync Engine

`CodeforcesSyncCoordinator` runs `PROFILE → RATING → SUBMISSIONS → CONTESTS → PROBLEMSET`.
`AtCoderSyncCoordinator` runs `SUBMISSIONS → CONTESTS → PROBLEMSET` using the community
source. The shared worker dispatches by judge plus account ID; each coordinator owns ordering,
disconnect checks, persistence, and per-stage outcomes.

## Submission policy

The first run pages from `from=1` until a short page. Later runs still fetch the newest page,
upsert every item in it, and stop once the page reaches the stored submission boundary. This
overlap is intentional: Codeforces rejudges can change a prior verdict. The stable submission
ID is the idempotency key, so reruns update instead of duplicating attempts.

Each page is persisted in its own Room transaction. The cursor and imported count are updated
after the page commits, providing backpressure and durable partial progress. An accepted result
promotes a problem to solved; later failures never unset that sticky local fact.

AtCoder starts at zero for an initial sync and overlaps incremental sync by 120 seconds. Full
pages advance to their maximum timestamp, repeated boundary rows are deduplicated by submission
ID, and a repeated full-page signature without a new ID produces a partial stalled outcome
without advancing the durable cursor past the saturated timestamp.

## WorkManager and failure behavior

Manual sync uses unique `KEEP` one-time work per account. Periodic sync uses unique periodic
work and stage freshness windows. Network constraints prevent needless offline attempts;
bounded exponential backoff handles retryable rate-limit failures. A stage failure is recorded
without discarding earlier successful stages, producing `PARTIAL` when appropriate.

Disconnect removes the account and sync state in one transaction, optionally removes cached
remote rows, and cancels both unique work names. Local notes, reviews, failures and training
sessions are retained.

## Verification

The Phase 3 suite covers independent request spacing/retry, API envelopes and error mapping,
DTO mappers, contest phases, migrations 1→2 and 2→3, account binding, merge/idempotency/
rejudge/sticky-solved invariants, AtCoder timestamp pagination, partial sync and freshness.
The authoritative completion check is `tools/gradlew-local.bat clean test assembleDebug`.
