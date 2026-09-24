# Luogu Public Sync Boundary / 洛谷公开同步边界

## Context / 背景

Luogu's adapter advertises public profile, rating, contest, problem catalog, and background-sync
capabilities, but deliberately does not advertise `SUBMISSIONS`. The coordinator still invokes the
submission-record stage, which makes every otherwise successful background sync end as
`PARTIAL/AUTH_REQUIRED` and sends an unsupported private-data request. / 洛谷适配器声明了公开资料、
Rating、竞赛、题库和后台同步能力，但明确没有声明 `SUBMISSIONS`；当前协调器仍调用提交记录阶段，
导致每次本应成功的后台同步都变成 `PARTIAL/AUTH_REQUIRED`，并发送无意义的私有数据请求。

## Goal / 目标

Make Luogu background synchronization execute only the capabilities the adapter actually advertises:
profile, rating, contests, and problems. / 让洛谷后台同步只执行适配器真实声明的资料、Rating、
竞赛和题库能力。

## Design / 设计

- Remove `syncSubmissions` from `LuoguSyncCoordinator.syncAccount`.
- Keep the repository method and `SUBMISSIONS` enum available as an explicit future boundary; it
  remains auth-gated and is not silently fabricated.
- A successful four-stage public run produces `SUCCESS`, allowing `JudgeSyncWorker` to complete
  normally instead of retrying around a known unsupported stage.
- Keep the existing local OpenApp submission center and result workers unchanged; they are separate
  request lifecycle data, not public account submission-history synchronization.
- Preserve the existing Settings warning for old persisted `AuthenticationRequired` states so old
  data remains understandable; new public runs do not create that warning.

## Safety boundary / 安全边界

- No Luogu main-site password, Cookie, Session, CSRF state, or private-record import is added.
- No fake submission records are created.
- Public profile/rating/contest/problem data remains local-first and uses existing rate-limited adapters.

## Verification / 验证

- Coordinator tests prove public stage order, success phase, and zero submission-record calls.
- Full `clean test assembleDebug lintDebug` must pass.
- Install and launch the existing emulator without shutting it down; verify no fatal exception.
