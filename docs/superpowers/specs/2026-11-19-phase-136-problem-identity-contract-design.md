# Phase 136 — Problem Identity and Retrieval Semantics — Design

Status: ACTIVE. This design is synthesized by the controller from the Android
reference implementation (main) and the roadmap entry, and freezes the v1
problem-identity/query contract that phases 137 (Android bounded query), 138
(Windows search), 139 (Linux Qt model) and 140 (Apple SwiftUI library) must
reproduce.

Baseline main: `395bd00` (phases 131-134 delivered; PRs #62/#66/#73/#75/#76).

## Inputs this design synthesizes

- **Roadmap** (`docs/LONG_RANGE_PLAN_121_220.md`, PHASE 136): dependencies 131;
  S + ProblemKey/ProblemModels and the three-OJ mapping. Deliverable: define
  the judge+externalId to local-UUID correspondence, favorite/tag/status
  filters, stable ordering, missing-difficulty semantics and search case
  semantics; provide the cross-platform query vectors. Acceptance G-S: same
  title on different OJs and same number on different OJs never merge; missing
  difficulty is never turned into 0; equal sort values keep a stable ID order.
- **Android identity reference**: `app/src/main/java/com/ojnexus/core/model/ProblemKey.kt`
  — stable multi-OJ identity is the pair `(judge, externalId)`; two judges may
  use the same external id string and the pair never collides; `LOCAL` problems
  use an app-side id (`JudgeId.kt`: judges are the fixed set `local`,
  `codeforces`, `atcoder`, `luogu`; the LOCAL identity never syncs).
- **Android row reference**: `core/database/entity/ProblemEntity.kt` — unique
  index on `(judge, external_id)`; `difficulty: Int?` documented "NULL means
  the judge does not define one — never 0".
- **Android retrieval reference**: `feature/problems/ProblemFilter.kt` —
  AND-combined filter (query/status/judge/favoriteOnly/tag/difficulty range),
  sorts UPDATED (updatedAt desc), DIFFICULTY (null last, then asc), TITLE
  (lowercase), ATTEMPTS (attemptCount desc). Search = trimmed lowercase
  substring over title and externalId. Status derivation in
  `core/model/TrainingModels.kt`: `inReview → REVIEW`, else `solved → SOLVED`,
  else `attemptCount > 0 → ATTEMPTED`, else `UNSOLVED`; REVIEW is independent
  of solved (a solved problem can sit in review).
- **Known Android gap the contract must pin** (the reason this phase exists):
  the current sorts tie-break on input order (`ProblemFilter.kt:59-63`), while
  the acceptance requires "equal sort values keep a stable ID order". Phase 137
  will have to add the explicit tie-break; the vectors make the target exact.
- **Contract precedent**: `contracts/portable/v1/` (phase 131) — stdlib-only
  Python checker as the executable oracle, golden corpus with `cases.json`,
  self-test, CI gate in `.github/workflows/contracts.yml`. Phase 136 reuses
  the pattern with a new root `contracts/problems/v1/`.

## Decisions

1. **Contract location and artifacts.** `contracts/problems/v1/`:
   `README.md` (the human-readable frozen semantics), `check.py` (stdlib-only
   reference implementation + vector gate), `selftest.py` (checker self-test),
   `golden/cases.json` + golden dataset/vector files. No new runtime code in
   this phase; the four platform phases build against the vectors.
2. **Identity.** A problem is identified by `(judge, externalId)` under exact
   byte equality: no case folding, no trimming, no numeric coercion. The judge
   set is the frozen enum `local|codeforces|atcoder|luogu`; adding a judge is a
   contract version bump. Same title on different OJs never merges; same
   number on different OJs never merges — both pinned by vectors. A `local`
   externalId is app-minted and its identity never syncs.
3. **Vector addressing and tie-break.** Query vectors address problems by the
   key string `judge/externalId`. The stable tie-break for every sort is
   code-point (UTF-8 byte) order of that key string — this is the "stable ID
   order" the acceptance demands, and it is data-derived so every platform can
   reproduce it deterministically. One vector deliberately pins an astral-plane
   character so a UTF-16-naive comparator fails loudly (code-point order is
   required, UTF-16 code-unit order is not).
4. **Status is derived, never stored as filter truth.** Vectors carry the
   primitives (`solved`, `attemptCount`, `inReview`) and the checker derives
   status with the frozen rule (REVIEW overrides, independent of solved).
   Filtering by status always filters on the derived value.
5. **Difficulty.** `difficulty` is an integer or `null`; `null` means the
   judge defines none and is never coerced to 0. DIFFICULTY sort: nulls last,
   then ascending. A bounded range filter (either bound set) excludes unknown
   difficulty; bounds are inclusive.
6. **Search case semantics.** The query is trimmed and folded with Unicode
   simple lowercase (Kotlin `lowercase()` / Python `str.lower()`, NOT full
   case-fold: `ß` stays `ß`); matching is a substring test over the lowered
   title and lowered externalId. An empty/whitespace query matches everything.
   Vectors pin the ß boundary (query `ss` does not match a `ß` title).
7. **Filters.** AND-combined: status equality (derived), judge equality
   (exact, lowercase enum id), `favoriteOnly` (favorite is true), tag
   membership (exact, case-sensitive, against the tag list), difficulty range
   (decision 5). All optional; absent means no constraint.
8. **Sort semantics.** UPDATED: `updatedAt` descending. DIFFICULTY: per
   decision 5. TITLE: lowered title compared by code point, then tie-break.
   ATTEMPTS: `attemptCount` descending. Every sort ends with decision 3's
   tie-break.
9. **Vector format.** `golden/cases.json` indexes vector files. Each vector:
   `{"dataset": [records...], "query": {...}, "expect": ["judge/externalId", ...]}`.
   Dataset record fields: `judge`, `externalId`, `title`, `difficulty` (int or
   null), `difficultySource` (string, default `UNKNOWN`), `favorite` (bool),
   `solved` (bool), `attemptCount` (int), `inReview` (bool), `tags` (string
   list), `createdAt`/`updatedAt` (integer epoch ms). `check.py` recomputes
   every `expect` from the dataset with the reference implementation and fails
   the gate on any mismatch — the same self-consistency model as the portable
   corpus. `selftest.py` asserts the checker rejects mutated vectors (trap
   coverage proof).
10. **Local-UUID correspondence.** The mapping `(judge, externalId) → local
    row id` is per-installation app state (Room/PersistedQueryKit/Qt store/Swift
    store respectively); the contract pins only that one key maps to exactly
    one local id per installation and that the key, not the local id, is the
    portable/cross-platform identity. Platform phases own the storage; the
    vectors never reference local ids.
11. **CI.** `contracts.yml` gains a problem-contract gate job (compile check +
    vector run + self-test) alongside the portable gate; `contracts/**` path
    filter already covers the new directory.

## Out of scope

Attempt/notes/review-session records, sync transport, local-UUID storage
format, UI surfaces (phases 137-140), portable-package integration (phase 131
already exports problem titles/tags), and any runtime code change.

## Verification shape (G-S)

- `python3 check.py --cases golden/cases.json golden/` green locally and in
  the contracts CI job (all vectors self-consistent).
- `python3 selftest.py` green (checker trap coverage).
- Platform adoption (Android first, phase 137) consumes the same vectors and
  reproduces every `expect` — that reproduction is each platform phase's
  acceptance, not this phase's.
