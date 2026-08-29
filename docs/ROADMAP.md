# OJ NEXUS — Roadmap

Each phase ends with: `assembleDebug` BUILD SUCCESSFUL, `test` green, code review, docs updated,
commits pushed.

## PHASE 0 — Foundation ✅ (this milestone)
Gradle/AGP 9 toolchain, design system (tokens + core components), app shell (edge-to-edge,
navigation, bottom bar), five skeleton screens (Dashboard / Problems / Training / Analytics /
Profile) rendering labeled development sample data, domain enums (`JudgeId`, `Verdict`,
`KnowledgeArea`, `TrainingType`), unit tests, CI, docs.

## PHASE 1 — Local Training Core
Room schema v1 (per DATABASE.md), repositories, ViewModels + StateFlow UiStates.
Manual problem entry, notes, failure entries, review scheduler (1/3/7/21d), training sessions
(local timing, end summaries). Everything works offline with user-entered data.

## PHASE 2 — Codeforces
First `JudgeAdapter`: official API client, incremental submission sync, rating history,
problems + tags, contests. Sync engine (manual + WorkManager background), sync states,
pull-to-refresh, offline cache correctness.

## PHASE 3 — Analytics
Heatmap (tap-through day detail), verdict/difficulty/knowledge distributions, rating chart,
trend metrics, weak tags — all computed from local data, drawn with Compose.

## PHASE 4 — AtCoder + Luogu
Second and third adapters under the isolation rules; multi-judge dashboard/aggregation;
unified timeline across judges.

## PHASE 5 — Arena
Contest center polish (reminders, calendar) and the live-contest focus view: countdown,
problem tracker, local markers, synced submission progress. No scraping, no auto-submit.

## PHASE 6 — Knowledge + Training Engine
Knowledge tree UI, problem-knowledge relations, explainable Mastery Engine, deterministic
Training Engine (priority + reasons), review integration.

## PHASE 7 — Achievements + Player Card
Achievement set with unlock detection, Player Card, share-image generation.

## PHASE 8 — Polish + Performance + Tests
Reduce-motion + haptics settings, command palette, data export/import/backup, theme slots,
startup/scroll performance pass, test coverage on engines/adapters/repositories.
