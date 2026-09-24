# Public Sync Queue Visibility / 公开同步队列可见性

## Goal / 目标

After a user binds a public judge handle or taps `SYNC NOW`, the local app must make the
sync lifecycle visible immediately and keep the result understandable after process death.
用户绑定公开账号或点击“立即同步”后，应用必须立即显示同步生命周期，并在进程重启后仍能
看懂同步结果。

## Scope / 范围

- Add a persisted `QUEUED` phase to the existing per-judge `sync_states` row.
- Mark the row queued before WorkManager receives a manual sync request, for both initial
  connection and manual retry.
- Keep `SYNCING`, `SUCCESS`, `PARTIAL`, and `ERROR` semantics unchanged; the worker and
  repositories remain the source of terminal truth.
- Show bilingual/localized queue text, active stage, last successful time, and a concise
  mapped error for sync failures in Settings.
- Keep all data local-first and public-data-only. Do not add Luogu main-site passwords,
  cookies, sessions, CSRF login, cloud accounts, cross-device sync, local compilation,
  custom-input execution, or automatic POST retries.

## Non-goals / 不包含

This phase does not add a WorkManager dashboard, cancel button, submission-history sync,
or any new network endpoint. It does not change the Luogu public synchronization stages.

## Design / 设计

`JudgeDataRepository.markSyncQueued(judge, accountId)` will upsert the existing sync row,
preserving timestamps and cached data while setting `accountId`, `state = QUEUED`,
`startedAt = null`, `finishedAt = null`, and `currentStage = null`. `SettingsViewModel` calls
this method immediately before `JudgeSyncWorker.enqueueManual`. If the scheduler throws,
the row is left visible as queued rather than silently losing the user's action; the next
manual action can reuse the same unique WorkManager name.

The Settings panel maps `QUEUED` to `QUEUED`, `SYNCING` to `SYNCING`, and terminal phases to
their existing labels. It renders `currentStage` only while `SYNCING`. For `PARTIAL` and
`ERROR`, it maps known error type markers (`RateLimited`, `UserNotFound`, `Network`, `Timeout`,
`ServerError`) to resource strings and otherwise shows a generic sync error. Raw server
messages are not surfaced, avoiding unstable or overly technical UI copy.

## Testing / 测试

- Repository test verifies queueing creates/updates the judge sync row without deleting
  existing successful timestamps.
- Settings helper tests verify phase-to-label and error-to-resource-key mapping.
- Existing Luogu sync tests and the full JVM/build/lint gates must remain green.
- Install the final APK on the already-running emulator and verify a connected judge panel
  renders the lifecycle state. The emulator must remain running.
