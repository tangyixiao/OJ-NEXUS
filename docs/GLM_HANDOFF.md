# OJ NEXUS 交接文档（给 GLM）

更新时间：2026-10-27（第七轮：Phase 127B 严格后台任务代际准入交付并完成交接）

工作区：`D:\AndroidAppCoding`

> **当前状态速览（2026-10-27，以本节和第 21–23 节为准）：** 主 checkout 分支
> `codex/phase-127-recovery-safety`，本地 HEAD 为包含本交接文档的交付提交（127B 验收文档
> 提交为 `ce582ef`）。Phase 121/122/126 已 VERIFIED，127A（PR #20）与 127B（PR #24）已
> 验收、均未合并，Phase 127 整期仍 ACTIVE。第 2–20 节是历史记录，checkout/分支描述已过时，
> 不要当作当前事实。

## 1. 先读这段

OJ NEXUS 是本地优先的原生 Android 竞赛编程工具，不是社交应用，也不是 AI 产品。UI 使用英文大写、遥测台风格；保持深色、单一 NEXUS BLUE、Material 3 和现有设计系统。禁止 WebView 壳、Flutter/React Native/Electron、伪造 OJ 数据、密码/Cookie 持久化、自动提交和营销文案。

用户要求“继续”时，先审计当前 checkout、分支、未提交改动和真实测试状态，再在现有基础上增量推进；不要重做已经提交的阶段，也不要把历史测试结果当成本次验证。

## 2. 当前 checkout 和边界

主 checkout：

- 分支：`codex/phase-5-arena`
- HEAD：`dde3716 fix: capture and theme Windows UI smoke`
- 相对 `origin/codex/phase-5-arena`：ahead 51
- `origin/codex/phase-5-arena` 当前指向 `b143ded`（Android v0.3.70 发布提交）
- 当前未提交内容包括：Phase 73–76 规划/设计文档、Windows `bin/`/`obj/` 构建产物、该交接草稿，以及 `.worktrees/linux-client/`

不要执行 `git add -A`，不要删除上述内容，不要用 reset/checkout 覆盖用户已有工作。Windows `bin/`/`obj/` 是生成物；如需整理，先确认并只处理明确目标。

Linux 是独立 worktree：

- 路径：`D:\AndroidAppCoding\.worktrees\linux-client`
- 分支：`codex/linux-client`
- 当前有未提交的 `AGENTS.md`、`docs/ARCHITECTURE.md`、`docs/DATA_SAFETY.md`、`docs/PRODUCT_SPEC.md`、`.github/workflows/linux.yml` 和整个 `linux/` 工程
- Linux 命令必须在该 worktree 内运行；不要从主 checkout 误判 Linux 文件不存在，也不要把 Linux 未提交工作混入主分支

## 3. 已完成或已存在的工作

### Android Phase 73–76

当前历史中已经有以下阶段提交：

- `7cdfd81`：safe restore v0.3.71
- `ffae3ce`：action continuity v0.3.72
- `87da387`：training calibration v0.3.73
- `27305b5`、`b8b5669`、`c6d5365`：sync operations ledger v0.3.74

涉及内容包括：可恢复数据库导入、数据代次防旧任务、跨日期本地时间流、训练目标/候选池校准、同步操作历史、模块结果和能力门控重试。关键文件：

- `app/src/main/java/com/ojnexus/core/data/restore/`
- `app/src/main/java/com/ojnexus/core/time/LocalDaySource.kt`
- `app/src/main/java/com/ojnexus/core/domain/TrainingPlanner.kt`
- `app/src/main/java/com/ojnexus/core/database/dao/SyncOperationDao.kt`
- `app/src/main/java/com/ojnexus/judge/JudgeSyncDispatcher.kt`
- `app/src/main/java/com/ojnexus/feature/settings/SyncOperationHistory.kt`

这表示 Android 阶段不应直接重写；但本次交接没有重新执行 Gradle 全套验收。继续 Android 工作前应先运行 focused tests，再运行 `test`、`assembleDebug`、`assembleRelease`、`lintDebug`，必要时做真机/模拟器 smoke。

Android Phase 73 的核心安全要求仍有效：Room 打开前完成恢复收敛；校验 schema、必需表、`quick_check`/`integrity_check`；同目录原子替换并保留 rollback；日志/代次文件不得含密码、Cookie、OpenApp secret、源代码或自定义输入。

### Windows Core / SQLite / Sync

Windows 代码位于 `windows/`。已完成并有历史验证的部分：

- 版本感知 SQLite migration 和数据保留
- 精确检查 PK 顺序、nullability、FK delete action
- `ojnexus.db` 本地数据库
- `SyncService`、`SyncReportProjector`、`InMemorySyncStore`
- 每账号串行、取消感知的重复等待、类型化终态、模块结果持久化
- failure category 显式 allowlist；不得直接持久化 adapter 原始错误字符串
- 重复模块序列 `A,B,A` 最终稳定为 `B,A`

历史结果：Windows Release Core tests 36/36，Release build 0 warning/0 error；这些是历史证据，不是本次新跑结果。

### Linux Qt/C++ 客户端

Linux 工程位于独立 worktree 的 `linux/`，架构为 Qt 6/C++20 + CMake/Ninja，包含 Core、CLI、Qt Quick/QML GUI、SQLite、Codeforces/AtCoder/Luogu 适配器和 DEB/RPM/Arch 打包。Linux v0.x 只接受公开句柄，不包含密码、Cookie、Session 或 Secret Service 凭据路径。

已有证据：Codeforces 公共账号 `tourist` live smoke 成功并写入临时 SQLite；CPack 曾生成 DEB/RPM；QML `qrc` alias 和 Ubuntu QtQuick 运行时问题已修复。

来源边界：

- Codeforces：已做过真实 smoke
- AtCoder：官方用户 HTML 的 `rank_history` 解析属于实验性能力
- Luogu：用户信息接口和列表页解析已修正；未登录 records 会重定向到登录；rating/history 仍未完成真实来源验证，且客户端不会通过 Cookie 绕过该边界

## 4. Windows CLI 四项缺陷：已修复（2026-09-11，本次验证）

上一轮 review 留下的四项问题已全部修复并提交为 `2180087`：

1. Ctrl+C：`Main` 挂接 `Console.CancelKeyPress` 到 `CancellationTokenSource`，取消时返回 exit code 4。
2. 异常脱敏：`Program.RunAsync` 统一捕获非预期异常，stderr 只输出稳定的 `CLI ERROR: ...`，无 stack trace；新增 exit code 5（`GeneralError`）。
3. Parser：`ParseJsonOnly` 改为循环扫描，未知 flag（如 `status --json --bogus`）正确报告 `--bogus`；重复 `--json` 报 `MAY ONLY BE SPECIFIED ONCE`。
4. 真实命令路径测试：新增 `windows/tests/OjNexus.Windows.Cli.Tests/ProgramTests.cs`（7 个用例），通过 `Program.RunAsync(args, output, error, ct, dataDirectoryOverride)` 在临时数据目录内测试 offline `status --json` schema、`history --json`、无 adapter 的 sync（exit 3）、取消（exit 4）、损坏 SQLite 的脱敏（exit 5）、参数错误（exit 2）。

本次验证证据（非历史证据）：

- `dotnet test windows/OjNexus.Windows.sln -c Release`：CLI 24/24 + Core 36/36，全部通过。
- `dotnet build windows/OjNexus.Windows.sln -c Release`：0 警告 0 错误。
- 真实 CLI smoke（Release exe，真实 LocalAppData 路径，跑完已清理）：`status --json` → 0 + `{"status":"ready","accountCount":0,"lastSync":null}`；`history --json` → 0 + `{"operations":[]}`；`sync --judge codeforces --handle tourist --json` → 3 + `UnsupportedJudge`；`--bogus` → 2；重复 `--json` → 2；`config show --json` → 0 且无敏感信息。
- 未做：真实进程中物理 Ctrl+C 的 smoke（CLI 无常驻任务可中断，取消路径由单测覆盖）；如需更强证据，需引入一个可阻塞的 adapter 或在 sync 网络阶段注入延迟。

## 5. Linux 状态（2026-09-15 全量验证，本次新证据）

worktree：`D:\AndroidAppCoding\.worktrees\linux-client`（分支 `codex/linux-client`，`linux/` 整个工程仍是未提交状态——这是用户既有工作，**不要代为提交**，也不要混入主分支）。

2026-09-15 本次验证结果：

- `cmake --build build --parallel 2`：成功，0 错误。
- `ctest --test-dir build --output-on-failure`：8/8 全部通过（core/store/adapter/path/sync-engine/qml/cli/gui）；Secret Service 测试和实现已移除。
- GUI offscreen（`QT_QPA_PLATFORM=offscreen timeout 3 ./build/src/gui/ojnexus-gui`）：持续运行至 timeout，stderr/stdout 零输出，无 QML 错误。
- 三站 live smoke（CLI 真实网络，临时 `XDG_DATA_HOME` 数据库）：
  - Codeforces `tourist`：全部 5 阶段 SUCCESS（profile 1、rating 306、submissions 1000、contests 2146、problemset 11385），exit 0。
  - AtCoder `tourist`：全部 5 阶段 SUCCESS（rating 246、submissions 500、contests 6430、problemset 9513），exit 0。
  - Luogu `uid:2`：PROFILE/CONTESTS/PROBLEMSET 匿名 SUCCESS；SUBMISSIONS 匿名 401，返回 typed `AUTHENTICATION`，客户端无凭据导入路径。

### Luogu rating 来源修正（本次改动）

真实探测证明 Luogu 公开 rating 历史端点不存在：`api/rating/elo` 返回 404，`record/list` 未登录 401，`api/user/info/<uid>` 与 `contest/list`/`problem/list` 匿名 200。此前 AdapterTest 用 fixture stub 了死端点（违反 AGENTS"不得用 fixture 代替真实来源"）。

已在 worktree 内修改（未提交）：

- `linux/src/core/adapters.cpp`：LuoguAdapter 移除 `RATING_HISTORY` 能力和 RATING 死端点阶段，sync 变为 4 阶段。
- `linux/tests/core/AdapterTest.cpp`：删除死端点 fixture，用例改为 4 阶段并重命名 `luoguSyncPersistsAllStages`。
- `docs/ARCHITECTURE.md`：记录 2026-09-12 真实探测结论。

受保护的 Luogu records 不属于当前公开数据客户端；不得通过 Cookie 或 Secret Service 重新引入该路径。

## 6. 推荐给 GLM 的下一轮动作

1. 读取本文件、仓库根目录 `AGENTS.md`、`README.md`，检查主 checkout 与 Linux 独立 worktree 的 status；不要把 Linux 未提交内容混入主分支。
2. Windows 的 AtCoder/Luogu adapter、结构化 payload 持久化、自包含 `win-x64` 目录/ZIP 包和 UI/CLI 验收已经完成；若继续 Windows，应先单独定义 installer、签名、商店发布或正常桌面人工视觉验收的范围，不要把它们默认为当前包已完成。
3. 若转做 Linux，切换到 `D:\AndroidAppCoding\.worktrees\linux-client`，并把 CTest、QML offscreen、三站 live-source 状态分别记录，不能混成一个“通过”。

## 7. 完成标准

除非所有相关测试和真实运行证据都通过，否则使用“部分完成/待验收”，不要使用“DONE”“已发布”。每次交付都应说明：改了哪些文件、使用哪个分支、跑了哪些命令、哪些是历史证据、哪些是本次验证、剩余什么阻塞。

## 8. 剩余阻塞（截至 2026-09-12 第二次更新）

- Windows：Codeforces adapter 已 live 验证（`05ec363`）；AtCoder/Luogu adapter、payload 持久化 schema 未做；Ctrl+C 物理进程级 smoke 未做。
- Linux：`linux/` 与文档改动仍全部未提交，需用户决定提交时机；本次已复验 DEB/RPM、Arch `PKGBUILD` 语法和旧 Cookie 命令拒绝路径。
- Luogu：SUBMISSIONS 与 rating 历史在当前公开数据边界下不可用；匿名下已按 typed `AUTHENTICATION` 诚实报告，不引入登录 Cookie 例外。

## 9. 后续实证（2026-09-12，主 checkout 继续开发）

以下内容是在本交接文档生成后完成的本次证据；不要把第 8 节的旧状态当作当前代码状态：

- Windows payload 持久化已扩展到 schema v3，已接入 AtCoder 公共提交适配器和 Luogu 匿名公开同步适配器；对应提交为 `3d87fee`、`da93a9c`。`data --judge <judge> [--handle <handle>] [--json]` 已在 `95f61f2` 接入。
- Windows Release 当前验证：Core 52/52、CLI 27/27；`dotnet build windows/OjNexus.Windows.sln -c Release --no-restore` 为 0 warning/0 error。真实 Luogu `uid:2` smoke 为 4 阶段成功 3、匿名 SUBMISSIONS 失败 1，exit 1；不宣称匿名提交可用。
- Windows WPF Release 启动 smoke 成功；真实进程 Ctrl+C 已观察到 `Cancelled` 终态。外层 PTY 返回码受 PowerShell 中断影响，不能把它当作 CLI exit code 4 的独立证据。
- Android 当前 checkout 的 `test`、`assembleDebug`、`assembleRelease`、`lintDebug` 均成功；Pixel_9 `connectedDebugAndroidTest` 为 14/14、0 skipped、0 failed。
- Linux 独立 worktree 本次重新验证 CTest 8/8、GUI offscreen 3 秒无 stderr；CPack DEB/RPM 生成并检查成功，包依赖和内容均无 libsecret；Arch `PKGBUILD` 语法通过，但该 WSL 未完成实际 Arch 包构建。

当前继续边界：主 checkout 仍有用户既有未提交内容；Linux worktree 仍独立且未提交，不要混入主分支。Apple 仍需 macOS/Xcode CI；Luogu rating 历史和受保护提交记录不在公开凭据边界内。

## 10. Windows WPF 桌面端继续开发（2026-09-12，本次新证据）

主 checkout 当前分支为 `codex/phase-5-arena`，本次提交为 `dff5d0c`（`feat: implement Windows desktop client shell`）。

- `windows/src/OjNexus.Windows.Desktop/` 已从占位窗口升级为深色、英文大写、NEXUS BLUE 单强调色的本地优先桌面壳：`DASHBOARD`、`CONNECTORS`、`SYNC HISTORY` 三个视图。
- 新增 `DesktopViewModel`：从 SQLite 投影账号、连接器状态、最近同步记录；支持保存公开 handle、同步、取消、Loading/Cancelled/Error 状态；运行时接入 Codeforces/AtCoder/Luogu 三个现有 adapter。
- 新增 Desktop ViewModel 测试项目并加入 `windows/OjNexus.Windows.sln`：刷新投影、保存并同步成功、取消同步、空 handle 错误、历史边界、OJ 筛选、失败重试共 7 个用例。

本次提交后验证：

- `dotnet test windows/OjNexus.Windows.sln -c Release --no-restore`：Core 53/53、CLI 27/27、Desktop 7/7，全部通过。
- `dotnet build windows/OjNexus.Windows.sln -c Release --no-restore`：0 warning、0 error。
- Release WPF 可执行文件真实启动并持续运行 6 秒，无立即退出；随后已结束 smoke 进程。
- `git diff --check` 通过；本次提交只包含 Windows 桌面端 7 个文件。

当前边界：WPF 尚未做人工逐控件点击和截图级视觉验收，也未做安装包/签名/发布验收；公开 API 的网络 live 证据仍沿用前述 Core/CLI 记录。`docs/GLM_HANDOFF.md` 仍为未跟踪交接草稿，本节也不属于 `dff5d0c`。

## 11. Windows Task 8：CI、smoke 与发布记录（2026-09-12，本次新证据）

在 `dff5d0c` 之后，主 checkout 又完成了 Windows Task 8 的增量工作，已提交为 `2a7f914`（`release: add Windows CI and smoke verification`）和 `11e39a7`（`fix: align Windows CLI artifact name`）；本交接草稿本身仍未跟踪：

- 新增 `.github/workflows/windows.yml`：Windows runner 上执行 .NET 8 restore、Release solution test/build、CLI/Desktop framework-dependent publish，并上传二进制和 TRX 报告。
- 新增 `windows/scripts/smoke.ps1`：检查两个绝对路径二进制，运行 `status --json`，校验 exit code 0 和 `status=ready`，确认临时 SQLite 被创建，再在 15 秒边界内启动 WPF，并只终止该进程。
- CLI 程序集名已统一为 `ojnexus.exe`，与 Windows 设计文档和命令示例一致；publish 产物目录、CI smoke 路径和 Windows 文档已同步。
- `SYNC HISTORY` 已补齐最多 5 条可见记录、按 OJ 筛选和失败/部分/取消/离线操作的显式 full-sync `RETRY`。
- `WindowsPaths` 支持 `OJ_NEXUS_DATA_DIRECTORY` 进程级自动化覆盖；默认路径仍为 `%LOCALAPPDATA%\OJ-NEXUS`。新增 Core 回归测试。
- 新增 `windows/README.md` 和 `docs/releases/windows-v0.1.0.md`，并更新根 README、ROADMAP、`.gitignore`。

本次 fresh 验证：`dotnet restore windows/OjNexus.Windows.sln --configfile windows/NuGet.Config`；`dotnet test ... -c Release --no-restore` 为 Core 53/53、CLI 27/27、Desktop 7/7；`dotnet build ... -c Release --no-restore` 为 0 warning/0 error；CLI/Desktop publish 成功；`pwsh -NoProfile -File windows/scripts/smoke.ps1` 成功并清理临时目录；缺失 CLI 二进制的负向 smoke 返回 exit 1；PowerShell AST 解析通过；UI Automation 完成 CONNECTORS、公开 handle 保存、状态切换，以及 SYNC HISTORY 筛选/空状态检查，并生成深色窗口截图；`git diff --check` 通过。

当前边界：Windows CI 尚未在 GitHub runner 上实际执行，只有本地 workflow/脚本静态检查和 Windows 本机执行证据；尚未制作 installer、签名或商店包。Linux worktree 仍保持用户既有未提交状态，不要代为提交或混入主 checkout。

## 12. Windows 桌面端并发取消修复（2026-09-12，本次继续开发）

本次在主 checkout 的 `codex/phase-5-arena` 分支继续修复了 WPF 同步交互：旧实现只保存一个全局取消源，多个连接器同时同步时后启动的任务会覆盖前一个取消源，导致 `CANCEL` 可能留下无法取消的任务。

- `DesktopViewModel` 现在按 `ConnectorRow` 保存活动 `CancellationTokenSource`；连接器按钮只取消对应 OJ，窗口关闭和无参数 `CancelSync()` 仍取消全部活动同步。
- 新增三个并发/重复操作回归用例：全局取消不留下 stuck operation；单连接器取消不影响另一个同步；同一连接器的重复同步会立即拒绝。
- `windows/README.md` 已记录该交互边界。

本次验证：先运行新增测试并确认旧实现按预期在 2 秒边界失败；修复后 Desktop tests 10/10；Windows solution tests Core 53/53、CLI 27/27、Desktop 10/10；Release build 0 warning/0 error；`pwsh -NoProfile -File windows/scripts/smoke.ps1 -Configuration Release` 成功，CLI `status --json` 返回 ready，WPF 在 15 秒内启动。未执行 GitHub runner、installer、签名或商店发布验收。

## 14. Windows 桌面端错误投影修复（2026-09-12，本次继续开发）

审计发现同步失败后 `SyncConnectorAsync` 的 finally 会刷新本地状态，而旧 `SyncOperation` 投影没有携带 `sync_operations.failure_category`，导致刷新后 `LastError` 被清空。现已保留 7 参数 `SyncOperation` 构造函数以维持反射契约，并新增带 `SyncError` 的重载；SyncService、InMemory store、SQLite store 和 Desktop 投影均传递/恢复该类型化错误。模块级失败仍从 allowlist 后的 `FailureType` 投影。

- 新增 Desktop 回归：失败同步刷新后仍显示 `NETWORK`。
- 新增 SQLite 回归断言：操作回读保留 `SyncError.Network`。
- 本次修复后 Windows solution tests 为 Core 53/53、CLI 27/27、Desktop 12/12；Release build 0 warning/0 error；Windows CLI/WPF smoke 成功。

## 15. Windows 历史摘要原因显示（2026-09-13，本次继续开发）

历史页现在在模块摘要中显示经过 allowlist 的失败原因，例如 `SUBMISSIONS:ERROR (NETWORK)`；整个操作错误则追加 `ERROR:<CATEGORY>`。新增 Desktop 回归覆盖模块级 Network 原因在刷新后仍出现在 `HistoryRow.ModuleSummary`。修复后 Windows solution tests 仍为 Core 53/53、CLI 27/27、Desktop 12/12；Release build 0 warning/0 error；CLI/WPF smoke 成功。

## 13. Android 当前 checkout 复验（2026-09-12，本次继续开发）

- 直接运行 `gradlew.bat` 在当前 PowerShell 环境中因 `JAVA_HOME` 未设置/解析异常而失败；使用交接约定的 `tools/gradlew-local.bat` 正确绑定 `D:\Android Studio\jbr`。
- `tools/gradlew-local.bat test assembleDebug assembleRelease lintDebug`：`BUILD SUCCESSFUL`。
- Pixel_9 模拟器已启动并报告 `sys.boot_completed=1`；`tools/gradlew-local.bat connectedDebugAndroidTest`：14/14、0 skipped、0 failed，`BUILD SUCCESSFUL`。
- 已安装 `app-debug.apk` 并冷启动 `com.ojnexus/.MainActivity`，进程 PID 4740；最近 800 行 logcat 未发现 `FATAL EXCEPTION` 或 `AndroidRuntime` 崩溃。
- 当前 `assembleRelease` 的 Gradle 原始产物为 `app-release-unsigned.apk`；随后使用本机标准 Android debug keystore 在仓库外生成了 `OJ-NEXUS-v0.3.74.apk`，`apksigner` v2/v3 校验通过并成功安装到 Pixel_9。系统报告 `versionCode=74`、`versionName=0.3.74`，冷启动 `com.ojnexus/.MainActivity` 的 PID 为 5073，崩溃扫描干净；`docs/releases/SHA256SUMS-v0.3.74.txt` 已与本地 APK 摘要核对；密钥未复制、记录或提交。

## 16. Windows UI Automation 验收脚本（2026-09-13，本次继续开发）

新增未提交脚本 `windows/scripts/ui-smoke.ps1`。它启动真实 Release WPF 可执行文件，使用隔离
临时数据目录，将窗口调整到产品支持的最小尺寸 `900x560`，通过 Windows UI Automation 依次访问
`DASHBOARD`、`CONNECTORS`、`SYNC HISTORY`，并断言公开 handle 编辑框数量为 3、历史筛选器数量至少为
1。脚本会在结束时只清理自己的临时目录和输出截图。

本次验证：PowerShell AST 解析通过；UI Automation 三个视图均通过，输出为
`DASHBOARD: RENDERED`、`CONNECTORS: RENDERED / EDITORS=3`、`SYNC HISTORY: RENDERED / FILTERS=1`、
`UI SMOKE: PASS`。当前 PowerShell 会话没有可用的交互式桌面截图句柄，因此截图采集单独报告
`SCREENSHOTS: 0/3` 并跳过，不把空白图当作视觉通过证据。该脚本证明最小尺寸下控件树和导航可用，
仍不能替代正常桌面上的人工视觉检查，也尚未覆盖放大字体验收。

## 17. Windows Luogu payload 持久化（2026-09-13，本次继续开发）

Windows Core 现在为 Luogu 增加 schema v4 的结构化公开 payload 摘要表
`luogu_payloads`，保存公开 profile、rating（若响应提供）以及 submissions、contests、problemset
计数；不保存原始 HTTP body。Luogu adapter 的四个成功阶段会携带结构化 payload，SQLite 和
InMemory store 均支持合并/回读，CLI `data --judge luogu [--handle <handle>] [--json]` 已返回该摘要。

新增 schema 3→4 迁移、adapter、SQLite、CLI 回归测试；Windows solution fresh 验证为 Core 55/55、
CLI 28/28、Desktop 12/12，Release build 0 warning/0 error。真实公开 `uid:2` smoke 在临时数据
目录中返回 Luogu sync 3/4 成功（匿名 SUBMISSIONS 认证受限），随后 `data` exit 0，返回 profile
`lzn`、contests 20、problems 50、submissions 0；临时目录已清理。提交为 `0af5fb7`。
## 18. Windows WPF 连接器可访问名称（2026-09-13，本次继续开发）

连接器页的三个公开 handle 输入框现在通过 `AutomationProperties.Name` 暴露稳定语义名称：
`CODEFORCES PUBLIC HANDLE`、`ATCODER PUBLIC HANDLE`、`LUOGU PUBLIC HANDLE`，并提供明确的
公开 handle 输入说明；可见 UI 文案和仅公开资料边界保持不变。UI smoke 先在旧 Release
二进制上按预期失败，重新构建后通过精确名称断言。提交为 `19843d8`。

本次 fresh 验证：Windows solution tests 为 Core 55/55、CLI 28/28、Desktop 12/12；Release
build 为 0 warning/0 error；CLI/WPF smoke 通过；UI Automation 在 `900x560` 下访问三个页面，
找到上述三个语义输入框和历史筛选器并通过。当前会话仍无法提供有效窗口截图，因此报告
`SCREENSHOTS: 0/3`；这不替代正常桌面上的人工视觉或放大字验收。

## 19. Windows 自包含包交付（2026-09-13，本次继续开发）

Windows 现在提供可复现的 `win-x64` self-contained 目录包和 ZIP 包，包含 CLI、WPF、.NET
runtime 文件、包说明和 SHA-256 清单；不使用 single-file，不包含数据库、凭据、Cookie、原始
HTTP body、源码或自定义输入。包内运行数据仍默认写入 `%LOCALAPPDATA%\\OJ-NEXUS\\ojnexus.db`，
当前包无 installer、签名、商店发布或自动更新能力。

- 设计、实现计划、清单校验器和打包脚本已提交：`4ea0219`、`0f1be2a`、`dba8c8b`、
  `b90d7a3`、`1b6ff1f`、`c4a5ee5`；发布记录、README 和截图验收修正提交为
  `ab8f603`、`480c18c`、`6aaf671`、`a10aea2`、`dde3716`。
- 最终包目录：`windows/artifacts/self-contained/OJ-NEXUS-Windows-v0.1.0-win-x64/`；
  ZIP 为 `OJ-NEXUS-Windows-v0.1.0-win-x64.zip`，SHA-256 为
  `8A16154B3F5E02BBC28BD1FE5792704ED7CDC40BE04B03062D626773D077DD95`。
- 包含 662 个清单文件，ZIP 共 663 个条目；连续两次重新打包哈希一致，目录和解压后的包均
  通过清单校验；CLI `status --json` 返回 `ready`，WPF 在 15 秒内启动。
- 最终打包后的 UI Automation 在 `900x560` 下完成 `DASHBOARD`、`CONNECTORS`、
  `SYNC HISTORY`，公开 handle 编辑框 3 个、历史筛选器 1 个；截图捕获器现会拒绝相同或
  未渲染的 `PrintWindow` 结果，并在 WPF 内部渲染可用时导出真实视觉树，本机最终报告
  `SCREENSHOTS: 3/3`。
- `.github/workflows/windows.yml` 已切换到带 `--runtime win-x64` 的 restore，并在 CI 中调用
  `-SkipUiSmoke` 生成并上传 self-contained 包；GitHub runner 尚未由本机实际触发。

本轮 Windows source gate：Core 55/55、CLI 28/28、Desktop 12/12；Release build 0 warning、
0 error；`git diff --check` 通过。Linux worktree 和既有未提交文件仍未触碰、未提交、未混入。

## 20. Android 与整仓库当前复验（2026-09-13，本次继续开发）

在 Windows 视觉修复后，本轮重新运行 Android 当前 checkout：

- `tools\\gradlew-local.bat test assembleDebug assembleRelease lintDebug`：`BUILD SUCCESSFUL`。
- `tools\\gradlew-local.bat connectedDebugAndroidTest`：Pixel_9(AVD)-17 共 14/14，通过，0 skipped、
  0 failed，`BUILD SUCCESSFUL`。
- Windows 同步复验仍为 Core 55/55、CLI 28/28、Desktop 12/12；Release build 0 warning、0 error；
  self-contained 包清单 662 文件通过，ZIP SHA-256 为
  `9A4C46D5A68C9A9DB1B899F8091A7CCC0B273F0451C33182594B6BD058EF2DEB`；最终包 UI smoke 为
  `wpf-render`、`SCREENSHOTS: 3/3`。

当前仍未执行 GitHub runner、Windows installer/签名/商店发布，也未对 Linux 未提交 worktree 做任何
提交或合并操作。

## 21. Phase 121–127A 台账（2026-09-13 之后至本轮之前的历史证据）

第 20 节之后的开发已迁移到 `docs/LONG_RANGE_PLAN_121_220.md` 的 100 期规划与
`docs/verification/*` 验收记录体系；以下仅是索引，全部为**历史证据**，细节以各 verification
文档为准，不要在本轮复述为本次验证：

- Phase 121（四端能力台账）与 Phase 126（恢复故障向量，PR #19）：VERIFIED，见
  `docs/verification/phase-121-baseline.md`、`phase-126-recovery-vectors.md`。
- Phase 122（Android 回归基线，PR #14/#15/#18）：VERIFIED（API 37 模拟器范围），见
  `docs/verification/phase-122-android.md`。
- Phase 123（Windows）/124（Linux 集成，PR #16）/125（Apple）：ACTIVE/ACTIVE/BLOCKED（缺
  macOS 宿主），见各自 verification 文档。
- Phase 127A（恢复源保留与启动拦截）：验收完成于 2026-10-24，提交 `690b5f5`，829 项单测 +
  35 项 connected + 专用 AVD 阻断验收，[PR #20](https://github.com/tangyixiao/OJ-NEXUS/pull/20)
  已创建未合并，见 `docs/verification/phase-127a-recovery-safety.md`。主 checkout 曾迁至
  `codex/phase-127-android-base` → `codex/phase-127-recovery-safety` 工作线。

本轮开始时的工作区边界：主 checkout 分支 `codex/phase-127-recovery-safety`，HEAD `5c6a6e7`
（127A 交付提交），工作树干净（仅一个未跟踪的用户文件 `SHA256SUMS`，本轮未触碰、未提交）。

## 22. Phase 127B 严格后台任务代际准入（2026-10-27，本次继续开发，全部为本次验证）

本轮依据 RV022（`contracts/recovery/v1`：缺失代次的工作 `originating_work_may_write=false`，
须"reconcile and requeue with explicit current generation"）补齐 127A 留下的缺口：分类器
`decideRestoreWorkGeneration` 与 `RestoreWorkDecision` 值**未改**（Phase126 合同投影不动），
只改了消费策略：

- 三个 worker（JudgeSyncWorker、LuoguOpenResultWorker、ScheduledBackupWorker）只放行与进程
  代次**精确一致**的戳记；stale 拒绝返回 `STALE_DATA_GENERATION`，缺失/空白戳记拒绝返回
  `UNSTAMPED_DATA_GENERATION`，均在业务 I/O 之前。sync 拒绝写入 `STALE_GENERATION` 台账
  （unstamped 记空代次）；Luogu 拒绝不动 Room；备份拒绝不动偏好/不导出。
- `ScheduledBackupScheduler.update` 现在把当前代次写进 periodic 请求输入；Judge periodic 与
  备份 periodic 都改用 `ExistingPeriodicWorkPolicy.UPDATE`，每次就绪启动由 bootstrap 重新
  戳记队列——严格拒绝不会把升级/恢复后的后台同步永久卡死。
- 提交：`c44c200`（spec）、`bedcbd8`（plan）、`e468fee`（实现，6 文件）、`ce582ef`（证据与
  roadmap/DATA_SAFETY）。文档：`docs/superpowers/specs/2026-10-27-phase-127b-worker-generation-design.md`、
  `docs/superpowers/plans/2026-10-27-phase-127b-worker-generation.md`、
  `docs/verification/phase-127b-worker-generation.md`。

本次验证证据（全部本轮新跑）：

- RED：实现前 focused 跑 `RecoveryGenerationAdmissionTest`，5 用例 4 失败，恰好命中四个现状
  缺陷（unstamped sync 放行、`recordStaleGeneration(null)` 不记录、unstamped Luogu 放行、
  备份无视戳记）；stale 回归锚已绿。
- GREEN：实现后同套件 6/6；focused 合计 8 套件 / 22 测试 0 失败。
- 全量门禁 `tools\gradlew-local.bat testDebugUnitTest --rerun test assembleDebug assembleRelease`：
  BUILD SUCCESSFUL 3m15s；XML 合计 **835 测试 / 182 套件，0 失败/错误/跳过**（127A 基线为
  829/181）。仅剩两条既有 JVM 警告（Conscrypt native access、DataStore/protobuf Unsafe），
  无新增生产编译警告。
- connected：专用 AVD `OJNexus_Phase127_Temp` 在本机已不存在，用同一镜像
  （android-37.1 google_apis_playstore_ps16k x86_64、pixel_7）重建，串号 `emulator-5582`。
  第一次运行中模拟器进程死亡（28/35 中断，1 条空记录失败，无 message/stack），已如实记为
  基础设施失败；清洁重启后重跑 **35 测试 / 11 套件，0 失败**，BUILD SUCCESSFUL 1m23s。
  跑完已 `adb emu kill`，adb 设备清空；未触碰任何物理设备与用户数据。
- [PR #24](https://github.com/tangyixiao/OJ-NEXUS/pull/24) 已创建：base
  `codex/phase-127-recovery-safety`（即 PR #20 的 127A 分支，堆叠 PR，只含 4 个 127B 提交），
  head `codex/phase-127b-generation-admission`，OPEN、MERGEABLE，未合并。

本轮未做：物理设备、真实断电、127C 类型化导入存储错误（RV004 ENOSPC/IO）、minSdk 26 旧引擎
导出兼容、全 Phase 127 release 门禁。`ExistingPeriodicWorkPolicy.KEEP→UPDATE` 的队列替换本身
没有 WorkManager 集成测试（未新增 work-testing 依赖），其安全网（stale/unstamped 拒绝）已被
测试覆盖——均已在 evidence 文档如实记录。

## 23. 当前 checkout / 分支 / PR 布局与剩余阻塞（2026-10-27 交接完成）

分支与远端布局（本轮结束时）：

- 独立定向审查（针对 `c44c200..62ce918`）：SPEC PASS / QUALITY APPROVED，0 个 P0/P1、3 个
  P2；三个 P2（companion 常量缩进回归、调度器不安全 `as` 转换、unstamped worker 未在
  worker 层钉住空代次台账行）已在 `51145ab` 修复并复跑 admission 套件 6/6。PR #24 最终含
  6 个提交（spec/plan/实现/验收文档/交接文档/审查修复）。
- 本地 `codex/phase-127-recovery-safety` 与远端 `origin/codex/phase-127b-generation-admission`
  的提交链（自 `5c6a6e7` 起）：`c44c200` → `bedcbd8` → `e468fee` → `ce582ef` → 交接文档提交 →
  `51145ab`（审查修复）。远端 `origin/codex/phase-127-recovery-safety` 仍停在 `5c6a6e7`
  （PR #20 的 head 不变，范围保持 127A）。**不要把本地 recovery-safety 分支再推到远端**，
  否则 PR #20 范围会混入 127B 与交接内容。
- 本轮 shell 为 cmd（不是 PowerShell/Git Bash）：`;` 不会分隔命令（用 `&&`）；
  `timeout /t` 在无控制台输入时报"输入重定向"错误（改用 `ping -n N 127.0.0.1 >nul`）。
- 本机全局 `ANDROID_SDK_ROOT` 指向失效路径 `D:\rea\tools\android-sdk`：直接启动 emulator
  会报 "Broken AVD system path"，启动命令必须前置
  `set ANDROID_SDK_ROOT=D:\Android && set ANDROID_AVD_HOME=D:\avd`；gradle 侧读
  `local.properties` 不受影响。后台任务方式启动模拟器在本轮可靠（`run_in_background`），
  MCP android-emulator 插件的 start_emulator 在 30 秒工具超时内起不来。
- 本轮新增两个未提交的机器本地辅助：`tools/summarize-test-xml.py`（汇总 JUnit XML 计数）、
  `tools/wait-boot-127b.bat`（等待模拟器 boot_completed），与 `tools/gradlew-local.bat`
  同类，不入库。
- GitHub 推送：本机 SSH（git@github.com，经 ssh.github.com:443 的 fake-IP 代理）被断连；
  `git push https://github.com/tangyixiao/OJ-NEXUS.git <branch>` 与 `gh` CLI（已认证）可用。

剩余阻塞（接替第 8/20 节的旧清单）：

- Android：Phase 127C 类型化导入存储错误、minSdk 26 旧引擎（SQLite < 3.27）导出兼容策略与
  真实旧引擎验证、全 Phase 127 release 门禁；PR #20/#24（及上游 #14/#15/#18/#19）均未合并。
- Phase 123（Windows 干净 VM 安装）、124（Linux Qt 桌面/干净主机安装）、125（Apple，缺
  macOS/iOS 宿主）仍按各自 verification 文档处于 ACTIVE/BLOCKED。
- 主工作区遗留用户未跟踪文件 `SHA256SUMS`（内容为空），本轮未动；Linux 独立 worktree 边界
  不变（`D:\AndroidAppCoding\.worktrees\linux-client`，分支 `codex/linux-client`）。

交接文档状态：本文件已更新至 2026-10-27；第 21–23 节为当前权威，其余章节为历史记录。

## 24. Phase 127C 类型化导入存储错误（2026-10-29，同一会话第二增量，全部为本次验证）

127B 交付后继续推进 Phase 127 剩余范围，本轮完成 127C（RV004–RV006）：

- **缺陷现状（RED 证实）**：`prepareImport` 把暂存临时创建失败（RV004）的原始 `IOException`
  直接抛出（设置页导入的未捕获崩溃路径）；staged 拷贝（RV005）与 journal 写入（RV006）失败
  均折叠为 `FILE_REPLACEMENT_FAILURE`，且 journal 写入失败会留下 staged pending 文件，下次
  启动被孤儿证据阻断而非允许合同要求的重试；`importFrom` 只返回 Boolean，设置页把导入失败
  显示为导出失败文案。
- **修复**：`RestoreFailure` 追加 `STORAGE_FULL`/`STORAGE_IO`；纯函数
  `classifyImportIoFailure` 沿 cause chain 分类（ENOSPC 特征消息 → STORAGE_FULL）；暂存临时
  创建与输入拷贝接入 `RestoreFileOperations` 注入边界；失败只清理本 run 拥有的产物（无既有
  journal 才删 pending），重试不再被阻断，预先存在的 staged 导入不被失败尝试破坏；独立审查
  的 P1（validation 分支漏清理导致 pending-temp 阻断下次启动）已在 `ed7e873` 修复并带回归。
  `importFrom` 返回完整 `RestoreOutcome`，`BackupResult` 携带类型化 failure，设置页把导入
  失败映射为稳定重试文案（中英目录各加三个键、真实中文翻译），导出失败文案不再被导入复用。
  Phase126 分类器与投影未改。
- **证据**：RED 6 用例 4 失败（含 RV004 崩溃路径）→ 故障套件 8/8、127A 既有 23 例回归无损；
  独立审查 SPEC PASS / CHANGES_REQUESTED，P1+P2 已修复；最终 HEAD `9253f0d` fresh **845 项
  单测 / 184 套件 0 失败**、debug/release 构建通过；专用 AVD connected **35/35** 通过。
  前两次 connected 因模拟器进程死亡中断——根因是两个空闲 Gradle daemon 占用 ~5.3GB 造成宿主
  内存压力；`gradlew --stop`+taskkill 后清洁重跑通过。已如实记为基础设施失败。
- **提交与 PR**：`3b66151`（spec）、`ff0c6a3`（plan）、`6d65852`、`545aa77`、`ed7e873`、
  `9253f0d`（验收文档）。[PR #25](https://github.com/tangyixiao/OJ-NEXUS/pull/25) 已创建：
  base 为 127B 分支 `codex/phase-127b-generation-admission`（堆叠），head
  `codex/phase-127c-import-errors`，OPEN、MERGEABLE，未合并。合并链 #20 → #24 → #25。
- **本轮新增机器本地辅助**：`tools/wait-boot-127b.bat` 已改为 ping 睡眠（timeout /t 在无控制台
  环境静默失效导致轮询过短误报 BOOT_TIMEOUT）；新增 `tools/summarize-test-xml.py`、
  `tools/show-failures.py`、`tools/patch-import-tests*.py`（均不入库）。

127C 后 Phase 127 剩余：minSdk 26 旧引擎（SQLite < 3.27）导出兼容策略与真实旧引擎验证、
整期 release 门禁。未触碰物理设备与真实 ENOSPC 硬件故障（边界注入的 I/O 异常代表 OS 级失败，
已如实记录）。

## 25. Phase 127D 旧引擎导出兼容（2026-10-30，同一会话第三增量，全部为本次验证）

127C 交付后继续推进 Phase 127 最后一项功能缺口（minSdk 26 旧引擎导出兼容）：

- **缺陷现状（真实旧引擎 RED 证实）**：导出走 `VACUUM INTO`（需 SQLite ≥ 3.27），API 26–28
  设备（SQLite 3.18–3.22）导出直接失败（`IllegalStateException` fail-closed）。在本机下载
  `system-images;android-27;default;x86_64`（远端无该 API 的 google_apis x86_64 变体）并建专用
  AVD `OJNexus_Phase127_OldEngine`（SQLite 3.19.4，emulator-5584）后，修复前 connected 测试以
  `export failed on engine 3.19.4` 失败——真实旧引擎的 RED。
- **策略与实现**：导出按 `SELECT sqlite_version()` 门控（`SqliteVersion` 纯解析，畸形版本安全
  退到回退路径）。≥3.27 保持 VACUUM INTO 不变；<3.27 走检查点拷贝回退：专用连接无事务执行
  `wal_checkpoint(TRUNCATE)`（busy==0 且 -wal 归零为完成证明；SQLite 禁止同连接事务内 checkpoint
  ——原 spec 的 BEGIN EXCLUSIVE 序列不可行，已在 spec 修订注记公开）、读事务物化后拷贝主文件
  （WAL 写者只追加 -wal；回滚模式写者被 SHARED 锁阻塞）、写连接 autocheckpoint 读改写恢复。
  全进程 `mainFileRewriteLock` 同时串行化维护 VACUUM（独立审查 P1：其 TRUNCATE checkpoint 会
  在拷贝窗口改写主文件）。双路径导出前均跑 schema/quick_check/integrity_check/必需表校验，
  失败销毁暂存、导出 fail-closed。
- **证据**：真实旧引擎 RED→GREEN（同测试同引擎）；独立审查 SPEC FAIL/CHANGES_REQUESTED 的
  P1+全部 P2 已在 `b226160` 修复（含非 WAL 低内存设备的读事务保护、connected 测试 WAL 驻留
  前置断言、异常安全 close）；最终 HEAD `ac48f19` fresh **848 项单测/185 套件 0 失败**，
  debug/release 构建通过；**双引擎全套 connected 各 36/36**（API 37.1=VACUUM 路径、API 27=回退
  路径，各含 35 既有用例 + 新导出用例）。两次中间全套运行因模拟器死亡中断（同一 Gradle daemon
  内存压力模式），清理后清洁重跑通过，已如实记为基础设施失败。
- **提交与 PR**：`803b6cc`（spec）、`b97c4b7`（plan）、`9088ee1`、`de8914e`、`b226160`、
  `ac48f19`。[PR #26](https://github.com/tangyixiao/OJ-NEXUS/pull/26) 已创建：base 为 127C 分支
  `codex/phase-127c-import-errors`（堆叠），head `codex/phase-127d-old-engine-export`，OPEN、
  MERGEABLE，未合并。合并链 #20 → #24 → #25 → #26。
- **本轮新增机器知识**：旧引擎镜像需用 `system-images;android-27;default;x86_64`（google_apis
  x86_64 变体远端不存在）；androidTest 方法名不能含空格（D8 拒绝）；`wal_checkpoint(TRUNCATE)`
  不能在自身事务内执行（SQLITE_LOCKED，框架包装为 SQLiteTableLockedException）。

Phase 127 至此仅剩**整期 release 门禁**（版本号决定、release note、签名构建、设备验收——
需用户决定版本与发布节奏）。功能验收项 127A–127D 全部完成；无新 release，整期保持 ACTIVE
直至 release 门禁通过。

## 26. Phase 127 整期 release 门禁（v1.0.4，2026-10-30，本次执行，GitHub 发布留待用户）

127D 交付后按用户指示启动 Phase 127 整期 release 门禁，全部本地门禁通过：

- **版本递增**：沿用每交付批次 +0.0.1/versionCode+1 的规则（1.0.0 code102 → 1.0.3 code105），
  本次 cut **v1.0.4 / versionCode 106**。发布分支 `codex/release-v1.0.4`（自 127D head
  `756d8f4` 切出），版本 bump 提交 `5909b2e`。
- **完整门禁（全部本次新跑）**：
  - `tools\gradlew-local.bat testDebugUnitTest --rerun test assembleDebug assembleRelease`：
    BUILD SUCCESSFUL 2m18s，**848 项单测/185 套件 0 失败/错误/跳过**；仅两条既有 JVM 警告。
  - 本地化：`tools/check-localization.ps1` → **KEY PARITY EN=1026 ZH=1026、0 硬编码文案**。
  - 可复现构建：`tools/check-reproducible-build.ps1` 两次干净 release pass 同哈希
    **`67BDB719DF760C979C2A619566601F5EF944AA6EB0FF725CEB2723369D5A26F6`**。
  - connected：专用 API37.1 AVD（emulator-5582）全套 **36 测试/12 套件 0 失败**
    （35 既有 + 新引擎导出用例）；API27 旧引擎 36/36 见第 25 节。
- **签名与核对**：`assembleRelease` 无签名产物在**仓库外**用本机标准 debug keystore 经
  apksigner 签名（**v2+v3 校验通过**），安装回专用 AVD：系统读回
  `versionCode=106 / versionName=1.0.4`，冷启动 `com.ojnexus/.MainActivity` PID 存活、
  logcat **0 条 FATAL EXCEPTION/AndroidRuntime**。签名 APK SHA-256
  `C916081B0F9FF89B1F282E227D643A21D1A511A6012CEA57FF7ECD350729601F` 记入
  `docs/releases/SHA256SUMS-v1.0.4.txt`（格式同历史文件；密钥与 APK 均未入库，APK 留在
  `%TEMP%` 供用户上传 release）。
- **发布记录**：`docs/releases/v1.0.4.md`（127A–127D 变更清单 + 质量姿态 + 台账）；
  README Status 更新（release gate passed、GitHub 发布待用户决定）；roadmap Phase 127 标记
  **VERIFIED** 并新增 Release 门禁行。
- **交接与 PR**：发布分支已推送并建
  [PR #27](https://github.com/tangyixiao/OJ-NEXUS/pull/27)（base main，包含 127A–127D 与
  release 收口全部提交——注意它同时携带未合并的 #20/#24/#25/#26 内容，合并即整链入 main）。
  **GitHub release 发布、tag 推送与 PR 合并决定留待用户确认**，本轮未执行。

用户决策点：合并 PR #27（整链入 main）→ 打 tag `v1.0.4` → 上传 `%TEMP%\OJ-NEXUS-v1.0.4.apk`
与 SHA256SUMS 作为 GitHub release 资产。签名材料从未离开本机 keystore；如需正式签名
（非 debug keystore），需用户提供发布密钥或沿用 CI secrets 流程。
