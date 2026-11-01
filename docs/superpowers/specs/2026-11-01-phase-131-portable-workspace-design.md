# Phase 131 — Portable Workspace v1 contract

Status: implementation design under the authorized balanced 121–220 roadmap. The controller
selects these implementation details; they are not additional user-approved requirements.
Scope: `contracts/portable/v1/` only — a versioned JSON package contract, a machine-checkable
schema, a stdlib-only reference checker, and golden packages (positive + negative). No platform
consumer ships in this phase; 132–135 bind to it. Baseline: main `6eddcf5` (post v1.0.4 +
linux-v0.1.1-rc.1 publication).

## Inputs this design synthesizes

- Cross-platform data inventory (2026-11-01 research pass): problems exist on Android
  (ProblemKey = judge+externalId, 14 fields), Windows (7 fields, same natural key), Linux
  (cache blob); Apple has none. Notes: Android only. Attempts: all four, shapes diverge
  (Android unified 17 fields with normalized verdict enum + raw_verdict; Windows per-judge
  tables; Linux blob + projection; Apple 9 fields). Reviews: Android + Windows only, result
  vocabularies already match (PASS/HARD/FAIL/SKIP). Sessions: Android only. Accounts,
  profiles/ratings caches, sync ledgers, workspace drafts and submission jobs are excluded
  from portability.
- Constraint pass: PLAN §2.1 invariants (portable ≠ full backup; UTC timestamps; historical
  day keys never recomputed; credentials never portable), DATA_SAFETY, snapshot
  manifest/container prior art, import-conflict report, recovery-contract house style
  (versioned corpus, `synthetic: true`, explicit expected outcomes).

## Package container and identity

One package = one UTF-8 JSON document (no ZIP in v1). Single-file choice removes the archive
stowaway class; the schema still rejects path-shaped strings and duplicate JSON keys so the
G-S path-traversal duty is covered at the format level. Recommended file extension
`.ojportable.json`; the contract does not depend on it.

- `formatVersion`: integer, exactly `1` in v1. Future major = whole-package reject
  (unsupported-version), never a best-effort decode. Minor extension policy: only fields the
  schema explicitly allows are preserved losslessly (see `extra` below); everything else
  unknown is rejected with the offending key named.
- `scopeId`: non-credential local identifier of the exporting workspace (string, 1–128 chars,
  `[A-Za-z0-9._:-]`). It is provenance data only — never auto-bound to a target handle.
- `sourcePlatform`: one of `android|windows|linux|macos|ios`.
- `generator`: `{ "name": string, "version": string }`.
- `createdAtEpochMs`: UTC epoch millis.
- `counts`: per-collection integer counts; importers must reconcile counts against actual
  array lengths (honest-counts rule).
- Duplicate JSON object keys anywhere → invalid (strict decode; last-key-wins is an attack,
  not a default).

## Entity model (v1 minimum)

All timestamps are UTC epoch millis integers. All day keys are `"YYYY-MM-DD"` strings
captured in the writer's local zone at write time and preserved verbatim on import (never
recomputed). Every entity record carries:

- `stableId` (string, unique inside its collection; charset `[A-Za-z0-9._:-]`, 1–128)
- `sourceScopeId` (string, mirrors a real scopeId shape; may differ from the manifest scopeId
  when records were themselves imported earlier)
- `origin`: `scope` (attributable to sourceScopeId) or `unassigned` (legacy/unclear — must
  never be attributed to the importing account; rendered as unassigned, never bound to the
  current handle)
- `extra` (optional object, string keys → JSON scalars/arrays of scalars): the only permitted
  extension point; unknown-but-declared content here is preserved byte-exactly and treated as
  stored-only semantics. Any other unknown key on an entity object → rejected.
- References are arrays of problem stableIds (`problemIds`) where applicable; a reference to
  an ID absent from the package is a missing-reference finding, not a silent drop.

Collections and minimum fields:

- `problems[]`: `stableId`, `judge` (enum `codeforces|atcoder|luogu`), `externalId`
  (1–64, judge-safe charset), `title`, `sourceUrl` (optional, must be http/https), `solved`
  (bool), `firstSolvedAtEpochMs` (optional), `difficulty` (optional int, absent stays absent —
  never coerced to 0), `tags` (optional string array), `dayKey`. Problems are the reference
  anchor; `problems` and `attempts` are the only collections all future consumers must accept.
- `attempts[]`: `stableId`, `problemId` (must exist in `problems`), `timestampEpochMs`,
  `dayKey`, `verdict` (enum `AC|WA|TLE|MLE|RE|CE|PE|OTHER`), `rawVerdict` (optional verbatim
  judge verdict), `language` (optional), `durationMin` (optional), `externalSubmissionId`
  (optional), `note` (optional). Exporters map unmappable raw verdicts to `OTHER` + `rawVerdict`
  on the way out; the schema only admits the enum, so an unknown verdict value in a package is
  a rejection, not a coercion.
- `notes[]`: `problemId` (must exist), `keyInsight`, `implementationNotes`, `complexity`,
  `general` (strings; empty allowed), `updatedEpochMs`, `dayKey`. One note per problem per
  package (duplicate problemId in `notes` → duplicate-ID rejection).
- `reviews[]`: `problemId` (must exist), `stage` (positive int), `dueEpochMs`, `dueDayKey`,
  `lastResult` (optional enum `PASS|HARD|FAIL|SKIP`), `lastReviewedEpochMs` (optional),
  `createdEpochMs`, `dayKey`. The interval ladder is NOT portable data — importers schedule
  with their own engine.
- `sessions[]`: `stableId`, `type` (enum `PRACTICE|FOCUS|UPSOLVE|REVIEW`), `state`
  (enum `PLANNED|RUNNING|PAUSED|FINISHED|CANCELLED`), `startedEpochMs`, `finishedEpochMs`
  (optional), `totalPausedMs`, `targetDurationMin` (optional), `problemIds` (optional array),
  `dayKey`.

## Capability negotiation

Import-time tiers (target-side, not package-side): `editable` (landed in a live editable
store), `storedOnly` (understood, persisted in the bounded portable archive — used when no
domain UI/table exists yet), `rejected` (unknown/incompatible; the record and the reason are
reported, never silently dropped). Every import reports three counts. v1's contract duty:
the vocabulary, the counts duty, and the rule that a record is never downgraded to `rejected`
without its reason. 132–135 must run the mixed golden package through import→export and show
lossless round-trip for accepted records (canonically re-encoded bytes identical).

## Reference checker

`contracts/portable/v1/check.py` — Python 3 stdlib only (no pip). Strict JSON decode
(duplicate-key rejection via object_pairs_hook), a deliberate draft-07 subset validator
driven by `schema.json`, plus contract rules the subset cannot express: size limits
(package ≤ 64 MiB, entity arrays ≤ 50 000 records, string fields ≤ 16 KiB), reference
integrity, per-collection duplicate detection, counts reconciliation, path-shape rejection
(any string value matching `^[A-Za-z]:\\|^/|\.\./|^\.\.$` inside a path-ish context —
v1 has no file fields, so this is defense in depth on all strings), verdict/enum
admission, `extra` depth limit (2 levels), idempotency identity = canonical re-encode
(sorted keys, fixed separators) SHA-256 reported by `--identity`.

`check.py` runs over `golden/cases.json`: each case names a package file and expects
`valid` or `invalid` with an error category (`INVALID_INPUT`, `UNSUPPORTED_VERSION`,
`UNKNOWN_FIELD`, `DUPLICATE_ID`, `MISSING_REFERENCE`, `SIZE_LIMIT`, `PATH_UNSAFE`,
`COUNTS_MISMATCH`). Exit nonzero on any deviation; `--identity` prints the canonical hash
of a valid package.

## Golden packages

`contracts/portable/v1/golden/` with `cases.json` (caseId, file, expect, errorCategory —
house style mirrors `contracts/recovery/v1/vectors.json`, all fixtures marked `synthetic: true`
inside the package where the schema expects provenance). Positive: `PW-P01` mixed package —
problems across all three judges, attempts from two sourcePlatforms, one note, two reviews,
one session, one `extra` extension, one `unassigned`-origin record. Negatives (one minimal
package each, derived from PW-P01): `PW-N02` formatVersion 2; `PW-N03` malformed JSON;
`PW-N04` unknown manifest key; `PW-N05` attempt referencing absent problemId;
`PW-N06` duplicate problem stableId; `PW-N08` is a positive-side property (re-check of PW-P01
must report the same identity hash — encoded as a check.py self-test, not a file);
`PW-N09` over-size record (16 KiB string); `PW-N10` path-shaped string in a free-text field;
`PW-N11` unknown verdict value; `PW-N12` `origin: unassigned` accepted but asserted
unattributable (positive variant in PW-P01 + checker assertion); `PW-N13` credential-shaped
key (`token`) anywhere → UNKNOWN_FIELD reject; `PW-N15` truncated bytes; `PW-N01` unknown
entity field (not under `extra`); missing `formatVersion`; counts mismatch (declared 3,
actual 2). Duplicate JSON object keys case. All cases cite the G-S duty they cover.

## Explicit non-goals

No platform consumer changes (132–135 bind later). No encrypted/compressed container. No
credential, source-code, input, account-config, workspace-draft, judge-profile, rating-cache
or sync-ledger fields. No automatic version upgrade path (v2 will define its own stance).
The checker is a reference implementation, not a platform proof — the same separation the
recovery contract uses.

## Verification plan

G-S on this phase = checker green over all golden cases (positive + negative), `--identity`
deterministic across runs, CI wiring via a minimal `.github/workflows/contracts.yml`
(ubuntu, python3 stdlib, runs check.py — keeps goldens continuously honest), document-phase
checks (numbering/links), independent whole-branch review, verification record
`docs/verification/phase-131-portable-contract.md`. Android gates unaffected (no app/
changes), but PR CI runs them anyway.
