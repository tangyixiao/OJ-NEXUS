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

## PHASE 3 — Multi-OJ + AtCoder ✅ (current milestone)
Judge-independent adapter/registry/sync contracts, Room v3 migration, AtCoder Problems
transport and mapping, soft public-handle binding, timestamp-cursor submission sync, catalog
and contest caching, per-judge WorkManager identity, and judge-labelled local-first UI.
The branch is locally complete; push/PR/CI remain separate release actions requiring explicit
authorization.

## PHASE 4 — Analytics (next)
Heatmap tap-through, verdict/difficulty/knowledge distributions, rating chart, trend metrics,
weak tags, and richer per-judge breakdowns — all computed from local data and drawn with Compose.

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
