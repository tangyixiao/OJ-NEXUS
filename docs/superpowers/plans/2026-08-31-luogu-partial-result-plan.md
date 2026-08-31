# Phase 22 Implementation Plan / Phase 22 实施计划

## 1. Test the contract first / 先测试契约

- Add client coverage for HTTP 200 non-terminal and terminal payloads.
- Add polling coverage for `Pending -> InProgress -> Ready` and bounded partial-result retention.
- Add repository coverage for persistence without attempt materialization.
- Add workspace coverage for `PENDING` plus visible partial evaluation.

- 增加 HTTP 200 非终态和终态响应的客户端测试。
- 增加 `Pending -> InProgress -> Ready` 及有界保留部分结果的轮询测试。
- 增加仓储保存部分结果但不生成提交记录的测试。
- 增加工作区保持 `PENDING` 且展示部分详情的测试。

## 2. Minimal implementation / 最小实现

- Introduce `LuoguOpenResult.InProgress`.
- Classify client responses using shared terminal-result rules.
- Let the polling helper retain and return the latest progress result.
- Persist partial evaluations as local `PENDING`; materialize only terminal results.
- Feed partial evaluations into the workspace without changing its terminal UI state.

- 引入 `LuoguOpenResult.InProgress`。
- 使用共享的终态规则分类客户端响应。
- 让轮询助手保留并返回最新进度结果。
- 部分评测保存为本地 `PENDING`，只有终态结果才生成完成记录。
- 工作区接收部分详情，但不改变终态 UI 状态。

## 3. Documentation and release / 文档与发布

- Append Phase 22 notes to `README.md` and `docs/ROADMAP.md`; do not rewrite historical notes.
- Commit implementation and documentation with Chinese/English explanations.
- Run the full verification command and install the APK on Pixel_9.
- Push the current branch and publish `v0.3.18` with the debug APK and bilingual release notes.

- 在 `README.md` 和 `docs/ROADMAP.md` 末尾追加 Phase 22 记录，不改写历史记录。
- 使用中英文结合的提交说明提交实现和文档。
- 执行全量验证并把 APK 安装到 Pixel_9。
- 推送当前分支并发布 `v0.3.18`，附带 debug APK 和中英文 Release 说明。
