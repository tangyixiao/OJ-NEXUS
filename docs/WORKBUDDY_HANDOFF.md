# OJ NEXUS 交接文档（给 Workbuddy）

更新时间：2026-09-16
工作区：`D:\AndroidAppCoding`
交接性质：基于当前 checkout、工作区差异和已有验证记录的增量接手说明

## 1. 先读这段

OJ NEXUS 是本地优先的原生竞赛编程工具，不是社交应用，也不是 AI 产品。Android 使用
Jetpack Compose + Material 3；Windows 使用 .NET/WPF + CLI；Linux 使用 Qt 6/C++20；Apple
使用 SwiftUI/Swift Package Manager。各端共享产品边界和公开 OJ 连接器概念，但不共享 Room、
SQLite 或 Windows 存储。

产品和安全边界必须保持：

- 只使用公开 handle/UID；不询问或保存 OJ 密码、Cookie、Session、token、secret。
- 不使用 WebView、浏览器 Cookie 抓取、Flutter、React Native、Electron 壳或伪造网络数据。
- 不自动提交代码；网络只做同步，本地缓存和历史必须可离线使用。
- UI 保持深色、英文大写、遥测台风格、单一 NEXUS BLUE 强调色和现有设计系统；禁止营销文案、
  emoji 图标、渐变、玻璃拟态和无意义循环动画。
- 用户要求“继续”时，先审计分支、worktree、差异和实际验证，再增量推进；不要重做已提交阶段。

除非本轮重新得到对应证据，不要把历史测试结果写成当前通过，也不要使用“DONE”“已发布”描述
仍缺少真实运行或发布门禁的部分。

## 2. 当前 checkout 与保护边界

主 checkout 当前状态：

- 分支：`codex/phase-5-arena`
- HEAD：`245cefd`（`docs: align Apple identity validation plan`）
- 远端 `origin/codex/phase-5-arena`：`b143ded`
- 相对远端：ahead 61
- 主工作区存在大量用户既有未提交修改，涉及 Android、Apple、Windows、文档和测试；另有未跟踪
  的网络客户端、测试、计划文档及 Windows `bin/`/`obj/` 构建产物。

主工作区保护规则：

- 不执行 `git reset --hard`、`git checkout --`、批量清理或覆盖已有修改。
- 不执行 `git add -A`，不要把 Linux worktree、构建产物或其他用户改动混入交付。
- 提交前只按明确范围逐文件检查 `git diff`、`git diff --check` 和敏感信息；未授权不要提交或推送。
- 当前 `docs/GLM_HANDOFF.md` 也是未跟踪文件；本文件与其并存，不要删除旧文档。

Linux 是独立 worktree：

- 路径：`D:\AndroidAppCoding\.worktrees\linux-client`
- 分支：`codex/linux-client`
- HEAD：`5c42103`
- `linux/` 工程及 `.github/workflows/linux.yml`、多份产品/架构/安全文档仍是用户既有未提交工作。
- Linux 命令必须在该 worktree 内执行；不要从主 checkout 操作或误判 Linux 改动不存在。

## 3. 已完成基线（历史证据，不等于本轮验收）

### Android

Android Phase 73–76 已存在于历史提交：

- `7cdfd81`：Safe Restore v0.3.71
- `ffae3ce`：Action Continuity v0.3.72
- `87da387`：Training Calibration v0.3.73
- `27305b5`、`b8b5669`、`c6d5365`：Sync Operations Ledger v0.3.74

对应能力包括可恢复导入、schema/integrity 检查和 rollback、数据代次防旧任务、跨日期本地日、
训练目标与候选池校准、同步操作历史、模块结果和类型化重试。历史复验曾确认：

- `tools\\gradlew-local.bat test assembleDebug assembleRelease lintDebug` 成功；
- Pixel_9 `connectedDebugAndroidTest` 为 14/14；
- v0.3.74 APK 曾完成安装、签名校验和冷启动无崩溃扫描。

这些证据发生在当前新增 Android 网络客户端改动之前。继续 Android 工作前必须重新运行 focused
测试和完整 Gradle 门禁。

### Windows

`windows/` 已具备 .NET Core/SQLite 同步核心、CLI 和 WPF 桌面壳：

- schema migration 会检查 PK 顺序、nullability 和 FK delete action；
- `SyncService` 支持每账号串行、取消、类型化终态、模块结果持久化和有限历史重试；
- CLI 已覆盖 `status --json`、`history --json`、`data`、参数错误、取消和异常脱敏；
- WPF 已有 `DASHBOARD`、`CONNECTORS`、`SYNC HISTORY`，并支持公开 handle、同步、取消、筛选和失败重试；
- Codeforces、AtCoder、Luogu 的 Windows 公开同步边界已实现；Luogu 匿名提交记录仍可能返回认证受限，
  不得宣称匿名 submissions 可用；
- 已有可复现的未签名 `win-x64` self-contained 目录包/ZIP，不含数据库、凭据、Cookie、原始 HTTP body、
  源码或自定义输入。

最近一次已记录的 Windows source gate 为 Core 55/55、CLI 28/28、Desktop 12/12，Release build
0 warning/0 error；WPF UI smoke 曾在 900x560 下完成三视图、3 个语义 handle 输入框和历史筛选器检查，
内部渲染截图达到 3/3。由于当前工作区又有 Windows 网络、安全和投影相关未提交修改，继续交付前需重跑。

尚未完成的 Windows 发布项：GitHub runner 实际执行、installer、签名、商店包和自动更新；self-contained
ZIP 不能被描述成正式发布安装包。

### Linux

Linux Qt/C++ 客户端位于独立 worktree，包含 Core、CLI、Qt Quick/QML GUI、SQLite、三站适配器和
DEB/RPM/Arch 打包。2026-09-15 的本次验证记录为：

- `cmake --build build --parallel 2` 成功；
- `ctest --test-dir build --output-on-failure`：8/8；
- `QT_QPA_PLATFORM=offscreen timeout 3 ./build/src/gui/ojnexus-gui` 持续运行且无输出错误；
- Codeforces `tourist` 五阶段 live smoke 成功；
- AtCoder `tourist` 五阶段 live smoke 成功；
- Luogu `uid:2` 的公开 profile/contests/problemset 成功，submissions 返回 typed `AUTHENTICATION`；
- Luogu 不再宣称 rating history：真实探测未找到稳定公开端点，死端点 fixture 已移除，Luogu sync 为 4 阶段。

以上 Linux 代码和文档仍未提交，不能代为提交，也不能混入主分支。

### Apple

`apple/` 是独立的原生 SwiftUI 包，部署下限为 macOS 13 / iOS 16，包含：

- `OJNexusCore`：judge-agnostic domain、公开账号、profile/rating/submission、local JSON workspace/ledger；
- Codeforces、AtCoder、Luogu public adapters；Luogu 当前 profile-only，rating/submissions 不跨越受保护数据边界；
- `OJNexusUI`：CONNECTORS、DASHBOARD、SYNC HISTORY 相关 SwiftUI 界面；
- `OJNexusMacOS` 和 `OJNexusIOS` 原生入口；
- Apple CI source audit、Swift tests、macOS build 和 iOS Simulator build 配置。

Apple 当前实现和计划有大量未提交修改。Windows 主机没有 `swift` 或 `xcodebuild`，因此 Apple 编译、单测、
macOS 启动和 iOS 模拟器验收均不能在此声称通过，必须交给 macOS/Xcode 或 GitHub Actions。

## 4. 当前正在进行的未提交工作

不要把以下内容当作已交付，也不要另起炉灶重写：

1. 跨平台 public HTTP 无 Cookie hardening：
   - Android 新增 `app/src/main/java/com/ojnexus/core/network/PublicHttpClient.kt` 及测试，
     `AppContainer` 已切换到 `CookieJar.NO_COOKIES`；
   - Windows 新增 `PublicHttpClientFactory` 及测试，默认三站 adapter 已切换到 `UseCookies = false`；
   - Apple transport 已显式关闭 cookie 设置/处理并清空 cookie storage；Apple CI 增加了可手动触发入口和边界审计。
   - Windows focused factory test 已有 1/1 通过记录；Android focused test 在上次工作流结束时没有形成完整终端证据，
     必须重新执行并等待 `BUILD SUCCESSFUL`。

2. Apple connector status / identity validation：Apple adapter、domain、local store、dashboard/UI、测试、README、
   workflow 和计划文档都有未提交差异。重点保持 judge-specific handle 校验、profile 返回身份匹配、能力门控、
   cancelled/failed history 保留、disabled account 状态和 `SYNC ALL` 的串行语义。

3. Windows 同步和 UI 投影相关修改：`JudgeAccount`、schema/store、`SyncService`、WPF ViewModel/XAML、测试和
   `windows/README.md` 均有工作区差异。先读 diff 和现有测试，避免用旧基线覆盖这些修改。

## 5. 推荐接手顺序

### A. 先做只读审计

在主 checkout 和 Linux worktree 分别运行：

```powershell
git status --short --branch
git log -12 --oneline --decorate
git worktree list --porcelain
```

阅读根目录 `AGENTS.md`、`README.md`、本文件，以及对应端的 `apple/README.md`、`windows/README.md` 和
Linux worktree 内的架构/安全文档。先把工作区差异按 Android、Apple、Windows、Linux、构建产物分类。

### B. 优先收口 public HTTP hardening

主 checkout 中逐步验证，不要扩大范围：

```powershell
tools\\gradlew-local.bat testDebugUnitTest --tests com.ojnexus.core.network.PublicHttpClientTest --no-daemon --console=plain
dotnet test windows/OjNexus.Windows.sln -c Release --no-restore
```

focused 通过后，再运行 Android 完整门禁：

```powershell
tools\\gradlew-local.bat test assembleDebug assembleRelease lintDebug
tools\\gradlew-local.bat connectedDebugAndroidTest
```

Windows 则至少重跑：

```powershell
dotnet test windows/OjNexus.Windows.sln -c Release --no-restore
dotnet build windows/OjNexus.Windows.sln -c Release --no-restore
pwsh -NoProfile -File windows/scripts/smoke.ps1 -Configuration Release
```

如果修改影响 self-contained 包，再使用仓库已有的打包/校验脚本逐步重建并核对清单；不要把旧 ZIP
哈希继续当作新工作区产物哈希。

### C. 再收口 Apple

在 macOS/Xcode 或 GitHub Actions 上运行：

```sh
swift test --package-path apple
swift build --package-path apple --product OJNexusMacOS
swift build --package-path apple --product OJNexusIOS
```

另外检查 Apple CI 的 forbidden-text audit、trailing whitespace 和真实三站公开来源边界。构建成功也不等于
交互式 simulator/device、签名或 App Store 发布已通过。

### D. Linux 只在独立 worktree 收口

```sh
cd /mnt/d/AndroidAppCoding/.worktrees/linux-client/linux
cmake --build build --parallel 2
ctest --test-dir build --output-on-failure
```

需要重新做真实来源 smoke 时，使用临时数据目录；分别记录 Codeforces、AtCoder、Luogu 的模块结果，不能
把 Luogu 的匿名认证受限写成客户端故障，也不能通过 Cookie/Secret Service 绕过边界。

## 6. 已知发布阻塞

- Apple：当前 Windows 主机无法编译；macOS/Xcode/CI、macOS 启动、iOS simulator/device、签名和商店验收待做。
- Windows：CI runner 实际执行、installer、签名、商店包和自动更新待做；CLI 物理进程 Ctrl+C 仍不应仅凭单测宣称完成。
- Linux：核心代码和文档仍在独立 worktree 未提交；Luogu rating history 和受保护 submissions 不在当前公开凭据边界内。
- Android：历史 v0.3.74 验收有效，但当前 public HTTP hardening 改动需要重新形成 focused、完整 Gradle 和设备证据。
- 整仓库：主分支 ahead 远端且有大量未提交文件；是否提交、拆分 commit、推送或合并必须由用户明确决定。

## 7. 交付报告格式

每次 Workbuddy 继续工作后，报告以下内容：

1. 实际修改的文件和分支；
2. 本次真实运行的命令及精确结果；
3. 哪些是历史证据、哪些是本次 fresh evidence；
4. 哪些改动仍未提交、哪些 worktree 受到保护；
5. 剩余阻塞和下一步，不用“全部完成”概括不同平台的状态。

只有相关代码、测试、真实来源、运行时和发布门禁全部满足时，才可以使用“完成”或“发布”措辞。

## 8. 本轮接手记录（Workbuddy，2026-09-16）

### 8.1 实际修改的文件（主 checkout，分支 `codex/phase-5-arena`）

- `windows/src/OjNexus.Windows.Desktop/ViewModels/DesktopViewModel.cs`（已存在的工作区差异之上再改）：
  WPF `DesktopRuntime.Create()` 的三个 adapter 由 `static () => new HttpClient()` 改为
  `PublicHttpClientFactory.Create`。这是 public HTTP hardening 的漏网点——CLI 已切换，Desktop 未切换。
- `windows/tests/OjNexus.Windows.Desktop.Tests/DesktopPublicTransportAuditTests.cs`（新增）：
  扫描 `windows/src/**/*.cs`（排除 `bin`/`obj`），断言不再出现 `new HttpClient(`，防止上述回退。

其余均为只读审计与验证，未改动 Android、Apple、Linux 代码，未提交、未推送、未清理任何工作区文件。

### 8.2 本轮真实运行的命令与精确结果（fresh evidence）

| 命令 | 结果 |
| --- | --- |
| `tools\gradlew-local.bat testDebugUnitTest --tests com.ojnexus.core.network.PublicHttpClientTest --no-daemon --console=plain` | `BUILD SUCCESSFUL in 2m 9s`；`TEST-...PublicHttpClientTest.xml`：`tests="1" failures="0" errors="0"` |
| `tools\gradlew-local.bat test assembleDebug assembleRelease lintDebug --no-daemon --console=plain` | 首次 `FAILURE`：`:app:dexBuilderDebug` 抛 `D8: java.nio.file.AccessDeniedException ... MainActivity.dex`（环境问题，非代码）。原样重试同命令（仅 `assembleDebug assembleRelease lintDebug`）得 `BUILD SUCCESSFUL in 4m 25s`、`EXIT=0` |
| Android 单测汇总（`app/build/test-results/testDebugUnitTest/*.xml`，131 个 suite） | `tests=524 failures=0 errors=0 skipped=0` |
| `dotnet test windows/OjNexus.Windows.sln -c Release --no-restore` | `EXIT=0`；Core 71/71、CLI 28/28、Desktop 19/19（Desktop 18→19 为新增审计测试） |
| `dotnet build windows/OjNexus.Windows.sln -c Release --no-restore` | `EXIT=0`，`0 个警告`、`0 个错误` |
| `windows\scripts\smoke.ps1 -Configuration Release` | `CLI EXIT CODE: 0`；`{"status":"ready","accountCount":0,"lastSync":null}`；`DESKTOP START: ALIVE WITHIN 15 SECONDS` |
| `windows\scripts\ui-smoke.ps1 -Configuration Release -Width 900 -Height 560 -OutputDirectory <temp>` | `UI SMOKE: PASS`，`SCREENSHOTS: 3/3`，`CONNECTOR LIFECYCLE: SAVE / DISABLE / ENABLE / PASS` |
| 负向验证（临时把 Codeforces adapter 改回 `new HttpClient()` 后跑审计测试） | `失败: 1，通过: 0`，`Assert.Empty() Failure` → 审计测试确实能捕获回退；随后已改回 `PublicHttpClientFactory.Create` |
| `tools\gradlew-local.bat connectedDebugAndroidTest --no-daemon --console=plain`（Pixel_9 AVD 冷启动） | 第一次 `No connected devices!`（模拟器进程被环境回收）；第二次 `Instrumentation run failed due to Process crashed.`（tests=0）；第三次 `BUILD SUCCESSFUL in 2m 50s`，`Starting 15 tests / Finished 15 tests`，`tests=15 failures=0 errors=0 skipped=0` |

设备测试 15 个用例：`CommandPaletteComposeTest`(1)、`ProblemLibraryTrainingHandoffComposeTest`(2)、
`DashboardCommandSurfaceComposeTest`(2)、`ProblemLibraryTrainingComposeTest`(2)、
`ConnectorCenterComposeTest`(3)、`SyncOperationHistoryComposeTest`(2)、
`SessionCommandDeckComposeTest`(1)、`SessionMomentumComposeTest`(2)。

### 8.3 历史证据 vs 本轮 fresh evidence

- 本轮 fresh：Android 单测 524/524、assembleDebug/Release/lintDebug、Pixel_9 设备测试 15/15、
  Windows 三组测试、Release 构建、CLI/Desktop smoke、WPF UI smoke、审计测试负向验证。
- 仍属历史、未在本轮复验：Android v0.3.74 APK 安装/签名/冷启动；Windows self-contained 包哈希；
  Linux 全部证据；Apple 全部证据。

### 8.4 仍未提交 / 受保护的范围

- 主 checkout 仍是大量未提交改动（Android、Apple、Windows、文档），本轮新增 1 个测试文件并修改
  1 个 Desktop 源文件，均未 `git add`，未提交、未推送。
- Linux worktree（`.worktrees/linux-client`，`codex/linux-client`）未触碰；`linux/`、
  `.github/workflows/linux.yml` 仍为未跟踪/未提交状态。
- Apple 改动未提交，`swift`/`xcodebuild` 在本机不存在，本轮只做静态审计。

### 8.5 Apple 静态审计结果（本轮，非编译验证）

- `apple/Sources` 中 cookie 相关只有三处显式关闭：`httpShouldSetCookies = false`、
  `httpCookieStorage = nil`、`request.httpShouldHandleCookies = false`；无 password/token/secret/Keychain。
- Apple CI 禁词正则在 `Sources`/`Tests`/`Apps` 上无命中（仅 `apple/README.md` 的描述文字命中，
  不在 CI 扫描范围内），trailing whitespace 无命中 → 该 audit 步骤预期通过，但仍需在 macOS runner 实跑。

### 8.6 剩余阻塞与下一步

1. Android：门禁已全绿，但 `dexBuilderDebug` 的 `AccessDeniedException` 说明本机存在文件占用/安全软件
   干扰，重跑时需注意；建议保留“失败先重试一次”的判读方式，不要直接判为代码缺陷。
2. Windows：CI runner 实际执行、installer、签名、商店包、自动更新未做；self-contained 包重建被
   宿主环境的删除保护阻断（见 8.8），旧哈希不得沿用；交互式终端 Ctrl+C 仍未验证（见 8.7）。
3. Apple：必须在 macOS/Xcode 或 GitHub Actions 上跑 `swift test` 与两个 product 构建。
4. Linux：本机的 `wsl.exe` 被安全策略阻止，无法构建/测试；只能在 Linux 主机或 CI 中收口。
5. 仓库治理：主分支 ahead 61、大量未提交文件；是否提交、如何拆分 commit、是否推送仍需用户决定。

### 8.7 Windows CLI 物理进程取消：本轮真实验证与残留限制

方法：以 `CREATE_NEW_PROCESS_GROUP` 启动 `ojnexus.exe sync --judge codeforces --handle tourist --json`
（真实公开来源，临时数据目录），2 秒后用 `GenerateConsoleCtrlEvent` 发送控制台事件。

- `CTRL_BREAK_EVENT`：**取消生效**。`EXIT_CODE 4`（`CliExitCode.Cancelled`），stdout 为
  `{"operation":{"id":1,"status":"Cancelled","judge":"Codeforces","moduleCounts":{"total":0,...}},
  "status":"Cancelled","error":"Cancelled"}`，stderr 为空（取消被 sync 内部吸收，未冒泡成
  `SYNC: CANCELLED` 错误路径）。
- 取消后持久化验证：对该临时数据目录再跑 `history --json` 与 `status --json`，两者均返回
  `{"id":1,"judge":"Codeforces","handle":"tourist","status":"Cancelled","moduleCount":0}`。
  即：物理信号 → 优雅取消 → 落库 → 可查询，端到端成立。
- `CTRL_C_EVENT`：**本环境无法给出有效结论**。新建进程组默认禁用 Ctrl+C（实测发送后进程照常跑完，
  `EXIT_CODE 0`，`status Success`，3 模块成功）；同进程组发送时宿主要先忽略 Ctrl+C，而该"忽略"属性
  会被子进程继承，导致子进程同样不响应。因此**交互式终端下真实 Ctrl+C 仍待人工或交互式环境验证**，
  不得凭本轮结果宣称 Ctrl+C 已验证。
- 附带结果：上述两次未被取消的运行同时构成 Codeforces `tourist` 的真实来源 live smoke
  （`moduleCounts total=3 successful=3 failed=0`，退出码 0）。

### 8.8 Windows self-contained 包重建：本轮被环境阻断

`windows\scripts\package.ps1 -Version 0.1.0 -Configuration Release -Runtime win-x64 -SkipUiSmoke`
在本轮宿主环境中失败：脚本清理临时工作目录时触发宿主的删除保护
（`[safe-delete][SAFE_DELETE_FAIL_CLOSED] ... reason":"trash-failed"`）。这是执行环境限制，不是打包脚本或
仓库缺陷。本轮已清理自己产生的临时目录，`windows/artifacts` 恢复原状，旧包与旧哈希保持不变。

后果：Desktop 侧 hardening 改动**尚未**进入 self-contained 包；下次应在不受删除拦截的 shell 中重建并跑
`verify-package.ps1`，再更新哈希，不要把 9-15 的旧 ZIP 当作当前产物。

## 9. 第二轮接手记录（Workbuddy，2026-09-17）

### 9.1 Android public HTTP 边界：与 Windows 对称收口

- 新增 `app/src/test/java/com/ojnexus/core/network/PublicHttpClientAuditTest.kt`：扫描
  `src/main/java/**/*.kt`，禁止在 `PublicHttpClient.kt` 之外出现 `OkHttpClient(` 或
  `OkHttpClient.Builder(`。**只禁构造调用**，不禁 import 与类型声明——第一版按类型名匹配时误报了
  `OjNexusApplication.kt:68`、`OjNexusApplication.kt:101`（`PublicHttpClient.builder()` 处的类型声明与
  import）等 4 处，已收紧为构造模式。
- **审计发现真实不一致**：`app/src/main/java/com/ojnexus/judge/luogu/open/LuoguOpenPlatform.kt` 的
  `private val webSocketClient: OkHttpClient = OkHttpClient()` 绕过了 `PublicHttpClient`。
  已改为 `PublicHttpClient.builder().build()` 并补充 import。语义上等价（OkHttp 默认 `cookieJar`
  本就是 `CookieJar.NO_COOKIES`），收益是构造入口统一并可防回退。
- 结果：`testDebugUnitTest` **132 suite / 525 tests / 0 failures / 0 errors**；
  `test assembleDebug assembleRelease lintDebug` → `BUILD SUCCESSFUL in 7m 5s`、`EXIT=0`。

### 9.2 Android 未提交改动（connector disabled/enabled）合规性审查（只读，未改代码）

- `values/strings.xml` 与 `values-zh-rCN/strings.xml` 双侧齐备地新增了
  `settings_connector_disabled`、`settings_state_disabled`、`settings_enable`、`settings_disable`，
  Kotlin 侧无硬编码文案。
- 状态均携带文字（DISABLED），非仅颜色；使用 `NexusTone` / `NexusSpacing` token，无 emoji、
  无硬编码色值/尺寸——符合设计系统约束。
- 语义链路：`canSync = account != null && account.enabled && BACKGROUND_SYNC in capabilities`，
  因此 `SYNC ALL` 天然排除停用账户（与设备用例
  `disabledAccountShowsDisabledStateAndIsExcludedFromSyncAll` 一致）；`setEnabled` 在事务中只改
  `enabled/updatedAt`，保留公开身份与本地缓存；启用时注册周期 Worker、停用时取消；`connect` 优先复用
  同 handle 的已停用账户而非删除重建。

### 9.3 本轮 fresh evidence

- Android 全量单测、完整 Gradle 门禁（见 9.1）。
- Android 设备测试（Pixel_9 AVD）：见 9.4。

### 9.4 设备回归与新的环境坑

- Pixel_9 AVD `connectedDebugAndroidTest` 回归：**15/15 通过**，`BUILD SUCCESSFUL in 5m`、`GRADLE_EXIT=0`，
  结果 XML `tests=15 failures=0 errors=0 skipped=0`。用于确认 `LuoguOpenPlatform` 改动无运行时回归。
- **新坑（重要）**：本轮第一次尝试时，`:app:compileDebugAndroidTestKotlin` 卡住 **29 分钟无输出**
  （java 进程仍在吃 CPU，属真实卡顿而非无进展），终止后清理残留 java 进程、复用同一台仍在线 AVD 重跑，
  5 分钟即完成。判读方式：`connectedDebugAndroidTest` 长时间停在某个 `Task` 上时，**先看该 Task 是否
  长时间无新增输出，再终止并重试**，不要直接判为代码缺陷；重跑前先 `Stop-Process java -Force` 清残留。

## 10. 第三轮接手记录（Workbuddy，2026-09-18）

### 10.1 真实缺陷：Windows 端缺少“认证受限”语义（跨端不一致）

发现路径：用 CLI 对 Luogu `uid:2` 做真实来源同步后，直接查临时库
`sync_modules`，看到 `SUBMISSIONS` 的 `failure_type = 'Api'`（此前 9 月 18 日之前的记录同样如此）。

跨端对照（说明这是 Windows 独有的缺口，不是设计选择）：

- Android：`judge/luogu/LuoguClient.kt` 抛 `LuoguApiError.AuthenticationRequired`，
  `SettingsScreen` 用 `lastErrorType == "AuthenticationRequired"` 渲染专门文案。
- Apple：`URLSessionHTTPClient` 对 401/403 抛 `AdapterError.authentication`，映射为
  `SyncError.authentication`。
- Linux：历史 live smoke 记录为 typed `AUTHENTICATION`。
- Windows：`SyncError` 枚举**没有** Authentication 成员，三个 adapter 一律 `IsSuccessStatusCode`
  失败即 `Api`，于是“匿名访问受限”被 UI 呈现为 `API ERROR`。

本轮修改（4 个文件，均在主 checkout）：

1. `windows/src/OjNexus.Windows.Core/Domain/SyncError.cs`：新增 `Authentication` 成员（枚举按名字符串
   持久化，`Enum.TryParse` 读取，向后兼容，无 schema 迁移）。
2. `windows/src/OjNexus.Windows.Core/Network/LuoguAdapter.cs`：`FetchAsync` 中 401/403 先于通用失败分支
   返回 `SyncError.Authentication`，并加注释说明“匿名受限是能力边界，不是客户端故障”。
3. `windows/src/OjNexus.Windows.Core/Sync/ModuleFailureType.cs`：白名单新增
   `Authentication` 分支（否则会被 `_ => Api` 归一回去）。
4. `windows/tests/OjNexus.Windows.Core.Tests/LuoguAdapterTests.cs`：原
   `..._ReportsApiFailureOnlyForThatStage` 改为 `[Theory]`，覆盖 401 与 403，断言
   `nameof(SyncError.Authentication)`。

刻意**未改** `CliExitCodeMapper`：`Authentication` 与 `Api` 一样落到 `Partial`(1)，避免改变 CLI 退出码
语义（否则会影响既有脚本与 CI 判读）。WPF 直接显示 `FailureType.ToUpperInvariant()`，无需新增文案，
界面自动从 `API` 变为 `AUTHENTICATION`。

### 10.2 本轮 fresh evidence（Windows）

| 命令 / 检查 | 结果 |
| --- | --- |
| `dotnet test windows/OjNexus.Windows.sln -c Release --no-restore` | `EXIT=0`；Core **72/72**（原 71，+1 为 403 新用例）、CLI **28/28**、Desktop **19/19** |
| `dotnet build windows/OjNexus.Windows.sln -c Release --no-restore` | `EXIT=0`，`0 个警告`、`0 个错误` |
| 真实来源 `sync --judge luogu --handle uid:2 --json` + 直接查库 | `Partial`、`total=4 successful=3 failed=1`、exit=1；`sync_modules` 中 `SUBMISSIONS` 为 `Error / Authentication`，其余 PROFILE 1、CONTESTS 20、PROBLEMSET 50 均 Success |
| `windows\scripts\ui-smoke.ps1 -Configuration Release -Width 900 -Height 560` | `UI SMOKE: PASS`，`SCREENSHOTS: 3/3`，`CONNECTOR LIFECYCLE: SAVE / DISABLE / ENABLE / PASS` |

### 10.3 附带完成的 CLI 命令面 fresh 验证（真实运行）

`status --json` → `{"status":"ready","accountCount":0,"lastSync":null}`；`history --json` → `{"operations":[]}`；
`config show --json` → 返回 dataDirectory/databasePath/accounts；未知命令 → `ARGUMENT ERROR: UNKNOWN COMMAND`
exit=2；`data --judge codeforces`（无 handle）→ `NO HANDLE CONFIGURED FOR CODEFORCES` exit=2；
`sync --handle tourist`（缺 `--judge`）→ `MISSING REQUIRED OPTION '--judge'` exit=2。

真实来源三站（同一临时数据目录）：Codeforces `tourist` → `Success` 3/3；AtCoder `tourist` → `Success` 1/1；
Luogu `uid:2` → `Partial` 3/4（认证受限，见 10.1）。`data --judge codeforces --json` 返回真实 payload
（`rating 3301`、`maxRating 4009`）。

### 10.4 仍未收口（与本轮无关，继续沿用）

Apple 需 macOS/CI 编译；Linux 需 Linux 主机或 CI；Windows self-contained 包重建仍被宿主删除保护阻断、
旧哈希作废；交互式终端 Ctrl+C 未验证。

## 11. 提交记录（Workbuddy，2026-09-18，经用户授权）

用户在 2026-09-18 明确授权提交「本轮改动 + 既有未提交工作」，并要求排除构建产物与 worktree。
分支 `codex/phase-5-arena`，ahead 61 → **67**，**未推送**。全部只按明确文件 `git add`，未使用
`git add -A`，每个 commit 前跑 `git diff --cached --check`。

| commit | 内容 |
| --- | --- |
| `b30b38f` | `test: guard the public HTTP boundary across Android and Windows`——`PublicHttpClient.kt`、Android/Windows 两侧审计测试、`Bootstrap.cs`、`DesktopViewModel.cs` 的 3 行 factory 切换 |
| `3af21f2` | `fix: surface Luogu authentication limits as a typed Windows error`——`SyncError.Authentication` + Luogu 401/403 映射 + 白名单 + Theory 测试 |
| `a3e65e9` | `feat(android): add connector enable and disable controls`——仓库/ViewModel/Compose/字符串/测试 |
| `3f24312` | `feat(windows): align sync store, payload projection and desktop UI`——`JudgeAccount`、schema/store、`SyncService`、WPF 与测试 |
| `31d149d` | `feat(apple): project connector status and validate public identities`——Apple 包、CI、`.gitignore`、`AGENTS.md`；body 注明**本机未编译** |
| `9ca689c` | `docs: record multi-platform status, plans and handoff notes`——README/ROADMAP/两份 handoff/superpowers 计划与规格 |

**仍排除、未提交**：`.worktrees/`（Linux worktree，属 `codex/linux-client`）、`.workbuddy-ai/`（项目 AI 数据）、
`windows/**/bin`、`windows/**/obj`（构建产物；`.gitignore` 目前只加了 `apple/.build/` 与
`apple/DerivedData/`，尚未覆盖 Windows 输出，建议后续补规则）。

**hunk 级拆分**：`DesktopViewModel.cs` 同时含用户改动与本次 3 行 hardening 改动，通过
`git diff` → 按 hunk 过滤 → `git apply --cached` 只把 3 行放进 `b30b38f`，使该 commit 单独 checkout 时
审计测试仍然自洽。注意 `git` 是 Windows 程序，`git apply` 的补丁路径必须写成 `C:/...`，给 `/tmp/...` 会失败。

**提交后验证（fresh）**：`dotnet test windows/OjNexus.Windows.sln -c Release --no-restore` → `EXIT=0`，
Core 72/72、CLI 28/28、Desktop 19/19；`tools\gradlew-local.bat test assembleDebug lintDebug` →
`BUILD SUCCESSFUL in 53s`、`EXIT=0`（源码与已构建状态一致，Gradle 判定 54 个任务 up-to-date），
单测报告仍为 132 suite / 525 tests / 0 failures。

`docs/GLM_HANDOFF.md` 含 Markdown 行尾双空格，`git diff --cached --check` 会报 trailing whitespace，
属 Markdown 换行语法，提交时保留原文。

### 11.1 忽略规则收口

`b1da8da chore: ignore local worktrees, project AI data and .NET build output` 补齐了 `.gitignore`：
`windows/**/bin/`、`windows/**/obj/`、`.worktrees/`、`.workbuddy-ai/`。此前只有 `apple/.build/` 与
`apple/DerivedData/`。提交后 `git status` 完全干净（ahead 69，无未跟踪项），构建产物不再构成误提交风险。

### 11.2 提交点回归（fresh）

| 验证 | 结果 |
| --- | --- |
| `dotnet test windows/OjNexus.Windows.sln -c Release --no-restore` | `EXIT=0`；Core 72/72、CLI 28/28、Desktop 19/19 |
| `tools\gradlew-local.bat test assembleDebug lintDebug` | `BUILD SUCCESSFUL in 53s`、`EXIT=0`；单测 132 suite / 525 tests / 0 failures |
| `windows\scripts\ui-smoke.ps1 -Configuration Release -Width 900 -Height 560` | `UI SMOKE: PASS`、`SCREENSHOTS: 3/3`、`CONNECTOR LIFECYCLE: SAVE / DISABLE / ENABLE / PASS` |
| Pixel_9 AVD `connectedDebugAndroidTest` | **15/15 通过**，`BUILD SUCCESSFUL in 2m 23s`、`GRADLE_EXIT=0`，XML `tests=15 failures=0 errors=0` |

即：`codex/phase-5-arena` 当前的提交点本身已通过四类门禁，不再依赖工作区的未提交状态。

### 11.3 仍未推送

`git push` 未执行，需用户明确授权；推送前应再扫一次 `git diff origin/codex/phase-5-arena..HEAD` 的敏感信息。
