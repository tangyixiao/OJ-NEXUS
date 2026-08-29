# OJ NEXUS — Product Spec

Competitive Programming Command Center for Android. A high-quality native client for
Codeforces / AtCoder / Luogu users (OI / ICPC / CP): accounts, submissions, rating, problems,
contests, training plans, review, mastery, sessions, analytics, achievements — one coherent,
technical tool.

Not a "problem log", not a chat product.

## Core Principles

1. **Multi-OJ** — every judge is an isolated adapter; core stays judge-agnostic.
2. **Local First** — offline access to problems, submissions, notes, review, stats; the network
   only syncs.
3. **Deterministic intelligence** — recommendation/mastery are explainable algorithms, never
   "AI suggestions". Show `TARGET PRIORITY 91 / REASON: LOW MASTERY · 18D REVIEW GAP`.
4. **Security** — public handle binding first; no passwords ever; no cookie scraping in early
   versions.
5. **Telemetry aesthetic** — dense, dark, single-accent, monospace data. Tool, not marketing.

## Feature Map

| Area | Purpose |
| --- | --- |
| Dashboard | Today's plan, sync status, rating/weekly AC/streak, next contest, recent activity, training load |
| Problems | Unified multi-OJ problem library: search/filter/sort (judge, difficulty, status, tag, mastery, review state) |
| Problem Detail | Attempts, failure log, root cause, key insight, mastery, notes, review schedule, submission history |
| Submissions | Unified timeline across judges with normalized verdicts (raw verdict preserved) |
| Review | Failure entries (root cause taxonomy), spaced review (1/3/7/21 days first version) |
| Knowledge / Mastery | Knowledge tree (DSU, Fenwick, shortest path, …), explainable mastery scores |
| Training Engine | Deterministic priority scoring: weakness, difficulty fit, review gap, failure count, coverage value |
| Training Session | Practice / Focus / Upsolve / Review sessions with elapsed time and end-of-session summary |
| Contest / Arena | Unified contest list with countdown; Arena = focus view during live contests |
| Analytics | Heatmap, verdict/knowledge/difficulty distributions, rating chart, first-try AC rate, weak tags |
| Profile | Player card: per-judge rating/rank/solved, global stats, later shareable image |
| Achievements | Restrained, competitive-style (FIRST BLOOD, IRON WILL, RED LINE…), no emoji |
| Command Palette | Text parser (no LLM): `cf 1800 dp unsolved`, `review today`, `@luogu` |
| Settings | Theme, reduce motion, haptics, sync interval, connected judges, data export/import/backup |

## Non-Goals

- Rebuilding OJ websites in-app (open problems via browser/Custom Tabs).
- Auto-submitting or scraping gated content.
- Social feed features.
- AI chat or generated advice.
