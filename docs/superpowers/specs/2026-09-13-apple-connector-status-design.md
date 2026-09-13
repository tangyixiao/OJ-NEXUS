# Apple Connector Status Projection Design

## Goal

让 macOS 和 iOS 的 CONNECTORS 页面在每个已配置账号行中显示该账号最近一次本地同步结果，同时保持同步状态与账号身份严格对应。

## Scope

本功能只投影已有 `SyncLedger` 数据，不新增网络请求、远端模块、数据库字段、凭据字段或后台任务。账号身份由 `JudgeAccount.judge` 与 `JudgeAccount.handle` 的组合定义；同一 OJ 的不同 handle 不能共享同步状态。

## Architecture

`NexusDashboardModel` 暴露一个只读查询：

```swift
public func lastOperation(for account: JudgeAccount) -> SyncOperation?
```

该查询从当前内存中的 `ledger.operations` 中返回第一个同时匹配 judge 和 handle 的 operation。账本已有倒序插入语义，因此第一个匹配项就是该账号最近的 operation；没有匹配项时返回 `nil`。

`NexusRootView` 在 CONNECTORS 的账号行中复用这个查询，显示 `status.rawValue`，并在 operation 存在时显示 allowlisted 的 `error.rawValue` 与 `finishedAt`。不显示原始网络响应、异常文本、URL、凭据或请求参数。

## UI behavior

- 没有历史 operation：继续只显示账号和画像摘要，不虚构同步时间或状态。
- 成功 operation：显示 `LAST SYNC SUCCESS` 和完成时间。
- `OFFLINE`、`ERROR`、`CANCELLED` 等非成功 operation：显示状态与类型化错误，并保留现有显式 `RETRY` 入口。
- 不同 OJ 使用相同 handle 时，各自只显示自己的 operation。
- 账号 handle 改变或账号移除后，旧 operation 仍保留在 SYNC HISTORY，但不出现在新账号行中。

## Error and lifecycle rules

`lastOperation(for:)` 是纯查询，不修改 ledger，也不触发同步。同步完成后现有 `performProfileSync` 更新 ledger，SwiftUI 通过 `@Published ledger` 刷新账号行。账号禁用、移除或 handle 不匹配时，现有同步入口拒绝创建 operation；该功能不改变这一安全边界。

时间使用已有 `SyncOperation.finishedAt`，仅在存在值时显示。正在运行且尚未完成的 operation 可显示 `RUNNING`，但不得显示不存在的完成时间。

## Testing

在 `DomainTests.swift` 增加模型查询测试，覆盖：

1. 精确匹配 judge+handle 返回最新 operation。
2. 同名 handle 跨 OJ 不串线。
3. 旧 handle operation 对新账号返回 `nil`。
4. 没有 operation 时返回 `nil`。

现有 Linux CTest 作为未修改 worktree 的回归门禁；Apple `swift test`、macOS product build 和 iOS Simulator build 必须在 macOS/Xcode 环境执行。本 Windows 主机缺少 Swift/Xcode 时，只报告静态审计结果，不宣称 Apple 编译通过。

## Non-goals

- 不实现 Apple contests、problemset 或 submissions 同步。
- 不把历史 operation 复制到账号存储中。
- 不添加自动重试、后台同步、通知或云端同步。
