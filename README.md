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

Early development — **Phase 7 (Achievements + Player Card)**: Codeforces and AtCoder now share
judge-independent sync contracts while keeping separate adapters, request gates, cursors,
and cached data. AtCoder uses the community AtCoder Problems data source, soft public-handle
binding, timestamp pagination, and source-native estimated difficulty. Settings, dashboard,
profile, problems, contests, and analytics expose judge-labelled local data. Analytics adds
heatmap day detail, first-try AC, weak-tag performance, and per-judge difficulty breakdowns.
Arena adds a cached contest focus view with countdowns, local markers, and joined submission
progress. Training now shows an explicit knowledge tree with local evidence-backed mastery
scores and reason codes. Profile now derives and displays local achievement milestones. The
Player Card can be exported as a PNG through the system share sheet.
No passwords,
cookies, private API signing, scraping, or auto-submit are used. See
[docs/MULTI_OJ.md](docs/MULTI_OJ.md), [docs/ATCODER.md](docs/ATCODER.md),
[docs/ARENA.md](docs/ARENA.md),
[docs/KNOWLEDGE.md](docs/KNOWLEDGE.md),
[docs/ACHIEVEMENTS.md](docs/ACHIEVEMENTS.md),
[docs/SYNC_ENGINE.md](docs/SYNC_ENGINE.md), and [docs/ROADMAP.md](docs/ROADMAP.md).

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
