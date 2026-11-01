# Phase 130 — Apple active workspace safety

Status: ACTIVE（2026-11-01）。[PR #23](https://github.com/tangyixiao/OJ-NEXUS/pull/23) 已实现
完毕并转为 ready、OPEN、MERGEABLE、未合并（base `codex/phase-130-apple-base`）。实现与原生 CI
门禁全部通过；交互式 macOS/iOS 恢复操作验收因本机无 macOS/iOS 宿主保持 BLOCKED，不把共享
XCTest 或模拟器 XCTest 冒充为应用交互验收，也不发布任何 Apple 二进制。

## Scope

- [设计](../superpowers/specs/2026-10-25-phase-130-apple-state-safety-design.md)
- [实施计划](../superpowers/plans/2026-10-25-phase-130-apple-state-safety.md)
- 活动权威：`apple/Sources/OJNexusCore/LocalWorkspaceStore.swift`，由 `NexusDashboardModel`
  与共享 SwiftUI 面调用；`state.json` 唯一权威，`state.json.bak` 是恢复快照而非第二存储；
  legacy `LocalLedgerStore` 保持 test-only。

## Evidence

| Gate | Source / run | Actual result |
|---|---|---|
| Existing Apple baseline | `036b333`; [40108976919](https://github.com/tangyixiao/OJ-NEXUS/actions/runs/40108976919) | Xcode 15.4 / Swift 5.10, arm64 macOS 14；89 tests zero failures；OJNexusMacOS 构建与 generic iOS Simulator OJNexusIOS 构建通过 |
| Behavioral RED | `9dc46a1`; [40110240638](https://github.com/tangyixiao/OJ-NEXUS/actions/runs/40110240638) | 既有 89 项通过；4 个 WorkspaceStartupRegressionTests 真实断言失败共 5 处（损坏文件被账号写入覆盖、future formatVersion 被丢弃重写、RUNNING ledger 启动被改写、未 load 直接 save 成功）；编译与源边界审计通过 |
| Implemented Core/UI GREEN | `1fd3275`; [41263992374](https://github.com/tangyixiao/OJ-NEXUS/actions/runs/41263992374) | fresh 全套 **103 tests / 0 failures**（含 7 项 WorkspaceRecoveryTests、4 项启动回归、模型迁移与新增防护用例）；源边界审计、OJNexusMacOS 构建、generic iOS 构建、`iOS Simulator` 恢复套件全部 success |
| iOS recovery execution | 同 run 41263992374 | `scripts/test-ios-recovery.sh` 发现 scheme `OJNexusApple-Package` 与真实可用模拟器 iPhone 16 Pro（iOS 18.2, udid `154781AA-E525-424F-9768-390D4DED6241`），`xcodebuild test` exit 0，结构化摘要 `executed=7 / failed=0 / skipped=0 / result=pass`（`ios-recovery-summary.json`），discovery/日志/xcresult 以 artifact `ios-recovery-evidence` 留存 |
| Interactive macOS / iOS recovery | Pending | 共享 XCTest 与模拟器 XCTest 不构成应用交互或物理设备验收；缺宿主，保持 BLOCKED |

### 交付的修复轨迹（全部在本 PR 分支）

- `68ae344`：活动存储、类型化状态机、锁与快照协议、显式恢复、模型/UI 准入与启动回归
  （前一会话实现，未验证）；`7168406`/`97255d3`：Dashboard fixtures 迁移到 load→save→model.load
  （模型准入使旧 fixture 全部空转，其中包括一个空 ledger 越界崩溃），并补齐计划承诺的
  last-cache 保留、存储失败截停批次、恢复后 ready 用例；`5301ba6`：被拒绝的 save 保留类型化
  blocked 原因（不再塌缩为 notLoaded）+ 回归；`d30caa8`：missing-established 观察时清空
  `observedMain`（否则显式恢复的 CAS 把 nil 现值与旧字节比较误报 conflict）；
  `c873d95`/`d5686f0`/`1fd3275`：iOS 恢复 runner——scheme 从 workspace 列表发现、legacy
  `xcresulttool` 的 `_values`/`testStatus` 结构通配解析、结构化摘要与非零门禁，自测 fixture
  覆盖 10 种结果树形态与 5 种发现形态（`python3 scripts/validate_ios_recovery.py --self-test`）。
- RED→GREEN 对应关系：4 个启动回归测试在 RED 运行真实失败，在 GREEN 运行同测试通过；
  未以编译失败冒充行为 RED。
- Android 侧：本 PR 仅改动 `apple/**` 与 `.github/workflows/apple.yml`，Android 源码与
  base 分支逐字节一致；对应 Android 树的门禁证据归 Phase 126 台账（794 项单测）所有，
  本 PR 不另行声明 Android 门禁。

## Intended acceptance contract

支持的旧文件在 load 时字节保持不变，仅在一次被允许的 save 时升级为 flat `formatVersion: 1`。
损坏/不支持/不可用启动会阻断账号与网络变更，同时保留只读缓存视图；负载失败保留最后缓存。
save 使用持久协作写者锁、精确原始字节比较、同目录暂存与原子安装；首建前先装快照，替换前先
把验证过的当前字节装为快照；显式恢复在校验锁下重读并比对观察字节，先隔离损坏主文件再安装
快照，未来格式永不参与恢复。post-install 故障区分已证实提交（推进基线）与需要 reload 的
uncertainOutcome。以上保证覆盖协作进程与同一文件系统上的逻辑替换，不主张断电持久性、非协作
写者或敌意命名空间防护；主文件与快照同时丢失且新鲜进程启动时无法重建历史存在性。

## Delivery boundaries

无 main 合并、无版本递增、无 Apple 发行产物。测试与 CI 证据只使用生成的临时目录与
`$RUNNER_TEMP` 证据目录；不读取或归档用户 Application Support。交互式 macOS/iOS 恢复与
设备验收按原计划保持待办；Phase 130 整期在交互验收完成前保持 ACTIVE。
