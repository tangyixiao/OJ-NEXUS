# OJ NEXUS — Roadmap

Each phase ends with: `assembleDebug` BUILD SUCCESSFUL, `test` green, code review, docs updated,
commits pushed.

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
