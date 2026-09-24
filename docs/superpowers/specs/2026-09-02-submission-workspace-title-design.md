# Submission workspace title restoration / 提交工作区题名恢复

## Goal / 目标

When a user opens a Luogu submission from the local submission center, the workspace should
retain the title already stored on that local request row. PID remains the only problem and
submission identity. / 用户从本地提交中心打开洛谷提交时，应继续使用该本地请求记录中已保存的题名；PID
仍是唯一题目和提交身份。

## Scope / 范围

- Change the submission-center callback from `(String) -> Unit` to `(String, String?) -> Unit`.
- Pass the nullable local `SubmissionJobEntity.title` into the existing `NexusRoutes.workspace` route.
- Keep PID-only callers and blank titles compatible with the current route.
- Keep the title as an optional encoded navigation/display value; it never enters an Open Platform
  request or any credential flow.

## Data flow / 数据流

`SubmissionJobEntity.title` → `SubmissionJobCard` action → `SubmissionCenterScreen` callback →
`NexusApp` → `NexusRoutes.workspace(pid, title)` → `WorkspaceScreen(title)`.

The existing route encoder remains the single place responsible for encoding PID and title. No
database migration, network DTO, repository, or background worker is needed. / 现有路由编码器继续作为
PID 和题名的唯一编码入口；本阶段不新增数据库迁移、网络 DTO、Repository 或后台 Worker。

## Error and compatibility behavior / 错误与兼容

Blank or null titles omit the optional route query and open a PID-only workspace. A malformed or
unexpected title is treated as display context only and does not change the PID. Existing
problem-list, detail, and direct workspace navigation remain source-compatible through their
current PID-only callbacks.

## Verification / 验证

- Add a failing route/navigation contract test proving a title is encoded and survives the
  submission-center callback contract.
- Run focused JVM tests, then the repository gate:
  `clean test assembleDebug lintDebug assembleRelease`.
- Install the signed Release APK with `adb install -r` without clearing data, open Submission
  Center, and verify the screen launches without a crash. / 使用 `adb install -r` 覆盖安装，不清除数据，
  打开提交中心并确认无崩溃。

Earlier phases, commits, tags, and Releases remain preserved. / 此前阶段、commit、标签和 Release 均保留。
