# Luogu Public Catalog Action Plan / 洛谷公开题库操作计划

> Execute in the current branch `codex/phase-5-arena`; keep historical documentation and
> published Releases unchanged. / 在当前分支 `codex/phase-5-arena` 执行；保留历史文档和已发布
> Releases，不做删除。

## 1. Contract and RED tests / 契约与 RED 测试

- Add `LuoguPublicCatalogSync` and tests proving account-free catalog synchronization. /
  增加 `LuoguPublicCatalogSync`，并先写证明无需账号即可同步题库的测试。
- Add ViewModel tests for duplicate suppression and success/failure notices. /
  增加 ViewModel 重复启动抑制和成功/失败提示测试。
- Run focused tests and capture the expected compile/test failure before implementation. /
  先运行聚焦测试，记录实现前的预期失败。

## 2. Implementation / 实现

- Refactor `LuoguSyncRepository` to share catalog writing between account-bound and public runs.
  / 重构 `LuoguSyncRepository`，让账号同步和公开同步共享题库写入逻辑。
- Persist standalone public-run status without clearing an existing account association. /
  持久化独立公开同步状态，同时不清空已有账号关联。
- Wire the contract through `AppContainer`, `ProblemsViewModel`, and the Luogu remote catalog UI.
  / 通过 `AppContainer`、`ProblemsViewModel` 和洛谷远端题库 UI 接入契约。
- Add bilingual strings and preserve all prior strings and phase notes. /
  增加双语字符串，保留既有字符串和阶段说明。

## 3. Verification and delivery / 验证与交付

- Run focused tests, `git diff --check`, then `clean test assembleDebug lintDebug`.
  / 运行聚焦测试、`git diff --check`，再运行全量构建测试。
- Install the APK on the existing emulator and verify the app launches without a fatal exception.
  / 安装 APK 到现有模拟器，确认启动无致命异常。
- Commit with bilingual explanation, push the branch, create v0.3.22, upload the APK, and verify
  the public tag/asset SHA. / 使用中英文结合的 commit 说明，推送分支，创建 v0.3.22，上传 APK，
  核对公开标签和产物 SHA。
