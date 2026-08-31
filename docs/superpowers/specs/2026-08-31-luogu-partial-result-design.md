# Phase 22: Luogu Partial Result Convergence / 洛谷部分评测结果收敛设计

## Goal / 目标

Make the Luogu Open Platform result flow distinguish an HTTP 200 partial result from a terminal result. A foreground user checking a submission must never see `READY` merely because the API returned a response body.

让洛谷 Open Platform 结果流程区分 HTTP 200 的部分结果和最终结果。用户在前台查询提交时，不能因为接口返回了响应体就被错误显示为“已完成”。

## Evidence / 依据

The official Open Platform documentation defines HTTP 204 as no result yet, while HTTP 200 can carry either a partial result or a finished result. The result object includes compile, judge, and run details.

洛谷官方 Open Platform 文档说明：HTTP 204 表示暂时没有结果；HTTP 200 既可能携带部分结果，也可能携带最终结果。结果对象包含编译、评测和运行信息。

## Scope / 范围

- Add an explicit `InProgress` result variant for non-terminal HTTP 200 responses.
- Keep `Pending` for HTTP 204 responses.
- Keep `Ready` only for terminal judge status, compile failure, or an available process exit code.
- Continue bounded foreground polling when a partial result is received.
- Preserve the latest partial evaluation in the workspace and submission history.
- Keep the HTTP GET authoritative after an optional official WebSocket wake-up signal.

- 为非终态 HTTP 200 响应增加明确的 `InProgress` 结果类型。
- HTTP 204 继续表示 `Pending`。
- 只有终态评测状态、编译失败或已获得进程退出码时才使用 `Ready`。
- 收到部分结果后继续进行有界前台轮询。
- 在工作区和提交历史中保留最新部分评测信息。
- WebSocket 只作为官方唤醒信号，HTTP GET 仍是权威结果来源。

## Non-goals / 不做事项

- No main-site password, Cookie, Session, or CSRF login.
- No cloud account or cross-device synchronization.
- No background submission, automatic POST retry, or unbounded polling.

- 不实现主站密码、Cookie、Session 或 CSRF 登录。
- 不实现云端账号或跨设备同步。
- 不实现后台提交、自动重试 POST 或无限轮询。

## State model / 状态模型

```text
204                    -> Pending
200 + non-terminal     -> InProgress(evaluation)
200 + terminal         -> Ready(evaluation)
network / API failure  -> typed error and local job FAILED
```

The repository persists `InProgress` details while leaving the local job `PENDING`; only `Ready` materializes a completed local attempt.

仓储层收到 `InProgress` 时保存详情但保持本地任务为 `PENDING`；只有 `Ready` 才会生成完成的本地提交记录。

## Acceptance criteria / 验收标准

1. Client tests prove a non-terminal 200 response is `InProgress`.
2. Polling tests prove partial results continue polling and the bounded fallback returns the latest partial result.
3. Repository tests prove partial details persist without creating a finished attempt.
4. Workspace tests prove partial details remain visible while the UI state stays `PENDING`.
5. `test`, `assembleDebug`, and `lintDebug` pass; the APK is installed and smoke-tested on Pixel_9.

1. 客户端测试证明非终态 200 响应是 `InProgress`。
2. 轮询测试证明部分结果会继续轮询，并且有界轮询结束时返回最新部分结果。
3. 仓储测试证明部分详情会保存，但不会生成完成的提交记录。
4. 工作区测试证明部分详情可见，同时 UI 状态保持 `PENDING`。
5. `test`、`assembleDebug` 和 `lintDebug` 通过；APK 安装到 Pixel_9 并完成冒烟验证。
