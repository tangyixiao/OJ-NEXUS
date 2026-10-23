# OJ NEXUS — Balanced Development Plan, Phase 121–220

> **For agentic workers:** read this roadmap and the linked design first. At implementation
> time, use `superpowers:executing-plans`, or explicitly authorized subagent-driven execution,
> on one phase at a time. Create a phase-specific spec and checkbox plan before writing code.

**Goal:** 用接下来的 100 期，把四端从各自能用推进到身份、数据交换、训练语义与发布证据一致的原生工具。

**Architecture:** 保留 Android Kotlin/Compose、Windows C#/WPF、Linux C++/Qt、Apple Swift/SwiftUI。
公共阶段定义领域契约、测试向量和证据；各端保留独立存储和原生交互，通过版本化领域格式交换数据。

**Tech Stack:** 沿用 [AGENTS.md](../AGENTS.md)。不因“多端”引入跨端 UI 框架，不提前模块化或引入服务端。

**Spec:** [规划设计与基线](superpowers/specs/2026-10-23-balanced-100-phase-roadmap-design.md)。

日期：2026-10-23（Asia/Shanghai）。状态：**执行已开始，逐期按证据更新**。
承接 [Phase 78–120](LONG_RANGE_PLAN.md)，编号严格为 **121–220，共 100 期**。

当前已完成 Phase 121 的[四端能力与证据台账](verification/platform-capabilities.md)及
[审计记录](verification/phase-121-baseline.md)。这只证明基线审计完成，不替代后续平台实现、
设备与发行验收；122–125 继续按各自结果独立登记。原始规划预发布保留为只读历史快照。

## 1. 基线、优先级和完成定义

### 1.1 以当前代码为准

| 项目 | 本轮核实的基线 | 不能据此声称 |
| --- | --- | --- |
| Git | main `1155509`，PR #10 已合并；原开发 head `f7dd035` | 旧本地 origin/main 已自动刷新 |
| Android | `1.0.3 / 105`，Room v15；已有训练、复习、备份、三 OJ 与同步 PoC | PoC 是可交付 E2EE；性能工具等于真机实测 |
| Windows | Core + CLI + WPF；已有题库、复习存储、安装和签名脚本 | 每项 Android 功能已有同等 UI；证书或干净 VM 验收已具备 |
| Linux | 独立 worktree `codex/linux-client`，检查时 `5c42103`；Qt/CLI/SQLite | 这些文件已经进入 main；Windows 上的目录存在等于 Linux 可运行 |
| Apple | Core + UI + macOS/iOS app 源码与 macOS CI 工作流 | 本机能跑 Xcode；CI 编译等于模拟器或真机操作通过 |
| Release | GitHub 已发布 `v1.0.3` | 下一版本可以沿用未经重跑的测试数字 |

旧计划的完成标记保留为历史记录；本续篇不继承其中未经设备证实的结论。
本轮检查没有附着的 Android 设备；Apple/macOS、Linux 桌面和签名材料也不能假定可用。
具体源码依据见第 3 节；未来 API 和商店规则在执行对应期时重新查官方资料。

### 1.2 均衡分配：20 轮 × 5 期

每轮顺序为 **S 公共契约/证据 → A Android → W Windows → L Linux → P Apple**。
五条泳道各 20 期；四端每端都有 20 个原生交付，公共 20 期共同服务四端。
“均衡”指交付机会和可验收能力均衡，不强求相同代码量、开发天数或同日发版。

| 轮次 | 编号 | 主题 | 用户能得到的变化 |
| --- | --- | --- | --- |
| 01 | 121–125 | 基线与证据补齐 | 知道每端哪些可用、哪些仍待实测 |
| 02 | 126–130 | 崩溃恢复与数据保护 | 中断、损坏、重启后保护现有数据 |
| 03 | 131–135 | 可移植数据交换 | 手动在不同端带走非敏感训练记录 |
| 04 | 136–140 | 题库与检索 | 大题库中稳定查找、筛选、恢复位置 |
| 05 | 141–145 | 笔记与知识关系 | 四端可编辑、查找并导出结构化笔记 |
| 06 | 146–150 | 复习闭环 | 一致的调度、结果记录与中断续接 |
| 07 | 151–155 | 训练会话 | 原生创建、暂停、恢复、结束并复盘 |
| 08 | 156–160 | 可解释训练编排 | 根据真实数据选题并说明原因 |
| 09 | 161–165 | 竞赛与赛后补题 | 从竞赛日程进入本地补题计划 |
| 10 | 166–170 | 提交历史与证据 | 各端清楚区分远程提交和本地记录 |
| 11 | 171–175 | 连接器可靠性 | 限流、取消、离线和部分失败可恢复 |
| 12 | 176–180 | 分析与能力证据 | 统计可追溯，不把历史 AC 当成掌握 |
| 13 | 181–185 | 原生交互与可访问性 | 键盘、读屏、缩放和导航各自可用 |
| 14 | 186–190 | 大数据与性能 | 带设备环境的性能与资源使用证据 |
| 15 | 191–195 | 多公开身份隔离 | 切换不同 handle 不串数据 |
| 16 | 196–200 | 隐私与维护 | 可解释地查看、导出或清理本地数据 |
| 17 | 201–205 | 同步安全决策 | 先决定联网同步能否安全继续 |
| 18 | 206–210 | 冲突预演与完整性 | 正式应用前看清冲突与恢复后果 |
| 19 | 211–215 | 分发与升级 | 安装、升级、卸载和回退有真实证据 |
| 20 | 216–220 | 四端候选发布 | 能下载且能核对来源的原生版本 |

### 1.3 状态和执行规则

- 每期标题中的 `[ ]` 初始表示 `PLANNED`，不表示已经批准该期具体设计或完成实现。
  执行时记录 `PLANNED / ACTIVE / BLOCKED / VERIFIED / CUT`，并附日期、commit、PR、证据链接。
- 每期都分别登记 **实现、构建/测试、运行/设备、发布** 四栏。源码和构建成功不能填满设备栏。
  `VERIFIED` 要满足本期所有必需项；host 不可用记 `BLOCKED`，记录恢复条件。
- 同轮共享阶段先定义契约；平台阶段只依赖本期列出的上游。一个端阻塞时，其他满足依赖的端继续。
  不跳过失败数据迁移，不把空实现勾选为完成。
- Phase 121–140 先排入近期；141–180 为中期；181–220 为远期。在每轮结束时重新审视后两轮，
  根据用户反馈、实际成本和接口变化调整内容，但保留编号与变更原因。
- 阶段不是日期承诺；不预填未来 schema/SDK/发行版本。Android 首个新发行版使用大于 105 的
  versionCode，SemVer 根据真实变化决定；文档期不机械增加应用版本。
- 先做现有数据到用户结果的闭环。新增算法、表、依赖和工具链，必须由本期功能证明必要性。
- 公共契约演进期 141/146/151/191 必须同时更新 portable schema、升降版本向量和已有四端 codec
  的契约测试。新字段不兼容时提升格式版本；旧客户端明确拒绝，不能静默忽略关键语义。

## 2. 全局约束、验证和 GitHub 交付

### 2.1 不变量

1. Local First；UI → 状态/用例 → repository → 本地/网络源，OJ 逻辑只在适配器内。
2. 不获取 OJ 密码、Cookie、Session 或浏览器登录，不新增自动提交或自动 POST 重试。
   Android 现有洛谷 OpenApp 的明确操作保留原边界；它的凭据、源码和输入不进入跨端格式。
3. 不引入 AI、聊天、社区动态、营销文案、跨端 WebView 壳或第一方同步服务。
4. Android 的颜色、间距、字体、形状和动效使用 design system；UI 文案资源化，延续英中本地化。
   Windows/Qt/SwiftUI 建立本端 token 与资源，保持 NEXUS 深色、单蓝色和文本状态语义。
5. 业务时间戳使用 UTC；历史 day key 保留写入时本地日，跨端导入不得重算历史日期。
6. 各端数据库互不共用。手动可移植包与“整库本地备份”是两个产品入口；明确告知覆盖与合并差别。
7. 合成 fixture 只用于测试，标注 synthetic；样例、缓存或历史检查不冒充真实远程数据或当前实测。
8. 不覆盖用户无关改动，不使用 broad staging/reset/clean，不混写已有 Linux worktree。

### 2.2 必需验证命令

| 代号 | 执行环境与最低证据 |
| --- | --- |
| G-S | 契约/schema 检查、golden fixture 正反例、所有受影响消费者回归；文档期检查编号/依赖/链接/范围 |
| G-A | Windows 根目录：`tools/gradlew-local.bat test assembleDebug`，必须 BUILD SUCCESSFUL；release/签名变更另跑 assembleRelease 和签名验证；UI/迁移按本期增加设备用例 |
| G-W | `dotnet restore windows/OjNexus.Windows.sln --configfile windows/NuGet.Config --runtime win-x64`；`dotnet test windows/OjNexus.Windows.sln -c Release --no-restore`；`dotnet build windows/OjNexus.Windows.sln -c Release --no-restore`；UI 变更跑本端 smoke 并补充新页面 |
| G-L | 在获准的 Linux checkout 内 `cmake -S linux -B linux/build -G Ninja -DOJNEXUS_BUILD_GUI=ON -DOJNEXUS_BUILD_TESTS=ON`；`cmake --build linux/build`；`ctest --test-dir linux/build --output-on-failure`；Qt GUI 操作另存证据 |
| G-P | macOS/Xcode：`swift test --package-path apple`；`swift build --package-path apple --product OJNexusMacOS`；从 apple 目录按已有 workflow 执行 `xcodebuild -scheme OJNexusIOS -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO`；交互验证分别记录 macOS/iOS |

这些是现有入口，执行前核实项目与 scheme；不在 Windows 伪造 Swift/Linux 成功。
Android 涉及任何代码、资源、schema 或构建配置的阶段必须编译，公共改动触及 Android 消费者同样执行 G-A。
只有规划文档时不改 APK 身份，也不把其他端未执行的检查写成通过。
公共阶段只要求验证已存在的消费者；后续新消费者在自己的平台阶段完成同一组契约测试。

### 2.3 PR、Release 与贡献记录

- **PR：** 每个可独立验收的阶段通常一个 scoped PR，分支 `codex/<phase>-<slug>`，正文包含问题、
  行为、证据、未完成设备项、影响端与依赖。公共契约和平台实现可以有明确依赖的 PR。
  不为凑 100 个 PR 拆空文档或重复提交，也不把 100 期压成一个巨大实现 PR。
- **审查：** 先自查 diff/凭据/迁移，再运行对应 gate，创建 reviewable PR；未满足项保持 draft。
  用户当前授权创建 PR 与发布 Release；**不据此自动合并 PR**。发布 tag 必须指向已审查的实现提交；
  规划快照可指向专用文档分支，明确其未合并状态。
- **频率：** 每轮至少评估一次 RC 发布；某端提前形成完整可用改动可独立发布。共享文档期不强制发二进制。
  未通过的端不随其他端的成功被宣称完成。
- **版本：** 平台独立 SemVer 和 tag；保留现有 Android `v1.0.3` 历史，不改写 tag。首轮明确
  Android/Windows/Linux/macOS/iOS 命名映射，避免多个产物争用不相关版本号。
- **资产：** 使用准确 commit 构建；提供原生包、SHA256、签名状态、环境、迁移/恢复说明和已知限制。
  无证书按渠道规则生成明确的未签名/测试产物；不把它描述为商店可分发安装包。
  候选修复改变 commit 或资产后，更新 216 冻结记录，重跑受影响的构建、设备与升级检查；
  不用旧候选证据批准新资产。
- **规划快照：** 允许 `roadmap-121-220-v1` 这样的 docs-only prerelease，附本规划与设计文档，
  `latest=false`，清楚写“无新应用二进制”；它不替代下一次应用 Release。
- **GitHub 成就：** 维护可追溯的真实 PR/审查/发行记录；共同作者只记录真实且同意署名的贡献者。
  不虚构 co-author，不自造采纳回答，不为徽章跳过审查或发布空版本，不保证徽章或等级。
  徽章规则与展示以 [GitHub 官方 Profile reference](https://docs.github.com/en/account-and-profile/reference/profile-reference)
  为准；Release 操作按 [官方文档](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)。

### 2.4 Review Focus

- 数据损坏、导入中断、旧 generation 的后台任务：126–135、191–195。
- 同题跨 OJ、同 OJ 多 handle、大小写与未知枚举：131、136、171、191 及其四端消费者。
- 午夜、时区变化、重复点击、进程退出后的结果重放：146–155。
- PoC 测试加密、路径穿越、重复 op、同序冲突和恶意包：201–210；在安全 gate 前没有真实联网同步。
- 设备缺席、签名失败、产物与 tag 不匹配：121–125、211–220；保持 BLOCKED，不降级成口头通过。

## 3. 源码范围索引

以下是本次核对的实际入口。阶段的“范围”引用这些代号；新文件路径在执行 spec 中确定，
不能把“规划创建”当作已经存在的源码。测试放在各端对应测试树。

| 代号 | 实际路径 / 用途 |
| --- | --- |
| A-CORE | `app/src/main/java/com/ojnexus/core/`：database、data/repository、domain、model、designsystem、ui |
| A-FEATURE | `app/src/main/java/com/ojnexus/feature/`：problems、training、analytics、contests、submissions、settings、workspace、profile |
| A-JUDGE | `app/src/main/java/com/ojnexus/judge/`：codeforces、atcoder、luogu、sync |
| A-SYNC | `app/src/main/java/com/ojnexus/core/sync/` 与 `core/data/sync/SyncLogRepository.kt` |
| A-TEST | `app/src/test/`、`app/src/androidTest/`、`app/schemas/`、`.github/workflows/android.yml` |
| W | `windows/src/OjNexus.Windows.Core/`、`OjNexus.Windows.Cli/`、`OjNexus.Windows.Desktop/`；`windows/tests/`、`windows/scripts/`、`.github/workflows/windows.yml` |
| L | 现有独立 worktree 的 `linux/src/core/`、`src/cli/`、`src/gui/`、`tests/`、`packaging/`；进入 main 的审查是 Phase 124 的交付 |
| P | `apple/Sources/OJNexusCore/`、`OJNexusUI/`、`apple/Apps/`、`apple/Tests/`、`apple/Package.swift`、`.github/workflows/apple.yml` |
| S | `docs/`、各端测试；规划新增 `docs/contracts/`、`testdata/contracts/` 作为语言无关的规则与合成向量目录 |

实际文档参考：[产品](PRODUCT_SPEC.md)、[架构](ARCHITECTURE.md)、[数据库](DATABASE.md)、
[训练](TRAINING_ENGINE.md)、[分析](ANALYTICS.md)、[适配器](OJ_ADAPTERS.md)、[数据安全](DATA_SAFETY.md)、
[性能](PERFORMANCE.md)、[Windows](../windows/README.md)、[Apple](../apple/README.md)、
[v1.0.0 未完设备清单](releases/v1.0.0-checklist.md)、[同步 RFC](rfcs/0001-e2ee-sync.md)。
旧文档描述落后于源码时，以源码 + 新运行结果纠正，不把历史描述当成当前事实。

## 4. 100 期详细规划

### 第 01 轮 · 基线与证据（121–125）

#### [x] PHASE 121 — S · 四端能力和验收台账（VERIFIED，2026-10-23）
- **依赖 / 范围：** 基线提交；S、既有 CI 与 releases，只读审查各端源码。
- **交付：** 新建四端能力矩阵，按功能列代码入口、支持状态、最近 commit、构建、设备和 Release；纠正旧文档的失真链接、版本与“工具已交付即实测通过”。定义平台 tag 命名。
- **验收：** 每条“支持”都能定位实现与测试；未知项标未验证；完整列出旧 102/103 与设备性能欠账；G-S，无伪造补测结果。

#### [x] PHASE 122 — A · Android 当前版回归基线（VERIFIED，2026-10-23，API 37 模拟器范围）
- **状态：** [最终现场记录](verification/phase-122-android.md) 对应组合提交 `19f0bbc`，785 项单测、33 项 connected、笔记/复习重启、22 表 WAL 完整快照及预览取消均通过；[PR #14](https://github.com/tangyixiao/OJ-NEXUS/pull/14)、[#15](https://github.com/tangyixiao/OJ-NEXUS/pull/15)、[#18](https://github.com/tangyixiao/OJ-NEXUS/pull/18) 待审查。真机、读屏、电量及旧 SQLite 兼容不在此完成范围。
- **依赖 / 范围：** 121；A-TEST、A-FEATURE、启动与备份入口，按失败点修复。
- **交付：** 在当前版本重跑单测、debug/release、迁移；覆盖导航、笔记、复习、训练、同步和导入基本路径，记录设备/模拟器区别。
- **验收：** G-A；可用设备上启动、离线浏览、编辑重开均成功；复现的崩溃有回归；无设备时运行栏阻塞，不能引用 779 个旧测试冒充本次结果。

#### [ ] PHASE 123 — W · Windows 实际功能与安装验收
- **状态：** ACTIVE（2026-10-23）；[验收记录](verification/phase-123-windows.md) 包含 [PR #13](https://github.com/tangyixiao/OJ-NEXUS/pull/13) 五项 CI、CLI 复习跨进程持久化、[PR #17](https://github.com/tangyixiao/OJ-NEXUS/pull/17) 四页 smoke 及[未签名 Windows RC](https://github.com/tangyixiao/OJ-NEXUS/releases/tag/windows-v0.1.1-rc.1)。干净 VM 安装仍独立待验。
- **依赖 / 范围：** 121；W，尤其 MainWindow、DesktopViewModel、CLI、安装脚本。
- **交付：** 核实已存储的题库/复习在 CLI 与 WPF 中的真实可达性；补充现有 smoke 对新入口的覆盖，记录 ZIP/installer 当前能力。
- **验收：** G-W；隔离数据目录从启动到本地题库/复习操作并重启检查；干净 VM 不可用则安装验收保持阻塞；README 不再把旧三页 UI 描述成完整现状。

#### [ ] PHASE 124 — L · Linux 分支收敛到可维护主线
- **状态：** ACTIVE（2026-10-23）；[集成 PR #16](https://github.com/tangyixiao/OJ-NEXUS/pull/16) 已完成五项审查修复，独立原生构建及 8/8 CTest 通过，[Ubuntu CI](https://github.com/tangyixiao/OJ-NEXUS/actions/runs/39967113915) 也通过；[CLI 预览版 RC](https://github.com/tangyixiao/OJ-NEXUS/releases/tag/linux-v0.1.0-rc.1) 已发布并回下载验哈希。原 worktree 保留不变，Qt 桌面交互和干净主机安装尚未验收。
- **依赖 / 范围：** 121；L 的既有分支 diff、构建、测试与拟新增 Linux CI。
- **交付：** 审查独立 Linux worktree 的未合并内容和共同历史，提交 scoped 集成 PR；处理实质冲突，建立主线可构建的 linux 路径与 Ubuntu Qt/CTest CI。
- **验收：** G-L；公共账号同步与离线历史可操作；CLI exit code 契约不变；保留原 worktree 的无关改动；合并前，后续 L 阶段不得假定 main 已含其文件。

#### [ ] PHASE 125 — P · Apple 102/103 的真实验收接续
- **状态：** BLOCKED（交互宿主，2026-10-23）；[接续记录](verification/phase-125-apple.md) 记录远程 89 项测试和两个产品编译通过。macOS/iOS 实际启动和离线操作尚未验收。
- **依赖 / 范围：** 121；P 的既有 Core/UI/apps/CI。
- **交付：** 恢复 Swift/macOS/iOS 构建证据；在具备 macOS/Xcode 的环境运行 app，核查 dashboard、连接器、公开 profile/rating，形成两个平台的欠账清单。
- **验收：** G-P；macOS 与 iOS 模拟器分别启动、离线重启和错误态操作；没有宿主机只记录 BLOCKED 与接续命令，不把远程 CI 当交互验收。

### 第 02 轮 · 中断恢复与数据保护（126–130）

#### [ ] PHASE 126 — S · 数据恢复故障向量
- **状态：** ACTIVE；[设计](superpowers/specs/2026-10-23-phase-126-recovery-vectors-design.md)、[实施计划](superpowers/plans/2026-10-23-phase-126-recovery-vectors.md)与[验收记录](verification/phase-126-recovery-vectors.md)已建立。32 个规范向量与 9 个 Android 决策投影分别登记，不将结构测试记为原生故障恢复。
- **依赖 / 范围：** 121；S，现有 DATA_SAFETY/RestoreProtocol 与各端存储。
- **交付：** 统一“预检→暂存→提交→恢复”状态和错误类别；提供截断、磁盘写满、取消、重启、旧 generation 的合成故障向量；规定提交前不改活动数据。
- **验收：** G-S；每个向量给出旧/新数据应保留哪份、可重试点和恢复结果；不要求桌面复制 Android 数据库结构。

#### [ ] PHASE 127 — A · 备份恢复与后台任务隔离
- **新增接续项（来自 122）：** 当前 WAL 修复使用 `VACUUM INTO`，API 37 的 SQLite 3.50.6 已实测；低于 SQLite 3.27 的设备会明确导出失败。为 minSdk 26 确定兼容策略并做真实旧引擎验证，不能恢复复制过时主库的回退。
- **依赖 / 范围：** 122、126；A-CORE 的 BackupRepository、DatabaseRestoreCoordinator、backup worker，A-TEST。
- **交付：** 对现有快照导入、定时备份增加中断恢复与旧任务写回回归；修复被证实的暂存、generation 或文件替换缺口。
- **验收：** G-A；故障注入后重启得到完整旧库或完整新库；旧 worker 不回写；取消预览不动库；OpenApp 凭据保持独立。

#### [ ] PHASE 128 — W · SQLite 写入和恢复的原子性
- **依赖 / 范围：** 123、126；W 的 SqliteSyncStore、SchemaMigrator、WindowsPaths。
- **交付：** 建立本端一致性备份/恢复入口，保护事务与活动连接；迁移失败、权限不足和损坏库显示稳定错误与用户可选恢复路径。
- **验收：** G-W；中断后旧文件可打开，失败不生成空库覆盖原文件；重启后的 CLI/WPF 展示一致；测试只操作隔离目录。

#### [ ] PHASE 129 — L · SQLite 与配置文件故障恢复
- **接续核查：** 当前配置以 SQLite 设置为实际权威，独立 JSON config 路径没有对应写入实现。先核实实际写入链，再决定恢复范围，避免新增第二份权威配置。
- **依赖 / 范围：** 124、126；L 的 store、paths、CLI runner、Qt 错误状态。
- **交付：** 对 SQLite 与配置文件更新定义临时文件/替换顺序，处理只读目录、损坏行和取消，增加本端备份恢复路径。
- **验收：** G-L；强制中断不产生半配置；存储错误保持既有 exit code；GUI 可读旧缓存并显示错误，正常 XDG 用户目录未被测试污染。

#### [ ] PHASE 130 — P · Apple 本地状态原子保存
- **依赖 / 范围：** 125、126；P 的活动 LocalWorkspaceStore/NexusDashboardModel；核实 legacy LocalLedgerStore 的使用后决定迁移或保留。
- **交付：** 验证现有 `.atomic` 文件保存，补格式版本、损坏隔离和故障恢复；写失败向 UI 传递，保留上一份可恢复快照，避免形成两个权威存储。
- **验收：** G-P；旧格式升级、截断、写失败和重复启动都有确定结果；不以空状态覆盖坏文件；macOS/iOS 各验证一次恢复。

### 第 03 轮 · 手动可移植交换（131–135）

#### [ ] PHASE 131 — S · Portable Workspace v1 契约
- **依赖 / 范围：** 126；S，规划新增 versioned JSON schema、manifest 与 golden 包。
- **交付：** 完整定义题目、笔记、attempt、review、session 的最低可移植字段、稳定 ID、来源端、sourceScopeId、day key、引用和冲突拒绝规则；scope 为非凭据本地标识，不自动绑定目标端 handle。排除凭据、源码、输入、账户配置与工作台草稿。
- **验收：** G-S；覆盖未知字段/版本、缺失引用、重复 ID、大小限制与路径穿越；同包重复导入幂等，同 ID 异内容必须显式处理。未知关键语义拒绝；仅 schema 明确允许的扩展无损保留。141/146/151/191 的新语义需版本化并同步更新 codec/golden；旧包身份不明保持 unassigned，不能归给当前账号。
- **能力协商：** 区分“可编辑”“已理解但仅保存”“未知/不兼容须拒绝”。132–135 都以同一混合 golden 包验证导入→导出无损；暂无领域 UI 时使用 schema 校验且有界的领域归档层保留记录、引用与 source ID，展示保存/可编辑/拒绝数量，后续功能再投影。不得默默丢行仍声称全包成功。

#### [ ] PHASE 132 — A · Android 可移植导入导出
- **依赖 / 范围：** 127、131；A-CORE repository、settings 和系统文件选择。
- **交付：** 新增与整库备份分开的 Portable Workspace 入口；预览数量、来源、跳过与冲突，用户确认后事务应用，支持系统文件访问取消。
- **验收：** G-A；同一混合 golden 包无损往返，重复导入幂等；预览/取消不修改数据库；越界字段拒绝；合成训练记录标注测试来源，暂不可编辑项有保存状态。

#### [ ] PHASE 133 — W · Windows 可移植导入导出
- **依赖 / 范围：** 128、131；W 的 Core codec/store、CLI 与 WPF 文件操作。
- **交付：** CLI 提供 inspect/import/export，WPF 提供预览确认；以领域映射落到本端 schema，保留不能编辑的受支持记录而不静默丢弃。
- **验收：** G-W；混合 golden 包无损往返，暂不能编辑的笔记/session 由领域归档保存；取消/失败事务回滚；CLI JSON 与 UI 计数相符，不直接打开 Android SQLite。

#### [ ] PHASE 134 — L · Linux 可移植导入导出
- **依赖 / 范围：** 129、131；L 的 Core codec/store、CLI 与 Qt file dialog。
- **交付：** 在 CLI/Qt 完成领域包检查、预览和显式导入；保留源 ID 与 day key，处理 sandbox/file permission 失败。
- **验收：** G-L；混合 golden 包无损往返，暂不能编辑的笔记/review/session 有保存状态；错误码一致；路径穿越包不落盘；重复导入不翻倍，取消无部分记录。

#### [ ] PHASE 135 — P · Apple 文档交换入口
- **依赖 / 范围：** 130、131；P 的 Core Codable/store、macOS/iOS 文件导入导出 UI。
- **交付：** 使用本端文档接口完成 inspect/import/export；处理沙盒授权文件生命周期，区分 portable package 与本端恢复快照。
- **验收：** G-P；混合 golden 包在 macOS/iOS 无损往返，尚无编辑 UI 的记录归档保存并报告；授权取消、失效 URL 与写失败有状态；越界字段和不支持版本拒绝。

### 第 04 轮 · 题库与检索（136–140）

#### [ ] PHASE 136 — S · 题目身份和检索语义
- **依赖 / 范围：** 131；S、ProblemKey/ProblemModels 与三 OJ 映射。
- **交付：** 定义 judge+externalId 与本地 UUID 的对应、收藏/标签/状态筛选、稳定排序、缺失难度和搜索大小写语义，提供跨端查询向量。
- **验收：** G-S；同名不同 OJ、相同编号不同 OJ 不合并；无难度不转为 0；排序同值有稳定 ID 次序。

#### [ ] PHASE 137 — A · 题库真正有界查询
- **依赖 / 范围：** 132、136；A-FEATURE problems、ProblemDao/RemoteProblemDao、LibraryWindow。
- **交付：** 对现有“全量过滤再窗口展示”测量，改为必要的 Room 查询分页/窗口，保留完整匹配总数、筛选和返回位置。
- **验收：** G-A；10k/50k synthetic 库无全量 UI 订阅退化；筛选中插入/删除不重复跳页；统计不只计算可见 60 行。

#### [ ] PHASE 138 — W · 原生题库搜索与虚拟化
- **依赖 / 范围：** 133、136；W 的 ProblemModels/store、CLI problems、WPF PROBLEMS。
- **交付：** 在已有题库上补搜索、标签/状态过滤、稳定分页和详情入口，使用 WPF 原生集合虚拟化。
- **验收：** G-W；筛选与 136 向量一致；大量记录不创建全部可视元素；重启和返回保留有效筛选，空结果有清除条件入口。

#### [ ] PHASE 139 — L · Qt 题库模型与查询
- **依赖 / 范围：** 134、136；L 的 store、GUI model、QML 与 CLI。
- **交付：** 新建或扩展本地题库原生列表，采用有界数据模型并支持检索、筛选、外部安全链接与详情。
- **验收：** G-L；10k 数据翻页、键盘选择、刷新不丢失记录身份；域名外异常链接拒绝；离线无网络请求。

#### [ ] PHASE 140 — P · SwiftUI 题库与详情
- **依赖 / 范围：** 135、136；P 的 Core 本地查询、UI 列表/详情。
- **交付：** 为 macOS/iOS 提供原生题库、搜索、筛选和稳定身份详情；外部题面使用系统浏览器，详情显示缓存来源。
- **验收：** G-P；查询向量一致；iOS 返回与 macOS 选择状态合理恢复；列表加载/空/离线/损坏状态可区分。

### 第 05 轮 · 笔记与知识关系（141–145）

#### [ ] PHASE 141 — S · 笔记版本和知识映射契约
- **依赖 / 范围：** 131、136；S、现有 NoteTemplate/KnowledgeAutoLink 文档与测试。
- **交付：** 固化 root cause/key insight/pitfall 的可移植结构、纯文本回退、人工与自动标签来源、未知标签保留和编辑版本字段。
- **验收：** G-S；旧单文本笔记无损往返；未知模板不丢内容；自动标签不能覆盖人工确认关系；跨端换行与 Unicode 样例一致。

#### [ ] PHASE 142 — A · 笔记保存冲突与搜索闭环
- **依赖 / 范围：** 137、141；A-FEATURE problems、KnowledgeRepository、note preferences。
- **交付：** 在现有模板/索引上补离开未保存提示、保存失败恢复和按内容/字段搜索；外部导入修改时避免静默覆盖正在编辑的笔记。
- **验收：** G-A；进程恢复与保存失败保留用户输入；历史单文本不被新模板重写；检索可回到正确题目与字段。

#### [ ] PHASE 143 — W · Windows 笔记编辑器
- **依赖 / 范围：** 138、141；W 的 Core 本地记录、WPF 详情与 CLI notes。
- **交付：** 提供结构化笔记编辑、明确保存反馈与本地搜索，区分人工知识关系和建议映射，写入 portable contract。
- **验收：** G-W；重启可读完整 Unicode/多行内容；冲突不静默覆盖；命令行与 UI 编辑同一记录后版本一致。

#### [ ] PHASE 144 — L · Qt 笔记编辑与知识筛选
- **依赖 / 范围：** 139、141；L 的 Core/store、QML 详情与 CLI。
- **交付：** 实现本端笔记编辑、状态反馈、文本检索及知识标签过滤，保留未知字段的可读回退。
- **验收：** G-L；输入法组合文本、换行、取消与保存失败不丢内容；再次导出符合 141；快捷键不触发重复保存。

#### [ ] PHASE 145 — P · Apple 笔记编辑与恢复
- **依赖 / 范围：** 140、141；P 的 Core store 与 SwiftUI 编辑/检索界面。
- **交付：** macOS/iOS 提供相同笔记字段语义、平台原生编辑、草稿恢复与知识关系选择，明确保存失败。
- **验收：** G-P；系统关闭、场景切换和导入冲突都有保存路径；多窗口编辑同笔记检测版本；导出无静默截断。

### 第 06 轮 · 复习闭环（146–150）

#### [ ] PHASE 146 — S · 复习结果与日历一致性
- **依赖 / 范围：** 131、141；S，现有 ReviewScheduler 与 Windows ReviewModels。
- **交付：** 统一 PASS/HARD/FAIL/SKIP、stage、due day、CRAM 不改正式计划的规则；为午夜、时区、重复点击、重复导入和旧枚举提供 golden 序列。
- **验收：** G-S；同一结果序列四端应得相同阶段和历史 day key；明确事件去重 ID；不把“浏览过题解”记录成独立通过。

#### [ ] PHASE 147 — A · 复习运行中断续接
- **依赖 / 范围：** 142、146；A-FEATURE training 的 ReviewRun/ViewModel、ReviewRepository。
- **交付：** 在现有复习/CRAM 上补进程重建后的待做集合、结果提交状态和已完成计数恢复，明确从正式队列进入还是临时练习。
- **验收：** G-A；重复点结果只记一次；结束前杀进程不重记、不跳过未答；午夜刷新不改变已完成的历史 day key。

#### [ ] PHASE 148 — W · Windows 连续复习原生界面
- **依赖 / 范围：** 143、146；W 的现有 review store/CLI 与 WPF。
- **交付：** 将已存在的 review ladder/CLI 能力接为可连续操作的原生复习队列，显示结果、下一次时间和本次进度，支持暂停恢复。
- **验收：** G-W；与 golden 序列一致；键盘/鼠标重复触发不重记；重启后的剩余队列和 CLI due 一致，空队列无假任务。

#### [ ] PHASE 149 — L · Linux 复习调度和队列
- **依赖 / 范围：** 144、146；L 的 Core 领域/store、CLI review、Qt 队列。
- **交付：** 原生实现调度与结果日志、due/cram 区分、连续复习和暂停续接；迁移只增加真实所需结构。
- **验收：** G-L；四种结果与事件去重 golden 全过；离线可用，事务失败不把 UI 进度推进；同日重复练习不改写原复习日志。

#### [ ] PHASE 150 — P · Apple 复习运行
- **依赖 / 范围：** 145、146；P 的 Core 纯调度器/store、SwiftUI queue/run。
- **交付：** macOS/iOS 原生复习列表、结果记录、下次时间和场景恢复，保留正式复习与 CRAM 的区别。
- **验收：** G-P；同一 golden 输出一致；后台挂起和窗口关闭可续接；VoiceOver 能说明题目、结果与进度，失败保存不跳题。

### 第 07 轮 · 训练会话（151–155）

#### [ ] PHASE 151 — S · Session 状态与计时契约
- **依赖 / 范围：** 131、146；S，现有 SessionStateMachine/SessionClock/SessionDebrief。
- **交付：** 规定 draft/running/paused/finished、单次结束事件、重启计时、题序、尝试来源和导入历史会话规则；活动会话不被导入自动启动。
- **验收：** G-S；时钟倒拨、睡眠/唤醒、重复 finish、部分题目删除均有期望；累计时间不为负，完成会话不再接受无声追加。

#### [ ] PHASE 152 — A · Android 会话完整恢复
- **依赖 / 范围：** 147、151；A-FEATURE training 的 SessionScreen/ViewModel/debrief、TrainingRepository。
- **交付：** 修复并覆盖会话中进程退出、暂停跨天、题目变更和重复结束；复盘区分本地结果、公开同步提交与后续补录。
- **验收：** G-A；重启后状态/题序/累计时长一致；只生成一份结束记录；导入的历史会话不触发当前计时或 worker。

#### [ ] PHASE 153 — W · Windows 训练会话
- **依赖 / 范围：** 148、151；W 的 Core session/store、WPF 原生训练页与 CLI。
- **交付：** 从题库选择题目建立会话，开始/暂停/恢复/结束，记录本地结果并显示简要复盘；不加入本地编译执行器。
- **验收：** G-W；状态向量通过；退出重开可恢复；UI 与 CLI 并发结束不重复写入；计时器仅在活动页需要时刷新显示。

#### [ ] PHASE 154 — L · Qt 训练会话
- **依赖 / 范围：** 149、151；L 的 Core state/store、CLI 和 QML training 页面。
- **交付：** 实现原生选题会话和复盘，支持窗口关闭后恢复；本地 verdict 明确标记来源，不伪装 OJ 提交。
- **验收：** G-L；系统休眠、恢复、取消保存与重复结束有测试；计时不通过每秒数据库写入实现，后台无无意义循环。

#### [ ] PHASE 155 — P · SwiftUI 训练会话
- **依赖 / 范围：** 150、151；P 的 Core session actor/store、macOS/iOS 界面。
- **交付：** 两个平台完成选题、运行、暂停、结束和复盘；跨 scene 的同一会话状态由明确所有者管理。
- **验收：** G-P；双窗口/前后台切换不创建两个活动会话；重启恢复与 151 一致；历史导入保持只读历史状态。

### 第 08 轮 · 可解释训练编排（156–160）

#### [ ] PHASE 156 — S · 候选优先级和理由向量
- **依赖 / 范围：** 136、146、151；S，现有 ActivityPolicy/MasteryEngine/FocusSprint。
- **交付：** 把到期、难度适配、失败次数、弱知识与覆盖的排序输入、权重和 reason code 写成版本化契约；规定数据不足与并列时的稳定回退。
- **验收：** G-S；公开 fixture 给出候选池、分数、最终顺序和理由；不把不同 OJ 原始难度直接等值比较，不推断缺失 rating。

#### [ ] PHASE 157 — A · 训练计划预览与反馈
- **依赖 / 范围：** 152、156；A-FEATURE training FocusSprint/TrainingViewModel、repository。
- **交付：** 在现有推荐上补“为何出现/为何排除”、调整候选与确认生成会话；用真实完成记录评估后续选题，保留用户手动选择。
- **验收：** G-A；同一数据得到同一结果；预览不写会话；失败/无目标/无候选明确；确认前数据变化时重新核验，避免创建空或过期会话。

#### [ ] PHASE 158 — W · Windows 选题计划器
- **依赖 / 范围：** 153、156；W 的 Core 纯排序、WPF 计划预览和 CLI。
- **交付：** 生成可调整的本地候选计划，解释原因并显式转为训练会话；不引入网络推荐服务。
- **验收：** G-W；golden 排序与理由一致；未确认不落库；缺少知识/难度证据显示不足；CLI JSON 与 WPF 同源。

#### [ ] PHASE 159 — L · Linux 选题计划器
- **依赖 / 范围：** 154、156；L 的 Core 候选查询、CLI 与 Qt 预览。
- **交付：** 对本地题库和复习记录生成有界候选，允许删除/替换题目并建立 session。
- **验收：** G-L；golden 与去重一致；大量题库不全量加载到 QML；理由文本资源化，离线和数据不足仍可手动选题。

#### [ ] PHASE 160 — P · Apple 选题计划器
- **依赖 / 范围：** 155、156；P 的 Core 排序与 SwiftUI preview/session handoff。
- **交付：** 原生计划预览、理由和手动调整，macOS 多选与 iOS 触控各自适配，确认后进入已有 session。
- **验收：** G-P；golden 一致；取消不创建会话；前后台更新不静默替换已选题目；说明数据不足而非给虚假的精确掌握值。

### 第 09 轮 · 竞赛与赛后补题（161–165）

#### [ ] PHASE 161 — S · 竞赛身份、时间和补题契约
- **依赖 / 范围：** 136、151；S、ARENA/Contests 模型。
- **交付：** 统一 contest key、未知/变更时间、进行中状态、时区展示、赛中本地标记和赛后 upsolve 关系；定义来源与抓取时间。
- **验收：** G-S；取消/延期、跨日、缺结束时间、同题多赛事向量完整；不从本地勾选推断官方排名或参赛事实。

#### [ ] PHASE 162 — A · Android 从竞赛到补题
- **依赖 / 范围：** 157、161；A-FEATURE contests/Arena、ContestFocusRepository、training。
- **交付：** 在已有 Arena 标记上补赛后未解决集合预览、显式选入 upsolve session、来源回溯，日历变化时及时更新状态。
- **验收：** G-A；标记不被公开同步覆盖；重复进入不建重复 session；赛事时间缺失时不用伪倒计时；离线仍可看已缓存竞赛。

#### [ ] PHASE 163 — W · Windows 竞赛指挥页
- **依赖 / 范围：** 158、161；W 的 contest cache/store、WPF、CLI。
- **交付：** 展示缓存赛程和题目，记录本地赛中标记并显式建立补题 session；适配器只使用已支持的公开能力。
- **验收：** G-W；进行中/即将/已结束状态符合向量；本地标记独立持久化；不支持的 judge 不出现虚假赛程或立即抓取按钮。

#### [ ] PHASE 164 — L · Qt 竞赛与补题工作流
- **依赖 / 范围：** 159、161；L 的 contest models/store、Qt 原生页与 CLI。
- **交付：** 原生赛程、题目上下文、本地标记和 upsolve session 转交，安全打开官方链接。
- **验收：** G-L；系统时间变化后状态刷新；离线保留原抓取时间；更换 handle 不错误归属历史参赛；自动化覆盖 session 去重。

#### [ ] PHASE 165 — P · Apple 竞赛与补题
- **依赖 / 范围：** 160、161；P 的 Core 缓存与 SwiftUI contest/upsolve。
- **交付：** macOS/iOS 均有赛程、本地标记、题目选择和补题 session；系统日历仅在单独明确操作时导出，不静默写入。
- **验收：** G-P；时间向量一致；来源/更新时间可读；无日历权限仍完整可用；不把缓存列表当实时官方状态。

### 第 10 轮 · 提交历史与证据（166–170）

#### [ ] PHASE 166 — S · 提交来源和重判语义
- **依赖 / 范围：** 131、136；S，既有 verdict/attempt/submission 模型。
- **交付：** 固化远程 submission、本地 attempt、OpenApp job 的不同身份；定义未知 verdict、重判更新、稳定去重、题目匹配和只含公开元数据的详情字段。
- **验收：** G-S；同秒不同提交不合并，重判不制造第二次尝试；不支持公开提交历史时保留 unsupported，不能用本地成功替代。

#### [ ] PHASE 167 — A · Android 提交详情与重判刷新
- **依赖 / 范围：** 162、166；A-FEATURE submissions/problems、SubmissionDetailMapping、A-JUDGE。
- **交付：** 统一已存在的详情入口与筛选，显示来源、原始/标准 verdict、更新时间和官方链接；对公开重判更正做到幂等。
- **验收：** G-A；更新历史不重复累计解决数；OpenApp 恢复只查询已有 request ID，不自动重发；未知 verdict 和无题名正常显示。

#### [ ] PHASE 168 — W · Windows 提交时间线
- **依赖 / 范围：** 163、166；W 的 payload persistence、CLI data、WPF history/detail。
- **交付：** 从现有计数/有限行扩展为有界时间线、筛选和详情；稳定去重与重判更新写回同一记录。
- **验收：** G-W；golden 归一化和去重通过；无源码/输入/凭据字段；旧 handle 的提交不投射到新账号；离线详情不发请求。

#### [ ] PHASE 169 — L · Linux 提交时间线
- **依赖 / 范围：** 164、166；L 的 adapter/store 与 Qt/CLI。
- **交付：** 在已有最近提交展示上增加分页、来源过滤和详情；公开接口缺失时保留 typed failure 和缓存。
- **验收：** G-L；反序页面/重复页/重判处理一致；不把缺失列表当成功空数组；外链白名单与不存在的题目回退有测试。

#### [ ] PHASE 170 — P · Apple 提交列表与详情
- **依赖 / 范围：** 165、166；P 的已有 submissions adapter/cache 和 SwiftUI。
- **交付：** 为现有有限行展示提供原生完整分页入口、verdict 过滤与详情，明确显示缓存数据与 unsupported 模块。
- **验收：** G-P；列表/详情的身份稳定；重判不重复；VoiceOver 读出 verdict 文本；不额外引入 Apple 登录或源码抓取。

### 第 11 轮 · 连接器可靠性（171–175）

#### [ ] PHASE 171 — S · 公开连接器故障与恢复契约
- **依赖 / 范围：** 121、166；S、各端公开 HTTP 与 sync 入口，新增连接器状态向量。
- **交付：** 定义超时、限流、Retry-After、取消、unsupported、部分成功与解析失败的分类；限定公开 GET 的退避、重试次数和持久化游标提交点，区分缓存可用与刷新失败。
- **验收：** G-S；429/503、畸形响应、半页成功和取消序列可重放；失败不清空缓存、不推进未落库游标，单 OJ 失败不阻断其他 OJ。

#### [ ] PHASE 172 — A · Android 同步重试与取消闭环
- **依赖 / 范围：** 167、171；A-JUDGE 的 SyncPolicy、JudgeSyncWorker/coordinator，A-FEATURE settings receipt。
- **交付：** 加深现有间隔、去重与模块回执，统一手动/后台刷新取消和限流倒计时；仅重试允许的公开读取，保留已提交模块与旧缓存。
- **验收：** G-A；假时钟与受控响应验证退避和重试上限；取消等待/分页后 worker 正确闭合；现有 Android OpenApp 不自动重发任务。

#### [ ] PHASE 173 — W · Windows 连接器故障恢复
- **依赖 / 范围：** 168、171；W 的 Network、SyncService、SqliteSyncStore、DesktopViewModel。
- **交付：** 扩展已有 CancellationToken 与模块报告，区分限流、超时和离线；WPF/CLI 显示可重试模块与缓存时间，失败重试复用稳定记录身份。
- **验收：** G-W；429 后取消能退出等待，连续点击不并发重复请求；模块落库失败不显示全成功；退出重开可识别未闭合 operation。

#### [ ] PHASE 174 — L · Linux 网络等待和部分失败恢复
- **依赖 / 范围：** 169、171；L 的 http_transport.cpp、sync_engine.cpp、adapters.cpp、Qt viewmodel。
- **交付：** 在已有 stop_token、请求间隔和 ledger 上补限流/超时分类、可取消退避及模块级重试；CLI 与 Qt 共用错误映射。
- **验收：** G-L；受控传输覆盖等待取消、断网和持久化失败；GUI 主线程不等待网络；已成功缓存不被失败模块覆盖，退出码与界面状态一致。

#### [ ] PHASE 175 — P · Apple 公开请求与同步状态恢复
- **依赖 / 范围：** 170、171；P 的 Adapter.swift、NexusDashboardModel、LocalWorkspaceStore。
- **交付：** 让现有 URLSession 读取保留状态码/限流信息，增加可取消的有界退避；统一批量同步、单模块重试和保存失败反馈，不覆盖可用快照。
- **验收：** G-P；受控 HTTP 与时钟验证重试上限；macOS 关闭窗口/iOS 后台取消可恢复；无 capability 明确 unsupported，禁止认证回退。

### 第 12 轮 · 分析与能力证据（176–180）

#### [ ] PHASE 176 — S · 统计与独立能力证据契约
- **依赖 / 范围：** 156、166、171；S、现有 MasteryEngine/AnalyticsDao 与训练领域。
- **交付：** 定义统计窗口、分母、重判和去重规则，区分远程 AC、本地尝试、复习与延迟独立验证；为能力指标规定来源、证据条数和不足状态，保留算法版本。
- **验收：** G-S；同一事件集合输出相同统计；缺 rating 不为零，历史 AC 不自动升格为掌握；图表的每个汇总能回溯纳入/排除记录。

#### [ ] PHASE 177 — A · Android 分析钻取与证据说明
- **依赖 / 范围：** 172、176；A-CORE 的 AnalyticsRepository/MasteryEngine，A-FEATURE analytics/training。
- **交付：** 在现有热图、趋势和知识分数上增加来源筛选、分母说明及记录钻取；单独展示延迟独立验证，旧分数标为规则指标并说明缺证据。
- **验收：** G-A；重判、导入去重和时区向量全过；钻取计数与汇总一致；少量样本不显示虚假精确能力，离线可追溯原记录。

#### [ ] PHASE 178 — W · Windows 本地分析与证据明细
- **依赖 / 范围：** 173、176；W 的 Core 查询/领域、WPF 分析页及 CLI；分析文件按本期新增。
- **交付：** 将本端训练、复习与提交记录汇总为时间窗口、OJ 分布和知识证据，提供原生明细与 CLI JSON；无证据时保留空状态。
- **验收：** G-W；与 176 golden 的分母和去重一致；明细合计能复算图表；远程 AC 与本地独立结果可分别筛选，刷新不改变历史 day key。

#### [ ] PHASE 179 — L · Qt 本地统计与证据钻取
- **依赖 / 范围：** 174、176；L 的 Core/store、CLI、Qt 分析视图，规划新增聚合查询测试。
- **交付：** 用有界聚合展示训练时长、复习、提交和知识证据，明示来源与样本量；图表配文本表格，支持返回参与统计的记录。
- **验收：** G-L；golden 与 CLI/GUI 汇总一致；零分母和未知难度不造数；重判只更正原记录，离线钻取不发请求。

#### [ ] PHASE 180 — P · Apple 分析和能力证据页
- **依赖 / 范围：** 175、176；P 的 Core 纯聚合与 SwiftUI 分析/明细，按本期新增文件。
- **交付：** macOS/iOS 提供时间窗口、来源筛选、可读统计表与证据明细；延迟验证独立记录，不从查看题解或公开通过推断独立完成。
- **验收：** G-P；相同 golden 得到相同计数；VoiceOver 可读指标含义、分母与不足状态；切换窗口不改写原事件，空窗口无假趋势。

### 第 13 轮 · 原生交互与可访问性（181–185）

#### [ ] PHASE 181 — S · 四端任务与可访问性验收脚本
- **依赖 / 范围：** 176；S、各端 design system/原生导航，新增本端任务验收表。
- **交付：** 定义查题、编辑、复习、训练、取消同步五条任务的焦点顺序、状态朗读、缩放和错误恢复结果；按平台分别记录读屏/键盘/触控，保留减弱动态效果。
- **验收：** G-S；每条脚本有起点、操作和可观察结果；图表提供文本等价物，状态不只靠颜色；自动树检查不能替代实际辅助技术操作。

#### [ ] PHASE 182 — A · Android TalkBack 与大字体闭环
- **依赖 / 范围：** 177、181；A-CORE designsystem/ui、A-FEATURE 的列表、表单、训练与分析。
- **交付：** 修复语义合并、焦点返回、触摸目标和错误播报；处理大字体下的表格/按钮重排，为图表补文本入口并沿用 reduce-motion。
- **验收：** G-A；设备上 TalkBack 完成 181 五条任务，200% 字体/320dp 窄屏关键操作可达；导航后焦点有依据；截图与读屏操作单独留证，未实测记阻塞。

#### [ ] PHASE 183 — W · WPF 键盘与 Narrator 完整操作
- **依赖 / 范围：** 178、181；W 的 MainWindow.xaml、DesktopViewModel、原生资源和 UI smoke。
- **交付：** 完善 Tab 顺序、访问键、焦点恢复与 AutomationProperties；长列表、详情和对话框均可键盘操作，缩放后状态及确认按钮可见。
- **验收：** G-W；Narrator 与纯键盘完成五条任务；100%/200% 缩放无关键裁切；取消/保存失败后焦点回到相关控件，动态状态不重复轰炸朗读。

#### [ ] PHASE 184 — L · Qt 焦点与辅助技术操作
- **依赖 / 范围：** 179、181；L 的 QML、viewmodel 和 GUI 测试；集中整理本端 token/文本资源。
- **交付：** 补 Accessible 名称、键盘导航和焦点恢复；改进输入法编辑、缩放重排及图表文本视图，让错误和离线状态可被读出。
- **验收：** G-L；真实 Linux 桌面用键盘及可用读屏工具完成任务；200% 缩放无操作丢失；组合输入不误触快捷键，未具备读屏环境时保留阻塞项。

#### [ ] PHASE 185 — P · macOS 与 iOS 原生辅助交互
- **依赖 / 范围：** 180、181；P 的 NexusRootView、NexusTheme 与后续原生详情/编辑视图。
- **交付：** 分别完善 macOS 键盘菜单/窗口焦点和 iOS Dynamic Type/触控；统一 VoiceOver 标签、状态与图表文本等价物，尊重系统减弱动态效果。
- **验收：** G-P；两平台分别完成 181 脚本；最大辅助字号关键按钮可达，键盘关闭详情后恢复选择；不以一个平台的证据代表另一个。

### 第 14 轮 · 大数据与性能（186–190）

#### [ ] PHASE 186 — S · 可复现数据规模与性能预算
- **依赖 / 范围：** 181；S、docs/PERFORMANCE.md、各端基准入口，新增标注 synthetic 的数据生成规则。
- **交付：** 固定 10k/50k 题目及分档历史记录、冷/热启动和连续导航场景；记录硬件、系统、构建、样本和原始结果，在首次基线后冻结各端预算及回归阈值。
- **验收：** G-S；生成可重现且不污染用户库；分别定义时间、内存和后台资源测量；旧 JVM 数字不充当设备结果，无 host 时证据栏保持阻塞。

#### [ ] PHASE 187 — A · Android 大库与设备性能收口
- **依赖 / 范围：** 182、186；A-CORE DAO/index、A-FEATURE library/analytics、A-TEST 与现有 StartupTracer。
- **交付：** 在查询分页基础上测冷启动、滚动、聚合和后台开销；依据真实热点缩减订阅/批量写入，补完整设备帧耗时及内存记录。
- **验收：** G-A；按 186 固定环境提交原始与汇总结果；验证既有 1500ms 启动预算并如实报告超限；索引改动保留迁移回归，工具存在不算测量通过。

#### [ ] PHASE 188 — W · Windows 大库与界面响应性能
- **依赖 / 范围：** 183、186；W 的 SqliteSyncStore、查询、WPF 长列表与性能脚本。
- **交付：** 测量冷启动、检索、时间线滚动和长时会话；针对热点改进 SQL、虚拟化与取消，记录工作集和 UI 响应，防止大对象全集复制。
- **验收：** G-W；同数据集前后对比满足已冻结预算；连续导航后资源趋稳；性能改动不减少正确匹配总数，结果记录设备/构建及实际样本。

#### [ ] PHASE 189 — L · Linux SQLite 与 Qt 大数据性能
- **依赖 / 范围：** 184、186；L 的 store/query、Qt 数据模型、CLI 与规划新增基准脚本。
- **交付：** 测量检索、分页、聚合、启动和内存；按真实执行计划修复扫描与 UI 阻塞，保留稳定排序及可取消加载，记录桌面与显示环境。
- **验收：** G-L；基线/优化结果可复跑且满足本端预算；50k 题库不实例化全部行；CLI 吞吐与 GUI 响应分开报告，Windows 结果不冒充 Linux 实测。

#### [ ] PHASE 190 — P · Apple 大状态与主线程性能
- **依赖 / 范围：** 185、186；P 的 LocalWorkspaceStore、NexusDashboardModel、本地查询与 SwiftUI 列表。
- **交付：** 测 macOS/iOS 启动、历史钻取、滚动和保存；定位 JSON 整包编解码及重复排序热点，只有证据证明必要时改变本端存储结构并提供迁移。
- **验收：** G-P；两平台分别保留时间/内存原始证据；大状态保存不阻塞交互，取消不产生半文件；新旧快照往返一致，未测设备不填估计值。

### 第 15 轮 · 多公开身份隔离（191–195）

#### [ ] PHASE 191 — S · 多 handle 生产化设计 gate
- **依赖 / 范围：** 131、166、186；S、现有 multi-handle spec/prototype 及四端 account/store。
- **交付：** 审查实际 schema，定义 OJ canonical identity、公开来源 subject/scope、共享题库与私有提交/游标边界、删除和回滚；portable v1 缺 owner 的历史保持 unassigned，不自动归属当前 handle。
- **验收：** G-S；双身份交错写、大小写、未知归属和恢复向量完整；无法无损迁移或阻断旧任务即 gate 失败，生产多身份范围记 CUT/设计 BLOCKED；192–195 交付单身份隔离回归。

#### [ ] PHASE 192 — A · Android 公开身份与游标隔离
- **依赖 / 范围：** 187、191 决策；A-CORE account/DAO/schema、A-JUDGE worker、A-FEATURE settings/profile。
- **交付：** gate 通过才迁移每身份游标、数据归属和切换预览；保留全库 generation，未知历史保持 unassigned。gate 失败则交付单身份替换、解绑及旧 worker 隔离回归，不启用 prototype。
- **验收：** G-A；按决策验证迁移/双 handle 或单身份替换；恢复及旧 worker 测试通过，不误删共享题库；缺 owner 的 v1 导入不归当前 handle，OpenApp 凭据不交换。

#### [ ] PHASE 193 — W · Windows 公开身份隔离与切换
- **依赖 / 范围：** 188、191 决策；W 的 JudgeAccount/JudgeIdentity、SyncService gate、SQLite 与 WPF/CLI。
- **交付：** gate 通过才扩为身份存储、独立缓存和原生切换；两分支均统一锁键与 OJ canonical 规则。失败则交付单身份更换/解绑、迟到响应和 v1 未归属记录隔离回归。
- **验收：** G-W；按 gate 决策验证相应分支；AtCoder 大小写与 Codeforces 等价规则分别测试；迟到响应拒绝、失败回滚，CLI/WPF 不串提交、rating 或统计。

#### [ ] PHASE 194 — L · Linux 多公开身份存储和 Qt 切换
- **依赖 / 范围：** 189、191 决策；L 的 accounts/profile/payload 表、SyncEngine、Qt/CLI。
- **交付：** gate 通过才原子迁移 judge-only 键和身份缓存，提供 Qt/CLI 切换预览；失败则强化单身份替换事务、取消/迟到写入隔离及恢复回归，v1 未归属记录保留 unassigned。
- **验收：** G-L；按决策验证双 handle 同 external_id 或单身份替换；旧库数据不丢，取消/崩溃后身份一致；共享题库不复制，未知归属不自动认领。

#### [ ] PHASE 195 — P · Apple 多公开身份与异步结果隔离
- **依赖 / 范围：** 190、191 决策；P 的 Domain、LocalWorkspaceStore actor、NexusDashboardModel 与原生设置。
- **交付：** gate 通过才用稳定身份隔离缓存/任务并提供切换预览；失败则交付单身份更换、删除和旧 Task 写回隔离。两分支均保留 v1 无 owner 记录为 unassigned，保存失败恢复旧状态。
- **验收：** G-P；两平台按决策验证双身份或单身份替换；双窗口/后台旧 Task 不覆盖新选择，快照恢复无损；portable 包不携带绑定/凭据，缺 owner 不认领。

### 第 16 轮 · 隐私与维护（196–200）

#### [ ] PHASE 196 — S · 数据保留与清理契约
- **依赖 / 范围：** 131、191；S、DATA_SAFETY 与各端存储分类。
- **交付：** 列清公开缓存、本地训练、诊断、备份及凭据的保留边界；定义查看、导出、按身份清理和取消语义，说明外部副本须另行处理。
- **验收：** G-S；向量覆盖共享题库与身份专属记录；预览准确列影响范围；不承诺安全擦除或删除第三方副本，日志不含秘密。 清理结果区分已完成、部分失败和仍需处理的副本。

#### [ ] PHASE 197 — A · Android 数据维护中心
- **依赖 / 范围：** 192、196；A-FEATURE settings、A-CORE backup/cache/crash 与 worker。
- **交付：** 沿用现有备份、崩溃文件和凭据清理入口，增加分类占用、保留说明和清理预览；区分公开缓存、个人记录和外部备份。
- **验收：** G-A；清理取消不写库；旧 worker 不回填已清记录；离线训练保留；OpenApp 清除独立确认且不导出凭据。 重启后重新核对占用及保留数据；错误说明给出可执行的恢复操作。

#### [ ] PHASE 198 — W · Windows 数据维护
- **依赖 / 范围：** 193、196；W 的 paths/store、WPF 设置与 CLI。
- **交付：** 原生展示本端数据目录与分类占用，预览并显式清理缓存/诊断或指定身份数据；导出沿用 portable 与整库恢复的不同入口。
- **验收：** G-W；文件占用、权限不足和中断给出可恢复结果；清理不越出应用数据目录，不影响其他身份或用户已导出的副本。 导出失败不显示成功，重启后仍能核对原有记录。

#### [ ] PHASE 199 — L · Linux 数据维护
- **依赖 / 范围：** 194、196；L 的 XDG paths/store、CLI 与 Qt 设置；124 已集成。
- **交付：** 提供原生分类占用和清理预览，显示配置、缓存、训练数据与导出包边界；删除仅作用明确选择的本端记录。
- **验收：** G-L；软链接、只读目录和活动连接有拒绝或恢复策略；取消无变化；CLI 与 GUI 计数一致，测试不清理真实用户目录。 删除失败显示实际保留项，重启后清理结果可核对。

#### [ ] PHASE 200 — P · Apple 数据维护
- **依赖 / 范围：** 195、196；P 的 workspace/ledger actor、SwiftUI 设置。
- **交付：** macOS/iOS 分别提供本端占用、公开缓存清理和个人记录删除预览；说明沙盒内文件与已导出文档的控制边界。
- **验收：** G-P；两个平台各验证取消、文件授权失效和中断重启；多 scene 不回写已删身份数据；保留其他身份记录，不承诺清除外部副本。 诊断导出前预览内容与范围。

### 第 17 轮 · 同步安全决策（201–205）

#### [ ] PHASE 201 — S · 联网同步 GO / NO-GO
- **依赖 / 范围：** 131、191、196；S、同步 RFC，只读审查 A-SYNC。
- **交付：** 给出威胁模型与书面 gate，审查 caller Boolean、main 中 XOR、事务外时钟、同序 merge、未使用 remoteOp；登记重现方法与结论，选择继续实验或 CUT。
- **验收：** G-S；未逐项证明入口隔离、密钥生命周期、认证完整性与重放防护则联网关闭；禁手写 crypto；CUT 仍交付离线包完整性契约，不承诺 E2EE 上线。 审查结论包含责任人、证据链接和重新开启的必要条件。

#### [ ] PHASE 202 — A · Android 同步隔离与本地完整性
- **依赖 / 范围：** 197、201；A-SYNC、A-TEST、构建开关。
- **交付：** 将 XOR 移出生产源；以 release 不可绕过的入口约束替代单靠 caller Boolean；重现并处理事务外 Lamport、remoteOp 元数据验证与留存缺口。
- **验收：** G-A；并发追加、伪造批次、禁用构建均有反例测试；201 未 GO 或 CUT 时只交付本地账本检查和可移植包损坏拒绝，网络不可达。 不能以配置值或注释替代真实入口验证。

#### [ ] PHASE 203 — W · Windows 同步边界落地
- **依赖 / 范围：** 198、201；W 的 Core portable/store、WPF 与 CLI。
- **交付：** 实现版本/大小/引用及包摘要检查和原生失败说明；仅当 201 GO 才另行落实获准的实验入口与依赖，不照搬 Android PoC。
- **验收：** G-W；默认无联网同步；CUT 时完整交付离线检查报告与损坏包拒绝；摘要不宣称来源认证，秘密不入包；实验入口缺任何 gate 即拒绝运行。 错误报告不包含用户完整路径或记录正文。

#### [ ] PHASE 204 — L · Linux 同步边界落地
- **依赖 / 范围：** 199、201；L 的 portable/store、CLI 与 Qt；124 已集成。
- **交付：** 为手动包提供本端完整性检查、隔离暂存和错误报告；201 GO 后才评估获准实验通道，CUT 则保留明确的原生离线交付。
- **验收：** G-L；截断、超限、重复字段与越界路径拒绝；禁止任意端点和自动上传；摘要只说明损坏检测，关闭 gate 的构建不存在可绕过入口。 错误状态能从原生界面复制脱敏诊断。

#### [ ] PHASE 205 — P · Apple 同步边界落地
- **依赖 / 范围：** 200、201；P 的 portable/store actor 与 macOS/iOS 文档入口。
- **交付：** 两端实现离线包结构/摘要检查和隔离读取；若 GO，单独评审平台维护库、密钥保管与实验隔离；CUT 后交付离线诊断并更新能力说明。
- **验收：** G-P；macOS/iOS 各验损坏、授权撤回、进程中断；无手写加密或登录路径；实验未逐项过 gate 不开启网络，文件摘要不冒充认证。 主线程不承担大包解码与文件校验。

### 第 18 轮 · 冲突预演与完整性（206–210）

#### [ ] PHASE 206 — S · 离线冲突与重放向量
- **依赖 / 范围：** 131、191、201；S、portable 契约与 SyncMergeKernel 审查。
- **交付：** 规定手动包的预演、确认前重检、原子应用和恢复；为同 Lamport/device 的不同 op、重复/乱序/删除提供排列向量，验证现有 merge 同序行为。
- **验收：** G-S；不预判所有审查项为已证实 bug；排列输出一致或明确拒绝歧义；未知关键版本、非法 scope、悬空身份引用拒绝；合法匿名 sourceScopeId/旧包 unassigned 可保存但不自动绑定。网络仍受 201 gate，恢复覆盖引用与历史日期。

#### [ ] PHASE 207 — A · Android 冲突预演
- **依赖 / 范围：** 202、206；A-CORE portable/restore、A-SYNC kernel、settings。
- **交付：** 手动包显示将新增、保留、冲突和拒绝的记录；按 206 修复被重现的同序决策问题，确认前重检目标 generation。
- **验收：** G-A；预演无写入，重复应用幂等；处理中断得到完整旧或新状态；旧预览禁止直接应用；201 未 GO 时此入口仅离线且不唤起传输。 提供可核对的应用结果与恢复记录，不把预演描述成已同步成功。

#### [ ] PHASE 208 — W · Windows 冲突预演
- **依赖 / 范围：** 203、206；W 的 Core import/store、WPF 差异页与 CLI。
- **交付：** 同源生成可读预演和结构化报告，明确每项保留原因；用户确认后原子应用，保存本端恢复点并给出恢复入口。
- **验收：** G-W；同序、乱序、删除和重复包与 206 一致；取消/过期预览不改数据；恢复不串身份；CUT 下离线流程完整，未获 gate 不加网络。 校验失败保留完整旧状态与可读原因。

#### [ ] PHASE 209 — L · Linux 冲突预演
- **依赖 / 范围：** 204、206；L 的 import/store、Qt 与 CLI；124 已集成。
- **交付：** 提供手动包差异预览、冲突理由、显式提交及恢复记录，长列表有界展示；与 CLI 使用同一领域决策。
- **验收：** G-L；golden 与任意输入排列结果一致；写满/中断不留半批次，重放不新增记录；CUT 仍验收完整本地链路，网络 gate 不由预览绕过。 命令行错误码区分预检失败、应用失败与恢复失败。

#### [ ] PHASE 210 — P · Apple 冲突预演
- **依赖 / 范围：** 205、206；P 的 import/store actor、SwiftUI 差异与恢复页。
- **交付：** macOS/iOS 原生展示离线包变化和冲突理由，显式确认再应用；确认时验证文件身份与目标状态未变。
- **验收：** G-P；两端分别验证多 scene 并发、授权失效及中断恢复；重复应用幂等，预览取消不写；CUT 后保留完整离线功能，不开启隐式同步。 失败保留旧状态，完成后可追溯应用批次。

### 第 19 轮 · 分发与升级（211–215）

#### [ ] PHASE 211 — S · 原生产物与升级证据契约
- **依赖 / 范围：** 121、126、196、206；S、各端 packaging/CI/release 文档。
- **交付：** 定义产物到 commit、构建环境、SHA256、签名状态和渠道的映射；列升级、迁移失败、卸载保留及恢复矩阵，版本按实际差异确定。
- **验收：** G-S；不预填未来证书/平台规则；执行时查官方要求；回退先验证格式兼容，否则恢复快照；无签名不得描述为可信正式发行。 发布限制直接显示在下载说明。

#### [ ] PHASE 212 — A · Android 安装与升级演练
- **依赖 / 范围：** 207、211；A-TEST、构建签名与 release 说明。
- **交付：** 对实际待发 APK 跑签名身份、安装/覆盖升级和数据迁移；验证失败恢复、离线启动、导出再恢复，保留升级前备份说明。
- **验收：** G-A；从已发布支持版本到候选版在设备上验证；versionCode 合法递增；不支持降级时明确恢复路径；无设备/签名材料的项保持 BLOCKED。 安装失败可定位对应产物。

#### [ ] PHASE 213 — W · Windows 分发与升级演练
- **依赖 / 范围：** 208、211；W 的 package/installer/sign/verify 脚本。
- **交付：** 将现有文件/版本检查补成干净 VM 安装、升级、卸载及数据保留演练；ZIP 与 installer 分别记录内容、架构和运行环境。
- **验收：** G-W；候选包启动并保留旧训练数据；有 CA 证书才验证可信签名，无则明确未签名/测试分发；自签名不冒充 CA，缺 VM 不算验收通过。 安装日志脱敏，失败路径可复查。

#### [ ] PHASE 214 — L · Linux 分发与升级演练
- **依赖 / 范围：** 209、211；L 的 packaging、CI 与安装说明；124 已集成。
- **交付：** 选择当前可维护的原生分发格式，在干净受支持环境验证依赖、Qt 插件、桌面入口和升级后数据；明确卸载/保留策略。
- **验收：** G-L；安装包不是仅开发机可运行目录；无开发环境也能启动与离线操作；升级失败保护 XDG 数据；支持发行版/架构按实测列出，不泛称全 Linux。 下载后按发布说明可复现验证。

#### [ ] PHASE 215 — P · macOS / iOS 分发演练
- **依赖 / 范围：** 210、211；P 的 apps、Package.swift、CI 与分发配置。
- **交付：** macOS 安装/升级/沙盒文档分别演练；iOS 在当时具备的签名与渠道条件下验证安装、升级和文件导入，说明各自产物身份。
- **验收：** G-P；macOS 与 iOS 独立记录签名、运行和迁移；无证书的模拟器构建不冒充可安装发行包；未具备公证/商店条件则明确 BLOCKED。 迁移失败保留可恢复数据。

### 第 20 轮 · 四端候选发布（216–220）

#### [ ] PHASE 216 — S · 候选发布冻结与证据账本
- **依赖 / 范围：** 211、212–215 的实测/阻塞记录；S、release 清单。
- **交付：** 冻结公共契约与各端候选 commit，汇总功能、升级、设备及同步 GO/CUT；逐端判定可发布渠道，拟定准确资产和已知限制。
- **验收：** G-S；阻塞端单列不连带伪通过；每个待发资产能追到证据；只记录真实 PR/审查贡献，禁止空 Release、虚构署名或预先承诺 GitHub 徽章。 每端放行结论附当前负责人。

#### [ ] PHASE 217 — A · Android 候选版发布
- **依赖 / 范围：** 212、216；A-TEST、APK、Android release 文档。
- **交付：** 对冻结 commit 重跑构建及关键真机链路；有可复现缺陷才形成 scoped 修复 PR，否则提交验证证据。审查和本端 gate 满足后发布 RC、SHA256、签名状态及升级说明。
- **验收：** G-A；从 Release 下载重验安装、离线训练、portable 和恢复；tag/资产/commit 对齐；不复制历史测试数字，设备或签名欠账未清则保持 BLOCKED。 发布说明包含本轮真实变化与未覆盖设备范围；测试数据不进入用户资产。

#### [ ] PHASE 218 — W · Windows 候选版发布
- **依赖 / 范围：** 213、216；W、冻结 ZIP/installer 与发行说明。
- **交付：** 有可复现候选问题才创建真实修复 PR，否则提交验证证据；发布满足 gate 的原生 RC 和哈希，列明支持系统/架构、签名状态、升级与恢复步骤。
- **验收：** G-W；下载产物在干净 VM 重验 WPF/CLI、离线训练与升级；版本和 commit 可核对；无 CA 时明确未签名渠道限制，不把测试证书包装为可信签名。 发布失败不改写旧资产或标签。

#### [ ] PHASE 219 — L · Linux 候选版发布
- **依赖 / 范围：** 214、216；L、CI 与原生发行包；124 已集成。
- **交付：** 在受支持 Linux 环境验证 Qt/CLI；发现缺陷才创建 scoped 修复 PR，否则提交验证证据。发布通过本端 gate 的候选资产、哈希、依赖及恢复说明。
- **验收：** G-L；从下载包完成安装、离线训练、portable 往返及升级；tag 指向审查提交；无 Linux 运行环境不发布伪验证包，独立 worktree 未集成不宣布主线支持。 修复后重跑受影响发布检查。

#### [ ] PHASE 220 — P · Apple 候选版与下一轮交接
- **依赖 / 范围：** 215、216；P、macOS/iOS 各自资产和 release 证据。
- **交付：** 分别验证 macOS/iOS，有可复现缺陷才创建修复 PR，否则提交证据；具备渠道 gate 的平台发布 RC，汇总四端已发/阻塞状态与下一轮缺口，不替其他端关闭欠账。
- **验收：** G-P；macOS 与 iOS 分别验证下载/安装、离线、升级及文档交换；仅源码/模拟器产物按实际标注；附真实 tag、哈希和限制，未达门槛保持 BLOCKED。 后续工作按端保留负责人。

## 5. 跨轮决策与执行接续

### 5.1 两个不能绕过的设计关口

- **191 多身份：** 通过才做生产迁移与切换；失败时把该可选范围记 CUT 或设计 BLOCKED，
  192–195 按已列出的单身份替换/解绑/旧任务隔离交付。不可选的安全回归照常完成。
  阶段只有其实际选择的范围验收后才 VERIFIED，不能把失败的多身份迁移也填作完成。
- **201 同步：** `GO` 首先只是允许按审查结论继续受控实验，不自动允许真实数据联网。
  正式联网还必须提供经维护库实现的认证加密、密钥生命周期、重放/回滚防护、身份校验、
  构建隔离和独立安全审查证据；逐项满足后更新原 gate 记录。未满足时网络保持关闭。
  `NO-GO` 记录整改与复审条件，`CUT` 交付既定离线替代。203–205/207–210 不能据阶段编号
  推定已获网络准入，不把文件摘要称为防恶意篡改的认证。
- 替代路径形成实际交付时，在标题/台账写实际范围和决策链接；不得用“关闭了功能”
  一句话充当整期实现。新发现使本期失去价值时，可提案换范围，保留编号和原因。

### 5.2 第一轮启动顺序

按 [Phase 121–125 启动计划](superpowers/plans/2026-10-23-phase-121-125-kickoff.md)执行：

1. 121 先完成能力/证据矩阵与各端 tag 命名，列出真实宿主机与设备可用性。
2. 122/123/124/125 只在满足各自环境后并行；同一目录/同一文件避免多代理同时写入。
3. Linux 先审查分支集成；Apple 先恢复真实运行环境。两者不是可以用 mock 替代的执行条件。
4. 为独立变化创建 scoped PR。没有实现变化的验证期可提交实测证据，不制造假代码 diff。
5. 具备本端 gate 的真实改动可形成 RC；整体波次完成与单端发行状态分开登记。

### 5.3 每期留档模板

```text
Phase / lane / actual scope:
Status: PLANNED | ACTIVE | BLOCKED | VERIFIED | CUT
Decision and dependency PRs:
Base commit / implementation commit / PR:
Implementation: source and user-visible behavior
Build/tests: command, environment, timestamp, exit code, fresh counts
Runtime: platform, device/VM/simulator, scenario, result, evidence
Release: not requested | not ready | draft | prerelease | stable; tag/commit/assets
Blocking reason / resume condition / owner:
Portable schema and compatibility evidence (when affected):
Known limits and next action:
```

每轮 PR 与发行说明引用该记录；发布日期填实际日期。发布后必须从远程重新下载核对哈希，
不能只对本机构建目录做验证。旧程序不支持新 schema 时，回退必须使用兼容备份或明确拒绝，
不能仅安装旧二进制并假定数据仍可读。

### 5.4 本次规划的审查结论

- 完成仓库基线核对，保留旧计划；前 50 期经独立只读审查，后 50 期分两份并行撰写后统一复核。
- 已纳入 portable 混合记录无损保留、匿名 scope、后续格式演进与四端 codec 回归的修正。
- Apple 现有 `.atomic` 写入按现状验证，不重复造存储；Linux 先集成再扩展。
- 所有 100 期初始状态都是 PLANNED；本次仅交付文档，不触发这些功能的实现或声明完成。
- GitHub 成就规则可能变化；只追踪真实贡献事件，不承诺徽章数。当前个人徽章状态未验证。
