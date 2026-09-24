# Background Sync Reconciliation / 后台同步启动校准

## Context / 背景

OJ NEXUS already schedules a manual and a six-hour periodic `JudgeSyncWorker` after a
judge account is connected. Existing accounts can survive an app restart, a local Room
backup restore, or an upgrade without that scheduling call being replayed. / OJ NEXUS
连接账号后已经会创建手动同步和六小时周期同步，但应用重启、本地 Room 备份恢复或
升级后，已有账号可能不会再次执行调度入口。

## Goal / 目标

Reconcile active accounts at application startup so every judge that advertises the real
`BACKGROUND_SYNC` capability has its periodic sync work restored. / 应用启动时校准活跃账号，
确保声明真实 `BACKGROUND_SYNC` 能力的平台恢复周期同步。

## Design / 设计

- `JudgeSyncBootstrap` receives an active-account lookup, the supported judge/capability
  snapshot, and a periodic enqueue callback. It contains no Android or database singleton.
- It visits each supported background-sync judge, looks up its active account, skips missing
  or disabled accounts, and enqueues that account's existing periodic work name.
- Each enqueue is isolated with `runCatching`, so one broken WorkManager call cannot prevent
  another judge from being reconciled. No account data is changed.
- `OjNexusApplication` runs the bootstrap in its existing IO application scope after the
  dependency container is ready. The existing connect/disconnect/manual-sync paths stay intact.
- Settings shows a localized `BACKGROUND SYNC ENABLED / 后台同步已启用` capability note for a
  connected judge. This is a capability statement, not a fabricated live WorkManager state.

## Safety boundary / 安全边界

- Sync remains local-first and uses the existing public/API adapters and account handles.
- No main-site password, Cookie, Session, CSRF state, cloud account, or cross-device sync is added.
- The bootstrap does not trigger an immediate sync and does not create submission work.
- Disabled or disconnected accounts are never scheduled.

## Verification / 验证

- Unit tests cover active, disabled, missing, unsupported, and scheduler-failure cases.
- Full `clean test assembleDebug lintDebug` must pass.
- Install and launch the existing emulator without shutting it down; verify no fatal exception.
