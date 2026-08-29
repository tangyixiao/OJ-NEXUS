# OJ NEXUS — Sync Engine

`CodeforcesSyncCoordinator` runs `PROFILE → RATING → SUBMISSIONS → CONTESTS → PROBLEMSET`.
The Worker owns WorkManager lifecycle and result mapping; the coordinator owns ordering and
disconnect checks; `CodeforcesSyncRepository` owns persistence and per-stage outcomes.

## Submission policy

The first run pages from `from=1` until a short page. Later runs still fetch the newest page,
upsert every item in it, and stop once the page reaches the stored submission boundary. This
overlap is intentional: Codeforces rejudges can change a prior verdict. The stable submission
ID is the idempotency key, so reruns update instead of duplicating attempts.

Each page is persisted in its own Room transaction. The cursor and imported count are updated
after the page commits, providing backpressure and durable partial progress. An accepted result
promotes a problem to solved; later failures never unset that sticky local fact.

## WorkManager and failure behavior

Manual sync uses unique `KEEP` one-time work per account. Periodic sync uses unique periodic
work and stage freshness windows. Network constraints prevent needless offline attempts;
bounded exponential backoff handles retryable rate-limit failures. A stage failure is recorded
without discarding earlier successful stages, producing `PARTIAL` when appropriate.

Disconnect removes the account and sync state in one transaction, optionally removes cached
remote rows, and cancels both unique work names. Local notes, reviews, failures and training
sessions are retained.

## Verification

The Phase 2 suite covers request spacing/retry, API envelopes and error mapping, DTO mappers,
contest phases, migration 1→2, account binding, merge/idempotency/rejudge/sticky-solved
invariants, partial sync and freshness. The repository currently contains 122 `@Test` methods;
the authoritative completion check is `tools/gradlew-local.bat clean test assembleDebug`.
