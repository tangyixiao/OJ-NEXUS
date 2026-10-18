# OJ NEXUS — Roadmap

Each phase ends with: `assembleDebug` BUILD SUCCESSFUL, `test` green, code review, docs updated,
commits pushed.

## STORE METADATA

The repo now carries fastlane-style store metadata under `fastlane/metadata/android/` for en-US
and zh-CN: title, short and full descriptions, and per-versionCode changelogs. The copy is
telemetry-tone — a feature spec sheet, not marketing — and every claim maps to a shipped phase.
The data-boundary paragraph states the credential rule explicitly: no passwords, cookies, or
session tokens; the Luogu open-platform credential lives in no-backup storage and never enters
backups, exports, or logs. Screenshots and video are deliberately absent until real device
captures exist — placeholder art is never invented. Future releases add one
`changelogs/<versionCode>.txt` per side as part of release bookkeeping (F-Droid reads them
directly).
/ 仓库现在带有 fastlane 风格的商店元数据，位于 `fastlane/metadata/android/`，覆盖 en-US 与 zh-CN：
应用名、简短与完整描述、按 versionCode 的更新日志。文案是遥测语气——像功能规格表而非营销——且
每条陈述都对应一个已交付阶段。数据边界段落明确写出凭据规则：不收集密码、Cookie、会话令牌；洛谷
开放平台凭据保存在不参与备份的存储中，绝不进入备份、导出或日志。在拿到真实设备截图之前，刻意不
放截图与视频——绝不虚构占位素材。今后每次发布随发布簿记在两侧各新增一份
`changelogs/<versionCode>.txt`（F-Droid 直接读取）。

## ANDROID RELEASE SIGNING PIPELINE

Release signing is now env-gated and secret-driven, with an unsigned fallback that keeps every
path green. When all four `OJ_NEXUS_KEYSTORE_PATH` / `OJ_NEXUS_KEYSTORE_PASSWORD` /
`OJ_NEXUS_KEY_ALIAS` / `OJ_NEXUS_KEY_PASSWORD` variables are present, the release buildType picks
up the keystore; with any missing the build produces the standard unsigned APK exactly as before.
No keystore material, path, or password ever enters the repository. The CI `release` job decodes
`ANDROID_KEYSTORE_BASE64` into `RUNNER_TEMP` (wiped per job, never uploaded), builds, verifies
with `apksigner verify --verbose`, generates `SHA256SUMS-v<version>.txt` in the established
`docs/releases/` convention, and uploads APK + sums as the `release-apk` artifact; without
secrets it still builds, verifies-honestly (unsigned note), sums, and uploads. The unsigned local
path was proven on this host (`assembleRelease` BUILD SUCCESSFUL). Committing each new sums file
into `docs/releases/` remains a maintainer download-and-commit step — CI never pushes.
/ 发布签名改为环境变量门控、密钥驱动，并保留无签名回退路径。四个 `OJ_NEXUS_KEYSTORE_PATH` /
`OJ_NEXUS_KEYSTORE_PASSWORD` / `OJ_NEXUS_KEY_ALIAS` / `OJ_NEXUS_KEY_PASSWORD` 变量齐全时 release
构建类型挂载 keystore；任一缺失则产出与过去完全一致的标准未签名 APK。任何 keystore 材料、路径或
密码都不进入仓库。CI 的 `release` job 将 `ANDROID_KEYSTORE_BASE64` 解码到 `RUNNER_TEMP`（每 job
清空、永不上传），构建后用 `apksigner verify --verbose` 验签，按 `docs/releases/` 既有惯例生成
`SHA256SUMS-v<version>.txt`，并将 APK 与校验和作为 `release-apk` artifact 上传；无密钥时依旧构建、
如实标注未签名、生成校验和并上传。本机已验证未签名路径（`assembleRelease` BUILD SUCCESSFUL）。
把新的校验和文件提交进 `docs/releases/` 仍是维护者下载后手动提交的步骤——CI 从不推送。

## MEMORY AUDIT

LeakCanary now watches every debug build (debugImplementation only — release carries zero of it),
and the codebase passed a host-verifiable static audit: every screen collects state through
`collectAsStateWithLifecycle` (zero bare `collectAsState()` call sites), the only bitmap in the
app is the transient Player Card render (created, compressed, immediately unreachable), the
rating chart draws through Compose Canvas with no bitmap at all, `applicationScope` is a
SupervisorJob so one failed bootstrap child cannot cancel siblings, and session tickers are
collector-bound producers that stop with their composition. The supervisor-isolation and
ticker-lifetime invariants are pinned by `MemoryAuditInvariantsTest`. The 20-minute
main-navigation leak loop stays honestly PENDING until hardware runs — the debug build will
produce the LeakCanary report automatically when it does.
/ LeakCanary 现在守护每个 debug 构建（仅 debugImplementation——release 不含任何一部分），代码库也
通过了可在主机验证的静态审计：所有界面都通过 `collectAsStateWithLifecycle` 收集状态（裸
`collectAsState()` 调用点为零），应用中唯一的位图是一次性的球员卡渲染（创建、压缩后立即不可达），
评分图表完全用 Compose Canvas 绘制不含位图，`applicationScope` 是 SupervisorJob——单个引导子任务
失败不会波及兄弟任务，会话计时器是随组合销毁的生产者。Supervisor 隔离与计时器生命周期不变量由
`MemoryAuditInvariantsTest` 钉住。20 分钟主导航泄漏循环在硬件运行前如实保持 PENDING——届时 debug
构建会自动产出 LeakCanary 报告。

## SYNC BATTERY PROFILE

Periodic sync now has a documented, unit-pinned battery profile. The worker builder reads its
6-hour cadence from `SyncPolicy.PERIODIC_SYNC_MINUTES` instead of a hardcoded literal, and adds
`requiresBatteryNotLow` next to the existing network constraint — the same posture the
scheduled-backup worker already had. Freshness windows (30 min profile/rating, 15 min
submissions, 60 min contests, 24 h problemset) keep a steady-state run to at most five
gate-spaced adapter calls (≥ 2.1 s apart) and one bounded 1000-row submissions page per account;
manual sync stays immediate and ignores both freshness and battery state. Idle/Charging
constraints were rejected on purpose — they would starve the cadence on daily-driver phones.
docs/SYNC_ENGINE.md records the full table; the device battery-draw number stays honestly
PENDING because no hardware is attached, per repo rules.
/ 周期同步现在有了文档化并由单测钉住的电池画像。工作器构建器从 `SyncPolicy.PERIODIC_SYNC_MINUTES`
读取 6 小时周期（不再硬编码字面量），并在网络约束旁增加 `requiresBatteryNotLow`——与定时备份工作器
一致。新鲜度窗口（档案/Rating 30 分钟、提交 15 分钟、比赛 60 分钟、题库 24 小时）把稳态运行压到至多
五次按门限间隔（≥2.1 秒）的适配器调用和每账号至多一页 1000 行的提交写入；手动同步保持即时，不受
新鲜度与电池状态约束。Idle/Charging 约束被有意否决——它们会让日常手机上的周期饿死。docs/SYNC_ENGINE.md
记录完整表格；设备电池消耗数值因主机无硬件而如实保持 PENDING。

## ROOM INDEX AUDIT

The ten hottest production queries were audited with `EXPLAIN QUERY PLAN` before any index was
added. Two composites proved their worth and shipped through a non-destructive v13→v14 migration:
`submission_jobs (judge, status, updated_at)` for the background Luogu job scan that previously
scanned the table and sorted through a temp B-tree, and `remote_problems (judge, external_id)` for
the delta-sync fingerprint read that previously searched `judge` then sorted. Every other hot
query already hit an existing index, so nothing else was added — index write-cost is paid only
where a plan regression was proven. The migration is additive `CREATE INDEX IF NOT EXISTS` DDL
only; `RoomIndexAuditTest` builds a fresh v14 database and also hand-builds a real v13 database
(captured DDL from a probe open), upgrades it in place, and asserts rows survive and both plans
switch to `USING INDEX`. Robolectric timings prove the plan shape honestly; device numbers are
recorded as PENDING in docs/PERFORMANCE.md rather than invented.
/ 对最热的十条生产查询先做 `EXPLAIN QUERY PLAN` 审计，再决定加索引。两条复合索引以非破坏性
v13→v14 迁移落地：`submission_jobs (judge, status, updated_at)` 服务后台洛谷任务扫描（此前全表
扫描加临时 B-tree 排序），`remote_problems (judge, external_id)` 服务增量同步指纹读取（此前仅按
judge 检索后排序）。其余热点查询均已命中既有索引，因此不再额外添加——索引写放大只在证明有计划
回退的地方支付。迁移仅为增量式 `CREATE INDEX IF NOT EXISTS` DDL；`RoomIndexAuditTest` 既构建全新
v14 库，也用探针库捕获的真实 v13 DDL 手工搭建旧库就地升级，断言数据保留且两个查询计划切换为
`USING INDEX`。Robolectric 计时只诚实地证明计划形态；设备数值在 docs/PERFORMANCE.md 中保持
PENDING，不做编造。

## COLD START TRACING

Cold start is now measured, not guessed. `OjNexusApplication` opens a `StartupTracer` span at
container construction — `app-create` covers restore recovery, Room open, and repository wiring —
and closes it before the background bootstrap coroutines launch, so none of that periodic-sync or
backup-schedule work counts against the first frame. The Dashboard composable closes the
`first-frame` span on its first composition and logs the whole chain exactly once under the
OJNEXUS-STARTUP tag: `cold-start total=…ms budget=1500ms verdict=WITHIN_BUDGET|OVER_BUDGET`
followed by per-span durations. The tracer is a pure object with an injectable clock (no
androidx.tracing dependency), unit-tested for span chains, idempotent ends, unknown-end no-ops,
negative-duration clamping, and the 1500/1501 ms budget verdict boundaries.
docs/PERFORMANCE.md records the harness and the measurement protocol (`adb shell am force-stop` +
cold launch, 5-run median from `adb logcat -s OJNEXUS-STARTUP`); the device number itself stays
explicitly PENDING because no hardware is attached to the development host, and repo rules forbid
inventing it.
/ 冷启动现在靠测量而非猜测。`OjNexusApplication` 在容器构造处打开 `StartupTracer` 区段——
`app-create` 覆盖恢复收敛、Room 打开与仓储装配——并在后台引导协程启动前关闭它，因此周期同步与
备份排程都不计入首帧。Dashboard 组合项在首次组合时关闭 `first-frame` 区段，并在 OJNEXUS-STARTUP
标签下恰好输出一次完整链路：`cold-start total=…ms budget=1500ms verdict=WITHIN_BUDGET|OVER_BUDGET`
及各区段耗时。tracer 是带可注入时钟的纯对象（不引入 androidx.tracing 依赖），单测覆盖区段链、
幂等关闭、未知端点无操作、负时长钳制以及 1500/1501ms 预算判定边界。docs/PERFORMANCE.md 记录了
测量协议（`adb shell am force-stop` + 冷启动，取 `adb logcat -s OJNEXUS-STARTUP` 五次中位数）；
设备数值明确标记 PENDING——开发主机没有连接硬件，仓库规则禁止编造。

## LIBRARY PAGING

The problem library now stays smooth at ten thousand rows without adopting Paging 3. The full
filtered-and-sorted list remains the single source of truth inside the ViewModel — every count,
pulse, and the BUILD TRAINING rail still reason over the entire scope — while the LazyColumn only
ever tracks a bounded sixty-row window. A SHOW MORE footer at the end of the list grows the window
by sixty rows per tap, and any filter or sort change resets it to the initial size, so
recomposition cost is a function of the window (flat) instead of the library (growing). The window
math lives in pure functions (`windowList`, `hasMoreLibrary`, initial/step constants) covered by
LibraryWindowTest: cap and negative-count clamps, boundary flips, growth without duplicates, and a
10,000-row fixture pass proving filter+sort+window completes in milliseconds against a 250ms CPU
budget. Small libraries render unchanged — when the window covers the whole list the footer
disappears.
/ 题库列表现在在万行规模下依然流畅，且没有引入 Paging 3。完整过滤排序列表仍是 ViewModel 内的
唯一事实来源——所有计数、脉冲和 BUILD TRAINING 操作条仍然作用于整个范围——而 LazyColumn 只跟踪
一个有界的六十行窗口。列表末尾的 SHOW MORE 页脚每次点击将窗口扩大六十行，任何筛选或排序变化都会
把窗口重置为初始大小，使重组成本取决于窗口（恒定）而非题库规模（增长）。窗口数学全部是纯函数
（`windowList`、`hasMoreLibrary`、初始/步进常量），由 LibraryWindowTest 覆盖：上限与负数钳制、
边界翻转、增长不重复，以及一个 10,000 行夹具测试证明过滤+排序+取窗在 250ms CPU 预算内毫秒级完成。
小题库渲染不变——窗口覆盖全列表时页脚自动消失。

## WINDOWS CODE SIGNING

Windows artifacts can now be Authenticode-signed whenever a real CA certificate is available, and
the contract is strictly opt-in: with no certificate configured every build still completes and
ships unsigned. `docs/WINDOWS_SIGNING.md` records the storage rules — the PFX or token lives
outside the repository, only a SHA-1 thumbprint is referenced, and it travels exclusively through
the `OJ_NEXUS_SIGNING_CERT_THUMBPRINT` and `OJ_NEXUS_SIGNTOOL_PATH` environment variables; no
key material, password, or thumbprint is ever committed. The shared `sign-binaries.ps1` runs the
documented invocation — `signtool sign /fd SHA256 /sha1 <thumbprint> /tr
http://timestamp.digicert.com /td SHA256 <file>` followed by `signtool verify /pa /v` — failing
loudly when signing is requested but signtool or the certificate is missing. `package.ps1` signs
the CLI and WPF executables immediately after publish and before `SHA256SUMS.txt` is written, so
the deterministic manifest describes the signed bytes; `build-installer.ps1` signs the compiled
Inno Setup installer the same way. All negative paths were probed on this machine: missing
signtool, empty thumbprint, and a missing signing target each fail with explicit errors, and the
unsigned end-to-end gate reports `SIGNING: SKIPPED` then succeeds.
/ 当真实的 CA 证书可用时，Windows 产物现在可以进行 Authenticode 签名，且该契约严格可选：未配置
证书时每次构建仍会完成并以未签名形式发布。`docs/WINDOWS_SIGNING.md` 记录了存储规则——PFX 或
硬件令牌位于仓库之外，只引用 SHA-1 指纹，且仅通过 `OJ_NEXUS_SIGNING_CERT_THUMBPRINT` 与
`OJ_NEXUS_SIGNTOOL_PATH` 环境变量传递；任何密钥材料、密码或指纹都不会提交入库。共享的
`sign-binaries.ps1` 执行文档化调用——`signtool sign /fd SHA256 /sha1 <指纹> /tr
http://timestamp.digicert.com /td SHA256 <文件>`，随后 `signtool verify /pa /v`——当请求签名但
缺少 signtool 或证书时明确报错。`package.ps1` 在发布后、写入 `SHA256SUMS.txt` 之前签名 CLI 与
WPF 可执行文件，使确定性清单描述的是签名后的字节；`build-installer.ps1` 以同样方式签名编译出的
Inno Setup 安装程序。所有否定路径均在本机实测：缺少 signtool、空指纹、缺失签名目标都会以明确
错误失败，未签名的端到端闸门输出 `SIGNING: SKIPPED` 后成功。

## WINDOWS INSTALLER

The Windows client gains an Inno Setup 6 installer as a second distribution channel alongside the
deterministic zip. `windows/scripts/installer.iss` packs the already-published CLI and WPF trees,
creates start-menu and desktop shortcuts, and registers a standard uninstaller with
`PrivilegesRequired=lowest` so a per-user install always works without elevation. The new
`build-installer.ps1` reuses `package.ps1` for the deterministic publish, compiles the installer
when ISCC is present (CI installs it via chocolatey), and moves the single produced executable to
the artifacts root. Code signing is strictly opt-in: when `OJ_NEXUS_SIGNTOOL_PATH` and
`OJ_NEXUS_SIGNING_CERT_THUMBPRINT` are both configured the installer is signed (SHA-256 with a
timestamp) and verified; otherwise the build reports `SIGNING: SKIPPED` and still succeeds, so an
unsigned build is always available. `verify-installer.ps1` validates the artifact honestly —
existence, size, and version-resource content for a built installer, or payload presence for a
package root — while the full silent install/uninstall round trip is documented as a manual step
on a clean VM. All cleanup goes through .NET file APIs, never `Remove-Item`. No credentials, no
cookies, and no network access are involved in the payload.
/ Windows 客户端新增 Inno Setup 6 安装程序，作为确定性 zip 之外的第二分发渠道。
`windows/scripts/installer.iss` 打包已发布的 CLI 与 WPF 目录，创建开始菜单与桌面快捷方式，并注册
标准卸载器；`PrivilegesRequired=lowest` 保证免提权的按用户安装始终可行。新的 `build-installer.ps1`
复用 `package.ps1` 完成确定性发布，在存在 ISCC 时编译安装程序（CI 经 chocolatey 安装），并把唯一
生成的可执行文件移入产物根目录。代码签名严格可选项：配置 `OJ_NEXUS_SIGNTOOL_PATH` 与
`OJ_NEXUS_SIGNING_CERT_THUMBPRINT` 后对安装程序签名（SHA-256 加时间戳）并验证；否则输出
`SIGNING: SKIPPED` 且构建仍然成功，未签名构建始终可得。`verify-installer.ps1` 如实验证产物——
安装程序检查存在性、体积与版本资源内容，包根目录检查载荷存在性——而完整的静默安装/卸载回环
作为清洁虚拟机上的手动步骤记录在案。所有清理均通过 .NET 文件 API，绝不使用 `Remove-Item`。
载荷不涉及凭据、Cookie 或网络访问。

## WINDOWS REVIEW QUEUE

The Windows client now carries its own spaced-repetition queue over the shared local SQLite
ledger. Schema v7 adds two additive tables: `reviews` keyed (judge, external_id) holding the
current stage, due timestamp (ISO-8601), local due day, last result, and last reviewed time; and
`review_log`, an append-only history where every mark appends a row even after the stage caps.
The interval ladder mirrors the Android scheduler exactly — 1, 3, 7, 21, 45, 90 days; PASS
advances the stage (capped at 5) with a full interval, HARD repeats the stage on a half interval
(minimum one day), FAIL drops a stage (floor 0) and retries the next day, SKIP repeats the stage
on a full interval. There is no foreign key to `problems`: marking accepts any judge/identity the
user names, and the due list joins the derived problem title and URL when a row exists. CLI
parity ships as `review due [--judge] [--limit] [--json]` (default 50, ordered by due day, judge,
external id) and `review mark --judge --problem --result pass|hard|fail|skip [--json]`, with
argument errors for unknown results and non-positive limits. No credentials, no cookies, and no
network access are involved; every row lives in the local ledger.
/ Windows 客户端现在在共享本地 SQLite 账本上拥有自己的间隔重复队列。Schema v7 新增两张增量表：
`reviews` 以 (judge, external_id) 为键，保存当前阶段、到期时间戳（ISO-8601）、本地到期日、最近
结果与最近复习时间；`review_log` 为只追加历史，即使阶段封顶后每次标记仍会写入一行。间隔阶梯与
Android 调度器完全一致——1、3、7、21、45、90 天；PASS 阶段 +1（封顶 5）并按完整间隔排期，HARD
保持阶段并按半间隔（至少一天），FAIL 阶段 -1（下限 0）次日重试，SKIP 保持阶段并按完整间隔。
`reviews` 不对 `problems` 设外键：标记接受用户命名的任意 OJ/身份，到期列表在存在派生行时联出
题目标题与链接。CLI 对齐为 `review due [--judge] [--limit] [--json]`（默认 50 条，按到期日、OJ、
身份排序）与 `review mark --judge --problem --result pass|hard|fail|skip [--json]`，未知结果或
非法上限都会给出参数错误。全程不涉及凭据、Cookie 或网络访问；每一行都存于本地账本。

## WINDOWS PROBLEM LIBRARY VIEW

The Windows client gains a PROBLEMS surface over the shared local SQLite ledger. Schema v6 adds
an additive `problems` table keyed (judge, external_id); rows are derived inside the same
transaction that persists each judge's submissions payload — Codeforces rows use the Android
identity rule `{contestId}{index}`, AtCoder rows use `{contestId}+{problemId}`, Luogu contributes
no rows because its payload is counts-only. A submission with an accepted verdict (`OK`, `AC`, or
an `Accepted` prefix) marks the row solved, and that flag is sticky: later non-accepted
submissions never reset it, and the first-seen title wins while `last_seen_at` advances. The CLI
exposes `problems list [--judge] [--status solved|attempted] [--limit] [--json]` (default limit
50, success exit even when empty) and the WPF shell gains a PROBLEMS page with judge/status
filter combos, a bounded list, and an OPEN button that launches the public problem link through
the shell — only when a URL can be built for that identity. No credentials, no cookies, and no
new network call sites are involved; every row is derived from data already persisted offline.
/ Windows 客户端新增基于共享本地 SQLite 账本的 PROBLEMS 界面。Schema v6 增加以 (judge, external_id)
为键的增量 `problems` 表；行数据在持久化各 OJ 提交负载的同一事务内派生——Codeforces 沿用
Android 侧的 `{contestId}{index}` 身份规则，AtCoder 使用 `{contestId}+{problemId}`，洛谷负载只有
计数、不产生行。被判题通过的提交（`OK`、`AC` 或 `Accepted` 前缀）将行标记为已解决，且该标记是
粘性的：后续未通过的提交不会重置它，标题保留首次取值，仅推进 `last_seen_at`。CLI 暴露
`problems list [--judge] [--status solved|attempted] [--limit] [--json]`（默认 50 条，空结果也以
成功退出），WPF 外壳新增 PROBLEMS 页面，含 OJ/状态筛选组合与有界列表，OPEN 按钮在能为该身份构建
公开题目链接时经系统 Shell 打开。全程不涉及凭据、Cookie 或新增网络调用点；每一行都由已离线持久
的数据派生而来。

## MULTI-HANDLE PER JUDGE — DESIGN GATE

Multi-handle shipped as a design-gated phase: a verified spec, a pure prototype, and a migration
test plan — deliberately no UI and no production wiring yet. The spec records the ground truth
(the accounts table already admits several handles per judge; the sync-state table's
judge-keyed cursor is the real constraint) and splits the work: stage 1 stores a second handle
as a disabled account with cursor reset on switch; stage 2 migrates the cursor to
(judge, account) behind a tested v14 plan. Data generation stays global — restore staleness is
whole-database, and per-handle staleness is already answered by the account id carried in work
inputs. The prototype encodes those rules as a pure planner (reuse, add secondary, replace at
the two-handle cap, account-scoped staleness) with unit tests; production `connect()` keeps its
current single-handle behavior until the UI phase adopts it.
/ 多账号按 OJ 以设计门槛阶段交付：一份经过核实的规格、一个纯原型、一份迁移测试计划——刻意
不做 UI，也不接入生产路径。规格记录了代码中的事实（账号表本就允许同一 OJ 多个 handle；真正
的约束是同步状态表以 judge 为键的游标），并把工作拆分：第一阶段把第二个 handle 存为停用账
号，切换时重置游标；第二阶段在经过测试的 v14 计划里把游标迁移为 (judge, account)。数据代
际保持全局——恢复失效面向整个数据库，按账号的失效已由工作输入携带的账号 id 回答。原型把这
些规则编码为纯规划器（复用、新增副号、双 handle 上限时替换、按账号判失效）并配单元测试；
生产 `connect()` 保持现有单 handle 行为，直到 UI 阶段采用它。

## JUDGE HEALTH DASHBOARD

The connector center now doubles as a judge health dashboard — still strictly read-only. Each
judge row projects the sync ledger's most recent finished run: its terminal status (with the
failure type appended when one exists) and the wall-clock latency of that run, both computed by a
pure projection with unit tests. No new network surface and no background probing: the only
luogu quota trigger is the explicit PROBE action, which reuses the existing manual quota check
and renders the honest point total, a checking state, or a failure line in place. The dashboard
answers "is this judge healthy right now" from data the app already recorded — never from a
hidden poll.
/ 连接器中心现在兼任 OJ 健康面板——依然严格只读。每个 OJ 行投影同步台账中最近一次已完成
的运行：其终态（存在失败类型时一并显示）与该次运行的墙钟延迟，全部由带单元测试的纯投影计
算。没有新的网络接口，也没有后台探测：洛谷配额的唯一触发是显式的 PROBE 动作，复用既有的
手动配额查询，并在原位渲染诚实的点数、探测中状态或失败提示。面板回答"这个 OJ 现在健康吗"
依据的是应用已记录的数据——绝不来自隐藏轮询。

## LUOGU PUBLIC TRAINING CATALOG

Luogu's official training plans are now browsable read-only inside the app. A capability probe
documented first that the public training pages (no login, no cookies) embed their payload in a
`lentille-context` script tag; a dedicated codec extracts and decodes that JSON with typed
failures — a wrong template or missing node never throws. The catalog screen lists public
official trainings with provider, problem count, and marks; the detail screen renders the
ordered problem ladder and every row deep-links into the existing local Luogu problem detail.
The entry point is a TRAININGS chip on the Luogu remote catalog, so the flow stays inside the
problems surface. Loading, empty, not-found, and failure states are explicit; the repository
surfaces typed `LuoguApiError`s and the screens never render a partial list as success. No
credential flow, no cookie jar, no writes — the catalog is a public mirror read through the
same gated, bounded-retry Luogu client as every other public surface.
/ 洛谷官方训练计划现在可以在应用内只读浏览。能力探测先行确认：公开训练页面（无需登录、
无 Cookie）把数据嵌在 `lentille-context` script 标签里；专用 codec 负责抽取并解码该 JSON，
失败有明确类型——模板不符或节点缺失绝不抛异常。目录屏列出公开官方训练的提供者、题目数与
收藏数；详情屏渲染有序的题目阶梯，每一行深链到既有的本地洛谷题目详情。入口是洛谷公开题库
上的 TRAININGS 芯片，流程始终留在题目页内。加载、空、未找到与失败状态都有明确呈现；仓库
层返回带类型的 `LuoguApiError`，屏幕绝不把残缺列表当成功渲染。没有凭据流程、没有 Cookie、
没有写入——目录是通过与其他公开面板相同的限速、有界重试洛谷客户端读取的公开镜像。

## UNIFIED SUBMISSION DETAIL SHEET

Attempt rows and Luogu submission cards now open one shared bottom-sheet detail view. A
`SubmissionDetailUiModel` carries judge id, unified verdict tag plus raw verdict text, time /
memory / score metrics when the judge reports them, language, timestamp, problem context, and
extra rows for compile output or error details. Locally recorded attempts map with no metrics
and no links; Luogu jobs map compile message, output, exit code, and custom-run kind into
labeled rows; a Codeforces attempt keeps its exact deep link while the problem link comes from
the Luogu problem page. The sheet adds a note shortcut and opens links through the existing
custom-tab opener. Verdict mapping stays honest: unknown statuses surface the raw status text
instead of guessing a tag.
/ 提交行与洛谷提交卡片现在打开同一个共享的底部详情面板。`SubmissionDetailUiModel`
承载 judge id、统一判定标签与原始判定文本、评测机报告时的耗时 / 内存 / 得分、语言、时间、
题目上下文，以及编译输出或错误详情等附加行。本地手动记录的提交没有指标也没有链接；洛谷任务把
编译信息、输出、退出码和自定义运行类型映射为带标签的行；Codeforces 提交保留精确深链，
题目链接则指向洛谷题面。面板提供添加笔记入口，链接继续走既有的自定义标签页打开器。
判定映射保持诚实：未知状态直接展示原始状态文本，而不是猜测一个标签。

## ATCODER RATING HISTORY

The AtCoder adapter gains a RATING stage backed by the official public
`https://atcoder.jp/users/<handle>/history/json` endpoint — no scraping, no auth, no cookies.
Unrated rows (heuristic contests, `IsRated=false`, `NewRating=0`) are filtered before mapping so
the UI never renders a missing rating as zero; rated rows map into the shared `rating_changes`
table keyed by `(judge, contest_id)` with the contest id extracted from
`ContestScreenName` and the end time parsed as an offset-aware `OffsetDateTime`. Because the
endpoint returns the full history, the refresh is an atomic delete+insert transaction — Room's
generated `@Upsert` resolves conflicts on the primary key only, so a unique-index collision
would otherwise silently drop changed rows. The sync coordinator now runs
SUBMISSIONS → RATING → CONTESTS → PROBLEMS with a durable module receipt per stage, a 6-hour
freshness stamp, and the existing kenkoooo request gate shared across both hosts. An unknown
handle returns `200 []`, which is a successful no-op consistent with `UNVERIFIED` account
binding; a failed refresh retains the previous cache.
/ AtCoder 适配器新增 RATING 阶段，数据来自官方公开的
`https://atcoder.jp/users/<handle>/history/json`——不爬页面、不需要登录、没有 cookie。
未评级行（AHC 等 `IsRated=false`、`NewRating=0`）在映射前过滤，UI 永远不会把缺失评级显示为
0；评级行按 `(judge, contest_id)` 写入共享的 `rating_changes` 表，contest id 取自
`ContestScreenName`，结束时间用带时区偏移的 `OffsetDateTime` 解析。由于该端点返回全量历史，
刷新采用一个事务内的 delete+insert——Room 生成的 `@Upsert` 只按主键解决冲突，唯一索引冲突
会被静默丢弃。协调器现在按 SUBMISSIONS → RATING → CONTESTS → PROBLEMS 执行，每阶段写入
持久化模块回执，6 小时新鲜度戳，两个主机共用既有的 kenkoooo 请求闸门。未知用户返回
`200 []`，按 `UNVERIFIED` 绑定语义处理为成功空操作；刷新失败保留旧缓存。

## CODEFORCES SUBMISSION DEEP-LINK

Attempt rows on the problem detail screen are now openable when they were imported from a
Codeforces submission. The row builds its target exclusively through the reviewed
`CodeforcesUrls.submission(contestId, submissionId)` builder — contest/submission ids must both
parse as numbers or no link is offered — and opens it with the screen's existing external-browser
helper (Custom Tabs where available, plain ACTION_VIEW otherwise). The domain `Attempt` model
now carries the remote origin (source judge, judge-side submission id, contest id) that the
database already stored; the mapper simply passes it through, so there is no schema change, no
new request, and manual entries keep exactly their old rendering.
/ 题目详情页的提交记录行现在在源自 Codeforces 导入时可以点击打开。目标链接一律由经过评审的
`CodeforcesUrls.submission(contestId, submissionId)` 构造——contest/submission 编号必须都能解析为
数字才提供链接——并使用页面既有的外部浏览器助手打开（可用时走 Custom Tabs，否则 ACTION_VIEW）。
领域模型 `Attempt` 现在携带数据库早已保存的远端来源（源 judge、judge 侧提交 id、比赛 id）；
mapper 只是透传，因此没有 schema 变更、没有新请求，手动条目的渲染完全不变。

## CODEFORCES PROBLEMSET DELTA SYNC

The Codeforces PROBLEMS stage no longer rewrites the whole ~10k-row catalog on every forced
refresh. A pure `ProblemsetDeltaPlanner` decides between SKIP_FRESH (the existing 24-hour
freshness stamp), SKIP_UNCHANGED and WRITE. The unchanged check compares the fetched catalog
against the live table through a SHA-256 fingerprint over canonical
`contestId:index:rating:solvedCount` rows, confirmed by two indexed SQL aggregates (row count
and solved-count sum) before any skip is trusted. When content matches, the chunked Room write
is skipped entirely and the ledger records 0 items; real drift still lands through the same
bounded 2000-row transactions. Everything stays on the official public API through the request
gate — no new endpoints, no scraping, and the 503 backoff path is untouched.
/ Codeforces 的 PROBLEMS 阶段不再在每次强制刷新时重写整张约一万行的目录表。纯函数
`ProblemsetDeltaPlanner` 在 SKIP_FRESH（沿用 24 小时新鲜度戳）、SKIP_UNCHANGED 与 WRITE 之间做决策。
未变化检查先对获取到的目录按 `contestId:index:rating:solvedCount` 规范行计算 SHA-256 指纹，再由两条
索引 SQL 聚合（行数与解题数之和）确认后才允许跳过；内容一致时完全跳过分块写入并在台账记录 0 项，
真实漂移仍走同样的 2000 行有界事务。全部经由请求网关走官方公开 API——不新增端点、不爬页面，
503 退避路径保持不变。

## DATABASE HOUSEKEEPING PANEL

The settings data section now reports the database honestly: one line shows the DB size, WAL file
size and free-page bytes computed from `PRAGMA page_size`, `PRAGMA page_count` and
`PRAGMA freelist_count` — never invented. Below it, every maintenance table lists its live row
count, using the same 22-table surface the restore path requires. A VACUUM action reclaims free
pages on the IO dispatcher with an elapsed-seconds ticker and a cancel affordance; because SQLite
VACUUM is atomic and cannot be interrupted through the framework API, a cancel that races a
committing vacuum surfaces a FINISHED notice instead of pretending the operation was stopped.
Stats refresh after the vacuum completes and on demand.
/ 设置页数据区现在如实报告数据库状况：一行显示 DB 大小、WAL 文件大小与空闲页字节数，数值来自
`PRAGMA page_size`、`PRAGMA page_count` 与 `PRAGMA freelist_count`，绝不臆造。下方逐一列出每个
维护表的实时行数，与恢复路径所需的 22 张表保持一致。VACUUM 操作在 IO 线程回收空闲页，提供经过
秒数的计时与取消入口；由于 SQLite VACUUM 原子执行且无法通过框架 API 中断，与提交赛跑的取消会
显示“已完成”提示而不是假装操作被停止。统计会在整理结束后与手动点击时刷新。

## IMPORT CONFLICT PREVIEW

Importing a snapshot archive no longer fires blindly. The settings screen now inspects the archive
before anything is staged: a preview dialog lists every required table with the INCOMING row count
from the archive manifest next to the CURRENT live count, and any table whose counts diverge is
highlighted in the accent color. The inspection reads only `manifest.json` — the database payload
is skipped, unknown or duplicate entries reject the archive, and the strict rules mirror the
restore path. Confirm proceeds to the exact same validated replace as before; cancel clears the
preview and leaves the live database, its pending-restore candidate and its journal completely
untouched. Legacy raw `.db` files (no manifest) skip the preview and import directly, unchanged.
/ 导入快照归档不再盲目执行。设置页现在会在暂存之前先检查归档：预览对话框为每个必需表列出归档
清单中的“导入”行数与当前“现存”行数，不一致的表以强调色高亮。检查只读取 `manifest.json`——
数据库负载被跳过，未知或重复条目会让归档被拒，严格规则与恢复路径一致。确认后走与之前完全相同
的校验替换；取消则清除预览，现用数据库、待恢复候选文件与日志都保持原样。旧版裸 `.db` 文件
（无清单）跳过预览直接导入，行为不变。

Three Robolectric tests pin the contract: a grown live database shows the real incoming → current
delta in the report; a mismatched schema version and a non-zip payload both return no report; and
the preview stages nothing — no pending-restore file, no journal, and live row counts unchanged.
The preview is read-only by construction: no network request, no Room migration, and no credential
flow is introduced.
/ 三个 Robolectric 测试钉死契约：现用库在导出后增长时报告展示真实的“导入 → 现存”差值；schema
版本不匹配与非 zip 负载都返回空报告；预览不暂存任何内容——无待恢复文件、无日志、现存行数不变。
预览按构造即只读：不发起网络请求，不做 Room 迁移，不引入凭据流程。

## PER-JUDGE DATA WIPE

Local data is now wipeable per judge. Each connected judge panel in the settings screen gains a
WIPE DATA action behind a typed-confirm dialog: the confirm button stays disabled until the user
types the judge's display name, so a mis-tap can never destroy a library. One transaction deletes
the judge's training library problems (attempts, failure entries, notes, knowledge, reviews and
training history cascade through foreign keys), the cached remote catalog and details, profile and
rating snapshots, contests and markers, workspace drafts, submission jobs, the sync state cursor
and the judge's sync operation ledger rows, then writes a `WIPE` audit entry into the fresh ledger.
The account binding itself survives — the public handle stays connected for the next sync, and
every other judge's data is untouched.
/ 本地数据现在支持按 OJ 清除。设置页每个已连接的 OJ 面板新增 WIPE DATA 操作，由输入确认对话框保护：
用户输入该 OJ 的显示名后确认按钮才可用，误触无法销毁题库。单个事务删除该 OJ 的训练题库题目
（提交记录、失败记录、笔记、知识、复习与训练历史经外键级联删除）、缓存的远端题库与题目详情、
个人档案与分数快照、比赛与标记、工作区草稿、提交任务、同步状态游标及该 OJ 的同步台账行，
随后在新台账中写入一条 `WIPE` 审计记录。账号绑定本身保留——公开句柄仍保持连接以便下次同步，
其他 OJ 的数据完全不受影响。

The wipe status is a stored ledger string, not a new enum value, so history rendering and retry
logic degrade gracefully. Cross-judge isolation is pinned by Robolectric tests: seeding two judges,
wiping one, and asserting the other's problems, profile, catalog detail and ledger rows survive.
The action is destructive by request only: no network request, no Room migration, and no credential
flow is introduced.
/ 清除状态是台账中的存储字符串而非新枚举值，历史渲染与重试逻辑可优雅降级。跨 OJ 隔离由 Robolectric
测试钉死：播种两个 OJ，清除其一，断言另一方的题目、档案、题库详情与台账行完好。该操作仅在用户明确
请求时具有破坏性：不引入网络请求、不涉及 Room 迁移、不新增凭据流程。

## SCHEDULED LOCAL BACKUP

Backups now run on a schedule. The settings data screen gains an AUTO BACKUP section: the user
picks a SAF folder once (persisted URI permission), chooses OFF / DAILY / WEEKLY, and sets how many
archives to keep (1–20, default 5). A `PeriodicWorkRequest` runs a `CoroutineWorker` that reads the
preferences, creates a dated `oj-nexus-auto-<yyyyMMdd-HHmmss>.zip` inside the picked tree through
`DocumentsContract.createDocument`, exports the Phase 86 snapshot archive into it, then prunes the
oldest archives beyond the keep count. The worker runs under battery-not-low and storage-not-low
constraints with exponential backoff, uses no network, and writes a `success:<ms>` / `failed:<ms>`
status stamp that the settings screen shows as a dismissible banner.
/ 备份从此按计划运行。设置页数据区新增 AUTO BACKUP 区块：用户一次性选定 SAF 文件夹（持久化 URI 权限），
选择关闭 / 每天 / 每周，并设定保留份数（1–20，默认 5）。`PeriodicWorkRequest` 驱动一个
`CoroutineWorker`：读取偏好，通过 `DocumentsContract.createDocument` 在所选目录内创建按时间命名的
`oj-nexus-auto-<yyyyMMdd-HHmmss>.zip`，写入 Phase 86 快照档案，再裁剪超出保留份数的最旧档案。
Worker 在电量非低与存储非低约束下运行，指数退避，不使用网络，并写入 `success:<ms>` / `failed:<ms>`
状态戳，设置页将其渲染为可关闭的横幅。

The scheduler is idempotent across cold starts (`enqueueUniquePeriodicWork` with UPDATE), so the
cadence chosen last always wins. Pure logic — cadence parsing, archive naming, keep-N pruning —
is unit-tested without Robolectric. The archive contents are unchanged from Phase 86: no network
request, no Room migration, and no credential flow is introduced.
/ 调度器跨冷启动幂等（`enqueueUniquePeriodicWork` 使用 UPDATE 策略），最后一次选择的节奏始终生效。
纯逻辑——节奏解析、档案命名、保留 N 份裁剪——以纯 JVM 单测覆盖。档案内容与 Phase 86 完全一致：
不引入网络请求、不涉及 Room 迁移、不新增凭据流程。

## EXPORT 2.0: FULL LOCAL SNAPSHOT

Backup became a self-describing archive. The settings data screen now exports a single ZIP holding
exactly two entries — `manifest.json` and `database.db` — where the manifest records the schema
version, the data-generation stamp, the export timestamp, and a per-table row count for every
required table, sorted by name for stable diffs. Import reads the archive, requires the manifest
before trusting the database, rejects a manifest whose schema version or generation stamp does not
match the running app, bounds the database entry at 512 MiB, and then hands the extracted database
to the same validated atomic-restore pipeline as before. Legacy raw `.db` exports remain
importable, so an older backup never becomes unreadable.
/ 备份成为自描述档案。设置页现在导出仅含两个条目的单一 ZIP——`manifest.json` 与 `database.db`——
清单记录 schema 版本、数据代次戳、导出时间，以及所有必需表的逐表行数（按表名排序，diff 稳定）。
导入先读清单再信任数据库：schema 版本或代次戳与运行中的应用不符即拒绝，数据库条目上限 512 MiB，
随后把解出的数据库交给与原来相同的校验原子恢复管线。旧的裸 `.db` 导出仍可导入，历史备份不会失效。

The manifest is metadata only: no credential, token, cookie, or OpenApp secret ever enters the
archive, and the database itself has no secret columns — the OpenApp credential stays in
`noBackupFilesDir`, outside every export path. Tests pin the entry list, the no-secret scan over
both entries, the round trip (export → import → restore → row counts match), tampered-version and
blank-stamp rejection, and the legacy fallback.
/ 清单只有元数据：任何凭据、令牌、Cookie 或 OpenApp 密钥都不会进入档案，数据库本身也没有密钥列——
OpenApp 凭据保存在 `noBackupFilesDir`，在任何导出路径之外。测试钉住条目清单、对两个条目的无密钥扫描、
往返一致性（导出 → 导入 → 恢复 → 行数一致）、篡改版本与空代次戳的拒绝，以及旧格式回退。

The archive adds no network request, no Room migration and no credential flow; it packages data
the app already holds. / 该档案不引入任何网络请求、Room 迁移或凭据流程，只是打包应用已有的本地数据。

## COMMAND PALETTE REVIEW / TRAINING / UNSOLVED QUERIES

The palette's direct-query card is no longer search-only. The local command grammar gains three
deterministic forms: `review due` jumps straight into the scheduled review run; `sprint <rating>`
opens training with the focus-sprint ladder anchored on the requested difficulty for that visit;
`unsolved <range>` and `<judge alias> unsolved <range>` open the library scoped to unsolved
problems in an inclusive difficulty range (`1600`, `1600-1900`, `1600-`, `-1900`). Judge aliases
(`@cf`, `ac`, `lg`, or bare) keep working in front of every form, and the card states exactly what
the command will do before it is tapped.
/ 命令面板的直达卡片不再只支持搜索。本地命令语法新增三种确定性形式：`review due` 直接进入到期复习冲刺；
`sprint <难度>` 打开训练页，专注冲刺阶梯在本次访问内以该难度为锚；`unsolved <范围>` 与
`<OJ 别名> unsolved <范围>` 打开按未解决题目和闭区间难度（`1600`、`1600-1900`、`1600-`、`-1900`）过滤的题库。
`@cf`、`ac`、`lg` 或裸别名继续适用于所有形式，卡片在点击前就写明这条命令会做什么。

Range filtering is a pure library predicate: bounds are inclusive, unknown difficulty is excluded
by any concrete range rather than guessed into one, and the filter composes with judge/status/tag
as before. The training target override is presentation-only state — it never writes the
calibrated preference.
/ 范围过滤是题库的纯谓词：边界闭区间，未知难度不会被猜进任何具体区间而是被排除，且与
OJ/状态/标签照旧组合。训练目标覆盖只是展示层状态，从不写回校准偏好。

Coverage includes the parser forms (aliases, tab/space normalization, malformed ranges, bounds),
filter inclusivity, unknown-difficulty exclusion, and judge composition. / 测试覆盖解析形式
（别名、空白归一、非法范围、边界）、过滤闭区间、未知难度排除以及 OJ 组合。

The commands add no network request, no Room migration and no credential flow; they only route to
screens the app already has and filter data it already holds. / 这些命令不引入任何网络请求、
Room 迁移或凭据流程，只是路由到应用已有的页面并过滤应用已有的本地数据。

## TRAINING CALENDAR HEAT STREAKS

Every heatmap cell now tells a screen-reader user which day it is. The cell description carries the
formatted local date next to the intensity and solved count, and the leading/trailing grid padding
is announced as an empty calendar cell instead of a fake zero-activity day. The totals strip shows
the longest streak beside the current one, so the season record is visible without reading the
heatmap cell by cell. The streak math itself was already a tested pure function
(`StreakCalculator`); this phase pins the contract with new boundary tests (longest covers current,
sparse history, single-day history) and a UI-layout test that fails if the date ever drops out of
the cell description.
/ 热力图的每个格子现在都会告诉读屏用户它是哪一天。格子描述在强度与解决数之外携带格式化后的本地日期，
网格首尾的补位格被朗读为"空白日历格"，而不是一个伪造的零活动日。总计条在当前连续天数旁边展示最长连续天数，
无需逐格阅读热力图即可看到赛季纪录。连签计算本身已是经过测试的纯函数（`StreakCalculator`），
本阶段用新的边界测试（最长≥当前、稀疏历史、单日历史）和 UI 布局测试固定这一契约——
日期一旦从格子描述中消失，测试即失败。

The phase adds no network request, no Room migration and no credential flow; it only relabels and
surfaces data the app already computes. / 本阶段不引入任何网络请求、Room 迁移或凭据流程，
只是重新标注并展示应用已经在计算的数据。

## FOCUS SPRINT DIFFICULTY LADDER

The focus sprint is now a ladder, not just a queue. The panel gained a four-chip difficulty band —
EASIER / FIT / STRETCH / ANY — anchored on the user's training target, and every preview row states
its own local reason: a rating tag (`1600` or `—` when the problem carries none) and a deterministic
reason code (DUE with the review gap in days, WEAK AREA, FAILURES, DIFFICULTY FIT, UNSOLVED,
UNRATED). Ordering walks the ladder: EASIER rows first, then FIT, then STRETCH, with unrated
candidates trailing instead of being dropped or guessed at — a missing rating never matches a
concrete band and never claims DIFFICULTY FIT. The chosen band is remembered across rotation while
the plan itself stays a pure function of the local snapshot, so the preview and the launched
session can never disagree.
/ 专注冲刺现在是一道阶梯，而不只是队列。面板新增以训练目标为锚点的四档难度选择——更简单 / 匹配 / 更难 / 全部，
每一行预览都声明自己的本地理由：难度标签（1600，无难度时显示 —）与确定性理由码
（待复习并带间隔天数、薄弱领域、多次失败、难度匹配、未解决、无难度）。排序沿阶梯上行：先更简单，再匹配，再更难，
无难度的候选排在末尾而不是被丢弃或被猜测——缺失的难度永远不会命中具体档位，也永远不会声称难度匹配。
所选档位在旋转后保留，而计划本身始终是本地快照的纯函数，因此预览与实际启动的会话不可能不一致。

Coverage includes band boundaries at exactly ±200, ascending difficulty inside a band, the unrated
trailing group, reason-code precedence, the due gap, and the limit/duplicate rules carried over
from the Phase 75 planner. / 测试覆盖 ±200 的档位边界、档位内难度升序、无难度尾组、理由码优先级、
复习间隔，以及沿用 Phase 75 规划器的上限与去重规则。

The ladder adds no network request, no Room migration and no credential flow; it only reorders and
labels data the app already holds. / 该阶梯不引入任何网络请求、Room 迁移或凭据流程，
只对应用已有的本地数据重新排序并加上标签。

## SESSION DEBRIEF EXPORT (MARKDOWN)

A finished session can now leave the phone. The debrief panel gained an EXPORT chip that copies a
Markdown document to the clipboard and hands the same text to the Android share sheet, so the
summary can be pasted into a notebook or a team channel. The document is generated by
`buildSessionDebriefMarkdown` — a pure function of a pre-resolved payload, which is why the
optional sections (target, note, finish time, duration, verdict, review state) are omitted rather
than rendered empty and why a problem title containing a pipe or a newline cannot break the table.
A session with no problems exports a `NO PROBLEMS LOGGED IN THIS SESSION.` line instead of a
header-only table. The UI resolves its labels once, so the builder itself never touches Android.
/ 已结束的训练会话现在可以离开手机。复盘面板新增「导出」按钮：把一份 Markdown 文档复制到剪贴板，并通过
Android 分享面板发送同一段文本，方便把摘要粘进笔记本或团队频道。文档由 `buildSessionDebriefMarkdown` 生成——
它是一个只依赖预解析载荷的纯函数，因此可选区块（目标、备注、结束时间、时长、判定、复习状态）会被省略而不是渲染成空行，
题目标题里的竖线或换行也不会破坏表格。没有问题记录的会话导出为 `NO PROBLEMS LOGGED IN THIS SESSION.` 一行，
而不是一张只有表头的表格。界面只解析一次标签，所以构建器本身完全不接触 Android。

Coverage spans the full, partial and empty sessions, the pipe/newline escaping rule, and the
exactly-one trailing newline. / 测试覆盖完整、部分与空会话三种情况、竖线与换行转义规则，以及结尾恰好一个换行。

The feature adds no network request, no Room migration and no credential flow; it only formats
text that is already local into Markdown. / 该功能不引入任何网络请求、Room 迁移或凭据流程，
只把本地已有的文本格式化成 Markdown。

## MASTERY DECAY V2

Knowledge mastery now ages. The score keeps its coverage (70 points) and efficiency (30) terms,
and time since the newest solved problem in the area then removes
`floor(score * days / 30)` points, capped at 30. An area solved two months ago reads as stale;
an area never solved has nothing to decay. The result carries both the score and the amount
removed, so the training screen renders `MASTERY 72 ▼ DECAY 8` and a `STALE` reason tag from one
evaluation. The engine reads no clock — the repository feeds it the local day delta from the
existing mastery query — and the formula plus its boundary table live in `docs/TRAINING_ENGINE.md`.
/ 知识掌握度现在会随时间衰减。分数保留覆盖率（70 分）与效率（30 分）两项，再用该区域内最新解题距今天数
减去 `floor(score * days / 30)` 分，上限 30 分。两个月前解过的区域会显示为久未复习；从未解过的区域没有可衰减的
内容。结果同时携带分数与被扣掉的分值，因此训练页可以用一次评估渲染 `MASTERY 72 ▼ DECAY 8` 与 `STALE` 原因标签。
引擎不读时钟——由仓库把本地日差值从既有的掌握度查询中喂给它——公式与边界表记录在 `docs/TRAINING_ENGINE.md`。

Coverage spans the engine boundaries (0 days, 8 days, the 30-day cap, past the cap, never solved,
negative deltas, floor-not-round, determinism) and the SQL expression itself, including the null
case for an area whose problems are attempted but unsolved. / 测试覆盖引擎边界（0 天、8 天、30 天上限、
超过上限、从未解出、负差值、向下取整、确定性）以及 SQL 表达式本身，包括区域内题目有尝试但未解出时的空值情况。

The feature adds no network request, no Room migration and no credential flow; it only reshapes
locally stored mastery evidence into a fresher score. / 该功能不引入任何网络请求、Room 迁移或凭据流程，
只把本地已存储的掌握度证据重组成更有时效的分数。

## KNOWLEDGE AUTO-LINKING

Problem detail can now bridge local tags to knowledge areas in one tap. A fixed alias table in
`core/model` maps judge tag spellings to the ten knowledge areas, matching is exact after
normalization, and the result is returned in enum declaration order so the same tags always
produce the same links. AUTO writes only the areas that are not linked yet, so a repeated tap
changes nothing and a manual link is never overwritten; CLEAR AUTO removes only the derived set
and leaves hand-picked links intact. The candidate count is derived inside the existing detail
`combine`, so the actions appear and disappear with the data instead of adding a new flow. /
题目详情现在可以一键把本地标签桥接到知识区域。`core/model` 中的固定别名表把评测机标签拼写映射到十个知识
区域，匹配在归一化后精确进行，结果按枚举声明顺序返回，因此同样的标签总是产生同样的链接。AUTO 只写入尚未链接的
区域，重复点击不会改变任何内容，手动链接也永不被覆盖；CLEAR AUTO 只移除自动派生的链接，手选链接保持不变。候选数量
在既有的详情 `combine` 中推导，操作随数据出现与消失，不新增数据流。

Coverage spans exact and case/whitespace-insensitive alias matching, Chinese aliases, one area per
alias, declaration-order output, unknown and blank tags linking nothing, the alias table never
mapping one alias to two areas, `pendingAutoLinks` subtracting the linked set, and determinism
across repeated calls. / 测试覆盖精确与大小写/空格不敏感的别名匹配、中文别名、一个别名对应一个区域、
按声明顺序输出、未知与空白标签不产生链接、别名表不会把同一别名映射到两个区域、`pendingAutoLinks` 减去已链接
集合、以及重复调用的确定性。

The feature adds no network request, no Room migration and no credential flow; it only links
existing local tags to existing local knowledge areas. / 该功能不引入任何网络请求、Room 迁移或凭据流程，
只把已有的本地标签链接到已有的本地知识区域。

## REVIEW DECK SHUFFLE & CRAM MODE

The review run now controls its own deck order. `SCHEDULE` keeps the due-day order; `SHUFFLE` permutes
the same queue with a per-local-day seed, so the order is stable within one day and reshuffles the
next; `CRAM` ignores the schedule and takes a user-picked judge plus an optional knowledge tag, then
shuffles that slice with the same seed. The review queue now exposes each problem's local tags, so a
cram slice can be as specific as "CODEFORCES + DP". The deck freezes once the first outcome is
recorded, so a run never reorders under the user. / 连续复习现在可以自己控制组卷顺序。`SCHEDULE`
保持到期日顺序；`SHUFFLE` 使用按本地日期生成的种子打乱同一队列，因此一天内顺序稳定、次日重新洗牌；
`CRAM` 无视排期，只取用户选定的评测机加可选知识标签，再用同一种子打乱该切片。复习队列现在会带上每道题的
本地标签，所以 cram 切片可以精确到「CODEFORCES + DP」。一旦记录了第一个结果，组卷即刻冻结，复习过程中不会重排。

All ordering lives in a pure `ReviewDeck` helper (schedule comparator, seeded Fisher–Yates, cram
filter) with a polled `java.util.Random` — no Android or clock dependency — and the view model only
snapshots the mode, filter and seed. Mode switching, tag chips and the shuffle seed are covered by
unit tests, including determinism, permutation invariants, an unusable cram filter being refused,
and a run freezing after its first outcome. / 所有排序都在纯函数 `ReviewDeck` 中完成
（排期比较器、带种子的 Fisher–Yates、cram 过滤），只依赖 `java.util.Random`，不碰 Android 或时钟；
ViewModel 只快照模式、过滤条件和种子。模式切换、标签 chip 与随机种子均有单元测试，覆盖确定性、
排列不变性、不可用 cram 过滤被拒绝、以及首次记录结果后冻结。

The deck mode changes only the local ordering of an existing queue; it adds no network request, no
Room migration and no credential flow. / 组卷模式只改变本地队列顺序，不引入网络请求、Room 迁移或凭据流程。

## NOTE FIELD TEMPLATES

Problem notes now follow a per-user editable template instead of four fixed headings. The template
lives in the preferences DataStore as an escaped `slot:label:hint` payload, so it survives a restart
without a Room migration; a corrupt value degrades to the default layout rather than failing. The
editor renders template fields in order first and then appends any remaining slot that still holds
saved text, so a renamed or removed heading can never hide existing notes. Reordering, adding,
renaming, hinting, removing, `RESET` and the `ROOT CAUSE / KEY INSIGHT / PITFALL` preset are all
available in a bottom sheet behind the note section's `TEMPLATE` action. / 题目笔记现在遵循每用户可编辑的
模板，而不再是四个固定标题。模板以转义后的 `slot:label:hint` 形式存放在设置 DataStore 中，重启后依然有效，
且不需要 Room 迁移；损坏的取值会退回到默认布局而不是报错。编辑器先按模板顺序渲染字段，再追加仍然存有内容的
其余字段，因此重命名或删除标题永远不会隐藏已有笔记。重排、新增、改名、提示、删除、`RESET` 以及
`ROOT CAUSE / KEY INSIGHT / PITFALL` 预设都在笔记区的 `TEMPLATE` 操作唤起的底部弹层中完成。

Coverage spans the template model (default and preset labels, ordering, cap at four fields, orphan
saved text, escape-safe codec, malformed payload rejection) and the DataStore round trip including a
corrupt stored value falling back to the default template. / 测试覆盖模板模型（默认与预设标签、排序、
四个字段上限、孤立已存文本、转义安全的编解码、非法载荷拒绝）以及 DataStore 的写入读回，包括损坏取值退回默认模板。

The editor adds no network request, no Room migration and no credential flow; it only reshapes local
note text and a local preference. / 编辑器不引入任何网络请求、Room 迁移或凭据流程，只重组本地笔记文本与本地偏好。

## ANDROID SHARED LAYOUT — Font scaling

Shared section headings now wrap within the space left by their trailing action, with a tokenized
gap between them. Bottom navigation retains a 60dp minimum and grows to fit wrapped labels;
all five tabs stay equally tall and their icons and label rows remain aligned. The full-width
48dp command button and navigation-bar inset remain intact. / 共用区块标题现在在右侧操作剩余的空间内换行，
并保留设计 token 定义的间距。底部导航保持 60dp 最小高度，按换行文字增高；五个入口等高，图标和标签行对齐。
命令栏仍为整行 48dp 点击区域，并保留系统导航栏 inset。

Device regression coverage checks long English/Chinese headings, all five destination callbacks,
command-row edge taps, and title truncation at 320dp / 200% font scale. Validation evidence:
[`2026-10-01-android-shared-layout.md`](verification/2026-10-01-android-shared-layout.md).

## WINDOWS VERTICAL SLICE — Native client and CLI

The `windows/` solution now contains a native WPF client with `DASHBOARD`, `CONNECTORS`, and
`SYNC HISTORY` views, plus the `status`, `sync`, `history`, `data`, and `config show` CLI commands.
History is bounded to five visible rows per selected judge and exposes typed full-sync retry for
non-success operations.
Both clients use the Windows Core SQLite ledger and accept public handles only. Windows CI runs
the full solution test/build gate, self-contained `win-x64` packaging, package hash verification,
and a temporary-data CLI/WPF smoke check. The current Windows artifact is an unsigned directory/ZIP
bundle; installer, signing, and store packaging remain separate work.
/ `windows/` 方案现在包含原生 WPF 客户端（`DASHBOARD`、`CONNECTORS`、`SYNC HISTORY`）以及
`status`、`sync`、`history`、`data`、`config show` 命令。两端共用 Windows Core SQLite 账本，
仅接受公开 handle。Windows CI 运行完整测试/构建门禁、自包含 `win-x64` 打包、包哈希校验和
临时数据 CLI/WPF 冒烟；当前 Windows 产物是未签名的目录/ZIP 包，安装包、签名和商店发布仍是后续工作。

Windows handle replacement now follows the same identity hygiene as the native clients: changing
a judge-specific handle clears only the old handle's local payload snapshots inside the account
update transaction, while retaining sync operations as history. / Windows handle 更换现在与其他原生客户端遵循相同的身份清理规则：
更换某个评测平台的 handle 时，只在账号更新事务内清理旧 handle 的本地 payload 快照，同时保留同步操作作为历史记录。

The Windows Dashboard now exposes `SYNC ALL`, which runs only configured and enabled connectors in
order and keeps each typed operation in the shared local history. / Windows Dashboard 现在提供“全部同步”，
按顺序运行已配置且启用的连接器，并将每次类型化操作保留在共享本地历史中。

## CLI EXIT CODE CONTRACT

Both command-line clients now emit the same number for the same outcome. The contract is
severity-ordered, and each platform emits only the categories it can actually produce, so a number
never means one thing on Windows and something else on Linux. / 两个命令行客户端现在对同一结果使用同一数字。
契约按严重程度排序，各端只发出自己能够产生的类别；同一个数字不会在 Windows 上表示一种含义、在 Linux 上表示另一种。

| code | contract name | Windows enum | Linux enum |
| --- | --- | --- | --- |
| 0 | success | `Success` | `Ok` |
| 1 | partial / uncategorized failure | `Partial` | `PartialFailure` |
| 2 | usage or argument error | `InvalidArguments` | `UsageError` |
| 3 | unavailable (offline, network, unobtainable resource) | `Unavailable` | `NetworkError` |
| 4 | authentication refused for public access | `Authentication` | `AuthenticationError` |
| 5 | cancelled by the user | `Cancelled` | `Cancelled` |
| 6 | local storage failure | `Storage` (declared, not emitted yet) | `StorageError` |
| 7 | unexpected internal error | `GeneralError` | `GeneralError` (declared, not emitted yet) |

Before this change the two clients disagreed on 3, 4 and 5: Linux reported an authentication limit
as 3 while Windows used 3 for `Unavailable`, so automation could not read one shared table. Windows
additionally folded an authentication limit into `Partial`. Both clients were renumbered onto the
table above, an authentication limit is now its own category on both sides, a fully cancelled batch
gets `5` on both sides instead of falling into `PartialFailure` on Linux, and the test suites on both
platforms pin every value. / 变更前两端在 3、4、5 上含义相反：Linux 把认证受限报告为 3，而 Windows 的 3 是
`Unavailable`，脚本无法只读一张表；Windows 还把认证受限并入 `Partial`。两端现已按上表重新编号，认证受限在两端都是独立类别，
整批取消在两端都是 5（此前在 Linux 会落进 `PartialFailure`），且两端测试都钉住了每个数值。

The category is derived the same way on both clients. A run-level error decides first. When a run is
only partial the run-level error is empty, so the first module that did not succeed decides it:
authentication → 4, network/offline/unsupported judge → 3, anything else → 1. Both clients were
checked against the same real run: Luogu `uid:2` reports `AUTHENTICATION` for its submissions stage
and exits 4 on Windows and on Linux, while Codeforces `tourist` exits 0 on both.
/ 类别的推导规则在两端一致：整轮错误优先；整轮只是部分失败时整轮错误为空，于是由**第一个未成功的模块**决定——
认证 → 4，网络/离线/不支持的平台 → 3，其余 → 1。两端已用同一次真实运行核对：洛谷 `uid:2` 的提交阶段报告
`AUTHENTICATION`，Windows 与 Linux 都退出 4；Codeforces `tourist` 两端都退出 0。

This is a breaking change for any local script that hard-coded the old numbers. CI expectations did
not need editing: both CI workflows only assert exit code 0 on the success path. / 对硬编码旧数字的本地脚本
属破坏性变更。CI 期望值无需修改：两个 CI 工作流都只在成功路径断言退出码 0。

## WINDOWS PACKAGING HARDENING

The self-contained `win-x64` package is rebuilt from the current CLI and Desktop sources, and it
now carries the renumbered exit-code contract. Replacing the previous package used to be
impossible on this host: the environment substitutes a guarded delete for `Remove-Item` that
reports failure even when the directory is already gone, and `package.ps1` runs with
`$ErrorActionPreference = 'Stop'`, so packaging aborted on its own cleanup step while the package
it had just built was already valid. Packaging now removes staged and temporary paths through the
.NET file APIs and verifies the resulting filesystem state, so a genuine deletion failure still
fails the run while a false report no longer does. `ui-smoke.ps1` carried the same exposure when
its screenshot directory sat inside the workspace, and was fixed the same way. / 自包含
`win-x64` 包已用当前 CLI 与 Desktop 源码重建,并已带上重新编号的退出码契约。在本机,替换旧包此前完全做不到:
该环境把 `Remove-Item` 换成带保护的删除,即使目录已经消失也会报告失败,而 `package.ps1` 以
`$ErrorActionPreference = 'Stop'` 运行,于是打包在自身的清理步骤上中断——而它刚构建出的包其实是有效的。
现在打包通过 .NET 文件 API 删除暂存与临时路径,并核对删除后的真实文件系统状态:真正的删除失败仍会让打包失败,
虚假的失败报告不再中断。`ui-smoke.ps1` 在截图目录位于工作区内时有同样的暴露面,已按相同方式修复。

The package was verified on a published artifact rather than on build output: `verify-package.ps1`
accepts all entries, the packaged CLI exits 4 for Luogu `uid:2` and 0 for Codeforces `tourist`,
and the packaged WPF client renders all three views with three distinct screenshots. / 该包是在**发布产物**上
验证的,而不是在编译输出上:`verify-package.ps1` 全部条目通过,包内 CLI 对洛谷 `uid:2` 退出 4、对 Codeforces
`tourist` 退出 0,包内 WPF 客户端渲染全部三个视图并产出三张互不相同的截图。

`windows.yml` has still never run on a real runner: the workflow exists in local commits only, and
the remote repository exposes the Android workflow alone, so the Windows job, its packaging step,
and its artifact upload remain unverified. / `windows.yml` 仍从未在真实 runner 上运行过:
该工作流只存在于本地提交中,而远程仓库仅发布了 Android 工作流,因此 Windows 任务、其打包步骤与产物上传仍未经验证。

`signtool` and `makeappx` are available with the Windows SDK, and the signing step was exercised
end to end against a certificate generated in memory; only a CA-issued code signing certificate is
missing. No installer toolchain is installed. / 本机随 Windows SDK 提供 `signtool` 与 `makeappx`,
并已用内存中生成的证书把签名步骤完整演练过一遍;缺的只有 CA 签发的代码签名证书。未安装安装包工具链。

## CURRENT PACKAGE IDENTITY

The current Android package identity is `versionName=0.3.75` and `versionCode=75`. It carries
the Phase 77 Note Index work described below; the release artifact, signing, and device
installation gates for this identity have not been re-run yet. / 当前 Android 安装包身份为
`versionName=0.3.75`、`versionCode=75`，对应下面的第 77 阶段笔记索引；该身份的发布产物、签名和设备安装门禁尚未重新执行。

## PHASE 77 — Note Index / 笔记索引

Saved local notes are now readable, searchable, and reviewable offline. The problem library gains a
third scope, `NOTE INDEX`, listing every problem whose saved notes carry text, newest note first.
Rows show the judge, public problem id, problem title, a text status, and a preview of the selected
note field. The index searches the note text of the chosen field scope, the problem title, and the
public id; it also filters by judge and by unsolved-only. Empty and filtered-to-nothing states are
distinct, and a notes row emptied field by field leaves the index. / 本地已保存的笔记现在可以离线阅读、检索和复习。
题库新增第三个范围「笔记索引」，按笔记更新时间倒序列出所有笔记非空的题目。行内展示评测平台、公开题号、题名、文字状态，
以及当前所选笔记字段的预览。索引会搜索所选字段范围、题名和公开题号，并支持按评测平台和「仅未解决」筛选。
空状态与筛选无结果状态彼此区分；逐字段清空的笔记行会退出索引。

This phase is a local read-only projection. Reading notes adds no network request, no database
migration, no Room version bump, and no new table; `problem_notes` and its problem identity join are
read through one bounded query. The screen writes nothing: filtering and previewing never rewrite or
re-sort stored notes. No main-site password, Cookie, Session, CSRF login, cloud account, cross-device
sync, local compiler, custom-input runner, background submission, or automatic POST retry is added.
Earlier phase notes and published Releases remain intact. / 本阶段是本地只读投影。读取笔记不新增网络请求、数据库迁移、
Room 版本升级或新数据表；`problem_notes` 与其题目身份的联表通过一条有界查询读取。界面不写入任何内容：筛选与预览都不会改写或重排已存笔记。
不新增主站密码、Cookie、Session、CSRF 登录、云端账号、跨设备同步、本地编译器、自定义输入运行器、后台提交或自动提交重试。
此前阶段说明和已发布 Releases 保持不变。

## PHASE 76 — Sync Operations Ledger / 同步操作账本

The connector center now projects a bounded recent history from Room, including per-module
outcomes and text status semantics. Failed stages expose an explicit retry action; adapters without
safe stage retry capability fall back to a full sync. Data-generation mismatches are recorded as
stale operations and cannot be retried against the current local dataset. CI now runs unit tests,
debug lint, release assembly, and the committed connected Android suite in a separate emulator job.
/ 连接中心现在从 Room 投影有界的近期历史，包含逐模块结果和文本状态语义；失败阶段提供明确的重试操作，不支持安全阶段重试的适配器回退到完整同步。数据代际不匹配会记录为过时操作，不能针对当前本地数据集重试。CI 现在运行单元测试、debug lint、release 构建，并在独立模拟器任务中运行已提交的连接测试套件。

Android Settings now exposes the persisted public-account lifecycle as `ENABLE` / `DISABLE`.
Disabled accounts remain visible with their local profile, receipt, and history data, are excluded
from `SYNC ALL`, and do not queue manual sync; re-enabling restores the periodic worker without
starting an unsolicited immediate refresh. / Android 设置页现在提供持久化公开账号的“启用 / 停用”生命周期控制。停用账号仍保留本地 profile、回执和历史数据，会从“全部同步”中排除且不会排队手动同步；重新启用时恢复周期 worker，但不会未经用户请求立即刷新。

Phase 75 stores optional default and per-judge difficulty targets, exposes localized calibration
controls near Focus Sprint, and ranks a bounded candidate pool from due reviews, recent unsolved
problems, failure history, and linked weak knowledge areas. Recommendations retain deterministic
reason codes and never infer a target from a missing rating. / 第 75 阶段保存可选的默认及按 OJ
难度目标，在 Focus Sprint 附近提供本地化校准控件，并从到期复习、近期未解决题、失败历史和关联薄弱知识区构建有界候选池。
推荐仍使用确定性的原因码，不会从缺失的 rating 推断目标。

Phase 74 keeps date-sensitive streams current across local midnight and app resume. Dashboard
commands now carry exact stable identifiers for sessions, tasks, reviews, submissions, and
contests; a submission request can be handed to the Submission Center as one-shot focus context.
Cells use minimum sizing so large text remains readable. / 第 74 阶段保证日期敏感数据在本地跨日和应用恢复后保持最新；Dashboard 动作携带会话、任务、复习、提交和竞赛的稳定标识，提交请求可通过一次性上下文交给提交中心聚焦；单元格使用最小尺寸，保证大字体仍可读。

## PHASE 73 — Safe Restore / 安全恢复

Database imports are validated against the current Room schema, SQLite quick/integrity checks,
and every required application table before staging. Startup recovery runs before Room opens,
uses a journaled same-directory replacement, retains WAL/SHM rollback companions, and converges
back to the old database when validation or replacement fails. Sync and Luogu result work carries
the local data generation and exits before adapter/OpenApp access when stale. Settings exposes a
dismissible localized restore outcome. / 数据库导入会在暂存前校验当前 Room schema、SQLite quick/integrity 检查以及所有必需应用表；启动恢复在 Room 打开前运行，通过带日志的同目录替换并保留 WAL/SHM 回滚副本，在验证或替换失败时回到旧数据库。同步和洛谷结果任务携带本地数据代际，过期时会在访问适配器/OpenApp 前退出；设置页显示可关闭的本地化恢复结果。

The release adds no credential or problem-content fields to backup metadata. Invalid SQLite,
schema, missing-table, integrity, replacement, and rollback failures remain stable categories. /
本版本不向备份元数据增加凭据或题面内容字段；SQLite、schema、缺表、完整性、替换和回滚失败均保持为稳定分类。

## PHASE 72 — Dashboard Command Surface / Dashboard 指挥面

Dashboard now opens with a local `NOW / NEXT / SIGNAL` surface beneath the existing command deck.
`NOW` selects the first incomplete task, `NEXT` selects the due review before an upcoming contest,
and `SIGNAL` reports sync attention, linked OJs, or local-ready state. Each actionable cell uses
an existing navigation callback; an empty next slot remains visibly non-actionable. / Dashboard 现在在已有指挥台下方展示本地“当前 / 下一步 / 信号”指挥面。“当前”选择第一项未完成任务，“下一步”优先选择到期复习，其次选择即将开始的竞赛，“信号”报告同步异常、已连接 OJ 或本地就绪状态。每个可操作单元都复用已有导航回调；没有下一步时仍明确显示不可操作状态。

The projection is derived from existing Room-backed state and adds no network request, fake
metric, credential flow, database migration, automatic submission, or looping animation. / 该投影依据已有 Room 状态生成，不新增网络请求、虚假指标、凭据流程、数据库迁移、自动提交或循环动画。

## PHASE 71 — OJ Connector Center / OJ 连接中心

Settings now starts with a consolidated connector surface for every registered judge. Each row
shows whether a public handle is connected, its persisted sync phase, receipt coverage, last
successful sync age, and active stage. `SYNC ALL` queues one explicit manual refresh per connected
judge that advertises `BACKGROUND_SYNC`; duplicate taps are ignored while the queue operation is
in flight. / 设置页现在首先展示每个已注册 OJ 的统一连接中心。每行显示公开用户名是否已连接、持久化同步阶段、回执覆盖、最近成功同步时间和当前阶段。“全部同步”会为每个声明支持 `BACKGROUND_SYNC` 的已连接 OJ 明确排队一次手动刷新；排队期间的重复点击会被忽略。

The center is a projection over existing Room data and the existing `JudgeSyncWorker` path. It
does not add credentials, cookies, main-site sessions, automatic submission, database migrations,
or background work beyond the already registered periodic worker. / 连接中心只是对已有 Room 数据和 `JudgeSyncWorker` 链路的投影，不新增凭据、Cookie、主站会话、自动提交、数据库迁移，也不增加已有周期 worker 之外的后台任务。

## PHASE 70 — Session Momentum / 训练节奏

An active training session now exposes a local `NOW / NEXT / LEFT` momentum rail. Recording a
verdict advances the ephemeral selection to the next pending queue row when one exists; `OPEN
NEXT` routes through the existing local problem detail action, and an all-resolved queue reports
completion without inventing a next item. / 活动训练会话现在提供本地“当前 / 下一题 / 剩余”训练节奏栏。记录 verdict 后，若仍有待处理题目，临时选中状态会推进到下一题；“打开下一题”复用已有的本地题目详情入口，全部解决时展示完成状态而不虚构下一题。

The projection and action guard use existing Room-backed flows and `ProblemRepository.addAttempt`.
Target time has a zero floor, failed actions keep the current selection visible, and repeated
actions are ignored while a local transaction is in flight. This phase adds no network request,
database migration, persisted selection, automatic OJ submission, credential flow, or background
work. / 训练节奏投影和动作保护复用已有 Room 流与 `ProblemRepository.addAttempt`。剩余时间不会低于零，失败动作会保留当前选中题目，本地事务进行中会忽略重复动作。本阶段不新增网络请求、数据库迁移、持久化选中状态、自动 OJ 提交、凭据流程或后台任务。

## PHASE 69 — Session Command Deck / 训练会话指挥台

An active training session now lets the user select one problem in `PROBLEM QUEUE` and expose a
`LOG RESULT` rail for the existing AC/WA/TLE/MLE/RE/CE/PE/OTHER verdicts. Each action is visibly
labeled and accessible; the selected row has an accent border and selection text, while `OPEN`
remains available as a separate route to local problem details. / 活动训练会话现在可以在“题目队列”中选中一道题，并显示已有
AC/WA/TLE/MLE/RE/CE/PE/OTHER verdict 的“记录结果”操作栏。每个操作都有可见文字和无障碍描述；选中行使用强调边框和选中说明，
“打开”仍然作为独立入口进入本地题目详情。

Verdicts reuse `ProblemRepository.addAttempt`, so Room remains the source of truth for attempt
counts, latest verdict, solved state, and the session progress pulse. The selection is ephemeral
and disappears with the running screen. This phase adds no automatic OJ submission, network
request, credential flow, schema migration, new session state, or persisted selection. /
verdict 复用 `ProblemRepository.addAttempt`，因此 Room 仍是尝试次数、最近 verdict、解决状态和会话进度脉冲的唯一数据源。
选中状态是临时的，离开活动会话页面即消失。本阶段不新增自动 OJ 提交、网络请求、凭据流程、数据库迁移、会话状态或持久化选中状态。

## PHASE 68 — Problem Library to Training Handoff / 题库到训练会话

The local problem library now exposes `BUILD FROM VIEW` below the current pulse. It opens the
existing Training form with the visible local problem IDs preselected, defaults to `PRACTICE`
and the editable `LIBRARY VIEW` tag, and keeps the existing `START` transaction as the only
session creator. Empty and remote views do not expose the action. / 本地题库现在在当前脉冲下方提供“从当前视图构建训练”，
打开已有训练表单并预选当前显示的本地题目，默认类型为 `PRACTICE`，标签为可编辑的“题库视图”；现有“开始”事务仍是唯一的会话创建入口。
空视图和远端视图不显示该操作。

This phase uses a one-shot in-memory navigation handoff and reuses the existing session form,
view model, repository, and transaction. It adds no network request, remote training flow,
database migration, credential storage, background work, compiler, or new persisted state. /
本阶段使用一次性内存导航上下文，复用现有会话表单、ViewModel、仓储和事务；不新增网络请求、远端训练流程、数据库迁移、凭据存储、后台任务、本地编译器或新的持久化状态。

## PHASE 67 — Command Palette Direct Jump / 命令面板直达

COMMANDS now recognizes deterministic local query forms such as `cf 1029e`, `atcoder abc 242g`,
`luogu p4551`, and `search segment tree`. A highlighted `DIRECT QUERY` result opens the existing
Problems library with the parsed judge and query prefilled; ordinary route commands and the
existing no-match state remain intact. / 命令面板现在识别 `cf 1029e`、`atcoder abc 242g`、
`luogu p4551` 和 `search segment tree` 等确定性的本地查询。“直达查询”结果会打开已有题库并预填解析出的 OJ 与搜索词，
普通页面命令和原有的无匹配状态保持不变。

This phase is local-only and one-shot. It reuses the existing Problems filter, adds no network
request, remote catalog access, schema migration, credential flow, compiler, or persisted search
state. / 本阶段仅限本地且一次性消费；复用已有题库筛选，不新增网络请求、远端题库访问、数据库迁移、凭据流程、本地编译器或持久化搜索状态。

## PHASE 66 — Focus Sprint / 专注冲刺

Training now derives a deterministic local focus plan from due reviews and ranked targets. The
inactive session section shows a `FOCUS SPRINT` preview with source counts and up to five problem
rows; `BUILD SPRINT` opens the existing session form pre-filled with `FOCUS`, 25 minutes, a
localized sprint tag, and the selected IDs. Users can edit the preset before the existing session
transaction starts. / 训练页现在从到期复习和排序后的训练目标中生成确定性的本地专注计划。没有活动会话时，会话区域展示“专注冲刺”预览、来源数量和最多五道题；“构建冲刺”打开已有会话表单，预填“专注”、25 分钟、本地化冲刺标签和选中题目 ID。用户可以在已有会话事务启动前编辑计划。

This phase is local-only and snapshot-based. It adds no schema migration, network request,
background work, compiler, credential flow, or new session state; no data is written until the
user confirms the existing form. / 本阶段仅限本地并基于快照；在用户确认已有表单前不会写入数据，不新增数据库迁移、网络请求、后台任务、本地编译器、凭据流程或会话状态。

## PHASE 65 — Review Run / 连续复习

Training's due-review pulse now opens a no-argument `REVIEW RUN` route. The run captures due
items once in due-time/problem-ID order, records PASS/HARD/FAIL/SKIP through the existing review
transaction, shows a progress rail, and advances explicitly after each result. Completed rows
never re-enter the current run, and errors keep the current item visible. / 训练页的到期复习脉冲现在打开无参数的“连续复习”路由；运行开始时按到期时间和题目 ID 固定候选顺序，通过现有复习事务记录 PASS/HARD/FAIL/SKIP，展示进度轨，并在每次结果后显式进入下一题。已完成题目不会重新进入当前运行，发生错误时当前题目仍保持可见。

## PHASE 64 — Session Review Actions / 会话复习操作

The terminal debrief now derives a local review-candidate set from ATTENTION and PENDING rows.
`SCHEDULE ATTENTION` inserts only missing stage-0 review rows in one transaction; existing review
rows are preserved. Room reactivity then changes each affected row to `OPEN REVIEW`, while a quiet
`REVIEW QUEUE READY` state replaces the bulk action when nothing remains to schedule. / 终端复盘现在从“需关注”和“待处理”行中计算本地复习候选；“安排需关注”在一个事务内只插入缺失的初始复习记录，不覆盖已有复习记录。Room 响应式更新会让受影响的行切换为“打开复习”，没有待排程题目时则显示安静的“复习队列已就绪”状态。

## PHASE 63 — Session Debrief / 会话复盘

Terminal sessions now expose a local `SESSION DEBRIEF` with solved, attention, and pending lanes,
latest verdicts constrained to the session window, and existing review-state signals. Filters
keep the report focused; each row routes to `OPEN REVIEW` when a review already exists, otherwise
to local problem details. / 已结束会话现在提供本地“会话复盘”，按已解决、需关注和待处理分组，最近 verdict 严格限制在会话时间窗内，并展示已有复习状态。
筛选器让报告保持聚焦；已有复习记录的题目进入“打开复习”，其他题目进入本地题目详情。

This phase is local-only and read-only. It adds no schema migration, network request, background
submission, compiler, credential flow, or new session state. / 本阶段仅限本地只读，不新增数据库迁移、网络请求、后台提交、本地编译器、凭据流程或会话状态。

## PHASE 62 — Live Session Board / 实时训练队列

Training sessions now expose a local `SESSION PULSE` with total, solved, attempted, and pending
counts, plus a determinate progress rail. The `PROBLEM QUEUE` uses the persisted session window
to show each attached problem's judge/external ID, status, attempt count, and a direct `OPEN`
route to local problem details. / 训练会话现在提供本地“会话脉冲”，展示总题数、已解决、已尝试和待处理数量，并提供确定性进度轨。
“题目队列”依据会话持久化时间窗口展示每道附加题的评测机/外部编号、状态、尝试次数，并可直接打开本地题目详情。

This phase is local-only and read-only at the progress layer. It adds no schema migration,
network request, background submission, compiler, credential flow, or new session state. / 本阶段仅限本地，进度层只读；
不新增数据库迁移、网络请求、后台提交、本地编译器、凭据流程或会话状态。

## PHASE 61 — Submission Ops Deck / 提交运营台

Submission Center now exposes manual `CHECK PENDING` and `QUEUE FAILED` actions over the existing
local request snapshot. Recent requests render as compact inspector rows by default; `DETAILS`
reveals the stored request, compile, judge, output, and resource metadata with reduced-motion-safe
expansion. / 提交中心现在基于已有本地请求快照提供手动“查询等待中”和“排队失败项”操作。最近请求默认以紧凑检查器行显示，“详情”可以展开请求、编译、评测、输出和资源元数据，并遵守减少动效设置。

Bulk actions reuse existing per-request foreground polling and recovery guards, and expansion is
screen-local. This phase adds no new jobs, network field, database migration, credential flow,
cloud service, cross-device sync, local compiler, custom-input runner, background submission, or
automatic POST retry. / 批量操作复用现有逐请求前台查询与恢复保护，展开状态仅存在于当前界面；本阶段不新增请求、网络字段、数据库迁移、凭据流程、云端服务、跨设备同步、本地编译器、自定义输入运行器、后台提交或自动提交重试。

## PHASE 60 — Workspace Execution Cockpit / 工作区执行驾驶舱

Workspace now opens with a local `WORKSPACE PULSE` for mode, language, source lines, and draft
state. The existing O2 request flag is exposed for both run and submit. Luogu detail navigation
can carry its first sample pair into Workspace, where users can load or clear sample input and
read expected output without editing it. / 工作区现在以本地“工作区脉冲”开场，展示模式、语言、源代码行数和草稿状态。
现有 O2 请求字段已同时开放给运行和提交；洛谷题目详情导航可以把第一组样例带入工作区，用户可载入或清空样例输入，并查看只读期望输出。

This phase is local-only and uses optional navigation context from the already loaded detail
screen. It adds no Workspace fetch, network field, database migration, credential storage, cloud
service, cross-device sync, local compiler, custom-input runner, background submission, or
automatic POST retry. / 本阶段仅限本地，使用已加载题目详情提供的可选导航上下文；不新增工作区抓取、网络字段、数据库迁移、凭据存储、
云端服务、跨设备同步、本地编译器、自定义输入运行器、后台提交或自动提交重试。

## PHASE 59 — Analytics Focus Lens / 分析聚焦透镜

Analytics now opens with a local `ANALYTICS PULSE` for the selected 14D, 30D, or 90D activity
window: solved, attempts, active days, and training time. The existing 365-day heatmap and
all-time distributions remain unchanged; solve and training charts follow the selected window. /
分析页现在以本地“分析脉冲”开场，可选择 14 天、30 天或 90 天活动窗口，展示已解决、尝试次数、活跃天数和训练时长。
现有 365 天热力图与全量分布保持不变，解题趋势和训练图会随窗口切换。

This phase is local-only and derives values from the existing Room/Flow activity snapshot. It
does not fabricate activity or rewrite stored data, and adds no network field, database
migration, credential flow, cloud service, cross-device sync, local compiler, custom-input
runner, background submission, or automatic POST retry. / 本阶段仅限本地，数值来自已有 Room/Flow 活动快照，
不伪造活动或改写已存数据；不新增网络字段、数据库迁移、凭据流程、云端服务、跨设备同步、本地编译器、
自定义输入运行器、后台提交或自动提交重试。

## PHASE 58 — Submission Control Tower / 提交控制塔

Submission Center now opens with a local `SUBMISSION PULSE` for total, pending, ready, and failed
request counts. Four local status filters isolate actionable history, while `CLEAR FILTER`
restores the full list without changing stored jobs. Existing checks, retries, and workspace
navigation remain intact. / 提交中心现在以本地“提交脉冲”开场，展示总数、等待中、已就绪和失败请求数量。
四个本地状态筛选可以聚焦需要处理的历史记录，“清除筛选”会恢复完整列表而不改写已存请求；现有查询、重试和工作区导航保持不变。

Summary values derive from the existing Room/Flow snapshot. Unknown statuses remain visible in
ALL, and the phase adds no network field, database migration, credential flow, cloud service,
cross-device sync, local compiler, custom-input runner, background submission, bulk retry, or
automatic POST retry. Earlier phase notes and published Releases remain intact. / 摘要来自已有 Room/Flow 快照，
未知状态在“全部”视图中继续可见。本阶段不新增网络字段、数据库迁移、凭据流程、云端服务、跨设备同步、本地编译器、
自定义输入运行器、后台提交、批量重试或自动提交重试；此前阶段说明和已发布 Release 均保持不变。

## PHASE 57 — Contest Command Center / 竞赛指挥中心

Contests now opens with a local `CONTEST PULSE` for live, upcoming, recent, and next-contest
state. Four local phase filters keep the visible groups focused, and `OPEN NEXT` opens the
earliest upcoming contest through the existing focus route. / 竞赛页现在以本地“竞赛脉冲”开场，展示进行中、即将开始、最近和下一场竞赛状态。
四个本地阶段筛选让可见分组保持聚焦，“打开下一场”通过现有 Arena 路由打开最早即将开始的竞赛。

Pulse values derive from the existing Room/Flow contest snapshot and local clock. The phase is
presentation, filtering, and navigation only: no network field, database migration, credential
flow, cloud service, cross-device sync, local compiler, custom-input runner, background
submission, or automatic POST retry is added. Earlier phase notes and published Releases remain
intact. / 脉冲数值来自已有 Room/Flow 竞赛快照和本地时钟。本阶段仅限展示、筛选和导航，不新增网络字段、数据库迁移、凭据流程、
云端服务、跨设备同步、本地编译器、自定义输入运行器、后台提交或自动提交重试；此前阶段说明和已发布 Release 均保持不变。

## PHASE 56 — Problem Library 2.0 / 题库 2.0

The local problem library now opens with a four-value pulse for total, visible, solved, and
review counts. Active filters expose `CLEAR FILTERS`, which resets both filter criteria and sort
order without touching stored problems. Existing rows gain a restrained status rail and explicit
favorite/delete accessibility actions. / 本地题库现在以四项脉冲开场，展示总数、当前显示、已解决和复习中数量。
启用筛选时显示“清除筛选”，可在不修改已存题目的情况下同时恢复筛选条件和排序。现有题目行增加克制的状态标尺，
并补充收藏/删除无障碍操作。

Counts are derived from the existing Room/Flow library stream. The pulse, reset action, and row
polish remain local-only, use reduced-motion-safe transitions, and add no network field, database
migration, credential flow, cloud service, cross-device sync, local compiler, custom-input runner,
background submission, or automatic POST retry. Earlier phase notes and published Releases remain
intact. / 数量来自已有 Room/Flow 题库流。脉冲、恢复操作和题目行优化仅限本地，并遵守减少动效设置；不新增网络字段、数据库迁移、
凭据流程、云端服务、跨设备同步、本地编译器、自定义输入运行器、后台提交或自动提交重试；此前阶段说明和已发布 Release 均保持不变。

## PHASE 55 — Review triage / 复习分诊台

Training now begins with a local review pulse for overdue, today, and later work. Three explicit
filters keep the queue readable, while `START NEXT` opens the earliest due review through the
existing problem route. Counts, empty states, and due highlighting remain honest to the current
local Room/Flow snapshot; reduced motion is respected. / 训练页现在以本地复习脉冲开场，展示逾期、今天和稍后任务。
三个明确筛选让队列保持可读；“START NEXT”通过现有题目路由打开最早到期的复习。数量、空状态和到期高亮均忠实于当前
本地 Room/Flow 快照，并遵守减少动效设置。

This phase is local-only and does not add network fields, database migrations, credential flow,
main-site passwords, Cookie, Session, CSRF state, cloud service, cross-device sync, local
compiler, custom-input runner, background submission, or automatic POST retry. Earlier phase
notes and published Releases remain intact. / 本阶段仅限本地，不新增网络字段、数据库迁移、凭据流程、主站密码、
Cookie、Session、CSRF 状态、云端服务、跨设备同步、本地编译器、自定义输入运行器、后台提交或自动提交重试；此前阶段说明和
已发布 Release 均保持不变。

## PHASE 54 — Dashboard command deck / Dashboard 指挥台

Dashboard now starts with a compact local command readout for due reviews, solved problems this
week, the next future contest countdown, and enabled judge connections. A four-cell command deck
opens the existing Training, Review, Problems, and Submission Center surfaces with explicit
accessibility descriptions. / Dashboard 现在以紧凑的本地指挥读数开场，展示待复习、本周解决题数、下一场未来竞赛倒计时和已启用的
OJ 连接。四格命令台通过明确的无障碍描述打开现有的训练、复习、题库和提交中心页面。

The readout is derived from existing Room/Flow state; a cancellable local clock tick keeps the
countdown current while subscribed. Summary changes and training-load bars use restrained motion
and immediately switch when reduce motion is enabled. This phase adds no network field, database
migration, credential flow, main-site password, Cookie, Session, CSRF state, cloud service,
cross-device sync, local compiler, custom-input runner, background submission, or automatic POST
retry. Earlier phase notes and published Releases remain intact. / 读数来自已有 Room/Flow 状态；页面订阅期间由可取消的本地时钟更新倒计时。
摘要变化和训练负荷柱使用克制的动效，启用减少动效后立即切换。本阶段不新增网络字段、数据库迁移、凭据流程、
主站密码、Cookie、Session、CSRF 状态、云服务、跨设备同步、本地编译器、自定义输入运行器、
后台提交或自动提交 POST 重试；此前阶段说明和已发布 Release 均保持不变。

## PHASE 53 — Submission title propagation / 提交题名传递

When a terminal Luogu OpenApp result materializes a new local problem row, the repository now
uses the already persisted local submission title. This keeps the title in the problem library
even when no prior problem cache exists. Existing problem rows are not overwritten, and a
missing title continues to fall back to the PID. / 洛谷 OpenApp 终态结果落库并新建本地题目记录时，现在会使用已保存的本地提交题名。
即使此前没有题目缓存，也能让题库保留题名；已有题目记录不会被覆盖，缺失题名时继续回退到 PID。

This phase changes local result materialization only. It adds no network field, credential flow,
main-site password, Cookie, Session, CSRF state, cloud service, cross-device sync, local
compiler, custom-input runner, background submission, or automatic POST retry. Earlier phase
notes and published Releases remain intact. / 本阶段仅修改本地评测结果落库，不新增网络字段、凭据流程、主站密码、Cookie、Session、CSRF 状态、
云端服务、跨设备同步、本地编译器、自定义输入运行器、后台提交或自动提交重试；此前阶段说明和已发布 Release 均保持不变。

## PHASE 52 — Legacy submission title backfill / 旧提交题名回填

Upgrading a database created before Phase 50 now fills missing local submission titles from the
cached `remote_problem_details` table first, then from the local `problems` table. Existing
titles are never overwritten; blank cached titles and records without a matching cache remain
PID-only. / 从第 50 阶段之前创建的数据库升级时，现在会优先从缓存的 `remote_problem_details` 表、再从本地 `problems` 表，
为缺失的本地提交题名回填标题。已有题名绝不会被覆盖；缓存题名为空或没有匹配缓存的记录继续仅显示 PID。

The 11→12 Room migration is local and non-destructive: it adds no network field, credential
flow, main-site password, Cookie, Session, CSRF state, cloud service, cross-device sync, local
compiler, custom-input runner, background submission, or automatic POST retry. Earlier phase
notes and published Releases remain intact. / 11→12 Room 迁移是本地、非破坏性的：不新增网络字段、凭据流程、主站密码、Cookie、
Session、CSRF 状态、云端服务、跨设备同步、本地编译器、自定义输入运行器、后台提交或自动提交重试；此前阶段说明和已发布
Release 均保持不变。

## PHASE 51 — Submission workspace title restoration / 提交工作区题名恢复

Opening a Luogu request from Submission Center now passes its locally stored public problem title
through the existing encoded workspace route. PID remains the only submission identity; null or
blank legacy titles use the existing PID-only route. / 从提交中心打开洛谷请求时，现在会通过现有编码工作区路由传递本地
保存的公开题目标题。PID 仍是唯一提交身份；旧记录中的空或空白题名继续使用仅 PID 的路由。

This phase changes local navigation/display context only. It adds no network field, database
migration, credential flow, cloud service, cross-device sync, local compiler, custom-input runner,
or automatic submission POST retry. Earlier phase notes and published Releases remain intact. /
本阶段仅修改本地导航和展示上下文，不新增网络字段、数据库迁移、凭据流程、云端服务、跨设备同步、本地编译器、
自定义输入运行器或自动提交 POST 重试；此前阶段说明和已发布 Release 均保留。

## PHASE 49 — First-use guide / 首次使用指南

The repository now includes a bilingual quick-start guide covering Release installation, public
Luogu profile synchronization, remote problem search, native details, OpenApp configuration,
foreground submission, and result recovery. It records the supported local-first boundary so a
user can operate the released APK without guessing which credential or workflow is supported. /
仓库现在提供双语首用指南，覆盖 Release 安装、洛谷公开资料同步、远端题目搜索、原生题面、OpenApp 配置、前台提交和结果恢复，
并明确记录本地优先边界，使用户无需猜测支持哪种凭据或流程即可操作已发布 APK。

This is documentation only; the v0.3.44 application behavior and all earlier phase notes,
commits, tags, and Releases remain unchanged. / 本阶段仅增加文档；v0.3.44 应用行为以及此前所有阶段说明、commit、标签和 Release 均保持不变。

## PHASE 48 — Foreground submission result convergence / 前台提交结果闭环

After an explicit Luogu Open Platform submission, the workspace immediately uses the existing
bounded foreground result poll. Terminal evaluations are rendered without a second tap; pending
or partial evaluations remain visible and recoverable through the existing result-check action.
Submission POST requests are never retried automatically. / 用户明确发起洛谷 Open Platform 提交后，工作区现在立即使用
已有的有界前台结果轮询；终态评测无需再次点击即可展示，待处理或部分评测会继续可见，并可通过原有结果查询操作恢复。
提交 POST 请求绝不自动重试。

This phase changes only the explicit foreground submission flow. It adds no background submission,
main-site password, Cookie, Session, CSRF state, cloud service, cross-device sync, local compiler,
or custom-input runner. Earlier phases, commits, tags, and releases remain preserved. /
本阶段仅修改用户明确触发的前台提交流程，不新增后台提交、主站密码、Cookie、Session、CSRF 状态、云服务、跨设备同步、
本地编译器或自定义输入运行器；此前阶段、commit、标签和 Release 均继续保留。

## PHASE 47 — Workspace problem context / 工作区题目上下文

Opening a workspace from native Luogu problem details now carries the live problem title as an
optional UTF-8 percent-encoded query value while keeping the PID as the only required identity.
The workspace displays the PID and title together; callers that only know a PID remain compatible.
/ 从原生洛谷题目详情打开工作区时，现在会携带经过 UTF-8 百分号编码的实时题名作为可选查询值，同时继续将 PID 作为唯一
必需身份。工作区会同时显示题号和题名；只知道 PID 的旧入口保持兼容。

This is a local navigation/display-context change only. It does not add main-site passwords,
Cookie, Session, CSRF state, cloud service, cross-device sync, local compiler, custom-input
runner, or automatic POST retry. Earlier phases, commits, tags, and releases remain preserved.
/ 本阶段仅修改本地导航和显示上下文，不新增主站密码、Cookie、Session、CSRF 状态、云服务、跨设备同步、本地编译器、
自定义输入运行器或自动 POST 重试；此前阶段、commit、标签和 Release 均继续保留。

## PHASE 46 — Luogu sample-pair compatibility / 洛谷样例对兼容

Live Luogu problem details may encode samples as nested `[input, output]` pairs, while older
fixtures use a flat string list. The network DTO now accepts both shapes and flattens nested
pairs into the existing ordered domain/cache contract, so native detail rendering can show real
sample content. / 洛谷实时题目详情可能将样例编码为嵌套的 `[输入, 输出]` 样例对，而旧 fixture 使用扁平字符串
列表。网络 DTO 现在同时接受两种格式，并将嵌套样例对按原顺序展开为现有领域/缓存契约，使原生详情页能够显示真实样例内容。

This is a parsing compatibility fix only. It does not add main-site passwords, Cookie, Session,
CSRF state, cloud service, cross-device sync, local compiler, custom-input runner, or automatic
POST retry. Earlier phases, commits, tags, and releases remain preserved. / 本阶段仅修复解析兼容性，不新增主站
密码、Cookie、Session、CSRF 状态、云服务、跨设备同步、本地编译器、自定义输入运行器或自动 POST 重试；此前阶段、
commit、标签和 Release 均继续保留。

## PHASE 45 — Transaction-safe OpenApp credential replacement / 事务安全的 OpenApp 凭据更换

Configured users can replace an OpenApp credential from a blank editor. The candidate is checked
with the read-only `quotaAvailable` endpoint using candidate Basic authorization before the local
Keystore-backed store is changed. Unauthorized, forbidden, network, API, and persistence failures
keep the existing credential active; `CANCEL` leaves the stored value untouched. / 已配置用户可以
通过空白编辑器更换 OpenApp 凭据。候选凭据会使用候选 Basic Auth 调用只读的 `quotaAvailable` 接口，
验证成功后才修改本地 Keystore 存储；未授权、禁止访问、网络、API 或持久化失败都会保留现有凭据，
`取消` 操作不会改动已保存值。

This phase does not add main-site passwords, Cookie, Session, CSRF state, cloud service,
cross-device sync, local compiler, custom-input runner, background submission, or automatic POST
retry. Earlier phases, commits, tags, and releases remain preserved. / 本阶段不新增主站密码、Cookie、
Session、CSRF 状态、云服务、跨设备同步、本地编译器、自定义输入运行器、后台提交或自动 POST 重试；
此前阶段、commit、标签和 Release 均继续保留。

## PHASE 44 — OpenApp credential input validation / OpenApp 凭据输入校验

OpenApp user and secret input is trimmed and validated locally before any Keystore write or
read-only quota verification. Missing fields receive specific localized feedback; valid input
continues through the existing secure store and verification flow. / OpenApp 用户和密钥输入现在会在任何
Keystore 写入或只读额度验证前去除首尾空白并本地校验；缺失字段显示对应的本地化提示，合法输入继续复用已有的安全存储和验证流程。

This phase does not expose stored secrets and does not add in-place replacement, main-site
password, Cookie, Session, CSRF state, cloud service, cross-device sync, local compiler,
custom-input runner, or automatic POST retry. Earlier Release, tag, and notes remain preserved.
/ 本阶段不显示已保存密钥，不新增原地更换、主站密码、Cookie、Session、CSRF 状态、云服务、跨设备同步、
本地编译器、自定义输入运行器或自动 POST 重试；此前 Release、标签和说明继续保留。

## PHASE 43 — Luogu settings focus correction / 洛谷设置定位修正

The Dashboard `CONNECT LUOGU` route now scrolls the existing Settings screen to the Luogu
public-account panel. OpenApp focus and ordinary Settings navigation remain unchanged; the
coordinate guard now covers both supported focused routes. / Dashboard 的“连接洛谷”入口现在会将已有
设置页滚动到洛谷公开账号面板。OpenApp 聚焦和普通设置导航保持不变；坐标守卫现在覆盖两个受支持的聚焦入口。

This is a local navigation correction only. It adds no network request, credential type,
main-site password, Cookie, Session, CSRF state, cloud service, cross-device sync, local
compiler, custom-input runner, or automatic POST retry. Earlier Release, tag, and notes remain
preserved. / 本阶段仅修正本地导航，不新增网络请求、凭据类型、主站密码、Cookie、Session、CSRF 状态、
云服务、跨设备同步、本地编译器、自定义输入运行器或自动 POST 重试；此前 Release、标签和说明继续保留。

## PHASE 42 — OpenApp action intent clarity / OpenApp 操作意图明确化

The Luogu workspace primary action now displays `SUBMIT` when the actual OpenApp gateway submits
code, and `RUN` only for a gateway whose capability explicitly supports custom-input execution.
Busy work continues to display `WORKING`; submission callbacks, language forwarding, result
polling, and persistence are unchanged. / 洛谷工作区主操作现在在真实 OpenApp 网关提交代码时显示
`SUBMIT`，只有明确支持自定义输入运行的网关才显示 `RUN`。忙碌时仍显示 `WORKING`；提交回调、语言传递、
结果查询和持久化保持不变。

This is an intent-clarity correction only. It adds no local compiler, custom-input runner,
main-site password, Cookie, Session, CSRF state, cloud service, cross-device sync, or automatic
POST retry. Earlier Release, tag, and notes remain preserved. / 本阶段仅明确操作意图，不新增本地编译器、自定义
输入运行器、主站密码、Cookie、Session、CSRF 状态、云服务、跨设备同步或自动 POST 重试；此前 Release、
标签和说明继续保留。

## PHASE 41 — Luogu first-use public sync loop / 洛谷首次使用公开同步闭环

Dashboard now shows a localized `CONNECT LUOGU` action whenever no enabled Luogu account is
connected. The action opens the dedicated `settings/luogu` route, which scrolls the existing
Settings screen to the Luogu public-account panel; ordinary Settings and the OpenApp focus route
remain unchanged. / 当没有启用的洛谷账号时，Dashboard 现在显示本地化的“连接洛谷”操作。该操作打开专用的
`settings/luogu` 路由，将现有设置页滚动到洛谷公开账号面板；普通设置和 OpenApp 聚焦路由保持不变。

The flow reuses the existing public handle connector, sync queue, profile/Rating/contest/problem
stages, localized errors, and sync receipt. It adds no main-site password, Cookie, Session, CSRF
state, cloud service, cross-device sync, local compiler, custom-input runner, or automatic POST
retry. / 此流程复用已有公开用户名连接器、同步队列、资料/Rating/竞赛/题库阶段、本地化错误和同步回执，
不新增主站密码、Cookie、Session、CSRF 状态、云服务、跨设备同步、本地编译器、自定义输入运行器或自动
POST 重试。此前 Release、tag 和说明继续保留。

## PHASE 40 — Installable Release build / 可安装 Release 构建

The GitHub distribution is now built from the Android `release` variant rather than the Debug
variant. It is signed locally for direct installation with the machine's standard Android debug
keystore, while `BuildConfig.DEBUG=false` keeps development-only Demo controls out of the APK.
The keystore is never committed. / GitHub 分发现在使用 Android `release` 变体，而不是 Debug 变体构建。
产物使用本机标准 Android debug keystore 进行本地签名以支持直接安装，同时 `BuildConfig.DEBUG=false`
确保开发专用 Demo 控件不会进入 APK。密钥库不会提交到仓库。

The release identity is `v0.3.36`, `versionName=0.3.36`, and `versionCode=36`. A SHA-256 checksum
asset accompanies the APK; earlier Debug releases, tags, and notes remain preserved. / 本次发布身份为
`v0.3.36`、`versionName=0.3.36`、`versionCode=36`。APK 同时附带 SHA-256 校验文件；此前 Debug
Release、标签和说明继续保留。

## PHASE 39 — Luogu result query alignment / 洛谷结果查询对齐

Luogu Open Platform result polling now uses the documented `GET /judge/result?id=<RequestId>`
query form instead of embedding the request ID in the path. The contract test locks the exact
request shape while preserving 204 Pending, partial-result handling, terminal-result handling,
WebSocket wake-up, and local recovery. / 洛谷 Open Platform 结果轮询现在使用文档规定的
`GET /judge/result?id=<RequestId>` query 形式，不再把请求 ID 放在路径中。契约测试锁定准确请求形式，
同时保留 204 Pending、部分结果处理、终态结果处理、WebSocket 唤醒和本地恢复。

This is an endpoint-shape correction only. No new login mode, main-site password, Cookie, Session,
CSRF state, cloud service, local compiler, custom-input runner, automatic POST retry, or public
submission-history import is added. / 本阶段仅修正接口请求形式，不新增登录模式、主站密码、Cookie、Session、
CSRF 状态、云端服务、本地编译器、自定义输入运行器、自动 POST 重试或公开提交历史导入。

## PHASE 38 — Release identity / 发布版本身份

The Android APK now carries `versionName=0.3.34` and `versionCode=34`, matching the `v0.3.34`
GitHub Release identity. This makes the installed package, release page, and upgrade metadata
describe the same build while preserving all earlier tags, releases, and notes. / Android APK
现在携带 `versionName=0.3.34` 和 `versionCode=34`，与 `v0.3.34` GitHub Release 身份一致。
这样已安装包、发布页面和升级元数据描述同一个构建，同时保留此前所有标签、Release 和说明。

This is a release-metadata and documentation change only. No Luogu endpoint, credential flow,
database migration, cloud service, cross-device sync, local compiler, custom-input runner, or
automatic submission retry is added. / 本阶段仅修改发布元数据和文档，不新增洛谷接口、凭据流程、数据库迁移、
云端服务、跨设备同步、本地编译器、自定义输入运行器或自动提交重试。

## PHASE 0 — Foundation ✅ (this milestone)
Gradle/AGP 9 toolchain, design system (tokens + core components), app shell (edge-to-edge,
navigation, bottom bar), five skeleton screens (Dashboard / Problems / Training / Analytics /
Profile) rendering labeled development sample data, domain enums (`JudgeId`, `Verdict`,
`KnowledgeArea`, `TrainingType`), unit tests, CI, docs.

## PHASE 1 — Local Training Core ✅
Room v1 (problems/tags/attempts/failures/notes/reviews/tasks/sessions), repositories, manual DI,
ViewModels + `Loadable` UI states. Local problem library with search/filter/sort/add/edit/
delete/favorite, problem detail (attempts, failure log, debounced notes, review actions,
browser open), review scheduler (1/3/7/21/45/90d + PASS/HARD/FAIL/SKIP), review queue with
OVERDUE/DUE TODAY/UPCOMING, TODAY tasks, training sessions (create/run/pause/resume/finish/
summary/history, process-death safe), heatmap + analytics from real local data with empty
states, dashboard over local data only (no fake ratings), debug-only demo seeder. 64 unit tests
including Robolectric DAO tests. No external OJ APIs — by design.

## PHASE 2 — Codeforces ✅
First judge adapter is implemented with the official public API, centralized request spacing,
bounded retry/error mapping, Room v2 migration, public-handle binding, rejudge-safe incremental
submissions, remote problem catalog, contests, local-first UI, and unique WorkManager sync.
The branch is locally complete; push/PR/CI remain separate release actions requiring explicit
authorization.

## PHASE 3 — Multi-OJ + AtCoder ✅
Judge-independent adapter/registry/sync contracts, Room v3 migration, AtCoder Problems
transport and mapping, soft public-handle binding, timestamp-cursor submission sync, catalog
and contest caching, per-judge WorkManager identity, and judge-labelled local-first UI.
The branch is locally complete; push/PR/CI remain separate release actions requiring explicit
authorization.

## PHASE 4 — Analytics ✅ (current milestone)
Heatmap tap-through day detail, verdict/difficulty distributions, Codeforces rating chart,
solve/training trends, first-try AC rate, weak-tag performance, and per-judge difficulty
breakdowns — all computed from local data and drawn with Compose. Knowledge distribution waits
for the problem-knowledge relation in Phase 6 rather than inventing data.

## PHASE 5 — Arena ✅ (current milestone)
Contest center now opens a live/upcoming Arena focus view with a ticking countdown, cached
problem tracker, local-only marker cycle, and submission progress joined from local attempts.
Contest and problem links use Custom Tabs; no scraping, auto-submit, passwords, or cookies.
See [docs/ARENA.md](ARENA.md).

## PHASE 6 — Knowledge + Mastery ✅ (current milestone)
Explicit problem-knowledge relations, Room v5 migration, complete knowledge-tree display in
Training, SQL evidence aggregation, and explainable deterministic Mastery Engine with reason
codes are implemented. Problem detail edits relations, and Training now displays a real local
candidate feed ranked by the pure candidate-level `TrainingPlanner`. See
[docs/KNOWLEDGE.md](KNOWLEDGE.md).

## PHASE 7 — Achievements + Player Card ✅ (current milestone)
Deterministic local achievement unlocks, Profile Player Card achievement display, and verified
token-colored PNG sharing through `FileProvider` are implemented.

## PHASE 8 — Polish + Performance + Tests ✅
Settings now exports a verified copy of the local Room database through the Android document
picker, and reduce-motion/haptics preferences persist through DataStore. The export contains
local study data only and never requires credentials. A global command palette now searches
local navigation and study actions without network access. Database backups can be imported,
schema-validated, and restored before the next app start. The visual system now exposes three
named dark accent slots while preserving one accent per theme. The bounded-feed audit and
repository coverage are recorded in [docs/PERFORMANCE.md](PERFORMANCE.md). The phase is locally
complete; publishing remains a separate release action requiring explicit authorization.

## PHASE 9 — Luogu public sync ✅

Luogu public profile, rating/ELO history, paginated problem catalog, and paginated contest
catalog are synchronized through a typed content-only JSON transport into local Room v6.
Manual and WorkManager sync use bounded retries, rate spacing, freshness timestamps, idempotent
upserts, per-page persistence, and partial-result reporting. Anonymous submission records are
explicitly `AUTH_REQUIRED` and never fabricated. The implementation is locally verified;
publishing remains a separate release action requiring explicit authorization.

## PHASE 10 — Authorized submission workflow ✅ (safe local slice)

The first slice uses the official Luogu Open Platform HTTP Basic API: local Keystore-protected
OpenApp credentials, Compose code workspace, explicit foreground `/judge/problem` action,
and user-triggered `/judge/result/{id}` polling. POST requests are not automatically retried, and the
workspace persists only request metadata, restores the latest local task after a restart, and
materializes terminal user-originated results as idempotent local attempts; it does not persist
source code or standard input. No plaintext main-site passwords, harvested browser cookies,
background submissions, WebView shell, local bundled compiler, or cloud synchronization is
permitted. Local Android runtime verification with a real OpenApp credential remains separate
from the unit-test/build verification. Main-site login, background automation, custom-input execution,
local compilation,
and cloud/cross-device sync remain intentionally out of scope for this safe slice.

The local submission center is now included in this slice. It lists recent Open Platform request
metadata from Room, shows pending/ready/failed state and available evaluation metadata, lets the
user manually query pending or failed requests, and reopens the related problem workspace. It is
reachable from Profile and the command palette; the five primary bottom-bar destinations remain
unchanged. The center is local-only and does not turn anonymous Luogu history into fabricated
submissions. Settings also provides a user-triggered foreground query of Open Platform available
quota points; the response is transient UI state only and is not persisted or synchronized.
Settings also links to the official OpenApp documentation next to the credential form so users can
verify the credential source before configuring it.
The workspace editor also exposes the supported Open Platform language identifiers and forwards
the selected language in each explicit submit request. The remote problem catalog now exposes
LUOGU alongside Codeforces and AtCoder and maps saved Luogu problems to their canonical URLs.
Remote catalog rows also provide a direct canonical source-page action before a problem is added
to the local library; Luogu rows also open the Open Platform workspace directly.
Analytics renders each judge's rating history independently, and Profile includes the Luogu
connection and current public rating when available. Contest Center now exposes a Luogu filter,
and the Luogu Arena header/problem actions open canonical Luogu contest and problem pages. Luogu
contest listings remain metadata-only until a supported public contest-problem endpoint is verified;
the app does not invent contest membership from unrelated problem catalog rows.

## PHASE 11 — Luogu native problem details / 洛谷原生题目详情

Remote Luogu rows can now open a native Compose detail screen backed by the public
`problem/{pid}` content-only response. The screen handles loading and failure states, renders
safe native blocks for headings, paragraphs, lists, quotes, code, and dividers, and shows samples
and first-level time/memory limits. / 洛谷远端题库条目现在可以打开由公开
`problem/{pid}` content-only 响应驱动的原生 Compose 详情页。页面处理加载和失败状态，使用原生
组件安全展示标题、段落、列表、引用、代码和分隔线，并展示样例以及首组时间/内存限制。

The source-page and local Open Platform workspace actions remain explicit foreground actions;
the detail screen does not embed a WebView or store remote content in the cloud. / 原题页面和本地
Open Platform 工作区仍必须由用户前台主动点击；详情页不嵌入 WebView，也不把远端内容存入云端。

## PHASE 12 — Luogu Arena contest details / 洛谷 Arena 竞赛详情

Luogu Arena now reads the public `contest/{id}` content-only response when the selected judge is
Luogu. It displays the official contest description, participant count, and the server-provided
`contestProblems` membership with score, index, PID, and title. / 选择洛谷时，Arena 现在读取公开的
`contest/{id}` content-only 响应，展示官方竞赛说明、参赛人数，以及服务器返回的
`contestProblems` 题目成员关系、分值、编号、PID 和题名。

The app does not infer membership from unrelated catalog rows; each listed problem comes from the
contest detail payload and can be opened explicitly on the canonical Luogu page. / 应用不会从无关
题库行推断竞赛归属；列表中的每道题都来自竞赛详情响应，并可由用户主动打开标准洛谷页面。

## PHASE 13 — Luogu on-demand problem search / 洛谷按需题库搜索

The remote problem catalog remains Room-first. When a non-blank Luogu keyword has no local hit,
the app requests the matching public `problem/list?keyword=...` page, maps the response through the
judge boundary, and upserts it into the local catalog. This avoids requiring a user to wait for the
entire public catalog before the first search. / 远端题库仍然坚持 Room 优先；当非空洛谷关键词在
本地没有命中时，应用才请求公开的 `problem/list?keyword=...` 页面，经 OJ 边界映射后写入本地
题库，从而不要求用户等待完整公开题库同步结束后才能首次搜索。

Blank queries remain local-only; later pages use the same public keyword endpoint on demand and are
cached page by page. The Settings panel also exposes the current background sync stage so a long
bounded catalog refresh is observable. / 空关键词仍只读本地缓存；后续分页会按需使用同一公开
关键词接口并逐页写入缓存。设置页同时显示后台同步当前阶段，使较长的有界题库刷新过程可见。

## PHASE 14 — Observable sync and paged Luogu search / 可见同步与洛谷分页搜索

The page provider now receives the requested offset, so a cached first page can be followed by
on-demand retrieval of page two and beyond. Room remains the source returned to the UI after each
upsert, preserving offline reads and solved-state joins. Settings renders the persisted sync stage
alongside SYNCING. / 分页提供者现在接收用户请求的 offset，因此首屏缓存后可以继续按需获取第二页
及更多页面；每次写入后仍由 Room 返回 UI，保留离线读取和已解决状态关联。设置页在 SYNCING
状态旁显示已持久化的同步阶段。
The provider is public-data-only and does not add Luogu main-site passwords, cookies, sessions,
CSRF state, cloud accounts, or cross-device synchronization. / 空关键词和后续分页目前仍只读
本地缓存，未来如需扩展会单独定义分页策略；本阶段仍只使用公开数据，不新增洛谷主站密码、
Cookie、Session、CSRF、云端账号或跨设备同步。

## PHASE 15 — First-use catalog guidance / 首次使用题库提示

When the remote catalog has no cached rows, the empty state now tells the user to enter a keyword.
It no longer claims that an OJ account is required, which matches the public Luogu keyword path;
other judges still remain local-cache-only until their own provider is registered. / 远端题库没有
缓存条目时，空状态现在提示用户输入关键词，不再声称必须连接 OJ 账号，这与公开洛谷关键词
路径一致；其他 OJ 在注册各自提供者前仍只读取本地缓存。

## PHASE 16 — Bidirectional problem scope navigation / 题库范围双向导航

The problem scope switcher now wires both local-library and remote-catalog transitions, so the
remote view can return directly to the local library without leaving the Problems screen. The
navigation boundary is covered by a focused unit test. / 题库范围切换器现在同时接通本地题库和远端
题库的切换回调，用户无需离开题库页面即可从远端视图直接返回本地题库，并由聚焦单元测试覆盖
该导航边界。

This is a UI/navigation correction only; it does not add main-site passwords, cookies, sessions,
CSRF state, cloud accounts, or cross-device synchronization. / 本阶段仅修正 UI 导航，不新增洛谷主站
密码、Cookie、Session、CSRF 状态、云端账号或跨设备同步。

## PHASE 17 — Luogu public profile surface / 洛谷公开资料展示

The Profile screen now reads the synchronized Luogu `JudgeProfileEntity` snapshot and exposes its
public ranking, passed/submitted problem counts, follower/following counts, slogan, and
introduction. A clear empty state is used when no Luogu public snapshot is available, and the
mapping rejects profiles from other judges. / Profile 页面现在读取已同步的洛谷
`JudgeProfileEntity` 快照，展示公开排名、通过题数、提交题数、粉丝、关注、签名和简介；没有洛谷
公开快照时显示明确空状态，映射也会拒绝其他 OJ 的资料。

The phase is presentation-only over the existing local Room data. It does not add main-site
passwords, cookies, sessions, CSRF state, cloud accounts, or cross-device synchronization. /
本阶段只在现有本地 Room 数据之上增加展示，不新增洛谷主站密码、Cookie、Session、CSRF 状态、
云端账号或跨设备同步。

## PHASE 18 — Foreground OpenApp result polling / 前台 OpenApp 结果轮询

After the user explicitly submits a Luogu problem through the Open Platform, the workspace now
polls the result in the foreground for a bounded window. It stops after a terminal result or a
fixed number of Pending responses, leaving the request visibly pending for a later manual check.
POST submission is still never retried automatically, and no background submit worker is added. /
用户通过 Open Platform 明确提交洛谷题目后，工作区现在会在前台有限轮询结果；遇到终态结果或达到
固定次数的 Pending 响应后停止，并将仍在等待的请求明确保留，用户之后可以再次手动查询。POST 提交
仍不会自动重试，也不会新增后台提交 Worker。

The feature remains local-first and uses the existing Keystore-backed OpenApp credential boundary;
it does not add main-site passwords, cookies, sessions, CSRF state, cloud accounts, or
cross-device synchronization. / 本功能仍为本地优先，沿用现有 Keystore 保护的 OpenApp 凭据边界，
不新增主站密码、Cookie、Session、CSRF 状态、云端账号或跨设备同步。

## PHASE 22 — Partial Luogu result convergence / 洛谷部分评测结果收敛

Luogu Open Platform results now distinguish HTTP 204 Pending, HTTP 200 InProgress, and terminal
Ready results. A non-terminal 200 response remains local `PENDING`, keeps its latest compile/judge/run
details, and continues through the bounded foreground poll window; only a terminal result creates or
updates a finished local attempt. / 洛谷 Open Platform 结果现在区分 HTTP 204 Pending、HTTP 200
InProgress 和终态 Ready。非终态 200 响应保持本地 `PENDING`，保存最新的编译/评测/运行详情，并继续
进入前台有界轮询；只有终态结果才会创建或更新完成的本地提交记录。

The HTTP result remains authoritative after an optional official WebSocket wake-up signal. This phase
continues the local-first, OpenApp-only boundary: no Luogu main-site password, cookies, sessions, CSRF
state, cloud account, cross-device sync, background submission, automatic POST retry, local compiler,
or custom-input execution is added. Previous phase notes and published Releases remain unchanged. /
可选的官方 WebSocket 唤醒信号之后，HTTP 结果仍是唯一权威来源。本阶段继续保持本地优先和仅使用
OpenApp 的边界：不新增洛谷主站密码、Cookie、Session、CSRF 状态、云端账号、跨设备同步、后台提交、
POST 自动重试、本地编译器或自定义输入运行；此前阶段说明和已发布 Releases 保持不变。

## PHASE 21 — Workspace result continuity / 工作区结果连续性

Reopening a Luogu workspace now restores all locally persisted OpenApp evaluation details: compile
success/message, output, exit code, execution time, and memory. The workspace renders an explicit
localized compile outcome even when the upstream compiler message is empty. / 重新打开洛谷工作区
现在会恢复本地保存的 OpenApp 评测详情：编译成功/信息、输出、退出码、运行时间和内存；即使
上游编译信息为空，工作区也会明确展示本地化的编译结果。

This phase changes only local state restoration and foreground presentation. It keeps the existing
local-first boundary and does not add main-site passwords, cookies, sessions, CSRF state, cloud
accounts, cross-device sync, background submission, automatic POST retry, a local compiler, or
custom-input execution. Previous phase notes and published Releases remain unchanged. /
本阶段只改进本地状态恢复和前台展示，继续保持本地优先边界，不新增主站密码、Cookie、Session、
CSRF 状态、云端账号、跨设备同步、后台提交、POST 自动重试、本地编译器或自定义输入运行；此前
阶段说明和已发布 Releases 保持不变。

## PHASE 20 — OpenApp evaluation usability / OpenApp 评测可用性

Foreground OpenApp result checks can now wait for the official WebSocket `judge.result` notification
as an optional wake-up signal. The notification never becomes a second authority: the existing
authenticated HTTP result response still determines persistence and status, and bounded HTTP polling
continues when the socket is unavailable or silent. / 前台 OpenApp 结果查询现在可以等待官方
WebSocket 的 `judge.result` 通知作为可选唤醒信号。通知不会成为第二个权威来源：仍由原有鉴权
HTTP 结果响应决定持久化和状态；Socket 不可用或没有通知时继续有限 HTTP 轮询。

Room v8 adds nullable compile success/message, output, exit code, execution time, and memory fields
to local submission jobs. The submission center renders available values and leaves missing upstream
fields absent, so a user can inspect the same evaluation snapshot after navigation or process restart.
/ Room v8 为本地提交任务增加可空的编译成功/信息、输出、退出码、运行时间和内存字段。提交
中心展示上游实际返回的值，缺失字段保持为空，使用户在跳转或进程重启后仍能查看相同评测快照。

This phase remains foreground-only and local-first. It does not add Luogu main-site login, passwords,
cookies, sessions, CSRF state, cloud accounts, cross-device sync, automatic POST retry, a local
compiler, or custom-input execution. Previous phase notes and published Releases remain unchanged.
/ 本阶段仍仅限前台和本地优先，不新增洛谷主站登录、密码、Cookie、Session、CSRF 状态、云端账号、
跨设备同步、POST 自动重试、本地编译器或自定义输入运行。此前阶段说明和已发布 Releases 保持不变。

## PHASE 19 — Submission center result polling / 提交中心结果轮询

The local submission center now shares the bounded foreground result polling helper with the problem
workspace. A user can query a pending OpenApp request from either the workspace or the submission center
and receive the same terminal-result or bounded-Pending behavior. / 本地提交中心现在与题目工作区共享前台
有限结果轮询工具，用户可从任一入口查询 Pending 的 OpenApp 请求，并获得一致的终态结果或有限 Pending 行为。

The helper only performs foreground GET result checks; POST submissions remain explicit and are never
automatically retried. No main-site passwords, cookies, sessions, CSRF state, cloud accounts, or
cross-device sync are added. / 该工具只在前台执行 GET 结果查询；POST 提交仍需明确触发且不会自动重试。
不新增主站密码、Cookie、Session、CSRF 状态、云端账号或跨设备同步。

## PHASE 23 — OpenApp credential verification / OpenApp 凭据连接测试

Saving a Luogu OpenApp credential now performs an immediate read-only `quotaAvailable` verification.
Success moves the Settings panel to the configured state and shows available points. Authorization
rejection clears the rejected credential; network or other API failures preserve the Keystore value
and expose a retryable error. / 保存洛谷 OpenApp 凭据后，现在会立即进行只读 `quotaAvailable` 验证。
成功后设置页进入已配置状态并展示可用计费点；鉴权拒绝会清除被拒绝的凭据，网络或其他 API 失败则
保留 Keystore 中的值并显示可重试错误。

This phase keeps the existing OpenApp-only, Keystore-backed, local-first boundary. It adds no Luogu
main-site password, Cookie, Session, CSRF login, cloud account, cross-device sync, background
submission, or automatic POST retry. Previous phase notes and published Releases remain unchanged. /
本阶段继续保持仅使用 OpenApp、Keystore 保护和本地优先的边界。不新增洛谷主站密码、Cookie、Session、
CSRF 登录、云端账号、跨设备同步、后台提交或 POST 自动重试；此前阶段说明和已发布 Releases 保持不变。

## PHASE 24 — Official problemset dump import / 官方题库导入

Full local Luogu problem synchronization now prefers the official streamed gzip NDJSON export at
`https://cdn.luogu.com.cn/problemset-open/latest.ndjson.gz`. The parser reads one JSON problem per
line, maps the catalog fields needed by local search, and writes Room rows in bounded batches.
Adapters that do not support this export retain the previous paginated endpoint as a fallback. /
洛谷完整本地题库同步现在优先使用官方流式 gzip NDJSON 导出
`https://cdn.luogu.com.cn/problemset-open/latest.ndjson.gz`。解析器逐行读取 JSON 题目，映射本地搜索
所需的题库字段，并以有界批次写入 Room。不支持该导出的适配器继续保留此前的分页接口作为回退。

Malformed nonblank lines and decompression failures surface as parse errors instead of claiming a
complete catalog. Problem details remain on-demand; the phase stays public, local-first, and
OpenApp-only, with no main-site password, cookies, sessions, CSRF login, cloud account,
cross-device sync, background submission, or automatic POST retry. Previous phase notes and
published Releases remain unchanged. / 非空行格式错误和解压失败会作为解析错误报告，不会误报题库已
完整同步。题面详情仍按需获取；本阶段继续保持公开数据、本地优先和仅 OpenApp，不新增主站密码、
Cookie、Session、CSRF 登录、云端账号、跨设备同步、后台提交或 POST 自动重试；此前阶段说明和已发布
Releases 保持不变。

## PHASE 25 — Offline-first Luogu problem details / 本地优先的洛谷题目详情

Native Luogu problem details now use a new Room v9 cache keyed by judge and problem id. Opening a
problem reads the cached public content-only snapshot first; a cache miss fetches and persists the
network detail. An explicit refresh replaces the snapshot, and network/timeout failures can return
the cached detail with a visible retryable warning. HTTP, authentication, and parse errors are not
hidden by stale data. / 原生洛谷题目详情现在使用 Room v9 缓存，按评测平台和题号隔离。打开题目时
优先读取已缓存的公开 content-only 快照；没有缓存时请求网络并持久化。显式刷新会替换快照，网络/超时
失败时可以返回缓存并明确提示可重试；HTTP、鉴权和解析错误不会被旧数据掩盖。

The existing safe native Markdown renderer remains the presentation boundary. This phase does not
bulk-import all detail fields from the official problemset dump and adds no Luogu main-site
password, cookies, sessions, CSRF login, cloud account, cross-device sync, background submission,
or automatic POST retry. Earlier phase notes and published Releases remain unchanged. /
现有安全原生 Markdown 渲染器仍是展示边界。本阶段不从官方题库导出批量导入全部题面详情，不新增
洛谷主站密码、Cookie、Session、CSRF 登录、云端账号、跨设备同步、后台提交或 POST 自动重试；此前
阶段说明和已发布 Releases 保持不变。

## PHASE 26 — Foreground Luogu public catalog action / 前台洛谷公开题库操作

The remote Problems scope now exposes an explicit Luogu `SYNC CATALOG` action. It can import the
public catalog without a connected account, reuses the official-dump/paged-fallback writer, keeps
Room as the read source, reports syncing and imported-item results, suppresses duplicate starts,
and preserves rows already written by bounded batches when a run fails. / 远端题库范围现在提供显式
洛谷“同步题库”操作。它无需连接账号即可导入公开题库，复用官方导出/分页回退写入逻辑，仍以 Room
为读取源，显示同步中和导入数量结果，禁止重复启动；同步失败时保留有界批次已经写入的题目。

The standalone public run preserves an existing account association in `sync_states`, but does not
start background work. It remains public-data-only, local-first, and OpenApp-only; no main-site
password, cookies, sessions, CSRF login, cloud account, cross-device sync, or automatic POST retry
is added. Earlier phase notes and published Releases remain unchanged. / 独立公开同步会保留
`sync_states` 中已有的账号关联，但不会创建后台任务；本阶段仍只使用公开数据、本地优先且仅
OpenApp，不新增主站密码、Cookie、Session、CSRF 登录、云端账号、跨设备同步或 POST 自动重试；此前
阶段说明和已发布 Releases 保持不变。

## PHASE 27 — Local Luogu workspace drafts / 本地洛谷工作区草稿

The native Luogu workspace now persists one Room draft per `(judge, pid)` with source code, optional
input, selected language, and O2 state. It restores on open, uses a 300 ms latest-state debounce,
shows loading/saving/saved/error state, and flushes the latest edit before page or system back.
草稿按 `(评测平台、题号)` 保存在 Room 中，打开工作区时恢复，编辑采用 300 毫秒最新状态防抖，并显示
加载中、保存中、已保存或错误；页面返回和系统返回前会先保存最新编辑。

Schema 10 adds only the `workspace_drafts` table, so existing data and historical migrations remain
intact. Drafts are included in the local Room backup, while `submission_jobs` still excludes source
code and standard input. This remains local-first and OpenApp-only: no Luogu main-site password,
Cookie, Session, CSRF login, cloud account, cross-device sync, local compiler, custom-input runner,
or automatic POST retry is added. / Schema 10 只新增 `workspace_drafts` 表，既有数据和历史迁移保持不变；
草稿随本地 Room 备份处理，而 `submission_jobs` 仍不保存源代码和标准输入。本阶段继续本地优先、仅
OpenApp，不新增洛谷主站密码、Cookie、Session、CSRF 登录、云端账号、跨设备同步、本地编译器、自定义
输入运行器或自动提交重试。

## PHASE 28 — Luogu OpenApp background result convergence / 洛谷 OpenApp 结果后台收敛

After local submission metadata is persisted, each Luogu OpenApp request now enters a unique
WorkManager result job. The worker performs only official `GET /judge/result/{requestId}`, requires
network connectivity, uses bounded exponential retry, and reconciles at most 50 pending local jobs
when the app starts. Existing foreground notification and polling remain unchanged. / 洛谷 OpenApp
提交元数据落盘后，每个请求现在加入唯一的 WorkManager 结果任务；Worker 只执行官方
`GET /judge/result/{requestId}`，要求网络连接，使用有界指数退避，并在应用启动时最多恢复 50 个本地
待处理任务。已有前台通知和轮询保持不变。

Transient network and retryable HTTP errors leave the local job pending; permanent credential,
permission, resource, and malformed-response errors are visible as local failures. The worker input
contains only the request ID and never source code, standard input, passwords, cookies, sessions, or
CSRF state. No background submission, POST retry, local compiler, custom-input runner, cloud account,
or cross-device sync is added. Earlier phase notes and published Releases remain intact. /
瞬时网络和可重试 HTTP 错误会让本地任务保持待处理；永久凭据、权限、资源和格式错误会显示为本地失败。
Worker 输入只包含 Request ID，绝不包含源代码、标准输入、密码、Cookie、Session 或 CSRF 状态。不新增后台
提交、POST 重试、本地编译器、自定义输入运行器、云端账号或跨设备同步；此前阶段说明和已发布 Releases 保持不变。

## PHASE 29 — Submission center manual recovery / 提交中心手动恢复

The submission center now provides explicit `QUEUE CHECK` for pending requests and `QUEUE RETRY`
for failed requests. Each action creates an immediate, unique local WorkManager result job without
replacing the normal delayed queue; the existing bounded foreground `CHECK RESULT` poll remains
available. / 提交中心现在为待处理请求提供“排队查询”，为失败请求提供“重新排队”。每次操作都会创建
立即执行的本地唯一 WorkManager 结果任务，不替换正常的延迟队列；现有有界前台“查询结果”轮询仍然可用。

The recovery button only sends the request ID to the official GET result worker. WorkManager keeps
the connected-network constraint and `KEEP` uniqueness, while scheduler errors stay visible as a
local action error. No submission is created, no POST is retried, and no main-site password,
Cookie, Session, CSRF login, cloud account, cross-device sync, local compiler, or custom-input
runner is added. Earlier phase notes and Releases remain intact. / 恢复按钮只向官方 GET 结果 Worker
传递 Request ID。WorkManager 保持联网约束和 `KEEP` 唯一策略，调度错误会作为本地操作错误显示。
该操作不会创建提交，也不会重试 POST；不新增主站密码、Cookie、Session、CSRF 登录、云端账号、
跨设备同步、本地编译器或自定义输入运行器；此前阶段说明和 Releases 保持不变。

## PHASE 30 — Background sync startup reconciliation / 后台同步启动校准

Existing active accounts now have their six-hour periodic `JudgeSyncWorker` work reconciled at
application startup. The pure bootstrap filters the registered judges by the real
`BACKGROUND_SYNC` capability, skips missing or disabled accounts, and isolates individual
scheduler failures so one platform cannot block another. / 应用启动时现在会为已有活跃账号校准六小时
周期 `JudgeSyncWorker` 任务。纯校准器按已注册评测平台的真实 `BACKGROUND_SYNC` 能力筛选，跳过缺失或
已禁用账号，并隔离单个平台的调度异常，避免阻塞其他平台。

Settings exposes `BACKGROUND SYNC ENABLED — EVERY 6 HOURS` for connected capable judges. The
startup pass restores scheduling only; it does not perform an immediate sync or create submission
work. No main-site password, Cookie, Session, CSRF login, cloud account, cross-device sync, local
compiler, or custom-input runner is added. Earlier phase notes and Releases remain intact. /
设置页会为已连接且具备能力的评测平台显示“后台同步已启用 — 每 6 小时”。启动校准只恢复调度，不会
立即执行同步，也不会创建提交任务。不新增主站密码、Cookie、Session、CSRF 登录、云端账号、跨设备同步、
本地编译器或自定义输入运行器；此前阶段说明和 Releases 保持不变。

## PHASE 31 — Luogu public sync boundary / 洛谷公开同步边界

Luogu account synchronization now runs only the public stages advertised by its adapter:
profile, rating, contests, and problems. The coordinator no longer calls the unsupported
submission-record endpoint, so a successful public run is recorded as `SUCCESS` instead of
being made `PARTIAL` by a known authorization failure. / 洛谷账号同步现在只执行适配器声明的公开
资料、Rating、竞赛和题库阶段。协调器不再调用未支持的提交记录接口，因此公开同步成功时会记录为
`SUCCESS`，不再因已知鉴权失败被人为标记为 `PARTIAL`。

Private submission history remains unsupported by the public adapter and no fabricated attempts are
created. Local Luogu OpenApp submission jobs remain a separate explicit workflow. No main-site
password, Cookie, Session, CSRF login, cloud account, cross-device sync, local compiler, or
custom-input runner is added. Earlier phase notes and Releases remain intact. / 私有提交历史仍不属于
公开适配器，不会创建伪造提交记录；本地洛谷 OpenApp 提交任务仍是独立的明确操作流程。不新增主站
密码、Cookie、Session、CSRF 登录、云端账号、跨设备同步、本地编译器或自定义输入运行器；此前阶段
说明和 Releases 保持不变。

## PHASE 32 — Luogu workspace first-use setup / 洛谷工作区首次配置入口

When a Luogu workspace has no OpenApp credential, it now keeps the existing warning and renders a
localized, accessible `OPEN SETTINGS / 打开设置` action. The action is routed by `NexusApp` to the
existing Settings destination, while the workspace execution action remains disabled until a
credential is configured. / 未配置洛谷 OpenApp 凭据时，工作区现在保留原有警告，并显示本地化、可访问的
“打开设置”操作。该操作由 `NexusApp` 导航到已有设置页；完成凭据配置前，工作区执行操作仍保持禁用。

This is a local navigation improvement only. It makes no network request and adds no main-site
password, Cookie, Session, CSRF login, cloud account, cross-device sync, local compiler, custom-input
runner, or automatic submission retry. Earlier phase notes and published Releases remain intact. /
本阶段仅改善本地导航，不发起网络请求，不新增主站密码、Cookie、Session、CSRF 登录、云端账号、跨设备
同步、本地编译器、自定义输入运行器或自动提交重试；此前阶段说明和已发布 Releases 保持不变。

## PHASE 33 — Luogu OpenApp setup focus / 洛谷 OpenApp 设置定位

The workspace setup action now uses a dedicated `settings/openapp` destination. After the route
is laid out, the Settings screen calculates the OpenApp section position relative to its scroll
viewport and moves it into view; the user, secret, and save controls are visible without manual
searching. Ordinary `settings` navigation remains top-aligned. / 工作区配置操作现在使用独立的
`settings/openapp` 路由。设置页完成布局后，会计算 OpenApp 区域相对于滚动视口的位置并自动定位；用户无需
手动寻找即可看到用户名、密钥和保存控件。普通 `settings` 入口仍从顶部开始。

The phase is local navigation only and does not make a network request. No Luogu main-site
password, Cookie, Session, CSRF login, cloud account, cross-device sync, local compiler,
custom-input runner, or automatic submission retry is added. Earlier phase notes and published
Releases remain intact. / 本阶段仅涉及本地导航，不发起网络请求，不新增洛谷主站密码、Cookie、Session、
CSRF 登录、云端账号、跨设备同步、本地编译器、自定义输入运行器或自动提交重试；此前阶段说明和已发布
Releases 保持不变。

## PHASE 34 — Public sync queue visibility / 公开同步队列可见性

Public judge sync now writes `QUEUED` to the existing local `sync_states` row before manual
WorkManager scheduling. This makes the first sync after binding a Luogu public handle and every
manual `SYNC NOW` request visible even while the connected-network constraint delays execution.
The existing worker still owns the transition to `SYNCING` and terminal `SUCCESS`, `PARTIAL`, or
`ERROR` states. / 公开评测平台同步现在会在手动 WorkManager 调度前，将 `QUEUED` 写入已有的本地
`sync_states` 记录。绑定洛谷公开用户名后的首次同步，以及每次手动“立即同步”，即使因联网约束暂缓执行，
也会立即可见。现有 Worker 仍负责转换到 `SYNCING` 以及最终的 `SUCCESS`、`PARTIAL` 或 `ERROR` 状态。

Settings displays the queued state, active stage, and stable localized error categories for known
rate-limit, account, network, timeout, server, and generic API failures. Raw server messages are
not rendered. This phase remains local-first and public-data-only: no main-site password, Cookie,
Session, CSRF login, cloud account, cross-device sync, local compiler, custom-input runner, or
automatic submission retry is added. Earlier phase notes and published Releases remain intact. /
设置页会显示排队状态、当前阶段，以及请求受限、账号、网络、超时、服务器和通用 API 错误的稳定本地化分类，
不直接渲染服务器原始消息。本阶段继续本地优先和仅公开数据：不新增主站密码、Cookie、Session、CSRF 登录、
云端账号、跨设备同步、本地编译器、自定义输入运行器或自动提交重试；此前阶段说明和已发布 Releases 保持不变。

## PHASE 36 — Luogu synced data visibility / 洛谷同步数据可见性

Analytics now treats a non-empty synchronized Rating history as content even when local
attempts and problems are both zero. A user who has just synchronized Luogu Rating history can
therefore see the per-judge Rating section instead of the generic empty state. / Analytics 现在将非空的
同步 Rating 历史视为有效内容，即使本地提交和题目数量都为零。用户刚完成洛谷 Rating 同步后，可以看到按
评测平台划分的 Rating 区域，而不会误进入通用空态。

Profile’s rated-contest summary is now judge-independent: it shows the aggregate count from
Codeforces, AtCoder, or Luogu whenever present, and a neutral no-history label otherwise. The
obsolete Phase 2 guidance is no longer shown to users. / Profile 的 Rated 竞赛摘要现在不依赖具体评测平台：
只要 Codeforces、AtCoder 或洛谷任一方有数据，就显示汇总数量；否则显示中性的无历史文案，不再向用户显示过期
的 Phase 2 指引。

This remains a local presentation change. No main-site password, Cookie, Session, CSRF login,
cloud account, cross-device sync, local compiler, custom-input runner, or automatic submission
retry is added. Earlier phase notes and published Releases remain intact. / 本阶段仅改变本地展示，不新增主站
密码、Cookie、Session、CSRF 登录、云端账号、跨设备同步、本地编译器、自定义输入运行器或自动提交重试；此前阶段
说明和已发布 Releases 保持不变。

## PHASE 35 — Luogu Rating history fallback / 洛谷 Rating 历史回退

The Luogu public sync now selects a non-empty practice `elo` list and otherwise falls back to
the public user-page `elo` list. This handles real accounts whose practice page returns an empty
array while the profile page still exposes Rating history, preserving the existing idempotent
Room import. / 洛谷公开同步现在优先选择非空的 practice `elo` 列表，否则回退到公开用户主页的 `elo` 列表。
这样可以兼容 practice 页面返回空数组、而资料页仍提供 Rating 历史的真实账号，并继续使用已有的 Room
幂等导入。

The phase adds no endpoint or credential flow and remains public-data-only. No main-site password,
Cookie, Session, CSRF login, cloud account, cross-device sync, local compiler, custom-input runner,
or automatic submission retry is added. Earlier phase notes and published Releases remain intact. /
## PHASE 37 — Sync receipt / 同步回执

Settings now renders a localized sync receipt for every connected judge. The receipt is built from
the adapter's declared capabilities and the existing per-module Room timestamps, so it lists only
real profile, Rating, submissions, contest, and problemset modules. Each supported module shows
`NEVER SYNCED` or a relative refresh age. / 设置页现在为每个已连接评测平台显示本地化同步回执。回执由适配器
声明的能力和现有 Room 模块时间戳生成，因此只列出真实的资料、Rating、提交、竞赛和题库模块；每个支持的模块
显示“从未同步”或相对更新时间。

Queued and active runs keep the last stamped module time, and a failed stage never receives a
fresh timestamp. Luogu's public capability set still does not claim private submission history;
OpenApp submission remains a separate explicit workflow. This phase adds no endpoint, database
migration, credential flow, cloud service, cross-device sync, local compiler, custom-input runner,
or automatic POST retry. Earlier phase notes and published Releases remain intact. /
排队或同步中的任务继续显示模块上一次已记录的时间，失败阶段不会获得新的时间戳。洛谷公开能力仍不声明私有提交
历史；OpenApp 提交保持独立的明确流程。本阶段不新增接口、数据库迁移、凭据流程、云端服务、跨设备同步、本地编译器、
自定义输入运行器或自动 POST 重试；此前阶段说明和 Releases 保持不变。
本阶段不新增接口或凭据流程，继续只使用公开数据。不新增主站密码、Cookie、Session、CSRF 登录、云端账号、
跨设备同步、本地编译器、自定义输入运行器或自动提交重试；此前阶段说明和已发布 Releases 保持不变。
## PHASE 50 — Submission title context / 提交题名上下文

Local Open Platform submission history now retains a nullable public problem title when a
workspace submission already has one. The submission center shows the title beside the PID and
keeps a separate title metadata line; legacy rows remain readable with PID-only context. The
title is local display metadata only and is deliberately excluded from the official Open
Platform request DTO. / 本地 Open Platform 提交历史现在会在工作区已有题名时保存可空的公开题目标题。
提交中心会在 PID 旁显示题名，并保留独立的题名元数据行；旧记录仍可仅凭 PID 正常显示。题名只是本地展示元数据，
会明确排除在官方 Open Platform 请求 DTO 之外。

Room migration 10→11 adds the nullable `submission_jobs.title` column without deleting existing
requests. This phase remains local-first and explicit: no main-site password, Cookie, Session,
CSRF login, cloud account, cross-device sync, local compiler, custom-input runner, or automatic
POST retry is added. Earlier phase notes and published Releases remain intact. / Room 10→11 迁移新增可空的
`submission_jobs.title` 列，不删除已有请求。本阶段继续本地优先和明确操作：不新增主站密码、Cookie、Session、CSRF 登录、
云端账号、跨设备同步、本地编译器、自定义输入运行器或自动 POST 重试；此前阶段说明和已发布 Release 均保留。
