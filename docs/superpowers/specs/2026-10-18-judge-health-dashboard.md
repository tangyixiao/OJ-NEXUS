# Spec — Judge Health Dashboard (Phase 96)

## Goal

docs/LONG_RANGE_PLAN.md:193-197: the connector center shows the last HTTP status, the latency
of the last sync, and the Luogu `quotaAvailable` points per judge.

## Constraints

- Read-only projection of the existing sync ledger plus ONE explicit user-triggered probe.
- No background probing; manual refresh only (DoD).
- No schema change: the ledger (`sync_operations.started_at/finished_at/status/last_error_type`)
  already stores everything needed for status + latency; the Luogu quota comes from the existing
  `LuoguOpenQuotaReader.fetchQuota()` flow already exposed in the OpenApp section.

## Design

- `feature/settings/JudgeHealth.kt` (pure): `JudgeHealthSnapshot(lastRunStatus, lastRunErrorType,
  lastRunLatencyMs)`; `deriveJudgeHealth(operations)` picks the most recent FINISHED
  `SyncOperationWithModules` (maxByOrNull startedAt), latency = finishedAt - startedAt clamped to
  null when negative; `formatJudgeHealthLatency` (ms under 1 s, one-decimal s above, "?" below 0);
  `judgeHealthStatusLabel` appends "/<errorType>" only when a failure type exists.
- `ConnectorCenterSection` gains `healthByJudge` + Luogu quota params; the per-judge row renders
  "LAST RUN <status>" and "LATENCY <n>" lines; the Luogu row gets a manual `LuoguQuotaRow`
  (PROBE action → `viewModel.checkOpenAppQuota()`, reusing the existing openAppState quota fields —
  no second probing path exists).
- SettingsScreen wires `deriveJudgeHealth` per connection and passes openAppState quota fields.
- No new network surface, no WorkManager work, no background loop. The probe is the same manual
  button the OpenApp section already had, surfaced in place.

## DoD

- Pure projection tests cover latest-finished selection, error-type pairing, negative-latency
  clamp, and honest latency rendering.
- No background probing: the only quota trigger is the explicit PROBE action.
- Full `test assembleDebug` green; versionCode/Name bump; ROADMAP + plan marks; feat: commit.
