# OJ NEXUS

Competitive Programming Command Center for Android.

A native client for competitive programmers (Codeforces, AtCoder, Luogu, and more) that
unifies OJ accounts, submissions, rating, problems, contests, training sessions, review,
mastery, and analytics into one dark, telemetry-style tool.

## Principles

- **Multi-OJ** — every judge is an isolated adapter; the core stays judge-agnostic.
- **Local First** — history, notes, review, and stats work fully offline; the network syncs.
- **Training & Review** — deterministic recommendation and spaced review with explainable
  reasons, not "AI suggestions".
- **Analytics** — heatmap, distributions, and trends computed from your own data.
- **Contest** — unified contest list with countdowns; a focus view for live rounds.

## Tech Stack

Kotlin · Jetpack Compose · Material 3 · Navigation Compose · Room · Coroutines/Flow ·
WorkManager · Retrofit/OkHttp · kotlinx.serialization

First-use guide: [docs/QUICK_START.md](docs/QUICK_START.md) / 首次使用指南：
[docs/QUICK_START.md](docs/QUICK_START.md)

## Status

Early development — **Phase 71 (OJ Connector Center)**: Settings now exposes a consolidated
connector surface for every registered judge, with honest connection state, sync receipt coverage,
last-success age, active stage, and an explicit `SYNC ALL` action. The action reuses the real
Room-backed WorkManager sync path and skips disconnected or unsupported judges. The APK identity
is aligned to `versionName=0.3.69`, `versionCode=69`. / 早期开发——**第 71 阶段（OJ 连接中心）**：设置页现在为每个已注册 OJ 提供统一连接中心，展示真实连接状态、同步回执覆盖、最近成功时间、当前阶段，并提供明确的“全部同步”操作。
该操作复用真实的 Room + WorkManager 同步链路，跳过未连接或不支持同步的 OJ。APK 版本身份同步为 `versionName=0.3.69`、`versionCode=69`。

Phase 71 keeps user control explicit: `SYNC ALL` only queues the existing foreground worker for
connected adapters that advertise `BACKGROUND_SYNC`; it does not add passwords, cookies, main-site
sessions, automatic submissions, or new persistence. / 第 71 阶段继续保持明确的用户控制：“全部同步”只为已连接且声明支持后台同步的适配器排队现有前台 worker；不新增密码、Cookie、主站会话、自动提交或持久化结构。

Phase 70 remains local-first: quick verdicts are existing local attempt records, not automatic
OJ submissions. No network, credentials, database migration, new session state, or persisted
selection was added. / 第 70 阶段继续坚持本地优先：快速 verdict 只是已有的本地尝试记录，不是自动提交到 OJ；不新增网络、凭据、数据库迁移、会话状态或持久化选中状态。

Phase 68 remains local-first: the visible local problem IDs are held only as one-shot navigation
context and passed into the existing editable session form. No network, remote training,
schema migration, credential storage, compiler, or new persisted data was added. / 第 68 阶段继续坚持本地优先：
当前本地题目 ID 仅作为一次性导航上下文传入已有可编辑会话表单；不新增网络、远端训练、数据库迁移、凭据存储、本地编译器或持久化数据。

Phase 67 remains local-first: direct queries reuse the existing Problems filter and are consumed
once by the library screen. No network, remote catalog, schema migration, credential storage,
compiler, or new persisted data was added. / 第 67 阶段继续坚持本地优先：直达查询复用已有题库筛选，并由题库页面一次性消费；不新增网络、远端题库、数据库迁移、凭据存储、本地编译器或持久化数据。

Phase 66 remains local-first: the plan is a snapshot and only the existing session transaction
creates data after confirmation; no migration, network request, background work, compiler,
credential storage, or new session state was added. / 第 66 阶段继续坚持本地优先：计划是本地快照，只有确认后才通过已有会话事务写入数据；不新增迁移、网络请求、后台任务、本地编译器、凭据存储或会话状态。

Phase 65 remains local-first: no database migration, network request, background work, compiler,
credential storage, or new review result was added. / 第 65 阶段继续坚持本地优先：不新增数据库迁移、网络请求、后台任务、本地编译器、凭据存储或复习结果类型。

Phase 65 remains local-first: no database migration, network request, background work, compiler,
credential storage, or new review result was added. / 第 65 阶段继续坚持本地优先：不新增数据库迁移、网络请求、后台任务、本地编译器、凭据存储或复习结果类型。

Phase 63 is a local read-only debrief and navigation surface. It adds no database migration,
network request, background submission, compiler, credential storage, or new session state. / 第 63 阶段仅增加本地只读复盘与导航展示，
不新增数据库迁移、网络请求、后台提交、本地编译器、凭据存储或会话状态。

Phase 62 is a local read-only progress and navigation surface. It adds no database migration,
network request, background submission, compiler, credential storage, or new session state. / 第 62 阶段仅增加本地只读进度与导航展示，
不新增数据库迁移、网络请求、后台提交、本地编译器、凭据存储或会话状态。

Phase 61 is a local presentation and manual-operation change. Bulk actions snapshot existing
request IDs and reuse the current foreground polling and recovery scheduler; no new jobs,
database migration, network field, credential storage, main-site password, Cookie, Session, CSRF
state, cloud service, cross-device sync, local compiler, custom-input runner, background
submission, or automatic POST retry is added. / 第 61 阶段仅改变本地展示和手动操作；批量操作读取已有请求 ID 快照并复用当前前台查询与恢复调度器，不新增请求、数据库迁移、网络字段、凭据存储、主站密码、Cookie、Session、CSRF 状态、云服务、跨设备同步、本地编译器、自定义输入运行器、后台提交或自动提交重试。

Early development — **Phase 60 (Workspace Execution Cockpit)**: Workspace now opens with a local
`WORKSPACE PULSE` for mode, language, source lines, and draft state. The O2 optimization flag is
now visible and wired to both run and submit requests. When a Luogu problem detail has a sample
pair, the Workspace carries it in as optional context, offers `LOAD SAMPLE` and `CLEAR INPUT`,
and shows read-only `EXPECTED OUTPUT`. The APK identity is aligned to `versionName=0.3.56`,
`versionCode=56`. / 早期开发——**第 60 阶段（工作区执行驾驶舱）**：工作区现在以本地“工作区脉冲”开场，
展示模式、语言、源代码行数和草稿状态。O2 优化开关已可见，并同时接入运行与提交请求；从洛谷题目详情进入且存在样例对时，
工作区会带入可选样例上下文，提供“载入样例”“清空输入”，并以只读方式展示“期望输出”。APK 版本身份同步为
`versionName=0.3.56`、`versionCode=56`。

Phase 60 is a local presentation and navigation-context change. Sample values come from the
already loaded Luogu detail screen; no Workspace fetch, database migration, network field,
credential storage, main-site password, Cookie, Session, CSRF state, cloud service, cross-device
sync, local compiler, custom-input runner, background submission, or automatic POST retry is
added. Existing drafts, requests, polling, and result semantics remain intact. / 第 60 阶段仅改变本地展示与导航上下文；
样例值来自已经加载的洛谷题目详情，工作区不会新增抓取、数据库迁移、网络字段、凭据存储、主站密码、Cookie、Session、CSRF 状态、
云服务、跨设备同步、本地编译器、自定义输入运行器、后台提交或自动提交重试。现有草稿、请求、轮询和结果语义保持不变。

Early development — **Phase 59 (Analytics Focus Lens)**: Analytics now opens with a local
`ANALYTICS PULSE` for the selected 14D, 30D, or 90D activity window, covering solved, attempts,
active days, and training time. The existing 365-day heatmap and all-time distributions remain
unchanged, while the solve and training charts follow the selected window. The APK identity is
aligned to `versionName=0.3.55`, `versionCode=55`. / 早期开发——**第 59 阶段（分析聚焦透镜）**：
分析页现在以本地“分析脉冲”开场，可选择 14 天、30 天或 90 天活动窗口，展示已解决、尝试次数、活跃天数和训练时长。
现有 365 天热力图与全量分布保持不变，解题趋势和训练图会随窗口切换。APK 版本身份同步为
`versionName=0.3.55`、`versionCode=55`。

Phase 59 is a local presentation and window-selection change. Summary values derive from the
existing Room/Flow activity snapshot; no activity is fabricated and no stored data is rewritten.
It adds no network fields, database migration, main-site passwords, Cookie, Session, CSRF state,
cloud service, cross-device sync, local compiler, custom-input runner, background submission,
or automatic POST retry. Earlier phases, commits, tags, and releases remain preserved. /
第 59 阶段仅改变本地展示和活动窗口选择；摘要来自已有 Room/Flow 活动快照，不伪造活动，也不改写已存数据。
不新增网络字段、数据库迁移、主站密码、Cookie、Session、CSRF 状态、云服务、跨设备同步、本地编译器、
自定义输入运行器、后台提交或自动提交 POST 重试。此前阶段、commit、标签和 Release 均继续保留。

Early development — **Phase 58 (Submission Control Tower)**: Submission Center now opens with a
local `SUBMISSION PULSE` showing total, pending, ready, and failed request counts. Local status
filters isolate actionable history, and `CLEAR FILTER` restores the full view without changing
stored jobs. Existing result checks, retries, and workspace navigation remain intact. The APK
identity is aligned to `versionName=0.3.54`, `versionCode=54`. / 早期开发——**第 58 阶段（提交控制塔）**：
提交中心现在以本地“提交脉冲”开场，展示总数、等待中、已就绪和失败请求数量。本地状态筛选可以聚焦需要处理的历史记录，
“清除筛选”会恢复完整视图而不改写已存请求。现有结果查询、重试和工作区导航保持不变。APK 版本身份同步为
`versionName=0.3.54`、`versionCode=54`。

Phase 58 is a local presentation and list-filtering change. Summary values derive from the
existing Room/Flow snapshot; unknown statuses remain visible in ALL and no bulk retry or new
request is created. It adds no network fields, database migration, main-site passwords, Cookie,
Session, CSRF state, cloud service, cross-device sync, local compiler, custom-input runner,
background submission, or automatic POST retry. Earlier phases, commits, tags, and releases
remain preserved. / 第 58 阶段仅改变本地展示和列表筛选；摘要来自已有 Room/Flow 快照，未知状态在“全部”视图中继续可见，
不会批量重试或创建新请求。不新增网络字段、数据库迁移、主站密码、Cookie、Session、CSRF 状态、云服务、跨设备同步、
本地编译器、自定义输入运行器、后台提交或自动提交 POST 重试。此前阶段、commit、标签和 Release 均继续保留。

Early development — **Phase 57 (Contest Command Center)**: Contests now opens with a local
`CONTEST PULSE` showing live, upcoming, recent, and next-contest state. Local phase filters keep
the list focused, while `OPEN NEXT` opens the earliest upcoming contest through the existing
focus route. The APK identity is aligned to `versionName=0.3.53`, `versionCode=53`. /
早期开发——**第 57 阶段（竞赛指挥中心）**：竞赛页现在以本地“竞赛脉冲”开场，展示进行中、即将开始、最近和下一场竞赛状态。
本地阶段筛选让列表保持聚焦，“打开下一场”通过现有 Arena 路由打开最早即将开始的竞赛。APK 版本身份同步为
`versionName=0.3.53`、`versionCode=53`。

Phase 57 is a local presentation, filtering, and navigation change. Pulse values derive from
the existing Room/Flow contest snapshot and local clock; no contest is fabricated when the
upcoming list is empty. It adds no network fields, database migration, main-site passwords,
Cookie, Session, CSRF state, cloud service, cross-device sync, local compiler, custom-input
runner, background submission, or automatic POST retry. Earlier phases, commits, tags, and
releases remain preserved. / 第 57 阶段仅改变本地展示、筛选和导航；脉冲来自已有 Room/Flow 竞赛快照与本地时钟，
即将开始列表为空时不会伪造竞赛。不新增网络字段、数据库迁移、主站密码、Cookie、Session、CSRF 状态、云服务、跨设备同步、
本地编译器、自定义输入运行器、后台提交或自动提交 POST 重试。此前阶段、commit、标签和 Release 均继续保留。

Early development — **Phase 56 (Problem Library 2.0)**: Problems now opens with a local library
pulse showing total, visible, solved, and review counts. Active filters expose a real `CLEAR
FILTERS` action that restores the default updated ordering, while problem rows gain a status rail
and explicit favorite/delete accessibility actions. The APK identity is aligned to
`versionName=0.3.52`, `versionCode=52`. / 早期开发——**第 56 阶段（题库 2.0）**：题库现在以本地题库脉冲开场，
展示总数、当前显示、已解决和复习中数量。启用筛选时会显示真实的“清除筛选”操作，恢复默认更新时间排序；题目行增加状态标尺，
并补充收藏/删除无障碍操作。APK 版本身份同步为 `versionName=0.3.52`、`versionCode=52`。

Phase 56 is a local presentation and state-reset change. Summary values derive from existing
Room/Flow state, and clearing filters changes only ViewModel filter/sort state; no problem data is
deleted or rewritten. It adds no network fields, database migration, main-site passwords, Cookie,
Session, CSRF state, cloud service, cross-device sync, local compiler, custom-input runner,
background submission, or automatic POST retry. Earlier phases, commits, tags, and releases
remain preserved. / 第 56 阶段仅改变本地展示和状态恢复；摘要来自已有 Room/Flow 状态，清除筛选只改变 ViewModel 的筛选/排序状态，
不会删除或改写题目数据。不新增网络字段、数据库迁移、主站密码、Cookie、Session、CSRF 状态、云服务、跨设备同步、
本地编译器、自定义输入运行器、后台提交或自动提交 POST 重试。此前阶段、commit、标签和 Release 均继续保留。

Early development — **Phase 55 (Review triage)**: Training now opens with a local review pulse
showing overdue, today, and later counts, three filters for all/due-now/upcoming items, and a
real `START NEXT` action that opens the earliest due review. The queue keeps its existing rows,
navigation, and empty states; reduced motion is respected. The APK identity is aligned to
`versionName=0.3.51`, `versionCode=51`. / 早期开发——**第 55 阶段（复习分诊台）**：训练页新增本地复习脉冲，
展示逾期、今天和稍后数量，提供全部、现在到期和即将到期三个筛选，并用真实的“开始下一题”打开最早到期的复习。
队列继续保留原有行、导航和空状态，并遵守减少动效设置。APK 版本身份同步为
`versionName=0.3.51`、`versionCode=51`。

Phase 55 is a local presentation and navigation change. Counts and next-item selection are
derived from existing Room/Flow state; no review record is created by the pulse. It adds no
network fields, database migration, main-site passwords, Cookie, Session, CSRF state, cloud
service, cross-device sync, local compiler, custom-input runner, background submission, or
automatic POST retry. Earlier phases, commits, tags, and releases remain preserved. /
第 55 阶段仅改变本地展示和导航；数量与下一题选择来自已有 Room/Flow 状态，脉冲不会创建复习记录。
不新增网络字段、数据库迁移、主站密码、Cookie、Session、CSRF 状态、云服务、跨设备同步、
本地编译器、自定义输入运行器、后台提交或自动提交 POST 重试。此前阶段、commit、标签和 Release 均继续保留。

Early development — **Phase 54 (Dashboard command deck)**: Dashboard now opens with a compact
local readout for due reviews, weekly solved count, the next contest countdown, and connected
judges. Four accessible command cells open the existing Training, Review, Problems, and Submission
Center surfaces. The APK identity is aligned to `versionName=0.3.49`, `versionCode=49`. / 早期开发——**第 54 阶段（Dashboard 指挥台）**：Dashboard
现在以紧凑的本地读数开场，展示待复习、本周解决题数、下一场竞赛倒计时和已连接 OJ 数量。
四个可访问的命令单元格可以打开现有的训练、复习、题库和提交中心页面。APK 版本身份同步为
`versionName=0.3.49`、`versionCode=49`。

Phase 54 is a local presentation and navigation change. The summary is derived from existing
Room/Flow state, the contest countdown refreshes from a cancellable local clock tick, and reduced
motion is respected. It adds no network fields, database migration, main-site passwords, Cookie,
Session, CSRF state, cloud service, cross-device sync, local compiler, custom-input runner,
background submission, or automatic POST retry. Earlier phases, commits, tags, and releases remain
preserved. / 第 54 阶段仅改变本地展示和导航；摘要来自已有 Room/Flow 状态，竞赛倒计时由可取消的本地时钟更新，
并遵守减少动效设置。不新增网络字段、数据库迁移、主站密码、Cookie、Session、CSRF 状态、
云服务、跨设备同步、本地编译器、自定义输入运行器、后台提交或自动提交 POST 重试。此前阶段、
commit、标签和 Release 均继续保留。

Early development — **Phase 53 (submission title propagation)**: when a terminal Luogu OpenApp
submission creates a missing local problem row, the saved local submission title is now used
instead of degrading the library title to the PID. Existing problem rows remain untouched, and
missing titles still fall back safely to the PID. The APK identity is aligned to
`versionName=0.3.48`, `versionCode=48`. / 早期开发——**第 53 阶段（提交题名传递）**：洛谷 OpenApp 提交完成并需要新建本地题目记录时，
现在会使用本地提交记录中的题名，不再把题库标题降级成 PID。已有题目记录不会被覆盖；缺失题名时仍安全回退到 PID。APK 版本身份同步为
`versionName=0.3.48`、`versionCode=48`。

Phase 53 is a local result-materialization change only. It does not add network fields,
main-site passwords, Cookie, Session, CSRF state, cloud service, cross-device sync, local
compiler, custom-input runner, background submission, or automatic POST retry. Earlier phases,
commits, tags, and releases remain preserved. / 第 53 阶段仅改变本地评测结果落库，不新增网络字段、主站密码、Cookie、Session、CSRF 状态、
云服务、跨设备同步、本地编译器、自定义输入运行器、后台提交或自动提交 POST 重试。此前阶段、commit、标签和 Release 均继续保留。

Early development — **Phase 52 (legacy submission title backfill)**: upgrading from the
pre-title schema now backfills missing local submission titles from cached remote problem
details first, then the local problem library. Existing titles are untouched, and records with
no matching cache remain safely PID-only. The APK identity is aligned to `versionName=0.3.47`,
`versionCode=47`. / 早期开发——**第 52 阶段（旧提交题名回填）**：从旧版本升级时，现在会优先从本地缓存的
远程题面详情、再从本地题库，为缺失题名的提交记录回填标题。已有题名不会被覆盖；找不到缓存的记录安全地保持仅 PID 显示。
APK 版本身份同步为 `versionName=0.3.47`、`versionCode=47`。

Phase 52 is a local, non-destructive Room migration only. It does not add network fields,
main-site passwords, Cookie, Session, CSRF state, cloud service, cross-device sync, local
compiler, custom-input runner, background submission, or automatic POST retry. Earlier phases,
commits, tags, and releases remain preserved. / 第 52 阶段仅是本地、非破坏性的 Room 数据库迁移，不新增网络字段、
主站密码、Cookie、Session、CSRF 状态、云服务、跨设备同步、本地编译器、自定义输入运行器、后台提交或自动提交 POST 重试。
此前阶段、commit、标签和 Release 均继续保留。

Early development — **Phase 51 (submission workspace title restoration)**: reopening a Luogu
workspace from Submission Center now carries the locally stored problem title through the
existing encoded route. PID remains the only submission identity; blank or legacy titles keep
the PID-only route. The APK identity is aligned to `versionName=0.3.46`, `versionCode=46`. /
早期开发——**第 51 阶段（提交工作区题名恢复）**：从提交中心重新打开洛谷工作区时，现在会通过现有编码路由继续携带
本地保存的题目标题。PID 仍是唯一提交身份；空题名或旧记录继续使用仅 PID 的路由。APK 版本身份同步为
`versionName=0.3.46`、`versionCode=46`。

Phase 51 is a local navigation/display-context change only. It does not add network fields,
database migrations, main-site passwords, Cookie, Session, CSRF state, cloud service,
cross-device sync, local compiler, custom-input runner, or automatic submission POST retry.
Earlier phases, commits, tags, and releases remain preserved. / 第 51 阶段仅改变本地导航和展示上下文，不新增网络字段、
数据库迁移、主站密码、Cookie、Session、CSRF 状态、云服务、跨设备同步、本地编译器、自定义输入运行器或自动提交 POST 重试。
此前阶段、commit、标签和 Release 均继续保留。

Early development — **Phase 50 (submission title context)**: local submission history now keeps
the public problem title beside the PID when the workspace already knows it. PID remains the
only submission identity, old rows migrate with a nullable title, and the title is excluded from
the official Open Platform request DTO. The APK identity is aligned to `versionName=0.3.45`,
`versionCode=45`. / 早期开发——**第 50 阶段（提交题名上下文）**：本地提交历史现在会在工作区已知题名时，
在 PID 旁保存并显示公开题目标题。PID 仍是唯一提交身份；旧记录通过可空标题字段迁移，题名不会进入官方
Open Platform 请求 DTO。APK 版本身份同步为 `versionName=0.3.45`、`versionCode=45`。

Phase 50 is a local display-context and additive migration change. It does not add background
submission, main-site passwords, Cookie, Session, CSRF state, cloud service, cross-device sync,
local compiler, custom-input runner, or automatic POST retry. Earlier phases, commits, tags, and
releases remain preserved. / 第 50 阶段仅增加本地展示上下文和非破坏性数据库迁移，不新增后台提交、主站密码、
Cookie、Session、CSRF 状态、云服务、跨设备同步、本地编译器、自定义输入运行器或自动 POST 重试。此前阶段、
commit、标签和 Release 均继续保留。

Early development — **Phase 48 (foreground submission result convergence)**: after the user
explicitly submits through Luogu Open Platform, the workspace now runs the existing bounded
foreground result poll immediately. Terminal evaluations render without a second tap; pending
evaluations remain recoverable through the existing result-check action. POST requests are still
never retried automatically. The APK identity is aligned to `versionName=0.3.44`, `versionCode=44`.
/ 早期开发——**第 48 阶段（前台提交结果闭环）**：用户通过洛谷 Open Platform 明确提交后，工作区现在会立即执行已有的
有界前台结果轮询；终态评测无需再次点击即可展示，仍在评测的请求会保留并可通过原有结果查询操作恢复。POST 请求仍不会
自动重试。APK 版本身份同步为 `versionName=0.3.44`、`versionCode=44`。

Phase 48 keeps submission foreground-only and local-first. It does not add background submission,
main-site passwords, Cookie, Session, CSRF state, cloud service, cross-device sync, local compiler,
or custom-input runner. Earlier phases, commits, tags, and releases remain preserved. /
第 48 阶段继续保持提交仅由用户前台触发、本地优先；不新增后台提交、主站密码、Cookie、Session、CSRF 状态、云服务、跨设备同步、
本地编译器或自定义输入运行器。此前阶段、commit、标签和 Release 均继续保留。

Early development — **Phase 47 (workspace problem context)**: opening the workspace from a
native Luogu problem detail now preserves the live problem title as an encoded optional route
context while keeping PID as the only submission identity. PID-only callers remain compatible.
The APK identity is aligned to `versionName=0.3.43`, `versionCode=43`.
/ 早期开发——**第 47 阶段（工作区题目上下文）**：从原生洛谷题目详情进入工作区时，现在会保留经过编码的实时题名
作为可选路由上下文，同时继续只使用 PID 作为提交身份；仅提供 PID 的旧入口保持兼容。APK 版本身份同步为
`versionName=0.3.43`、`versionCode=43`。

Phase 47 is a local navigation and display-context improvement only. It does not add main-site
passwords, Cookie, Session, CSRF state, cloud service, cross-device sync, local compiler, or
custom-input runner. Earlier phases, commits, tags, and releases remain preserved. / 第 47 阶段仅改进本地
导航和显示上下文，不新增主站密码、Cookie、Session、CSRF 状态、云服务、跨设备同步、本地编译器或自定义输入运行器。
此前阶段、commit、标签和 Release 均继续保留。

Early development — **Phase 46 (Luogu sample-pair compatibility)**: live Luogu problem
responses whose samples are encoded as nested `[input, output]` pairs are now decoded at the
network boundary and rendered by the existing native detail screen; older flat sample fixtures
remain supported. The APK identity is aligned to `versionName=0.3.42`, `versionCode=42`.
/ 早期开发——**第 46 阶段（洛谷样例对兼容）**：洛谷实时题目响应中的嵌套 `[输入, 输出]` 样例对现在会在
网络 DTO 边界解码，并由现有原生题目详情页渲染；旧的扁平样例格式继续支持。APK 版本身份同步为
`versionName=0.3.42`、`versionCode=42`。

Phase 46 is a compatibility fix at the DTO boundary only. It does not add main-site passwords,
Cookie, Session, CSRF state, cloud service, cross-device sync, local compiler, or custom-input
runner. Earlier phases, commits, tags, and releases remain preserved. / 第 46 阶段仅修复 DTO 边界的
兼容性，不新增主站密码、Cookie、Session、CSRF 状态、云服务、跨设备同步、本地编译器或自定义输入运行器。
此前阶段、commit、标签和 Release 均继续保留。

Early development — **Phase 45 (transaction-safe OpenApp credential replacement)**: configured users can
replace an OpenApp credential through a blank editor; the candidate is verified with the read-only quota
endpoint before it is written, and failed verification preserves the existing credential. The editor also
offers an explicit cancel action. **Phase 44** input validation and all earlier phases remain included.
The APK identity is aligned to `versionName=0.3.41`, `versionCode=41`.
/ 早期开发——**第 45 阶段（事务安全的 OpenApp 凭据更换）**：已配置用户可以通过空白编辑器更换
OpenApp 凭据；候选凭据会先使用只读额度接口验证，验证失败时保留原凭据，并提供明确的取消操作。
**第 44 阶段**输入校验及此前全部阶段继续保留。APK 版本身份同步为 `versionName=0.3.41`、`versionCode=41`。

Phase 44 remains preserved below: blank or
whitespace-only OpenApp user/secret input is rejected locally with field-specific feedback before
Keystore storage or quota verification. The settings-focus correction from Phase 43 remains
included. The APK identity is aligned to `versionName=0.3.40`, `versionCode=40`.
/ 早期开发——**第 44 阶段（OpenApp 凭据输入校验，安全的本地切片）**：空白或仅空格的 OpenApp 用户/密钥会在
写入 Keystore 或额度验证前被本地拒绝，并显示对应字段提示。第 43 阶段的设置定位修正继续保留。APK 版本身份同步为
`versionName=0.3.40`、`versionCode=40`。

Phase 43 remains preserved below: the Dashboard
`CONNECT LUOGU` route now scrolls Settings to the Luogu public-account panel; the OpenApp action
intent correction from Phase 42 remains included. The APK identity is aligned to
`versionName=0.3.39`, `versionCode=39`.
/ 早期开发——**第 43 阶段（洛谷设置定位修正，安全的本地切片）**：Dashboard 的“连接洛谷”入口现在会将
设置页滚动到洛谷公开账号面板；第 42 阶段的 OpenApp 操作意图修正继续保留。APK 版本身份同步为
`versionName=0.3.39`、`versionCode=39`。

Phase 42 remains preserved below: the Luogu
workspace now labels the real OpenApp problem action `SUBMIT`, while `RUN` remains reserved for
gateways that explicitly support custom-input execution. The APK identity is aligned to
`versionName=0.3.38`, `versionCode=38`.
/ 早期开发——**第 42 阶段（OpenApp 操作意图明确化，安全的本地切片）**：洛谷工作区现在将真实 OpenApp
题目操作明确显示为“提交”；“运行”仍只保留给明确支持自定义输入运行的网关。APK 版本身份同步为
`versionName=0.3.38`、`versionCode=38`。

Phase 41 remains preserved below: users without a Luogu connection can open the focused
public-account setup panel directly from Dashboard. / 第 41 阶段保留如下：尚未连接洛谷的用户可以从
Dashboard 直接打开聚焦的公开账号配置面板。

Phase 40 remains preserved below: the GitHub artifact is an installable Release variant with
`BuildConfig.DEBUG=false`, so development-only Demo controls stay hidden. / 第 40 阶段保留如下：GitHub
产物是可安装的 Release 变体，`BuildConfig.DEBUG=false`，开发专用 Demo 控件保持隐藏。

Phase 39 remains preserved below: Luogu Open Platform result polling now uses the documented
`GET /judge/result?id=<RequestId>` query form, so an accepted submission can be followed by a
compatible result request. / 第 39 阶段保留如下：洛谷 Open Platform 结果轮询现在使用文档规定的
`GET /judge/result?id=<RequestId>` query 形式，提交被接受后可以继续查询兼容的结果。

Phase 38 remains preserved below: The APK metadata now
matches the published release identity (`versionName=0.3.34`, `versionCode=34`) so Android users
can identify the installed build and future upgrades consistently. / 早期开发——**第 38 阶段（发布版本身份，
安全的本地切片）**：APK 元数据现在与发布身份一致（`versionName=0.3.34`、`versionCode=34`），
Android 用户可以准确识别已安装版本并保持后续升级判断一致。

Phase 37 remains preserved below: Codeforces and AtCoder now share
judge-independent sync contracts while keeping separate adapters, request gates, cursors,
and cached data. AtCoder uses the community AtCoder Problems data source, soft public-handle
binding, timestamp pagination, and source-native estimated difficulty. Settings, dashboard,
profile, problems, contests, and analytics expose judge-labelled local data. Analytics adds
heatmap day detail, first-try AC, weak-tag performance, and per-judge difficulty breakdowns.
Arena adds a cached contest focus view with countdowns, local markers, and joined submission
progress. Training now shows an explicit knowledge tree with local evidence-backed mastery
scores and reason codes. Profile now derives and displays local achievement milestones. The
Player Card can be exported as a PNG through the system share sheet. Settings can export a
verified copy of the local Room database through the Android document picker; it contains local
study data only and never requires credentials. Reduce-motion and haptics preferences are
persisted locally and affect navigation and primary-tab feedback. The global command palette
searches local navigation and study actions without network access. A validated database backup
can be imported and restored before the next app start. See [docs/DATA_SAFETY.md](docs/DATA_SAFETY.md).
Settings also exposes three named dark accent slots: BLUE, GREEN, and AMBER.
The performance audit and bounded-feed check are documented in [docs/PERFORMANCE.md](docs/PERFORMANCE.md).
The interface follows the system language by default and can be switched between SYSTEM,
ENGLISH, and 简体中文 in Settings.
Settings supports public Luogu username binding plus local-first synchronization of public
profile, rating history, problem catalog, and contest catalog data. Luogu uses its structured
content-only JSON transport, remains marked EXPERIMENTAL, and reports anonymous submission
records as AUTH_REQUIRED without importing fabricated attempts. Main-site passwords, cookies,
sessions, and CSRF state are never requested or stored. The local code workspace uses the
official Luogu Open Platform credential only; problem judging is an explicit foreground action,
and the editor forwards a selected official language identifier with each request. There is no
automatic POST retry or cloud code storage. The local submission center lists recent
Open Platform request metadata, supports foreground result checks, and reopens related problem
workspaces without storing source code or standard input. The remote problem catalog can switch
to LUOGU and adds canonical Luogu problem links when a problem is saved locally. Analytics and
Profile now consume rating history for Codeforces, AtCoder, and Luogu independently. Contest
Center also exposes Luogu as a judge filter, and Arena opens canonical Luogu contest and problem
pages when the public cache has no contest-problem membership data. Remote catalog rows can also
open the canonical problem page before adding a problem to the local library; Luogu rows also
open the local Open Platform workspace directly. Settings also links to the official OpenApp
documentation before credential configuration. See
[docs/MULTI_OJ.md](docs/MULTI_OJ.md), [docs/ATCODER.md](docs/ATCODER.md),
[docs/ARENA.md](docs/ARENA.md),
[docs/KNOWLEDGE.md](docs/KNOWLEDGE.md),
[docs/ACHIEVEMENTS.md](docs/ACHIEVEMENTS.md),
[docs/SYNC_ENGINE.md](docs/SYNC_ENGINE.md), and [docs/ROADMAP.md](docs/ROADMAP.md).

Phase 11 / 第 11 阶段: Luogu remote rows can open a native problem-detail screen that reads
public content-only problem data, renders safe Markdown blocks, samples, and limits, and links
to the official source page or local workspace. / 洛谷远端题库条目现在可以打开原生题目详情页，
读取公开 content-only 题面，安全展示 Markdown 区块、样例和限制，并可跳转官方原题或本地工作区。
This remains public-data-only: no main-site login, cookies, CSRF state, or cloud service. /
本阶段仍只使用公开数据，不实现主站登录、Cookie、CSRF 状态或云端服务。

Phase 12 / 第 12 阶段: Luogu Arena can read the public contest detail response and show the
official contest description and contest problem membership when available. / 洛谷 Arena 现在可以
读取公开竞赛详情响应，在数据可用时展示官方竞赛说明和竞赛题目成员关系。

Phase 13 / 第 13 阶段: Luogu remote problem search now reads Room first and fetches a matching
public keyword page only when the local cache has no hit, then stores the result locally. This
makes first-use search practical while the bounded background catalog sync continues. / 洛谷远端
题库搜索现在先读 Room；本地没有命中时，才按关键词请求公开题库页并写入本地缓存，使后台
同步尚未完成时也能实际搜索。该阶段仍不使用主站密码、Cookie、Session、CSRF 或云端服务。

Phase 14 / 第 14 阶段: Luogu keyword results now fetch and cache later pages on demand as the user
loads more, while Settings exposes the active background sync stage. / 第 14 阶段：用户加载更多
洛谷关键词结果时，应用现在会按需请求并缓存后续分页；设置页同时显示后台同步当前阶段，
让较长的公开题库刷新过程可见。

Phase 15 / 第 15 阶段: An empty remote catalog now directs the user to enter a keyword instead of
implying that an OJ account is required for public Luogu search. / 第 15 阶段：远端题库为空时，
现在提示用户输入关键词，不再误导用户认为公开洛谷搜索必须先连接 OJ 账号。

Phase 16 / 第 16 阶段: The problem scope switcher now navigates in both directions between the
local library and the remote catalog, including a direct return from the remote view. / 第 16 阶段：
题库范围切换器现在支持本地题库与远端题库双向导航，进入远端视图后可以直接返回本地题库。
The change remains local-first and public-data-only. / 本阶段仍保持本地优先和仅使用公开数据。

Phase 17 / 第 17 阶段: Profile now renders the synchronized Luogu public profile snapshot,
including ranking, passed/submitted problem counts, follower/following counts, slogan, and
introduction, with an explicit empty state when no public snapshot is available. / 第 17 阶段：
个人档案现在展示已同步的洛谷公开资料快照，包括排名、通过题数、提交题数、粉丝、关注、签名
和简介；没有公开资料快照时显示明确的空状态。
Only the public Room snapshot is rendered; no main-site credentials, cookies, sessions, CSRF state,
cloud account, or cross-device sync is introduced. / 本阶段只展示 Room 中的公开资料快照，不新增
主站凭据、Cookie、Session、CSRF 状态、云端账号或跨设备同步。

Phase 18 / 第 18 阶段: After an explicit Luogu OpenApp submission, the workspace now performs a
bounded foreground-only result wait and stops cleanly if the judge remains pending. / 第 18 阶段：
用户明确提交洛谷 OpenApp 请求后，工作区现在会在前台有限等待结果；如果评测仍在等待，会干净地
停止轮询。POST 提交仍不会自动重试，也不会创建后台提交任务。
This keeps the workflow local-first and credential-safe. / 本阶段仍保持本地优先和凭据安全。

Phase 19 / 第 19 阶段: The local submission center now uses the same bounded foreground-only result wait as
the workspace, so pending OpenApp requests converge consistently from either entry. / 第 19 阶段：本地提交中心
现在与工作区使用相同的前台有限结果等待，Pending 的 OpenApp 请求从任一入口都能一致收敛。
POST submissions remain explicit and are never retried automatically; historical notes and Releases remain intact. /
POST 提交仍需用户明确触发且不会自动重试；历史说明和 Releases 保持不变。

Phase 20 / 第 20 阶段: Foreground OpenApp result checks can use the official WebSocket notification
as an optional wake-up signal, while the authenticated HTTP result response remains authoritative.
The local submission job now preserves nullable compile status/message, output, exit code, execution
time, and memory, and the submission center renders them after navigation or restart. / 第 20 阶段：
前台 OpenApp 结果查询可以使用官方 WebSocket 通知作为可选唤醒信号，但鉴权 HTTP 结果响应仍是
唯一权威来源。本地提交任务现在保存可空的编译状态/信息、输出、退出码、运行时间和内存，
提交中心在跳转或重启后仍会展示这些详情。
The phase remains local-first and foreground-only: no Luogu main-site password, cookie, session,
CSRF state, cloud account, cross-device sync, automatic POST retry, local compiler, or custom-input
runner is added. / 本阶段仍坚持本地优先和仅前台执行，不新增洛谷主站密码、Cookie、Session、
CSRF 状态、云端账号、跨设备同步、POST 自动重试、本地编译器或自定义输入运行器。历史版本、
历史 Release 和既有说明继续保留。 / Historical versions, Releases, and existing explanations
remain intact.

Phase 21 / 第 21 阶段: Reopening a Luogu workspace now restores the locally persisted OpenApp
evaluation details, including compile status/message, output, exit code, execution time, and memory.
The workspace also shows an explicit localized compile success/failure label, even when the upstream
compiler message is empty. / 第 21 阶段：重新打开洛谷工作区时，现在会恢复本地保存的 OpenApp
评测详情，包括编译状态/信息、输出、退出码、运行时间和内存；即使上游没有编译文字信息，
工作区也会明确显示本地化的编译成功/失败状态。
This is a local-first presentation and restoration improvement. Historical notes and Releases remain
intact, and no main-site password, cookie, session, CSRF state, cloud account, cross-device sync,
background submission, automatic POST retry, local compiler, or custom-input runner is added. /
本阶段是本地优先的展示与恢复改进；历史说明和 Releases 保持不变，不新增主站密码、Cookie、
Session、CSRF 状态、云端账号、跨设备同步、后台提交、POST 自动重试、本地编译器或自定义输入运行器。

Phase 22 / 第 22 阶段: Luogu Open Platform result handling now distinguishes HTTP 204 Pending,
HTTP 200 InProgress, and terminal Ready results. Non-terminal 200 responses continue through the
bounded foreground poll window, while the latest compile/judge/run details remain visible locally.
Only a terminal result materializes a finished local attempt. / 第 22 阶段：洛谷 Open Platform
结果处理现在区分 HTTP 204 Pending、HTTP 200 InProgress 和终态 Ready。非终态 200 响应会继续进入
前台有界轮询，最新的编译/评测/运行详情会保留并在本地展示；只有终态结果才生成完成的本地提交记录。
This follows the official result contract and preserves the existing local-first, OpenApp-only boundary;
historical notes and Releases remain intact. No main-site password, cookie, session, CSRF state,
cloud account, cross-device sync, background submission, automatic POST retry, local compiler, or
custom-input runner is added. / 本阶段遵循官方结果契约，继续保持本地优先和仅使用 OpenApp 的边界；
历史说明和 Releases 保持不变，不新增主站密码、Cookie、Session、CSRF 状态、云端账号、跨设备同步、
后台提交、POST 自动重试、本地编译器或自定义输入运行器。

Phase 23 / 第 23 阶段: Saving a Luogu OpenApp credential now performs an immediate read-only
`quotaAvailable` verification. Successful credentials show the available points immediately;
authorization failures clear the rejected value, while network/API failures keep the Keystore value
and show a retryable verification error. / 第 23 阶段：保存洛谷 OpenApp 凭据后，现在会立即调用只读
`quotaAvailable` 接口验证。验证成功后立即显示可用计费点；鉴权失败会清除被拒绝的凭据，网络/API
失败则保留 Keystore 中的值并提示可重试的验证错误。
The setup remains OpenApp-only and local-first. Historical notes and Releases remain intact; no
Luogu main-site password, Cookie, Session, CSRF login, cloud account, cross-device sync, background
submission, or automatic POST retry is added. / 配置流程仍仅使用 OpenApp、本地优先；历史说明和
Releases 保持不变，不新增洛谷主站密码、Cookie、Session、CSRF 登录、云端账号、跨设备同步、后台
提交或 POST 自动重试。

Phase 24 / 第 24 阶段: Luogu's full local problem catalog now prefers the official streamed
`https://cdn.luogu.com.cn/problemset-open/latest.ndjson.gz` export. The client decompresses gzip
line by line, maps catalog fields, and writes Room rows in bounded batches; adapters without this
capability keep the historical paged fallback. / 第 24 阶段：洛谷本地完整题库现在优先使用官方流式
`https://cdn.luogu.com.cn/problemset-open/latest.ndjson.gz` 导出。客户端逐行解压 gzip、映射题库字段，
并以有界批次写入 Room；不支持该能力的适配器继续使用历史分页回退。
The catalog phase does not bulk-import problem details and remains public, local-first, and
OpenApp-only. Historical notes and Releases remain intact; no main-site password, cookie, session,
CSRF login, cloud account, cross-device sync, background submission, or automatic POST retry is added.
/ 本阶段不批量导入题面详情，仍保持公开数据、本地优先和仅 OpenApp；历史说明和 Releases 保持不变，
不新增主站密码、Cookie、Session、CSRF 登录、云端账号、跨设备同步、后台提交或 POST 自动重试。

Phase 25 / 第 25 阶段: Native Luogu problem details now use a local-first cache. The first online
view persists the public content-only snapshot in Room; later opens can render it offline, and an
explicit REFRESH action updates it. Network/timeout failures with an existing snapshot show the
cached detail with a retryable warning, while HTTP and parse errors remain visible. / 第 25 阶段：
原生洛谷题目详情现在采用本地优先缓存。首次在线查看会把公开 content-only 快照保存到 Room，之后
断网仍可打开；用户可通过显式“刷新”更新快照。已有快照遇到网络/超时失败时显示缓存并提示可重试，
HTTP 和解析错误则继续明确展示。
This phase adds no bulk detail import, main-site password, Cookie, Session, CSRF login, cloud
account, cross-device sync, background submission, or automatic POST retry. Historical phase notes
and Releases remain intact. / 本阶段不批量导入全部题面详情，不新增主站密码、Cookie、Session、CSRF
登录、云端账号、跨设备同步、后台提交或 POST 自动重试；历史阶段说明和 Releases 保持不变。

Phase 26 / 第 26 阶段: The Luogu remote catalog now exposes an explicit foreground SYNC CATALOG
action. It imports the public catalog without requiring a connected account, keeps Room as the
read source, shows progress and imported-item results, suppresses duplicate starts, and preserves
already-written rows when a bounded sync fails. / 第 26 阶段：洛谷远端题库现在提供显式前台“同步题库”
操作。它无需连接账号即可导入公开题库，仍以 Room 为读取源，显示同步中和导入数量结果，禁止重复
启动；有界同步失败时保留已经写入的题目。
This phase does not add main-site password, Cookie, Session, CSRF login, cloud account,
cross-device sync, background work from the button, or automatic POST retry. Historical phase notes
and Releases remain intact. / 本阶段不新增主站密码、Cookie、Session、CSRF 登录、云端账号、跨设备
同步，不会由该按钮创建后台任务，也不增加 POST 自动重试；历史阶段说明和 Releases 保持不变。

Phase 27 / 第 27 阶段: The native Luogu workspace now restores one local Room draft per judge and
problem, including source code, optional input, language, and O2 selection. Draft writes are
debounced, their local persistence state is visible, and page/system back flushes the latest edit
before leaving. / 第 27 阶段：原生洛谷工作区现在按评测平台和题号恢复一份本地 Room 草稿，包括源代码、
可选输入、语言和 O2 选择。草稿写入带防抖并显示本地保存状态，页面返回和系统返回会在离开前保存最新编辑。
The database migration is non-destructive; submission request metadata still never stores source
code or input. Drafts are local backup data only. No main-site password, Cookie, Session, CSRF login,
cloud account, cross-device sync, local compiler, custom-input runner, or automatic POST retry is
added. Earlier phase notes and Releases remain intact. / 数据库迁移不破坏旧数据；提交请求元数据仍不会
保存源代码或输入，草稿只属于本地备份数据。不新增主站密码、Cookie、Session、CSRF 登录、云端账号、
跨设备同步、本地编译器、自定义输入运行器或自动提交重试；此前阶段说明和 Releases 保持不变。

Phase 28 / 第 28 阶段: Existing Luogu OpenApp submissions now enqueue a unique local WorkManager
result job after their lifecycle metadata is persisted. The job uses only the official GET result
endpoint, connected-network constraints, bounded exponential retry, and a startup reconciliation
of at most 50 pending jobs; foreground polling remains available. / 第 28 阶段：已有洛谷 OpenApp 提交
在生命周期元数据落盘后加入本地唯一 WorkManager 结果任务。任务只使用官方 GET 结果接口、联网约束和
有界指数退避，并在启动时最多恢复 50 个待处理任务；前台查询仍然可用。
Transient network/server errors remain pending for retry; permanent credential or resource errors
remain visible locally. No background submission, POST retry, main-site password, Cookie, Session,
CSRF login, cloud account, cross-device sync, local compiler, or custom-input runner is added.
Historical phase notes and Releases remain intact. / 瞬时网络或服务器错误保持待处理并重试；永久凭据或资源
错误在本地可见。不新增后台提交、POST 重试、主站密码、Cookie、Session、CSRF 登录、云端账号、跨设备同步、
本地编译器或自定义输入运行器；历史阶段说明和 Releases 保持不变。

Phase 29 / 第 29 阶段: The submission center now exposes explicit `QUEUE CHECK` and
`QUEUE RETRY` actions for pending or failed Luogu OpenApp result requests. Manual recovery
uses an immediate, unique local WorkManager result job and keeps the existing foreground
`CHECK RESULT` poll available. / 第 29 阶段：提交中心现在为待处理或失败的洛谷 OpenApp
结果请求提供明确的“排队查询”和“重新排队”操作。手动恢复使用立即执行的本地唯一
WorkManager 结果任务，同时保留现有前台“查询结果”轮询。

The manual action only carries the request ID and only performs the official GET result check;
it never creates a submission or retries a POST. No main-site password, Cookie, Session, CSRF
login, cloud account, cross-device sync, local compiler, or custom-input runner is added.
Previous phase notes and Releases remain intact. / 手动操作只携带 Request ID，只执行官方 GET
结果查询，不创建提交，也不重试 POST。不新增主站密码、Cookie、Session、CSRF 登录、云端账号、
跨设备同步、本地编译器或自定义输入运行器；此前阶段说明和 Releases 保持不变。

Phase 30 / 第 30 阶段: Existing active accounts now have their six-hour background sync work
reconciled when the application starts. The bootstrap covers every judge advertising the real
`BACKGROUND_SYNC` capability, skips missing or disabled accounts, and isolates scheduler failures;
Settings now exposes the capability as `BACKGROUND SYNC ENABLED`. / 第 30 阶段：应用启动时现在会为
已有活跃账号校准六小时后台同步任务。启动校准覆盖声明真实 `BACKGROUND_SYNC` 能力的评测平台，跳过
缺失或已禁用账号，并隔离调度异常；设置页现在明确显示“后台同步已启用”。

This restores existing sync scheduling only; it does not trigger an immediate sync or create
submission work. No main-site password, Cookie, Session, CSRF login, cloud account, cross-device
sync, local compiler, or custom-input runner is added. Previous phase notes and Releases remain
intact. / 本阶段只恢复已有账号的同步调度，不立即执行同步，也不创建提交任务。不新增主站密码、
Cookie、Session、CSRF 登录、云端账号、跨设备同步、本地编译器或自定义输入运行器；此前阶段说明和
Releases 保持不变。

Phase 31 / 第 31 阶段: Luogu account synchronization now executes only the four capabilities its
adapter actually advertises: public profile, rating, contests, and problem catalog. It no longer
calls the unsupported submission-record endpoint, so a successful public sync finishes as `SUCCESS`
instead of a known `PARTIAL/AUTH_REQUIRED`. / 第 31 阶段：洛谷账号同步现在只执行适配器真实声明的四项
能力：公开资料、Rating、竞赛和题库。不再调用未支持的提交记录接口，因此公开同步成功时会返回
`SUCCESS`，不再被已知的 `PARTIAL/AUTH_REQUIRED` 人为污染。

Private submission history remains outside the public adapter and no fabricated attempts are imported.
Local OpenApp submission requests and their result workers remain available in the submission center.
No main-site password, Cookie, Session, CSRF login, cloud account, cross-device sync, local compiler,
or custom-input runner is added. Earlier phase notes and Releases remain intact. / 私有提交历史仍不属于
公开适配器，不会伪造导入提交记录；本地 OpenApp 提交请求及结果 Worker 继续由提交中心提供。不新增
主站密码、Cookie、Session、CSRF 登录、云端账号、跨设备同步、本地编译器或自定义输入运行器；此前
阶段说明和 Releases 保持不变。

Phase 32 / 第 32 阶段: A Luogu workspace without an OpenApp credential now keeps the existing
credential warning and exposes a direct `OPEN SETTINGS / 打开设置` action. The action navigates to
the existing Settings screen, carries an accessible localized description, and leaves execution
disabled until configuration is complete. / 第 32 阶段：未配置 OpenApp 凭据的洛谷工作区现在保留原有
凭据警告，并提供可直接进入设置的“打开设置”操作。该操作导航到已有设置页，带有本地化无障碍描述；
完成配置前，执行按钮仍保持禁用。

This phase changes only the local first-use navigation path. It adds no network request, main-site
password, Cookie, Session, CSRF login, cloud account, cross-device sync, local compiler, custom-input
runner, or automatic submission retry. Earlier phase notes and Releases remain intact. / 本阶段只改变
本地首次使用导航路径，不新增网络请求、主站密码、Cookie、Session、CSRF 登录、云端账号、跨设备同步、
本地编译器、自定义输入运行器或自动提交重试；此前阶段说明和 Releases 保持不变。

Phase 33 / 第 33 阶段: The Luogu workspace setup action now opens a dedicated Settings route
that automatically scrolls to the `LUOGU OPEN PLATFORM` section. The OpenApp user, secret, and
save controls are brought into the visible area using layout-aware coordinates, while ordinary
Settings navigation still starts at the top. / 第 33 阶段：洛谷工作区配置操作现在进入独立的设置路由，
并自动滚动到“洛谷 Open Platform”区域。OpenApp 用户名、密钥和保存控件会通过布局感知坐标进入可视范围；
普通设置入口仍从顶部开始。

This phase changes only local navigation and scroll positioning. It adds no main-site password,
Cookie, Session, CSRF login, cloud account, cross-device sync, local compiler, custom-input runner,
or automatic submission retry. Earlier phase notes and Releases remain intact. / 本阶段只改变本地导航和
滚动定位，不新增主站密码、Cookie、Session、CSRF 登录、云端账号、跨设备同步、本地编译器、自定义输入
运行器或自动提交重试；此前阶段说明和 Releases 保持不变。

Phase 34 / 第 34 阶段: Public judge sync now persists `QUEUED` before WorkManager scheduling,
so a newly connected Luogu handle and manual `SYNC NOW` action remain visible while waiting for
network constraints. Settings distinguishes queued, active, partial, failed, and successful runs,
and maps known failures to stable localized messages without exposing raw server text. / 第 34 阶段：
公开评测平台同步现在会在交给 WorkManager 前持久化 `QUEUED` 状态，因此新绑定的洛谷账号和手动“立即同步”
在等待网络约束时仍然可见。设置页区分排队、进行中、部分完成、失败和成功，并将已知错误映射为稳定的本地化
文案，不直接暴露服务器原始文本。

This remains local-first and public-data-only. No main-site password, Cookie, Session, CSRF login,
cloud account, cross-device sync, local compiler, custom-input runner, or automatic submission retry
is added. Earlier phase notes and Releases remain intact. / 本阶段继续本地优先和仅公开数据，不新增主站密码、
Cookie、Session、CSRF 登录、云端账号、跨设备同步、本地编译器、自定义输入运行器或自动提交重试；此前阶段
说明和 Releases 保持不变。

Phase 35 / 第 35 阶段: Luogu Rating synchronization now prefers a non-empty practice `elo`
history and falls back to the public user-page `elo` history when practice returns an empty
array. This keeps public Rating history for accounts whose two public pages expose different
payload shapes. / 第 35 阶段：洛谷 Rating 同步现在优先使用非空的 practice `elo` 历史；当 practice
返回空数组时，回退到公开用户主页的 `elo` 历史，从而兼容两个公开页面返回结构不同的账号，保留公开 Rating 历史。

The fix changes no authentication or storage boundary: Luogu remains public-data-only, and no
main-site password, Cookie, Session, CSRF login, cloud account, cross-device sync, local compiler,
custom-input runner, or automatic submission retry is added. Earlier phase notes and Releases
remain intact. / 本修复不改变登录或存储边界：洛谷仍只同步公开数据，不新增主站密码、Cookie、Session、CSRF 登录、
云端账号、跨设备同步、本地编译器、自定义输入运行器或自动提交重试；此前阶段说明和 Releases 保持不变。

Phase 36 / 第 36 阶段: Analytics now treats synchronized Rating history as real content, so a
user with Luogu Rating data but no local attempts is not sent to the empty state. Profile shows
the aggregate rated-contest count from any connected judge and uses neutral localized copy when
there is no history. / 第 36 阶段：Analytics 现在将同步得到的 Rating 历史视为真实内容，因此只有洛谷
Rating 数据、尚无本地做题记录的用户不会再被错误送入空态。Profile 显示所有已连接评测平台的 Rated 竞赛总数，
没有历史时使用中性的本地化文案。

This is a local presentation fix only. Public Luogu sync remains local-first and content-only;
no main-site password, Cookie, Session, CSRF login, cloud account, cross-device sync, local
compiler, custom-input runner, or automatic submission retry is added. Earlier phase notes and
Releases remain intact. / 本阶段仅修复本地展示。洛谷公开同步继续本地优先且只使用公开内容，不新增主站密码、
Cookie、Session、CSRF 登录、云端账号、跨设备同步、本地编译器、自定义输入运行器或自动提交重试；此前阶段说明
和 Releases 保持不变。

Phase 37 / 第 37 阶段: Settings now shows a capability-backed sync receipt for every connected
judge. Profile, Rating, submissions, contests, and problemset rows appear only when the adapter
actually declares that capability; each row reports NEVER SYNCED or a localized relative refresh
age from the existing Room sync timestamp. / 第 37 阶段：设置页现在为每个已连接评测平台显示基于真实能力的
同步回执。只有适配器实际声明的资料、Rating、提交、竞赛和题库能力才会出现对应行；每行使用现有 Room 同步
时间戳显示“从未同步”或本地化的相对更新时间。

The receipt preserves the local-first boundary and does not turn Luogu's unsupported private
submission history into a missing module. No main-site password, Cookie, Session, CSRF login,
cloud account, cross-device sync, local compiler, custom-input runner, or automatic submission
retry is added. Earlier phase notes and Releases remain intact. / 同步回执继续保持本地优先边界，不会把洛谷不支持的
私有提交历史误显示为缺失模块。不新增主站密码、Cookie、Session、CSRF 登录、云端账号、跨设备同步、本地编译器、
自定义输入运行器或自动提交重试；此前阶段说明和 Releases 保持不变。

Phase 38 changes release metadata and documentation only. It adds no endpoint, database migration,
credential flow, cloud service, cross-device sync, local compiler, custom-input runner, or automatic
submission retry. Earlier phase notes and Releases remain intact. / 第 38 阶段仅修改发布元数据和文档，
不新增接口、数据库迁移、凭据流程、云端服务、跨设备同步、本地编译器、自定义输入运行器或自动提交重试；
此前阶段说明和 Releases 保持不变。

Phase 39 changes only the documented result-request shape and its contract test. It adds no new
login mode, main-site password, Cookie, Session, CSRF state, cloud service, local compiler,
custom-input runner, automatic POST retry, or public submission-history import. Earlier phase notes
and Releases remain intact. / 第 39 阶段仅修正文档规定的结果请求形式及其契约测试，不新增登录模式、主站密码、
Cookie、Session、CSRF 状态、云端服务、本地编译器、自定义输入运行器、自动 POST 重试或公开提交历史导入；
此前阶段说明和 Releases 保持不变。

## Documentation

- [Product Spec](docs/PRODUCT_SPEC.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Design System](docs/DESIGN_SYSTEM.md)
- [Database](docs/DATABASE.md)
- [Training Engine](docs/TRAINING_ENGINE.md)
- [OJ Adapter Spec](docs/OJ_ADAPTERS.md)
- [Multi-OJ Architecture](docs/MULTI_OJ.md)
- [AtCoder Integration](docs/ATCODER.md)
- [Analytics](docs/ANALYTICS.md)
- [Roadmap](docs/ROADMAP.md)

## Build

```bash
./gradlew assembleDebug   # Windows: .\gradlew.bat assembleDebug
./gradlew test
```

Requires JDK 17+ and an Android SDK with API 37. Point `sdk.dir` at your SDK in a
(non-committed) `local.properties`, and pin a JDK via `JAVA_HOME` or the user-level
`~/.gradle/gradle.properties` if needed.

## License

Copyright 2026 tangyixiao. Licensed under the [Apache License 2.0](LICENSE).
