# Submission title context / 提交题名上下文

## Goal / 目标

Make locally persisted Luogu OpenApp submissions easier to identify in the submission center by
retaining the public problem title that was visible when the user submitted. / 保存用户提交时可见的公开题名，
让本地提交中心中的洛谷 OpenApp 记录更容易辨认。

## Scope / 范围

- Add a nullable local `title` column to `submission_jobs`; existing rows migrate to `NULL`.
- Pass the title from the title-aware workspace into the local submission repository.
- Render a non-blank title next to the PID and include it in the accessible row description.
- Keep PID as the only request identity. The OpenApp HTTP DTO continues to contain only the official
  `pid`, `lang`, `o2`, `code`, and optional `trackId` fields; `title` never crosses the network boundary.
- PID-only and older restored jobs remain valid and retain the existing PID-only presentation.

## Data flow / 数据流

```text
Luogu detail title → WorkspaceState.title → LuoguProblemJudgeRequest.displayTitle
                   → local SubmissionJobEntity.title → Submission Center display
                                      └──────────────X OpenApp request JSON
```

`displayTitle` is local-only request context, not an API field. Blank values are normalized to `NULL`
before persistence. The migration is additive (`10 → 11`) and does not delete or rewrite existing
submission lifecycle or result fields. / `displayTitle` 仅是本地请求上下文，不是 API 字段；空白值在保存前归一化为 `NULL`。
迁移只追加 `10 → 11` 字段，不删除或重写已有提交生命周期和结果字段。

## Error and compatibility behavior / 错误与兼容

- A missing title does not block submission or result polling.
- A title is display-only and never changes request deduplication, result lookup, or attempt
  materialization.
- If a local title write fails, the existing submission request still follows the current repository
  lifecycle; no source code or input is added to submission metadata.

## Verification / 验证

- Unit tests prove the title is forwarded locally, omitted from the OpenApp DTO, shown for new rows,
  and absent-title rows still render.
- Migration tests prove v10 data survives the additive v11 column.
- Run `git diff --check` and the repository gate:
  `clean test assembleDebug lintDebug assembleRelease`.
- Install the signed Release APK over the existing emulator without clearing data, open the existing
  local submission center, and verify no crash. / 在不清除数据的情况下覆盖安装签名 Release APK，打开已有本地提交中心并确认无崩溃。

## Non-goals / 不包含

No main-site password, Cookie, Session, CSRF login, public-history fabrication, cloud service,
cross-device sync, background submission, POST retry, local compiler, or custom-input runner. /
不新增主站密码、Cookie、Session、CSRF 登录，不伪造公开历史，不新增云服务、跨设备同步、后台提交、POST 重试、本地编译器或自定义输入运行器。
