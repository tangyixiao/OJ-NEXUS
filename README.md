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

Early development — **Phase 13 (Luogu on-demand catalog search, safe local slice)**: Codeforces and AtCoder now share
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
