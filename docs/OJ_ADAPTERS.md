# OJ NEXUS — OJ Adapter Spec

All judge-specific behavior lives behind one boundary. Core code sees only the unified model.

## Interface Sketch (finalized at implementation)

```kotlin
interface JudgeAdapter {
    val judgeId: JudgeId
    val capabilities: Set<JudgeCapability>   // PROFILE, RATING, SUBMISSIONS, PROBLEMS, CONTESTS

    suspend fun fetchProfile(handle: String): JudgeUser
    suspend fun fetchRatingHistory(handle: String): List<RatingChange>
    suspend fun fetchSubmissions(handle: String, since: Instant?): List<UnifiedSubmission>
    suspend fun fetchProblems(cursor: ProblemCursor?): ProblemPage
    suspend fun fetchContests(from: Instant?, to: Instant?): List<UnifiedContest>
}
```

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

### AtCoder (Phase 4)
No official API. Use reliable public sources (e.g., the AtCoder Problems dataset) for
submissions/contests; keep all quirks inside `AtCoderAdapter`.

### Luogu (Phase 4)
Unofficial/public web endpoints may change without notice. `LuoguAdapter` must:
- treat every response as suspicious (strict parsing, defensive defaults),
- return a clear `AdapterError` on any structure drift,
- never store Luogu credentials,
- remain fully removable without touching core.

### Later
LeetCode, NowCoder, AcWing, Hydro, LibreOJ, Kattis, CodeChef, SPOJ, USACO — same pattern,
same rules.

## Sync States

`IDLE → SYNCING → SUCCESS | PARTIAL | ERROR`, per judge, surfaced in Dashboard SYSTEM STATUS
and Settings. WorkManager owns background sync; identical sync jobs are deduplicated;
network-unavailable is a normal, quiet state (never an error popup).
