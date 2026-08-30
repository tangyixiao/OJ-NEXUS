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

## PHASE 8 — Polish + Performance + Tests (in progress)
Settings now exports a verified copy of the local Room database through the Android document
picker, and reduce-motion/haptics preferences persist through DataStore. The export contains
local study data only and never requires credentials. A global command palette now searches
local navigation and study actions without network access. Database backups can be imported,
schema-validated, and restored before the next app start. The visual system now exposes three
named dark accent slots while preserving one accent per theme. Remaining work: startup/scroll
performance pass and further coverage on engines/adapters/repositories.
