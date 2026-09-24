# OJ NEXUS 交接文档（给 GLM）

更新时间：2026-09-13（第六轮：Windows 自包含包交付验收）  
工作区：`D:\AndroidAppCoding`

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
