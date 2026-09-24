# Submission Center Manual Recovery / 提交中心手动恢复

## Context / 背景

v0.3.24 adds a local WorkManager job that checks each Luogu OpenApp result after
submission. The submission center can still perform a foreground poll, but it does
not expose a direct way to re-enqueue a pending result when the first background
job was delayed or interrupted. / v0.3.24 已经为洛谷 OpenApp 提交增加本地
WorkManager 结果查询，但提交中心还不能在后台任务延迟或中断时直接重新排队。

## Goal / 目标

Make recovery explicit and usable from the submission center without expanding the
security boundary. / 让提交中心具备明确、可用的恢复入口，同时不扩大安全边界。

## Scope / 范围

- Add a manual `QUEUE CHECK` action for a pending job.
- Add a manual `QUEUE RETRY` action for a failed job.
- Manual queueing is immediate, unique, and independent from the normal delayed
  work name. It uses the same request ID only.
- Keep `CHECK RESULT` as the bounded foreground result poll.
- Keep local Room rows as the source of truth for visible status.
- Add English and Simplified Chinese strings; system language remains the default
  with the existing Settings switch.

## Safety boundary / 安全边界

- The worker remains GET-only against the official Luogu OpenApp result endpoint.
- No main-site password, cookie, session, CSRF state, cloud account, source code,
  standard input, local compiler, or automatic POST retry is added.
- Manual recovery is user-triggered; it does not create a submission and does not
  silently repeat a POST.
- Existing delayed work is not replaced by the manual action. Unique names keep
  one normal queue and one explicit recovery queue per request.

## State and failure handling / 状态与失败处理

- `PENDING`: show `QUEUE CHECK`; after enqueue, show a local action acknowledgement.
- `FAILED`: show `QUEUE RETRY`; the worker can try the GET result endpoint again.
- `READY`: show no recovery action; existing details remain visible.
- Invalid or blank request IDs are ignored by the scheduler.
- Scheduler exceptions become a localized action error and never crash the screen.

## Verification / 验证

- Unit tests verify the two work names, immediate delay, unique KEEP policy, and
  ViewModel duplicate suppression/error handling.
- Full `test`, `assembleDebug`, and `lintDebug` must pass.
- Install and launch the debug APK on the existing emulator without shutting it
  down; verify no fatal exception.
