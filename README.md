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

## Status

Early development — **Phase 26 (foreground Luogu public catalog action, safe local slice)**: Codeforces and AtCoder now share
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
