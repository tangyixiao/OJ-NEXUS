# Sync Worker Battery Profile — Spec

Date: 2026-10-18
Phase: 107 (docs/LONG_RANGE_PLAN.md:273-275)

## Goal

Measure and cap periodic sync cost; document interval defaults. DoD: `docs/SYNC_ENGINE.md`
updated with measurements.

## Ground truth (audited, not invented)

- `JudgeSyncWorker.enqueuePeriodic` builds a `PeriodicWorkRequestBuilder<JudgeSyncWorker>(6, HOURS)`
  with `ExistingPeriodicWorkPolicy.KEEP`, network `CONNECTED` constraint, and exponential backoff
  of 10 minutes. `SyncPolicy.PERIODIC_SYNC_MINUTES = 6 * 60` documents the cadence constant, but
  the worker builder hardcodes `6` — this phase unifies both to read `SyncPolicy`.
- Freshness windows gate every stage (`SyncPolicy`): PROFILE/RATING 30 min, SUBMISSIONS 15 min,
  CONTESTS 60 min, PROBLEMSET 24 h. A periodic run therefore performs real network work only for
  stages whose freshness window has elapsed; everything else is a no-op stamp read.
- Per-run request ceiling (steady state, one account, everything fresh except submissions):
  1 submissions boundary page (≤ 1000 items, `SyncPolicy.SUBMISSION_PAGE_SIZE`) + rate-limit gate
  spacing of `SyncPolicy.REQUEST_INTERVAL_MS = 2100 ms` between adapter calls.
- The scheduled-backup worker (Phase 87) already declares `setRequiresBatteryNotLow(true)` +
  `setRequiresStorageNotLow(true)`. The sync worker declares none of the battery constraints —
  this phase aligns it: battery-not-low on periodic sync, manual sync stays immediate.

## Changes

1. `JudgeSyncWorker.enqueuePeriodic` reads the interval from `SyncPolicy.PERIODIC_SYNC_MINUTES`
   (one source of truth) and adds `setRequiresBatteryNotLow(true)`.
2. `docs/SYNC_ENGINE.md` gains a "Battery profile" section documenting the constraint set, the
   interval defaults, the per-run request ceiling, and how measurements were taken.
3. Tests: worker-constraint test asserts the periodic request carries battery-not-low; a policy
   test pins `PERIODIC_SYNC_MINUTES == 360` and the freshness constants as the documented
   defaults.

## Cost measurement (Robolectric, honest caveats)

Wall-clock battery measurement requires hardware; none is attached to this host. What IS
measurable here and is recorded in docs/SYNC_ENGINE.md:

- freshness-window arithmetic (pure): a steady-state periodic run touches at most
  `1` network stage (submissions) when only the 15-minute window elapsed, and `0` stages when
  the worker fires early within all windows;
- request spacing math: N adapter calls take at least `(N-1) × 2.1 s` by the gate;
- WorkManager constraint set verified via test builder input.

Device numbers (battery draw per periodic run) stay explicitly PENDING, same policy as the
cold-start and index-audit phases: never invent hardware numbers.

## Out of scope

- Changing the 6-hour default cadence (documented, kept).
- DeviceIdle / Charging constraints (would starve the 6 h cadence on daily-driver phones).
