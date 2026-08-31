# Phase 23: OpenApp Credential Verification / OpenApp 凭据连接测试

## Goal / 目标

Make first-time Luogu OpenApp setup immediately actionable. After the user saves an OpenApp token pair, the app verifies it through the official quota endpoint and reports whether the connection is usable before the user reaches the submission screen.

让首次配置洛谷 OpenApp 变得可用且可判断。用户保存 OpenApp Token 对之后，应用立即通过官方额度接口验证，并在进入提交页面前反馈连接是否可用。

## Data flow / 数据流

```text
validate input -> store in Keystore -> call quotaAvailable
                         |                   |
                         |                   +-- success: configured + quota
                         |                   +-- 401/403: clear invalid credential
                         |                   +-- network/API: keep credential, show pending verification
                         +-- storage failure: remain unconfigured
```

The saved values remain in the existing Keystore-backed store and never enter logs, saved UI state, or backup data. The quota endpoint is read-only and does not submit code or consume judge quota.

保存值继续使用现有 Keystore 存储，不进入日志、保存的 UI 状态或备份数据。额度接口是只读接口，不提交代码，也不消耗评测额度。

## UI behavior / UI 行为

- The save action shows a localized verifying state while the quota request is running.
- A successful verification shows the configured state and available quota.
- Unauthorized/forbidden verification returns to the editor with a localized credential error.
- Network or other API failures retain the credential and show a retryable verification error.
- Existing manual quota checking remains available after configuration.

- 保存按钮在额度请求期间显示本地化的验证中状态。
- 验证成功后显示已配置状态和可用额度。
- 鉴权失败后回到编辑器并显示本地化凭据错误。
- 网络或其他 API 失败时保留凭据，并显示可重试的验证错误。
- 配置完成后仍保留原有手动检查额度入口。

## Non-goals / 不做事项

- No Luogu main-site password, Cookie, Session, or CSRF login.
- No cloud account or cross-device synchronization.
- No background code submission or automatic POST retry.

- 不实现洛谷主站密码、Cookie、Session 或 CSRF 登录。
- 不实现云端账号或跨设备同步。
- 不实现后台代码提交或 POST 自动重试。

## Acceptance criteria / 验收标准

1. Blank input is rejected without a network call.
2. Successful quota verification stores the credential and exposes quota.
3. Unauthorized/forbidden verification clears the credential.
4. Network/API failure keeps the credential and exposes a retryable error.
5. Focused and full tests pass; the APK launches on Pixel_9; README, ROADMAP, commit, and Release remain bilingual.

1. 空输入不发起网络请求并被拒绝。
2. 额度验证成功后保存凭据并展示额度。
3. 鉴权失败后清除凭据。
4. 网络/API 失败时保留凭据并展示可重试错误。
5. 聚焦测试和全量测试通过；APK 在 Pixel_9 启动；README、ROADMAP、commit 和 Release 继续中英文结合。
