# Portable Workspace v1 — Developer Guide

## 1. Purpose

A portable workspace v1 package is one UTF-8 JSON document that moves OJ NEXUS workspace records (problems, attempts, notes, reviews, sessions) between devices and clients. It is a record handoff format, not a full backup. Phase 131 delivers the CONTRACT ONLY: the human contract (`contracts/portable/v1/README.md`), the machine-checkable schema (`schema.json`), the stdlib-only reference checker (`check.py`), the golden corpus (`golden/`) and CI wiring. No platform consumer ships in 131: this phase contains no Android, Windows, Linux, macOS or iOS import/export code, and no import or export behavior is claimed. Phases 132–135 bind import and export to this contract. The checker and goldens are a reference oracle, not a platform proof.

## 2. Scope and non-goals

- One package = one UTF-8 JSON document. The recommended file extension is `.ojportable.json`; it is not required and the contract does not depend on it.
- No ZIP or other container, no compression, no encryption. The single-file choice removes the archive-stowaway class.
- Excluded from portability by design: credentials, cookies, tokens, account configs, source code, test inputs, judge profiles, rating caches, sync ledgers, workspace drafts, submission jobs.
- No automatic version upgrade path (see §12).
- Every schema object is closed (`additionalProperties: false`) except `extra`. Duplicate JSON object keys anywhere are invalid. Path-shaped strings are rejected (§7).

## 3. Package shape

Complete valid minimal package: `scopeId` `scope.alpha`, one problem `cf.1A`, one attempt `att.1`, empty `notes`/`reviews`/`sessions`, and declared counts equal to array lengths. No string contains `../` and no key is credential-shaped. The reference checker reports `VALID` (exit 0) for this document.

```json
{
  "formatVersion": 1,
  "scopeId": "scope.alpha",
  "sourcePlatform": "android",
  "generator": { "name": "ojnexus-export", "version": "1.0.0" },
  "createdAtEpochMs": 1735689600000,
  "counts": { "problems": 1, "attempts": 1, "notes": 0, "reviews": 0, "sessions": 0 },
  "problems": [
    {
      "stableId": "cf.1A",
      "sourceScopeId": "scope.alpha",
      "origin": "scope",
      "judge": "codeforces",
      "externalId": "1A",
      "title": "Theatre Square",
      "solved": true,
      "dayKey": "2025-01-01"
    }
  ],
  "attempts": [
    {
      "stableId": "att.1",
      "sourceScopeId": "scope.alpha",
      "origin": "scope",
      "problemId": "cf.1A",
      "timestampEpochMs": 1735689600000,
      "verdict": "AC",
      "dayKey": "2025-01-01"
    }
  ],
  "notes": [],
  "reviews": [],
  "sessions": []
}
```

## 4. Manifest

The root object carries exactly these members, all required.

| Field | Type / constraint | Semantics |
| --- | --- | --- |
| `formatVersion` | integer, exactly `1` | Version gate; see §12. |
| `scopeId` | string, `^[A-Za-z0-9._:-]{1,128}$` | Non-credential local identifier of the exporting workspace. Provenance data only; importers never auto-bind it to a target handle or account. |
| `sourcePlatform` | enum: `android`, `windows`, `linux`, `macos`, `ios` | Exporting platform label. |
| `generator` | object `{ name, version }` | Both non-empty strings; identifies the exporting program. |
| `createdAtEpochMs` | integer, `>= 0` | UTC epoch milliseconds. |
| `counts` | object `{ problems, attempts, notes, reviews, sessions }` | Non-negative integers. Honest-counts duty: each declared count must equal the actual array length; a mismatch is `COUNTS_MISMATCH`. |
| `problems`, `attempts`, `notes`, `reviews`, `sessions` | arrays | The five collections (§6). |

## 5. Entity records

Every record in every collection carries these fields.

| Field | Rule |
| --- | --- |
| `stableId` | String, `^[A-Za-z0-9._:-]{1,128}$`, unique inside its collection. A duplicate in a collection is `DUPLICATE_ID`. |
| `sourceScopeId` | String mirroring the `scopeId` shape. May differ from the manifest `scopeId` when the record was itself imported earlier. |
| `origin` | `scope` (attributable to `sourceScopeId`) or `unassigned` (legacy / unclear provenance). An `unassigned` record is never attributed to the importing account: it is rendered as unassigned and never bound to the current handle. `scope` is attribution data, not a binding either. |
| `extra` | Optional. The ONLY extension point. An object with string keys whose values are JSON scalars or flat arrays of scalars, at most 2 levels deep. Stored-only semantics: persisted, never interpreted, never promoted into domain fields, never normalized; unknown-but-declared content is preserved value-for-value. Any other unknown key on an entity object is rejected (`UNKNOWN_FIELD`). |

Duplicate JSON object keys anywhere make the document invalid at strict decode (`INVALID_INPUT`); last-key-wins is not a default. References are problem `stableId`s: a required `problemId` on attempts, notes and reviews, and an optional `problemIds` array on sessions. A reference to a `stableId` absent from `problems[].stableId` is `MISSING_REFERENCE`, never a silent drop.

## 6. Collections

`problems` is the reference anchor; `problems` and `attempts` are the only collections all future consumers must accept. Every record also carries the common entity fields from §5 (`stableId`, `sourceScopeId`, `origin`, optional `extra`); those rows appear in each table for completeness. All timestamps are UTC epoch-millisecond integers. All `dayKey` / `dueDayKey` values are `"YYYY-MM-DD"` strings captured in the writer's local zone at write time and preserved verbatim — never recomputed, never re-zoned, never re-derived from timestamps. The schema checks shape only (`^\d{4}-\d{2}-\d{2}$`); v1 does not validate calendar correctness.

### `problems[]`

| Field | Required | Constraint |
| --- | --- | --- |
| `stableId`, `sourceScopeId`, `origin` | yes | §5 |
| `extra` | no | §5 |
| `judge` | yes | `codeforces` \| `atcoder` \| `luogu` |
| `externalId` | yes | `^[A-Za-z0-9._:-]{1,64}$` (1–64, judge-safe charset) |
| `title` | yes | string |
| `sourceUrl` | no | `^https?://` (http/https) |
| `solved` | yes | boolean |
| `firstSolvedAtEpochMs` | no | integer, `>= 0` |
| `difficulty` | no | integer; absent stays absent, never coerced to `0` |
| `tags` | no | array of strings |
| `dayKey` | yes | `^\d{4}-\d{2}-\d{2}$` |

### `attempts[]`

| Field | Required | Constraint |
| --- | --- | --- |
| `stableId`, `sourceScopeId`, `origin` | yes | §5 |
| `extra` | no | §5 |
| `problemId` | yes | must exist in `problems[].stableId` |
| `timestampEpochMs` | yes | integer, `>= 0` |
| `dayKey` | yes | `^\d{4}-\d{2}-\d{2}$` |
| `verdict` | yes | `AC` \| `WA` \| `TLE` \| `MLE` \| `RE` \| `CE` \| `PE` \| `OTHER`. An unknown verdict is a REJECTION (`UNKNOWN_FIELD`), not a coercion. Exporters map unmappable raw verdicts to `OTHER` plus `rawVerdict` on the way out. |
| `rawVerdict` | no | string, verbatim judge verdict |
| `language` | no | string |
| `durationMin` | no | integer, `>= 0` |
| `externalSubmissionId` | no | string |
| `note` | no | string |

### `notes[]`

| Field | Required | Constraint |
| --- | --- | --- |
| `stableId`, `sourceScopeId`, `origin` | yes | §5 |
| `extra` | no | §5 |
| `problemId` | yes | must exist in `problems[].stableId`; one note per problem per package (a duplicate `problemId` is `DUPLICATE_ID`) |
| `keyInsight` | yes | string, empty allowed |
| `implementationNotes` | yes | string, empty allowed |
| `complexity` | yes | string, empty allowed |
| `general` | yes | string, empty allowed |
| `updatedEpochMs` | yes | integer, `>= 0` |
| `dayKey` | yes | `^\d{4}-\d{2}-\d{2}$` |

### `reviews[]`

| Field | Required | Constraint |
| --- | --- | --- |
| `stableId`, `sourceScopeId`, `origin` | yes | §5 |
| `extra` | no | §5 |
| `problemId` | yes | must exist in `problems[].stableId` |
| `stage` | yes | positive integer (`>= 1`) |
| `dueEpochMs` | yes | integer, `>= 0` |
| `dueDayKey` | yes | `^\d{4}-\d{2}-\d{2}$` |
| `lastResult` | no | `PASS` \| `HARD` \| `FAIL` \| `SKIP` |
| `lastReviewedEpochMs` | no | integer, `>= 0` |
| `createdEpochMs` | yes | integer, `>= 0` |
| `dayKey` | yes | `^\d{4}-\d{2}-\d{2}$` |

The interval ladder is NOT portable data; importers schedule with their own engine.

### `sessions[]`

| Field | Required | Constraint |
| --- | --- | --- |
| `stableId`, `sourceScopeId`, `origin` | yes | §5 |
| `extra` | no | §5 |
| `type` | yes | `PRACTICE` \| `FOCUS` \| `UPSOLVE` \| `REVIEW` |
| `state` | yes | `PLANNED` \| `RUNNING` \| `PAUSED` \| `FINISHED` \| `CANCELLED` |
| `startedEpochMs` | yes | integer, `>= 0` |
| `finishedEpochMs` | no | integer, `>= 0` |
| `totalPausedMs` | yes | integer, `>= 0` |
| `targetDurationMin` | no | integer, `>= 0` |
| `problemIds` | no | array of problem `stableId`s; each must exist in `problems` |
| `dayKey` | yes | `^\d{4}-\d{2}-\d{2}$` |

## 7. Size limits

| Limit | Value |
| --- | --- |
| Package file | at most 64 MiB (64 × 1024 × 1024 bytes, inclusive); checked before decode |
| Each entity collection | at most 50000 records |
| Every string value (and object key) | at most 16 KiB (16384 UTF-8 bytes) |

Path-shaped string values are rejected as `PATH_UNSAFE`: a string containing `../`, a leading `/`, a leading `X:\` (drive letter + colon + backslash), or exactly `..`. v1 has no file fields, so this is defense in depth across all strings. The schema's `maxLength` of 16384 counts code points; the checker enforces the UTF-8 byte bound.

## 8. Error categories

The checker reports exactly one category per rejection. Triggers:

| Category | Trigger |
| --- | --- |
| `INVALID_INPUT` | Malformed or truncated UTF-8 / JSON, duplicate JSON object keys anywhere, non-object root, or missing / non-integer `formatVersion`. |
| `UNSUPPORTED_VERSION` | `formatVersion` is an integer but not `1`. |
| `UNKNOWN_FIELD` | Any schema-shape violation: unknown key, type / enum / pattern / required / `additionalProperties` violation, credential-shaped key, or `extra` value deeper than 2 levels. |
| `DUPLICATE_ID` | Duplicate `stableId` inside a collection, or duplicate `problemId` inside `notes[]`. |
| `MISSING_REFERENCE` | A `problemId` (attempts, notes, reviews) or a `problemIds[]` entry (sessions) absent from `problems[].stableId`. |
| `SIZE_LIMIT` | Package over 64 MiB, a collection over 50000 records, or a string over 16 KiB. |
| `PATH_UNSAFE` | A string containing `../`, a leading `/`, a leading `X:\`, or exactly `..`. |
| `COUNTS_MISMATCH` | A declared `counts` value disagrees with the actual array length. |

On an unknown-key rejection the checker names the offending key in the detail line; it never reports a bare category. Precedence is fixed by `check.py` (size before decode, decode/version before shape, shape before references, references before counts), so a malformed document cannot be reclassified by a later rule.

## 9. Running the checker

`check.py` is a Python 3 stdlib-only reference implementation; no pip install and no network. All commands are relative to the repo root. Three modes:

```
python3 contracts/portable/v1/check.py <file>
python3 contracts/portable/v1/check.py --cases contracts/portable/v1/golden/cases.json contracts/portable/v1/golden/
python3 contracts/portable/v1/check.py --identity <file>
```

- Single-file mode prints `VALID` on success; on rejection it prints the category, a JSON-path-ish location and a detail line to stderr.
- `--cases` runs the golden corpus and exits 0 only if every case matches its expected valid/invalid outcome and expected error category.
- `--identity` prints the SHA-256 of the canonical re-encode: `json.dumps` with sorted keys, fixed `(",", ":")` separators, UTF-8, `ensure_ascii=False`, and a trailing newline. This is the deterministic byte form used for round-trip checks.

`--schema PATH` is optional; by default the checker loads `schema.json` next to itself. Exit codes: `0` = valid package / every case matched; `1` = invalid package / at least one case deviated; `2` = configuration error (bad arguments, unreadable file, missing or unusable schema, malformed cases file).

## 10. Golden corpus

The machine-readable corpus lives at [contracts/portable/v1/golden/](../contracts/portable/v1/golden/cases.json); each fixture is synthetic and marked through schema-permitted provenance, never through a schema-unknown key. `cases.json` names each case file with its expectation (`valid`, or `invalid` plus an error category) and cites the gate duty it covers.

- Positive: `PW-P01` — a mixed package (problems across all three judges, attempts from two platforms, one note, two reviews, one session, one `extra` extension, one `unassigned`-origin record).
- Negative package files: `PW-N01` (unknown entity field outside `extra`), `PW-N02` (`formatVersion` 2), `PW-N03` (malformed JSON), `PW-N04` (unknown manifest key), `PW-N05` (attempt referencing absent `problemId`), `PW-N06` (duplicate problem `stableId`), `PW-N09` (over-size string), `PW-N10` (path-shaped string), `PW-N11` (unknown verdict), `PW-N13` (credential-shaped key), `PW-N15` (truncated bytes), plus the missing-`formatVersion`, counts-mismatch and duplicate-JSON-object-keys cases.
- `PW-N08` (re-check identity stability of `PW-P01`) and `PW-N12` (`origin: unassigned` accepted but unattributable) are checker self-tests / assertions, not package files.

`.github/workflows/contracts.yml` runs the corpus in CI on Python 3 with no dependencies; any deviation fails the workflow.

## 11. Capability negotiation for phases 132–135

Phase 131 ships no importer or exporter, but the contract fixes the negotiation model that phases 132–135 must bind. Each importer classifies every record into one tier and reports three honest counts (one per tier):

- `editable` — stored in the live editable store.
- `storedOnly` — understood and persisted in a bounded portable archive where no domain UI / table exists yet.
- `rejected` — unknown or incompatible; the record and its reason are reported.

Nothing is silently dropped. A record is never downgraded to `rejected` without its reason. `problems` and `attempts` are the only collections all future consumers must accept. Phases 132–135 must run the mixed golden package through import then export and show a lossless round-trip for accepted records: the canonically re-encoded bytes are identical.

## 12. Versioning

`formatVersion` is a single integer and must be exactly `1` (exact major match). A future major version is a whole-package rejection (`UNSUPPORTED_VERSION`), never a best-effort decode; v1 defines no automatic upgrade path. Within major version 1 the only extension point is `extra` (§5) with stored-only semantics: only schema-declared extensions are preserved losslessly, everything else unknown is rejected. v2 defines its own stance. A later phase introducing new semantics must bump `formatVersion` and update `schema.json` and the golden corpus together in the same change; schema, contract and goldens never drift apart.

## 13. Links

- [Portable v1 contract](../contracts/portable/v1/README.md)
- [schema.json](../contracts/portable/v1/schema.json)
- [check.py](../contracts/portable/v1/check.py)
- [Golden corpus cases.json](../contracts/portable/v1/golden/cases.json)
- [Phase 131 design spec](superpowers/specs/2026-11-01-phase-131-portable-workspace-design.md)
- [Phase 131 implementation plan](superpowers/plans/2026-11-01-phase-131-portable-workspace.md)
- [Phase 131 verification record](verification/phase-131-portable-contract.md) (added by a sibling PR)
