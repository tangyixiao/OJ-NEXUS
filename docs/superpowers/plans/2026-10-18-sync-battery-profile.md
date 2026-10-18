# Sync Worker Battery Profile — Plan

Date: 2026-10-18
Phase: 107 (docs/LONG_RANGE_PLAN.md:273-275)
Spec: docs/superpowers/specs/2026-10-18-sync-battery-profile.md

## Steps

1. Audit `JudgeSyncWorker` + `SyncPolicy` ground truth (cadence, constraints, freshness windows).
2. Unify the periodic cadence: builder reads `SyncPolicy.PERIODIC_SYNC_MINUTES`.
3. Add `setRequiresBatteryNotLow(true)` to the periodic constraint set.
4. Pin the documented defaults in `SyncBatteryProfileTest` (cadence, freshness ordering, gate
   spacing ≥ 2 s, page budget).
5. Update `docs/SYNC_ENGINE.md` with the battery-profile table + measurement policy.

## Result

- `JudgeSyncWorker.enqueuePeriodic`: interval now `SyncPolicy.PERIODIC_SYNC_MINUTES` (6 h),
  constraints = CONNECTED + requiresBatteryNotLow, backoff unchanged (exponential 10 min).
- `SyncBatteryProfileTest` (5 tests, green): cadence 360 min; freshness ordering
  SUBMISSIONS < PROFILE == RATING < CONTESTS < PROBLEMSET; gate spacing ≥ 2 s; worst-case burst
  ≤ 10 s of spacing; page budget 1000.
- `docs/SYNC_ENGINE.md` gained the "Battery profile — Phase 107" section with the settings
  table, the freshness-window rationale, the Idle/Charging rejection, and the honest PENDING
  device-battery row.
- Full gate: `test assembleDebug` BUILD SUCCESSFUL, 747 tests, 0 failures.
- versionCode 99 / versionName 0.3.99; ROADMAP bilingual section prepended;
  docs/LONG_RANGE_PLAN.md PHASE 107 ✅.

### Lessons

- The cadence constant existed in `SyncPolicy` but the worker hardcoded `6, TimeUnit.HOURS` —
  documentation drift waiting to happen; single source now.
- WorkManager constraint posture already differed between workers (backup had battery-not-low,
  sync did not); aligning sync matched the engine's own philosophy (thrift in background,
  immediacy on manual).
