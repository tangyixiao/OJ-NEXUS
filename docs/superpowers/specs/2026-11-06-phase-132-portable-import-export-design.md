# Phase 132 — A · Android Portable Workspace Import/Export

Status: implementation design under the authorized balanced 121–220 roadmap.
The controller selects these implementation details; they are not additional
user-approved requirements.

Scope: a Portable Workspace import/export entry in the Android app, separate
from whole-database backup; read-only preview; user-confirmed transactional
apply; cancellable system-file (SAF) access; lossless round-trip of the mixed
golden package; idempotent reimport; capability negotiation with saved state.

Baseline: main `5a8134723680f9743d7e59f9a3244c4ced55fe8a` (phase 131 merged).

## Inputs this design synthesizes

- `docs/LONG_RANGE_PLAN_121_220.md:259` — capability-negotiation duty:
  distinguish 可编辑 / 已理解但仅保存 / 未知或不兼容须拒绝; phases 132–135 all
  verify the SAME mixed golden package import→export lossless; with no domain
  UI use a bounded domain-archive layer, report saved/editable/rejected counts,
  never silently drop rows while claiming full success.
- `docs/LONG_RANGE_PLAN_121_220.md:261-264` — deps 127, 131; scope A-CORE
  repository, settings and system file selection; deliver "新增与整库备份分开的
  Portable Workspace 入口；预览数量、来源、跳过与冲突，用户确认后事务应用，支持系统文件访问取消";
  accept "G-A；同一混合 golden 包无损往返，重复导入幂等；预览/取消不修改数据库；
  越界字段拒绝；合成训练记录标注测试来源，暂不可编辑项有保存状态".
- `contracts/portable/v1/` (phase 131) — `README.md`, `schema.json`,
  `check.py`, `golden/cases.json`, `golden/pw-p01.json`. The mixed golden
  `pw-p01.json` canonical identity is
  `81bb13c1746381f2d7f95ae69e421503dcc898aae823222e8a3caf40d55b8d44`.
- `docs/superpowers/specs/2026-11-01-phase-131-portable-workspace-design.md`
  — capability tiers `editable` / `storedOnly` / `rejected`.
- `docs/PORTABLE_WORKSPACE.md` — package contract semantics.
- `app/src/main/java/com/ojnexus/feature/settings/SettingsScreen.kt` — SAF
  `CreateDocument`/`OpenDocument` launchers, `importSource` rememberSaveable,
  and the existing preview→confirm→apply import dialog (whole-DB backup).
  Phase 132 copies this pattern but owns a SEPARATE entry, dialog and state.
- `app/src/main/java/com/ojnexus/core/data/repository/BackupRepository.kt` —
  whole-DB backup. It MUST NOT be modified by phase 132.
- `AGENTS.md` — AGP built-in Kotlin (no standalone kotlin.android plugin),
  every UI string in `res/values/strings.xml`, design-system tokens only,
  `BUILD SUCCESSFUL` gate, conventional commits, no secrets.

## Package container and identity

One package is one UTF-8 JSON document (no ZIP, no encryption). The Android
port reads raw bytes from a SAF `content://` URI and writes raw bytes to a
SAF `content://` URI; MIME `application/json`.

The reference identity is the SHA-256 of the canonical bytes:

```
canonical_bytes(doc) = json.dumps(doc, sort_keys=True, separators=(",", ":"),
                                 ensure_ascii=False) + "\n"   (UTF-8)
```

The Android `PortableJson` writer reproduces this byte-for-byte: recursively
sorted object keys, compact separators, Python-compatible string escaping
(`"`, `\`, control characters as `\b \f \n \r \t` or `\u00xx`; non-ASCII
emitted as raw UTF-8), integer literals preserved verbatim, and a trailing
newline. The v1 contract and the golden package contain only integer numbers,
so the integer-only literal path is exact for the binding acceptance input;
fractional/exponent literals are normalized by the strict decoder rather than
silently reformatted. Identity is computed with `java.security.MessageDigest`.

Validation precedence matches `check.py` exactly: SIZE_LIMIT → INVALID_INPUT →
UNSUPPORTED_VERSION → UNKNOWN_FIELD (closed schema + credential keys) →
SIZE_LIMIT (overflow) → PATH_UNSAFE → DUPLICATE_ID → MISSING_REFERENCE →
COUNTS_MISMATCH. A strict decoder rejects non-UTF-8 bytes, malformed JSON,
trailing data, NaN/Infinity, lone surrogate escapes, and duplicate object keys
anywhere before any schema rule runs. Exactly one `PortableReject` is raised.

## Entity model — bounded domain archive

Phase 132 deliberately adds no domain UI for the five portable collections
(problems, attempts, notes, reviews, sessions). Per the phase-131 design, the
understood-but-not-yet-editable records go into a **bounded domain-archive
layer**: two new Room tables, version 15→16 migration, exported schema
`app/schemas/com.ojnexus.core.database.OjNexusDatabase/16.json`.

- `portable_archive_packages` — one row, keyed by `identity` (SHA-256 hex):
  `formatVersion`, `scopeId`, `sourcePlatform`, `generatorJson`,
  `createdAtEpochMs`, `canonicalBytes` (BLOB), `sourceTag`, `importedAtEpochMs`.
  This preserves the manifest, provenance and exact canonical package bytes.
- `portable_archive_records` — one row per accepted record, primary key
  `(collection, stableId)`: `sourceScopeId`, `origin`, `canonicalJson` (the
  canonical JSON of that single record), `tier`, `tierReason`, `synthetic`,
  `sourceTag`, `packageIdentity`. Rows retain references (`problemId`),
  source scope IDs and stored-only state for later projection.

`PortableRepository.export()` rebuilds the package from the archived rows plus
manifest metadata with the same canonical writer and asserts the rebuilt
identity equals the stored identity. This proves no accepted record was lost
instead of merely replaying a cached blob. Reimport of the same identity is a
no-op (`ApplyStats.identical = true`); reimport of a different package replaces
the archive in ONE Room `@Transaction`.

`PortableArchiveStore` is an interface so the codec, repository, UI and tests
are developed against a frozen contract; `RoomPortableArchiveStore` is the only
Room-aware implementation.

## Capability negotiation

Every import reports three counts, computed by `PortableCodec.classify`:

- **EDITABLE** — records with a live editable domain store. Phase 132 has none,
  so `editableCount = 0`; this is reported explicitly, not hidden.
- **STORED_ONLY** — records understood by the v1 contract but with no domain
  UI; persisted in the bounded archive with saved state. The UI labels them
  `SAVED / STORED ONLY` (暂不可编辑项有保存状态). The mixed golden package has
  15 records (3 problems + 8 attempts + 1 note + 2 reviews + 1 session), all
  stored-only → `storedOnlyCount = 15`, `rejectedCount = 0`.
- **REJECTED** — unknown/incompatible records with a reason, never silently
  dropped. Out-of-contract fields (closed-object violations, unknown enums,
  credential-shaped keys, path-shaped strings, unsupported version) reject the
  WHOLE package with one `PortableReject`; the UI shows the wire category.

Provenance: the preview breaks counts down by `sourceScopeId` (golden has
`scope.alpha` and `scope.beta`), reports conflicts with existing archived rows
sharing `(collection, stableId)`, and lists skipped items. `origin =
"unassigned"` is preserved and never attributed to the importing account.

Synthetic test source: when the package generator is a synthetic fixture
(`synthetic-fixture-gen`) or a record carries `extra.synthetic = true`, the
archive sets `sourceTag = "SYNTHETIC-TEST-FIXTURE"` and `synthetic = true`.
The preview and record list show a `TEST SOURCE` label. Synthetic records are
data, never UI copy; the label is a status string in `strings.xml`.

## Frozen shared API (controller-authored, all tasks compile against it)

Files owned by the controller, not by any task branch:

- `app/src/main/java/com/ojnexus/core/portable/PortableModels.kt` —
  `PortableSpec` constants (keys, enums, patterns, limits), `PortableRejectCategory`,
  `PortableTier`, `PortableTierReason`, `TierDecision`, `SourceBreakdown`,
  `PortableConflict`, `PortablePreview`, `PortableApplyResult`, `PortableReject`,
  `PortableContractException`.
- `app/src/main/java/com/ojnexus/core/portable/PortableValue.kt` —
  `PortableValue` sealed hierarchy (`Obj`, `Arr`, `Str`, `Num`, `Bool`, `Null`)
  and `PortableJson` (`parse`, `write`, `sha256Hex`).
- `app/src/main/java/com/ojnexus/core/portable/PortableCodec.kt` —
  `class PortableCodec` with `validate(bytes): ValidatedPackage`,
  `classify(ValidatedPackage): List<TierDecision>`,
  `canonicalBytes(PortableValue): ByteArray`, `identity(bytes): String`,
  `rebuild(PortableArchive): ByteArray`, `preview(bytes): PortablePreview`.
- `app/src/main/java/com/ojnexus/core/portable/PortableArchiveStore.kt` —
  `ArchivedRecord`, `ArchivedManifest`, `PortableArchive`, `ApplyStats`, and the
  `PortableArchiveStore` interface (`load`, `recordCount`, `replaceAll`, `clear`).
- `app/src/main/java/com/ojnexus/core/portable/PortableWorkspaceRepository.kt` —
  `PortableWorkspaceState` and the `PortableWorkspaceRepository` interface
  (`preview`, `apply`, `export`, `state`, `cancel`).

Frozen concrete names the task branches MUST provide:

- `class PortableRepository(store: PortableArchiveStore, codec: PortableCodec = PortableCodec()) : PortableWorkspaceRepository`
- `class RoomPortableArchiveStore(private val dao: PortableArchiveDao) : PortableArchiveStore`
- `interface PortableArchiveDao` (Room `@Dao`), exposed as
  `OjNexusDatabase.portableArchiveDao()`
- `@Entity(tableName = "portable_archive_packages")` and
  `@Entity(tableName = "portable_archive_records")`, `MIGRATION_15_16`,
  `OJ_NEXUS_SCHEMA_VERSION = 16`.

## Explicit non-goals

- No changes to `BackupRepository` or whole-DB backup UI; the Portable
  Workspace entry is a distinct settings row, screen, dialog and state.
- No domain UI/table projection for the five collections (phases 133+).
- No ZIP/compression/encryption, no network access, no credentials.
- No `formatVersion` auto-upgrade; a non-1 version is a hard reject.
- No re-zoning of `dayKey` values; they are preserved verbatim.
- No silent row dropping; a partial success is never reported as full success.

## Verification plan

- `tools/gradlew-local.bat test assembleDebug` → `BUILD SUCCESSFUL` (G-A).
- `python contracts/portable/v1/check.py --cases ... --identity ...` →
  15/15 and identity `81bb13c1…8d44` (Python reference, re-run).
- Kotlin unit tests: `PortableCodecTest` (all 15 golden cases with exact wire
  categories), `PortableGoldenRoundTripTest` (mixed golden import→apply→export
  identity equals the Python identity; idempotent reimport; preview/cancel leave
  `recordCount()` unchanged; out-of-contract field rejected; synthetic records
  marked `SYNTHETIC-TEST-FIXTURE`; stored-only records expose saved state),
  `PortableArchiveStoreTest` (Room in-memory, `@Transaction` replace),
  `PortableRepositoryTest`, `MigrationTest` (15→16 schema + migration).
- Independent review subagent over the final diff, then verification record,
  plan ledger and handoff with measured (not estimated) results.
