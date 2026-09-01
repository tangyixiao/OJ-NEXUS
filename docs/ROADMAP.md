# OJ NEXUS — Roadmap

Each phase ends with: `assembleDebug` BUILD SUCCESSFUL, `test` green, code review, docs updated,
commits pushed.

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
