# Submission Center Manual Recovery Plan / 提交中心手动恢复计划

## Task 1 — Define the scheduler contract / 定义调度契约

- Add an explicit manual-recovery scheduling method.
- Write RED tests for immediate delay and an independent unique work name.

## Task 2 — Implement manual WorkManager recovery / 实现手动后台恢复

- Keep the normal delayed queue unchanged.
- Add a user-triggered immediate queue using only the request ID.
- Preserve `ExistingWorkPolicy.KEEP` and network constraints.

## Task 3 — Connect the submission center / 接入提交中心

- Add ViewModel action state and exception isolation.
- Render localized `QUEUE CHECK` / `QUEUE RETRY` labels.
- Keep foreground `CHECK RESULT` behavior unchanged.

## Task 4 — Verify and publish / 验证并发布

- Update README, ROADMAP, and release notes bilingually without removing history.
- Run diff checks, the full Gradle verification, emulator smoke, commit, push the
  branch, create the next GitHub Release, and verify tag/branch/asset SHA.
