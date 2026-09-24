# Phase 23 Implementation Plan / Phase 23 实施计划

## 1. RED tests / RED 测试

- Cover successful save plus quota verification.
- Cover unauthorized cleanup and transient network retention.
- Cover the existing manual quota-check path remains compatible.

- 覆盖保存成功并完成额度验证。
- 覆盖鉴权失败清理凭据和临时网络错误保留凭据。
- 覆盖已有手动检查额度流程保持兼容。

## 2. GREEN implementation / GREEN 实现

- Extend `OpenAppUiState` with verification progress.
- Verify through the injected `LuoguOpenQuotaReader` after writing the Keystore value.
- Clear only credentials rejected by authorization; preserve credentials for transient failures.
- Render localized verifying and validation-error states without exposing secrets.

- 为 `OpenAppUiState` 增加验证进度。
- 写入 Keystore 后使用注入的 `LuoguOpenQuotaReader` 验证。
- 只清理被鉴权拒绝的凭据；临时错误保留凭据。
- 增加本地化验证中和验证错误展示，不暴露秘密值。

## 3. Verify and release / 验证与发布

- Run focused Settings tests, then `clean test assembleDebug lintDebug`.
- Install and launch on Pixel_9; check no fatal crash.
- Append Phase 23 to README and ROADMAP without deleting prior history.
- Push and publish `v0.3.19` with bilingual release notes and APK checksum.

- 先运行 Settings 聚焦测试，再执行 `clean test assembleDebug lintDebug`。
- 安装并启动到 Pixel_9，确认无致命崩溃。
- 在不删除历史记录的前提下追加 README 和 ROADMAP 的 Phase 23。
- 推送并发布 `v0.3.19`，附带双语 Release 说明和 APK 校验值。
