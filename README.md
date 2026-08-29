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
WorkManager · Retrofit/OkHttp (later phases)

## Status

Early development — **Phase 0 (Foundation)**: design system, app shell, and skeleton screens
rendering labeled development sample data. See [docs/ROADMAP.md](docs/ROADMAP.md).

## Documentation

- [Product Spec](docs/PRODUCT_SPEC.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Design System](docs/DESIGN_SYSTEM.md)
- [Database Plan](docs/DATABASE.md)
- [OJ Adapter Spec](docs/OJ_ADAPTERS.md)
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

Not yet decided — see the repository discussion. All rights reserved in the meantime.
