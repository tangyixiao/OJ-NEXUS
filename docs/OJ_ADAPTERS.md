# OJ NEXUS — OJ Adapter Spec

All judge-specific behavior lives behind one boundary. Core code sees only the unified model.

## Implemented Contract

`JudgeAdapter` declares a `JudgeId`, supported `JudgeCapability` values, source
`DataSourceReliability`, and runtime `AdapterStatus`. Judge-specific operations remain in
the adapter package. `JudgeRegistry` resolves adapters, account connectors, and sync
coordinators; feature code never branches on concrete network implementations.

Each judge ships: DTOs (network JSON), a mapper to unified models, an adapter implementation,
and its own error surface. DTOs never leave the judge package.

## Adapter Rules

1. **Isolation** — one judge's parse failure, rate limit, or schema change can never throw into
   another judge's code path or crash the app. Failures surface as `AdapterError`.
2. **Domain mapping** — network DTO → unified model mapping is tested code. Unknown verdicts,
   tags, or fields degrade gracefully (`Verdict.OTHER`, null difficulty), never throw.
3. **Auth** — v0.x: public handle binding only. No passwords, no cookie jars. Any future
   session/cookie feature is experimental, Keystore-backed, clearly flagged, and clearable.
4. **Politeness** — per-judge rate limiting and request spacing; incremental sync keyed by
   last submission time; retries are bounded and only for retryable errors.

## Judge Notes

### Codeforces (Phase 2 — first adapter)
Official public API (`codeforces.com/api`). Sync: profile, rating history, submissions
(incremental via `from` count), problems + problem rating + tags, contests. Bound by the
public rate limit — responses are cached in Room and every screen reads local data.

### AtCoder (Phase 3)
AtCoder has no official public API used by this app. The adapter uses the documented/current
AtCoder Problems resources for submissions, contests, merged problems, and estimated models.
It declares no profile or rating capability. See [ATCODER.md](ATCODER.md) for source and
pagination details.

### Luogu (public sync — Phase 9)
Luogu public user search is currently integrated for account binding through
`GET /api/user/search?keyword=...`. The adapter is marked `EXPERIMENTAL`, parses only the
public user summary, requires an exact username match, and never stores Luogu credentials.
Public profile, rating/ELO history, problems, and contests use the first-party structured
`content-only` JSON transport for `/user/:uid`, `/user/:uid/practice`, `/problem/list`, and
`/contest/list`. The adapter applies bounded retries and per-source rate spacing. The cached
rating history is consumed by the shared Analytics and Profile surfaces alongside other judges.
Contest Center also exposes the cached Luogu contests and opens canonical Luogu contest/problem
pages from Arena. The public contest-list payload currently supplies contest metadata but no
problem membership, so Arena does not fabricate a Luogu contest problem list.

The anonymous `/record/list` response is an auth/login envelope. It is surfaced as
`AuthenticationRequired` and leaves submissions empty; password login, Cookie/Session/CSRF
handling, and auto-submit are not part of this phase. Cloud account and cross-device sync are
also deferred.

### Later
LeetCode, NowCoder, AcWing, Hydro, LibreOJ, Kattis, CodeChef, SPOJ, USACO — same pattern,
same rules.

## Sync States

`IDLE → SYNCING → SUCCESS | PARTIAL | ERROR`, per judge, surfaced in Dashboard SYSTEM STATUS
and Settings. WorkManager owns background sync; identical sync jobs are deduplicated;
network-unavailable is a normal, quiet state (never an error popup).
