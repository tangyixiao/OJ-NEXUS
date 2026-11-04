# Portable workspace package contract v1

A portable workspace package moves OJ NEXUS workspace records between devices and clients. It is a record handoff format, not a full backup: accounts, profile and rating caches, sync ledgers, workspace drafts, submission jobs, source code, test inputs and credentials are excluded from portability by design. This directory ships the contract (this file), the machine-checkable schema (`schema.json`), a stdlib-only reference checker (`check.py`) and the golden corpus (`golden/`). No platform consumer ships in this phase; phases 132–135 bind import and export to this contract. Like the recovery contract, the checker and goldens are a reference oracle, not a platform proof.

## Package container

One package is one UTF-8 JSON document. There is no ZIP or other container in v1, and no encrypted or compressed container; the single-file choice removes the archive-stowaway class. The recommended file extension is `.ojportable.json`; the contract does not depend on it. Every object in the schema is closed (`additionalProperties: false`) except `extra`, duplicate JSON object keys are rejected anywhere in the document, and path-shaped strings are rejected, so path traversal and key-collision confusion are covered at the format level.

## Manifest

The root object carries exactly these members, all required:

- `formatVersion` — integer, exactly `1` in v1 (versioning policy below).
- `scopeId` — the non-credential local identifier of the exporting workspace; a string matching `^[A-Za-z0-9._:-]{1,128}$`. Provenance data only: importers never auto-bind it to a target handle or account.
- `sourcePlatform` — one of `android`, `windows`, `linux`, `macos`, `ios`.
- `generator` — object `{ name, version }`, both non-empty strings, identifying the exporting program.
- `createdAtEpochMs` — UTC epoch milliseconds.
- `counts` — object with the integer members `problems`, `attempts`, `notes`, `reviews`, `sessions`. Declared counts must reconcile with the actual array lengths (honest-counts rule); a mismatch is `COUNTS_MISMATCH`.
- `problems`, `attempts`, `notes`, `reviews`, `sessions` — the five collections, each an array of entity records.

## Timestamps and day keys

All timestamps are UTC epoch-millisecond integers. All day keys are `"YYYY-MM-DD"` strings captured in the writer's local zone at write time and preserved verbatim on import: never recomputed, never re-zoned, never re-derived from timestamps. The schema checks day-key shape only (`^\d{4}-\d{2}-\d{2}$`); v1 does not validate calendar correctness.

## Entity records

Every entity record in every collection carries:

- `stableId` — string, unique inside its collection, matching `^[A-Za-z0-9._:-]{1,128}$`.
- `sourceScopeId` — string mirroring a real scopeId shape; it may differ from the manifest `scopeId` when the record was itself imported earlier.
- `origin` — `scope` when the record is attributable to `sourceScopeId`, or `unassigned` for legacy or unclear provenance. An `unassigned` record is never attributed to the importing account: it is rendered as unassigned and never bound to the current handle. `scope` provenance is attribution data, not a binding either.
- `extra` — optional; the single schema-permitted extension point (below).

References are problem stableIds: a required `problemId` on attempts, notes and reviews, and an optional `problemIds` array on sessions. A reference to a stableId absent from `problems` is a missing-reference finding (`MISSING_REFERENCE`), never a silent drop.

`extra` is an object with string keys whose values are JSON scalars or arrays of scalars, at most two levels deep. Within major version 1 it is the only permitted extension point: unknown-but-declared content under `extra` is preserved value-for-value through import and export round-trips and carries stored-only semantics — persisted, never interpreted, never promoted into domain fields, never normalized. Any other unknown key on an entity object is rejected (`UNKNOWN_FIELD`, naming the offending key).

## Collections (v1 minimum fields)

`problems` is the reference anchor, and `problems` and `attempts` are the only collections all future consumers must accept. The common entity fields above apply everywhere; what follows are the per-collection v1 minimums.

- `problems[]`: `judge` (`codeforces|atcoder|luogu`), `externalId` (1–64 characters over the judge-safe charset `^[A-Za-z0-9._:-]{1,64}$`), `title`, `sourceUrl` (optional; must match `^https?://`), `solved` (boolean), `firstSolvedAtEpochMs` (optional), `difficulty` (optional integer; absent stays absent, never coerced to 0), `tags` (optional array of strings), `dayKey`.
- `attempts[]`: `problemId` (must exist in `problems`), `timestampEpochMs`, `dayKey`, `verdict` (`AC|WA|TLE|MLE|RE|CE|PE|OTHER`), `rawVerdict` (optional verbatim judge verdict), `language` (optional), `durationMin` (optional integer minutes), `externalSubmissionId` (optional), `note` (optional). Exporters map unmappable raw verdicts to `OTHER` plus `rawVerdict` on the way out; the schema admits only the verdict enum, so an unknown verdict value in a package is a rejection, not a coercion.
- `notes[]`: `problemId` (must exist in `problems`), `keyInsight`, `implementationNotes`, `complexity`, `general` (strings; empty allowed), `updatedEpochMs`, `dayKey`. One note per problem per package: a duplicate `problemId` inside `notes` is a `DUPLICATE_ID` rejection.
- `reviews[]`: `problemId` (must exist in `problems`), `stage` (positive integer), `dueEpochMs`, `dueDayKey`, `lastResult` (optional; `PASS|HARD|FAIL|SKIP`), `lastReviewedEpochMs` (optional), `createdEpochMs`, `dayKey`. The interval ladder is not portable data; importers schedule with their own engine.
- `sessions[]`: `type` (`PRACTICE|FOCUS|UPSOLVE|REVIEW`), `state` (`PLANNED|RUNNING|PAUSED|FINISHED|CANCELLED`), `startedEpochMs`, `finishedEpochMs` (optional), `totalPausedMs`, `targetDurationMin` (optional), `problemIds` (optional array of problem stableIds), `dayKey`.

## Capability negotiation

Import tiers are target-side: they describe what the importing platform did with each record, never what the package claims.

- `editable` — the record landed in a live editable store.
- `storedOnly` — the record was understood and persisted in the bounded portable archive; used where no domain UI or table exists yet.
- `rejected` — the record was unknown or incompatible; the record and the reason are reported.

Every import reports one count per tier. v1 fixes the vocabulary, the counts duty, and the rule that a record is never downgraded to `rejected` without its reason: nothing is silently dropped. Phases 132–135 must run the mixed golden package through import and export and show a lossless round-trip for accepted records (canonically re-encoded bytes identical).

## Rejection behavior

- Unknown keys — on the manifest, inside `generator` or `counts`, or on an entity object outside `extra` — are rejected as `UNKNOWN_FIELD`, and the error names the offending key; never a bare category. A credential-shaped key (`token`) anywhere is an unknown-key rejection.
- Duplicate JSON object keys anywhere make the document invalid at strict decode (`INVALID_INPUT`); last-key-wins is an attack, not a default.
- Size limits (`SIZE_LIMIT`): package file at most 64 MiB, each entity array at most 50 000 records, any string value at most 16 KiB (16 384 UTF-8 bytes; the schema's `maxLength` of 16384 counts code points, the checker enforces the byte bound).
- Any string value matching `^[A-Za-z]:\\|^/|\.\./|^\.\.$` is `PATH_UNSAFE`; v1 has no file fields, so this is defense in depth across all strings.

## Error categories

`INVALID_INPUT` (not UTF-8, malformed or truncated JSON, not a JSON object, duplicate JSON keys), `UNSUPPORTED_VERSION` (formatVersion other than 1), `UNKNOWN_FIELD`, `DUPLICATE_ID`, `MISSING_REFERENCE`, `SIZE_LIMIT`, `PATH_UNSAFE`, `COUNTS_MISMATCH`.

## Versioning policy

`formatVersion` is a single integer and must be exactly `1`. A future major version is a whole-package reject (`UNSUPPORTED_VERSION`), never a best-effort decode, and v1 defines no automatic upgrade path; v2 will define its own stance. Within major version 1 the only extension point is `extra` with stored-only semantics; everything else unknown is rejected. A later phase that introduces new semantics (141, 146, 151, 191 or beyond) must bump `formatVersion` and update `schema.json` and the golden corpus together in the same change: schema, contract and goldens never drift apart.

## Reference checker

`check.py` is Python 3 standard library only (no pip, no network). It strictly decodes the package (duplicate-key rejection), validates it with a deliberate draft-07 subset driven by `schema.json`, and enforces the contract rules the subset cannot express: size limits, reference integrity, per-collection duplicate detection, counts reconciliation, path-shape rejection, verdict/enum admission, and the `extra` value and depth constraints. The subset it implements: `type`, `required`, `properties`, `additionalProperties: false`, `enum`, `pattern`, `minLength`/`maxLength`, `minimum`/`maximum`, `items`, `minItems`, `maxItems`.

`check.py <file>` exits 0 for a valid package and 1 with the error category otherwise. `check.py --cases golden/cases.json golden/` runs the whole corpus and exits nonzero on any deviation. `check.py --identity <file>` prints the SHA-256 of the canonical re-encode of a valid package (sorted keys, fixed separators); it is deterministic across runs and stable across re-checks of the same package.

Golden packages live in `golden/`; `golden/cases.json` names each case file with its expectation (`valid`, or `invalid` with the error category) and cites the gate duty it covers. All fixtures are synthetic and mark themselves inside the package through schema-permitted provenance (for example the generator identity or the `extra` extension), never through schema-unknown keys.
