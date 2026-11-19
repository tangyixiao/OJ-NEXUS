# Problem Identity and Retrieval Contract — v1

Phase 136 (G-S). This directory is the executable cross-platform contract for
problem identity and retrieval semantics. Phases 137 (Android bounded query),
138 (Windows search), 139 (Linux Qt model) and 140 (Apple SwiftUI library)
must reproduce every golden vector in `golden/` with their own implementations.

Frozen on main; changes require a version bump and updated codecs/golden files
on every consumer (mirrors the phase-131 portable contract policy).

## Identity (frozen)

- A problem is `(judge, externalId)` under **exact byte equality**: no case
  folding, no trimming, no numeric coercion. `1234A`, `1234a` and `"1234A "`
  are three different problems.
- The judge set is frozen: `local | codeforces | atcoder | luogu` (Android
  `JudgeId`). Adding a judge is a contract version bump.
- Same title on different OJs never merges; same number on different OJs never
  merges (vectors 001/002).
- A dataset with a duplicate `(judge, externalId)` pair is invalid (checker
  rejects it).
- `local` externalIds are app-minted; the LOCAL identity never syncs.
- The mapping `(judge, externalId) → local row id` is per-installation app
  state. The contract pins only that one key maps to exactly one local id per
  installation; the key — not the local id — is the cross-platform identity.
  Vectors address problems exclusively by the key string `"judge/externalId"`.

## Derived status (frozen)

`inReview → REVIEW`, else `solved → SOLVED`, else `attemptCount > 0 →
ATTEMPTED`, else `UNSOLVED`. REVIEW overrides solved and is independent of it
(a solved problem can sit in review). Status filters act on the derived value,
never on a stored status (vectors 018-021).

## Search (frozen)

- The query is **trimmed** then folded with Unicode **simple lowercase**
  (Kotlin `lowercase()`, Python `str.lower()` — NOT full case-fold: `ß`
  stays `ß`, vectors 012/013).
- A record matches when the lowered query is a **substring** of the lowered
  title **or** of the lowered externalId (search is case-insensitive even
  though identity is not; vector 011).
- An empty or whitespace-only query matches everything (vector 022).

## Filters (frozen, AND-combined)

| Filter | Semantics |
|---|---|
| `status` | equality on the derived status |
| `judge` | equality on the frozen enum id |
| `favoriteOnly` | record `favorite` must be true |
| `tag` | exact, case-sensitive membership in `tags` (vector 014) |
| `difficultyMin` / `difficultyMax` | inclusive bounds; unknown difficulty is excluded whenever either bound is set (vectors 004/024/025) |

## Sort (frozen)

Every sort ends with the **stable tie-break: code-point (UTF-8 byte) order of
the key string** `"judge/externalId"` — the acceptance's "equal sort values
keep a stable ID order". Code-point order, not UTF-16 code-unit order: vector
007 pins an astral-plane title (`U+1D400`) against a fullwidth BMP title
(`U+FF41`) precisely because the two orderings disagree there.

| Sort | Order |
|---|---|
| `UPDATED` | `updatedAt` descending (vector 006) |
| `DIFFICULTY` | nulls last, then `difficulty` ascending; `0` is a valid rating that sorts before `null` (vectors 003/005) |
| `TITLE` | lowered title code-point ascending (vectors 007/008) |
| `ATTEMPTS` | `attemptCount` descending (vector 009) |

## Dataset record schema

```json
{
  "judge": "codeforces",
  "externalId": "1234A",
  "title": "Solution to a Problem",
  "difficulty": 1200,
  "difficultySource": "CODEFORCES",
  "favorite": false,
  "solved": true,
  "attemptCount": 3,
  "inReview": false,
  "tags": ["dp", "greedy"],
  "createdAt": 1700000000000,
  "updatedAt": 1700000100000
}
```

`difficulty` is an integer or `null` (JSON floats are rejected). `difficultySource`
is optional and defaults to `"UNKNOWN"`. All other fields are required with the
types shown; the checker validates them strictly (bool is not an int).

## Vector format

`golden/cases.json` lists vector files. Each vector file:

```json
{
  "name": "001-same-title-different-oj",
  "note": "why this vector exists",
  "dataset": [ ...records... ],
  "query": {"search": null, "status": null, "judge": null, "favoriteOnly": false,
            "tag": null, "difficultyMin": null, "difficultyMax": null, "sort": "TITLE"},
  "expect": ["atcoder/abc123a", "codeforces/1234A", "codeforces/999B"]
}
```

`expect` lists key strings in the exact order the reference implementation
produces. Every `expect` in this corpus was **hand-derived** from the frozen
semantics — the gate is a real test of any implementation, not a tautology.

## Running

```sh
python3 check.py --cases golden/cases.json golden/   # gate: recompute all vectors
python3 selftest.py                                  # checker trap coverage
python3 check.py --run golden/001-same-title-different-oj.json   # one vector
```

Python 3 stdlib only; no network, no secrets. The gate runs in CI via
`.github/workflows/contracts.yml`.

## How platforms adopt

1. Parse a vector's dataset/query with the platform's real filter/sort/search
   implementation.
2. Compare the produced key order against `expect`.
3. Any divergence is a platform bug (or a contract bug — in which case stop
   and version the contract, do not bend an implementation to a broken vector).

## Version history

- v1 (2026-11-19, phase 136): initial freeze — identity, derived status,
  search, filters, sorts with the code-point stable tie-break; 25 golden
  vectors; checker self-test 15 checks.
