# Phase 131 — Independent schema/checker review

- **Reviewer role:** independent, read-only with respect to the implementation. This review
  writes only this report; `check.py`, `schema.json` and `README.md` were not modified.
- **Date:** 2026-11-04
- **Worktree / branch:** `D:/AndroidAppCoding/.worktrees/p131-review`, branch
  `codex/phase-131-schema-review` at `acf4f8c` (`acf4f8c88a214b4599c8aef2a8f7ee177a4e2ea8`).
- **Subject files (all read fully at `acf4f8c`):**
  - `contracts/portable/v1/schema.json` (464 lines)
  - `contracts/portable/v1/check.py` (1096 lines)
  - `contracts/portable/v1/README.md` (79 lines)
  - `docs/superpowers/specs/2026-11-01-phase-131-portable-workspace-design.md` (150 lines)
  - `docs/superpowers/plans/2026-11-01-phase-131-portable-workspace.md` (72 lines; review focus
    lines 66–72)
- **Method:** static read of every line of the subject files; `python -m py_compile check.py`;
  `scan_schema()` against the real `schema.json`; small synthetic probe packages written to
  `D:/tmp/dsh-temp/pw131/` (outside the worktree, never committed) to confirm actual error
  categories and canonical-byte behavior. No implementation file was edited.
- **Scope:** source + contract text only. The golden corpus gate (`golden/`, `cases.json`) and
  CI wiring are measured separately by the controller in
  `docs/verification/phase-131-portable-contract.md`. At `acf4f8c` the worktree tracks only
  `README.md`, `check.py`, `schema.json` under `contracts/portable/v1/` (there is no `golden/`
  directory on this branch), so corpus assertions are explicitly out of this review's scope.

## Verdict

**PASS-WITH-OBSERVATIONS** — no blocking defect found in the source and contract text. The
draft-07 subset is enforced honestly, the two layers agree on admission/rejection, unknown keys
are named, no silent normalization occurs, canonical bytes are deterministic, and the
decode guards are effective. One **minor** category-label disagreement and several
determinism/anchoring observations are recorded below; none admits a non-conforming package
or corrupts data. Observations O1–O7 are recorded inline in the question sections below and
consolidated in the Findings table.

---

## Q1 — Subset agreement

**Conclusion: PASS.** Every keyword used in `schema.json` is either in
`SUPPORTED_SCHEMA_KEYWORDS` (enforced) or `BENIGN_SCHEMA_KEYWORDS` (ignored by design). No
keyword is in neither list; `scan_schema()` would hard-fail on any such keyword.

**Evidence.** Keyword inventory of `schema.json` (occurrences, verified by a keyword-walk of
the parsed schema):

| Keyword | Sites | Classification | Enforced at |
|---|---|---|---|
| `$schema` | 1 (`schema.json:2`) | `BENIGN_SCHEMA_KEYWORDS` (`check.py:106-108`) | ignored by design |
| `title` | 1 (`schema.json:3`) | `BENIGN_SCHEMA_KEYWORDS` | ignored by design |
| `description` | 15 (`schema.json:4,24,30,...`) | `BENIGN_SCHEMA_KEYWORDS` | ignored by design |
| `type` | 87 | `SUPPORTED_SCHEMA_KEYWORDS` (`check.py:114`) | `_validate` `:483-490`, `_type_matches` `:462-470` |
| `properties` | 8 | `SUPPORTED_SCHEMA_KEYWORDS` (`:115`) | `_validate_object` `:516-530` |
| `required` | 8 | `SUPPORTED_SCHEMA_KEYWORDS` (`:116`) | `_validate_object` `:511-515` |
| `additionalProperties` | 8, all literal `false` (e.g. `schema.json:6,38,59,91`) | `SUPPORTED_SCHEMA_KEYWORDS` (`:117`) | `_validate_object` `:517-525` |
| `enum` | 12 (`schema.json:23,34,115,123,193,216,272,369,424,428`) | `SUPPORTED_SCHEMA_KEYWORDS` (`:118`) | `_validate_enum_const` `:611-618` |
| `pattern` | 23 | `SUPPORTED_SCHEMA_KEYWORDS` (`:120`) | `_validate_string` `:559-565` (`re.search`) |
| `minLength` | 2 (`schema.json:43,48`) | `SUPPORTED_SCHEMA_KEYWORDS` (`:121`) | `_validate_string` `:566-573` |
| `maxLength` | 35 | `SUPPORTED_SCHEMA_KEYWORDS` (`:122`) | `_validate_string` `:574-581` |
| `minimum` | 18 | `SUPPORTED_SCHEMA_KEYWORDS` (`:123`) | `_validate_number` `:586-596` |
| `items` | 7 | `SUPPORTED_SCHEMA_KEYWORDS` (`:126`) | `_validate_array` `:535-540` |
| `maxItems` | 5 (`schema.json:87,167,243,314,389`) | `SUPPORTED_SCHEMA_KEYWORDS` (`:128`) | `_validate_array` `:548-554` |

`SUPPORTED_SCHEMA_KEYWORDS` also admits `const` (`:119`), `maximum` (`:124`),
`exclusiveMinimum` (`:125`) and `minItems` (`:127`) which the schema does not use; these are
extra capability, not unenforced schema rules. `scan_schema()` checks every schema key
against the two lists (`:376-381`) and is invoked on every run before any package is read
(`main` `:1069-1070`), so an unlisted keyword is a configuration error (exit 2), never a
silently ignored constraint. `README.md:4` and `README.md:75` document exactly the supported
set; the `schema.json:4` description string matches. The deliberate-superset keywords
(`const`, `maximum`, `exclusiveMinimum`, `minItems`) are documented in the `check.py`
precedence note (`:34-40`) but not used by this schema — benign.

**Observation:** none for Q1. Zero keywords used by the schema fall outside the two lists.

---

## Q2 — Schema/checker agreement on rejection reasons

**Conclusion: PASS with one minor labeling mismatch (O1).** Every one of the 8 categories is
produced, and each concern is covered by at least one layer; no concern is enforced in one
layer and silently absent in the other. The pipeline order is
`decode → version gate → schema → credential keys → contract rules` (`check_package`
`:862-877`, `check_contract_rules` `:846-854`), matching the documented precedence
(`check.py:28-50`).

| Category | Produced at (layer) | Concern coverage across layers | Same concern missing in other layer? |
|---|---|---|---|
| `INVALID_INPUT` | decode `:268-299` (UTF-8, malformed/truncated, NaN/Infinity `:215-216`,`:297-298`, duplicate keys `:239-242`, lone surrogates `:244-261`); root-not-object/version-missing `:307-324` | malformed JSON, duplicate keys, non-object root, bad `formatVersion` have no schema equivalent by design (they precede schema) | no — decode-level only, intentional |
| `UNSUPPORTED_VERSION` | `check_version_gate` `:325-332` | `formatVersion != 1`; schema also has `enum:[1]` at `schema.json:23` (redundant defense, version gate wins by precedence) | no — present in both (O2) |
| `UNKNOWN_FIELD` | `validate_against_schema` `:622-627` (type/required/additionalProperties/enum/const/pattern/min-maxLength/minimum/maximum/exclusiveMinimum/items/min-maxItems); `_check_credential_keys` `:640-654`; `_check_extra_depth` `:657-684` | enum/type/pattern/required/unknown-key all here; credential keys and `extra` depth are contract-rule additions the schema cannot express (schema `extra` has no `additionalProperties:false` — `schema.json:117-120`) | no |
| `DUPLICATE_ID` | `_check_duplicates` `:741-775` (duplicate `stableId` per collection; duplicate `notes[].problemId`) | pure contract rule; no schema expression exists | no |
| `MISSING_REFERENCE` | `_check_references` `:790-822` | pure contract rule; schema `problemId` is only a charset pattern (`schema.json:199-204`) | no |
| `SIZE_LIMIT` | decode (package bytes) `:274-280`; `_check_collection_sizes` `:687-696` (records); `_check_string_sizes` `:699-722` (UTF-8 bytes) | package bytes decode-only; **records also have schema `maxItems:50000`**; strings also have schema `maxLength:16384` (code points) | **O1: record count is double-covered but with different categories** |
| `PATH_UNSAFE` | `_check_path_shapes` `:725-738` | pure contract rule | no |
| `COUNTS_MISMATCH` | `_check_counts` `:825-843` | pure contract rule; schema only types `counts` members as integers (`schema.json:62-81`) | no |

Specific mappings requested, confirmed by probe:

- enum/type/pattern/required/unknown-key → `UNKNOWN_FIELD` (`validate_against_schema`
  `:627`; e.g. unknown manifest key probe → `UNKNOWN_FIELD` at `$.oops`; unknown verdict →
  `enum:` detail `UNKNOWN_FIELD`).
- version → `UNSUPPORTED_VERSION` for integer `!= 1` (`:325-332`), `INVALID_INPUT` for
  missing/non-integer (`:314-324`).
- duplicate `stableId` / `notes[].problemId` → `DUPLICATE_ID` (`:755-760`, `:768-774`).
- references → `MISSING_REFERENCE` (`:800-805`, `:816-822`).
- string/array/package size → `SIZE_LIMIT` for package bytes and UTF-8 string bytes; record
  count is `UNKNOWN_FIELD` in practice (O1).
- path shapes → `PATH_UNSAFE` (`:733-738`).
- counts → `COUNTS_MISMATCH` (`:837-843`).
- malformed/truncated/duplicate-keys → `INVALID_INPUT` (`:291-298`, `:239-242`).

`CATEGORIES` (`:69-78`) lists exactly the 8 strings; all 8 were reproduced by probes.

**O1 (minor):** README `README.md:62` documents "each entity array at most 50 000 records"
under `SIZE_LIMIT`, and `check.py:41` lists "entity collection longer than 50000 records" as
step-5 `SIZE_LIMIT`. But each collection also carries schema `maxItems: 50000`
(`schema.json:87,167,243,314,389`), and schema validation (step 4) runs before
`check_contract_rules` (step 5) at `:870` vs `:872`. Probe with 50001 `problems` records
returned `UNKNOWN_FIELD | maxItems: array must have at most 50000 items, got 50001 |
$.problems`, not `SIZE_LIMIT`. The package is still rejected (no acceptance defect), but the
reported category disagrees with both README and the checker's own precedence docstring. This
is a label/contract-text mismatch, not a validation bypass. Recommended: either drop
`maxItems` from the five collection arrays (let `_check_collection_sizes` own record count as
`SIZE_LIMIT`), or change README/`check.py:41` to document `UNKNOWN_FIELD` for record count.
The string path does not have this problem (O3 demonstrates the two-layer defense works).

**O2 (observation):** `formatVersion` is triplicated — decode/version gate
(`INVALID_INPUT`/`UNSUPPORTED_VERSION`), schema `required` + `type:integer` + `enum:[1]`
(`schema.json:8,22-23`). Precedence (`:869` before `:870`) makes the schema branch
unreachable for any `!= 1` or non-integer value. Redundant but harmless defense in depth.

---

## Q3 — Unknown-key naming

**Conclusion: PASS.** Both the `additionalProperties` violation and the credential-key check
name the offending key in `detail`.

**Exact message strings:**

- `additionalProperties` (entity/manifest/generator/counts unknown key):
  `'unknown key "%s" (the schema declares additionalProperties: false; nothing is silently ignored)' % key`
  — `check.py:523-524`; wrapped as `"additionalProperties: <msg>"` at `:627`. Probe with
  two unknown keys named `"zzUnknown"` and `"aaUnknown"` in its detail (see Q5).
- credential-shaped key:
  `'credential-shaped key "%s" — credentials are never portable' % key` — `check.py:647-648`.
  Probe with `extra: {"token": "abc"}` returned
  `credential-shaped key "token" — credentials are never portable` at
  `$.problems[0].extra.token`; with `"AUTH_SECRET_X"` returned
  `credential-shaped key "AUTH_SECRET_X" ...` at `$.problems[0].extra.AUTH_SECRET_X`.

`emit_error` (`:1011-1014`) prints `INVALID: <category>`, then `location:`, then
`detail:` — the key name is in `detail`, so the requirement "name the offending key in the
error detail (not a bare category)" is met. `README.md:60` and `README.md:36` state this
contract; the code matches. Credential keys at entity/manifest level are caught by the
schema's `additionalProperties:false` first (also naming the key, `:523`), and credential
keys inside `extra` (where the schema permits arbitrary keys) are caught by
`_check_credential_keys` (`:640-654`). Both paths name the key.

**Observation:** `_is_credential_shaped` (`:635-637`) is a substring match over a normalized
key, so e.g. a domain key containing "tokenizer" would also be rejected; given v1's field
set and the `extra` stored-only purpose, benign. No false positive on any schema-declared key
(`check.py:90`).

---

## Q4 — No silent normalization

**Conclusion: PASS.** `check_package` returns the decoded document unchanged; the only
subscript assignments in the whole file are inside `_materialize` (building the initial
object, `:250`), the case-runner's local `shaped.append` (`:961`), and two bookkeeping dicts
`seen_stable`/`seen_problem` (`:761`,`:775`). There is no `.pop`, `.update`, `.setdefault`,
`.sort`, or any write to `doc` or its nested containers after decode — verified by an AST
walk of `check.py`. Probes confirm value preservation:

- **absent `difficulty` stays absent:** `schema.json:146-149` declares `difficulty` optional
  (not in `required`, `schema.json:92-101`); no coercion code exists. Probe: a valid package
  with no `difficulty` returned `VALID`, and `"difficulty" in doc["problems"][0]` was
  `False`. `README.md:42`, design spec `:68-69`.
- **unknown verdict rejected, not coerced to OTHER:** `schema.json:214-217` `enum`
  `["AC","WA","TLE","MLE","RE","CE","PE","OTHER"]`; `_validate_enum_const` `:611-618`
  rejects any other value. Probe `"UNMAPPABLE"` → `UNKNOWN_FIELD | enum: value
  "UNMAPPABLE" is not one of the admitted values [...]`. `README.md:43`, design spec `:74-76`.
- **historical `dayKey` preserved verbatim:** nothing recomputes day keys; `dayKey` is only
  shape-checked (`schema.json:157-161` pattern). Probe with `dayKey: "1999-12-31"` and a
  `2020-01-01` `timestampEpochMs` returned the value `1999-12-31` unchanged. `README.md:23`.
- **unassigned origin not rewritten:** `origin` is an enum `["scope","unassigned"]`
  (`schema.json:113-116`) with no fallback; probe with `origin: "unassigned"` returned
  `unassigned` unchanged. `README.md:31`.
- **unknown-but-declared `extra` preserved value-for-value:** `schema.json:117-120` declares
  `extra` as an object with no `additionalProperties:false`; `_check_extra_depth` (`:657-684`)
  only rejects dict/list-of-dict values, never mutates. Probe with
  `extra: {"mystery": [1, "x", True, None], "tier": 7}` returned the identical Python object
  and the same canonical hash material. `README.md:36`, `README.md:56`.

**Observation:** `_check_extra_depth` only recurses into a value when `key != "extra"`
(`:680-681`); when `key == "extra"` and the value is a dict, it checks the shape but does not
recurse deeper (`:661-679`). Because v1 admits `extra` only at entity level (the schema
declares `extra` in exactly the five item schemas: `schema.json:117,195,274,344,418`) and
`extra` values may only be scalars or arrays of scalars, a nested `"extra"` key inside an
`extra` dict is already rejected by the dict-value check at `:664-670` (confirmed: probe
`extra: {"inner": {"extra": {"x": 1}}}` → `UNKNOWN_FIELD` at `$.problems[0].extra.inner`).
Benign, not exploitable.

---

## Q5 — Determinism

**Conclusion: PASS with observations (O4, O5).** Canonical bytes are deterministic and no
clock/locale/dict-order dependence exists in any decision path.

**Evidence.**
- `canonical_bytes` (`:880-883`):
  `json.dumps(doc, sort_keys=True, separators=(",", ":"), ensure_ascii=False)` then
  `(text + "\n").encode("utf-8")` — sorted keys, fixed separators, `ensure_ascii=False`,
  UTF-8, trailing newline. Probe: two packages differing only in object-key insertion order
  produced identical `canonical_bytes` (`True`); output ends with `\n` (`True`); a CJK value
  is emitted as raw UTF-8 bytes (`True`), confirming `ensure_ascii=False`.
- No `time`, `datetime`, `random`, `locale`, `getenv`, or wall-clock calls anywhere in
  `check.py` (imports are `argparse, hashlib, json, os, re, sys` `:58-63`; `os` is used only
  for `os.path` at `:975-976`,`:1064-1066`). Output paths are deterministic functions of the
  input document. `_json_repr` (`:184-188`) uses `ensure_ascii=False` consistently.
- `scan_schema` iterates `for key in schema` (`:376`) and `for key in properties` (`:392`),
  but those loops only raise `ConfigError` on bad schema and do not influence a package
  decision. `_validate_object` checks `required` in schema order (`:513`) and iterates
  `properties` in schema order (`:526`), so schema-declared constraints are order-stable.

**O4 (observation — the requested nuance):** `_validate_object` iterates
`for key in value:  # document order` at `check.py:518` for the `additionalProperties` check.
With two different unknown keys, the reported key is the first in document order: probe with
`{"zzUnknown":1,"aaUnknown":2}` reported `unknown key "zzUnknown"`, and with
`{"aaUnknown":2,"zzUnknown":1}` reported `unknown key "aaUnknown"`. So the chosen offending
key is document-order dependent. Two byte-identical inputs always produce the same result
(determinism holds for a given package); two packages that differ only in key order are
different inputs and may report different keys. Report as a minor nuance, not a blocker: the
first-failing-key choice is stable for a fixed document, and the contract only requires that
"the offending key" be named (`README.md:60`), which holds. If strict determinism of the
chosen key across re-orderings is desired, `min(...)` over `sorted(value)` would fix it.

**O5 (observation):** the precedence docstring (`check.py:28-50`) lists `SIZE_LIMIT` at both
step 1 (package bytes) and step 5 (records/strings); because schema step 4 runs first,
record-count overflow resolves to `UNKNOWN_FIELD` (O1). Cosmetic doc/code drift.

---

## Q6 — Size limits

**Conclusion: PASS.** Constants are exact; string size is UTF-8 bytes; schema `maxLength` is
code points; the two layers are a deliberate, non-contradicting defense.

**Evidence.**
- `MAX_PACKAGE_BYTES = 64 * 1024 * 1024` (`check.py:65` = 67 108 864 = 64 MiB, inclusive),
  checked before decode at `:274-280`.
- `MAX_RECORDS = 50000` (`check.py:66`), enforced at `:687-696`.
- `MAX_STRING_BYTES = 16384` (`check.py:67`), enforced at `:699-722`.
- UTF-8 bytes, not code points: `_check_string_sizes` uses
  `size = len(node.encode("utf-8"))` for string values (`:716`) and
  `size = len(key.encode("utf-8"))` for object keys (`:703`). Probe: a `title` of 6000 CJK
  characters = 6000 code points but 18 000 UTF-8 bytes → `SIZE_LIMIT | string is 18000
  UTF-8 bytes, limit is 16384`. The same 6000-code-point string is under schema
  `maxLength:16384` so schema does not fire — confirming the byte layer is the one that
  catches multibyte overflow.
- Schema `maxLength` is code points: `_validate_string` uses `len(value)` at `:575` (Python
  `str` length = code points), value `16384` at every `maxLength` site (verified: 35 sites,
  all `16384`). Probe: 16 385 ASCII characters → `UNKNOWN_FIELD | maxLength: string must
  have at most 16384 characters, got 16385` (schema fires before the byte check, at `:870`
  vs `:872`).
- `README.md:62` states both bounds explicitly ("16 384 UTF-8 bytes; the schema's
  `maxLength` of 16384 counts code points, the checker enforces the byte bound"). They do not
  contradict: for ASCII the two thresholds coincide; for multibyte text the byte bound is
  strictly tighter and is reached by the checker; for a string that is exactly at the code
  point limit with multibyte chars, `SIZE_LIMIT` fires. Two-layer defense confirmed.

**O3 (observation):** because schema `maxLength` (code points) is checked before
`_check_string_sizes` (bytes), the reported category for a size overflow depends on the
character mix — multibyte overflow → `SIZE_LIMIT`, ASCII overflow → `UNKNOWN_FIELD`.
`README.md:62` groups both under `SIZE_LIMIT`. Same class of labeling nuance as O1; no
package is wrongly accepted.

---

## Q7 — Defensive gaps (honest assessment)

**Conclusion: PASS.** The listed gaps are benign given the pipeline order
`decode → version → schema → credential → contract rules` (`:862-877`); no exploitable
issue found. A real anchoring weakness (O6) and one PATH_UNSAFE regex nuance (O7) are
recorded as minor/observation.

1. **`_check_extra_depth` only checks depth when `key == "extra"`** (`:661`). Benign: `extra`
   is declared only in the five entity item schemas (`schema.json:117,195,274,344,418`), its
   values are restricted to scalars or arrays of scalars, and any nested dict value —
   including one containing a nested `"extra"` key — is rejected at `:664-670` before
   recursion could matter (probe confirmed). Not exploitable.

2. **`_check_references` skips records where `problemId` is not a string** (`:799`, and
   `:815` for `problemIds`). Benign: `problemId` is schema-declared `type: "string"`
   (`schema.json:199-204`, `:278-283`, `:348-353`) and schema validation runs first
   (`:870` before `:872`), so a non-string `problemId` is already `UNKNOWN_FIELD` before
   `_check_references` executes. `problemIds` array items are likewise `type: "string"`
   (`schema.json:446-452`). Not exploitable.

3. **`pattern` uses `re.search`, not `fullmatch`** (`:560`). Assessment of anchors: all 23
   `pattern` values in `schema.json` begin with `^` (verified); 22 of 23 also end with `$`.
   The sole exception is `sourceUrl` `^https?://` (`schema.json:136`), which is a deliberate
   prefix check — `re.search` with `^` correctly requires the value to start with
   `http://` or `https://` (probe: `re.search("^https?://","https://ok/path") == True`,
   `ftp://... == False`). For the identifier and day-key patterns, the leading `^` plus
   trailing `$` prevents a malicious prefix/suffix from being smuggled past a `search` that
   could otherwise match a substring. **O6 (minor):** Python's `$` also matches immediately
   before a trailing `\n`, so `re.search(r"^\d{4}-\d{2}-\d{2}$", "2020-01-01\n") == True`
   (confirmed); probes showed `dayKey`, `stableId`, `scopeId`, `externalId` values with a
   trailing newline are accepted as `VALID` despite containing a character outside the
   declared charset/shape. This is a real shape-check looseness, but low impact: the value is
   still preserved verbatim, no path/credential/size rule is bypassed, and the checker is a
   reference oracle (not a platform parser). `\Z` instead of `$` (or `fullmatch`) would close
   it. Not a blocker.

4. **`parse_constant` rejects NaN/Infinity:** `_reject_constant` (`:215-216`) is passed as
   `parse_constant` (`:289`); probe `{"formatVersion":1,"x":NaN}` →
   `INVALID_INPUT | JSON constant NaN is not valid strict JSON`. Effective.

5. **Duplicate JSON keys rejected via `object_pairs_hook`:** `_object_pairs_hook` (`:211-212`)
   returns `_ObjNode` preserving pairs; `_materialize` (`:234-251`) raises
   `INVALID_INPUT | duplicate object key "..."` (`:239-242`). Probe with two `formatVersion`
   keys → `INVALID_INPUT | duplicate object key "formatVersion" | $.formatVersion`.
   Effective, with a precise path.

6. **Lone surrogates rejected:** `_has_surrogate` (`:219-223`) is applied to object keys
   (`:244-249`) and string values (`:257-261`). Probe `{"formatVersion":1,"scopeId":"\ud800"}`
   → `INVALID_INPUT | string value contains a lone surrogate escape | $.scopeId`. Effective;
   also prevents `canonical_bytes`/UTF-8 encode failures on ill-formed strings.

7. **PATH_UNSAFE regex:** `PATH_UNSAFE_RE = re.compile(r"(^([A-Za-z]:\\)|^/|\.\./|^\.\.$)")`
   (`check.py:84`), applied with `.search` to every string value (`:732-738`). **O7
   (observation):** due to alternation grouping, the `^` anchors apply only to the
   `[A-Za-z]:\` and `^/` and `^\.\.$` arms; the `\.\./` arm is unanchored (matches anywhere,
   which is the intent for traversal segments). Probe: `/etc/passwd`, `C:\Windows`, `../a`,
   `x/../y`, `..` all match; `C:/Windows` (forward-slash drive path) does **not** match.
   README `README.md:63` documents the pattern as `^[A-Za-z]:\\|^/|\.\./|^\.\.$`, which is
   the same expression, so code and contract agree. Because v1 has no file fields
   (`README.md:63`, design spec `:106-107`), this is defense in depth; a `C:/Windows`-style
   string would be accepted but has no import path to exploit. Benign.

Also confirmed: `scan_schema` rejects `additionalProperties` values other than literal
`false`/`true` (`:398-403`), rejects unsupported `items` tuple form (`:430-437`), validates
`type` names against `JSON_TYPES` (`:382-387`), and requires `exclusiveMinimum` to be the
draft-07 boolean form (`:424-429`) — the subset stays honest at the configuration level.

---

## Findings

Consolidated from observations O1–O7 above. Severity scale: blocker / major / minor /
observation. No blocker or major finding.

| ID | Severity | Location | Description | Recommended action |
|---|---|---|---|---|
| F1 (O1) | minor | `check.py:870` vs `check.py:872`; `schema.json:87,167,243,314,389`; `README.md:62`; `check.py:41` | A collection with >50000 records is reported `UNKNOWN_FIELD` (schema `maxItems`), not the documented `SIZE_LIMIT`. Rejection still occurs; no acceptance defect. | Drop `maxItems` from the 5 collection arrays so `_check_collection_sizes` owns record count as `SIZE_LIMIT`, or update README + `check.py:41` to document `UNKNOWN_FIELD` for record count. |
| F2 (O2) | observation | `schema.json:8,22-23`; `check.py:314-332`, `:869-870` | `formatVersion` is enforced by decode/version gate and again by schema `required`+`type`+`enum:[1]`; the schema branch is unreachable by precedence. | Accept as defense in depth, or note the redundancy in the docstring. No change required. |
| F3 (O3) | observation | `check.py:574-581`, `:716-722`, `:870-872`; `README.md:62` | String-overflow category depends on character mix: multibyte → `SIZE_LIMIT`, ASCII → `UNKNOWN_FIELD` (`maxLength`). README groups both under `SIZE_LIMIT`. | Clarify README that ASCII `maxLength` overflow is `UNKNOWN_FIELD`; byte-bound overflow is `SIZE_LIMIT`. Two-layer defense is correct. |
| F4 (O4) | observation | `check.py:518` | Two different unknown keys: the reported key is the first in document order (probe: `zz,aa`→`zzUnknown`; `aa,zz`→`aaUnknown`). Deterministic for a fixed document; differs across re-orderings. | If strict key-choice determinism is wanted, iterate `sorted(value)` for the unknown-key check. Not a blocker. |
| F5 (O5) | observation | `check.py:28-50` | Precedence docstring lists record-count `SIZE_LIMIT` at step 5, but step-4 schema `maxItems` preempts it (see F1). | Align docstring with actual behavior when F1 is resolved. |
| F6 (O6) | minor | `check.py:560` (`re.search`); `schema.json:28,105,110,127,159,...` (`$`-anchored patterns) | Python `$` matches before a trailing `\n`, so identifier/dayKey strings with a trailing newline pass the shape check (probe: `stableId="p1\n"`, `dayKey="2020-01-01\n"` accepted as `VALID`). | Use `\Z` instead of `$` in the anchored patterns, or switch `_validate_string` to `fullmatch`-equivalent semantics. Low impact; value still preserved verbatim, no security rule bypassed. |
| F7 (O7) | observation | `check.py:84`, `:733`; `README.md:63` | PATH_UNSAFE regex does not match `C:/Windows`-style forward-slash drive paths; `\.\./` arm is unanchored as documented. v1 has no file fields. | Accept as documented defense in depth, or broaden if a future phase adds path-bearing fields. |

The single most actionable item is F1 (category-label disagreement); F6 is a low-impact
shape-check looseness. Both are reported to the controller for routing to the original
implementer; neither was fixed in this review.

---

## Claims vs non-claims

**Claims (verified in this review):**
- `check.py` compiles (`python -m py_compile` exit 0) and `scan_schema()` accepts the real
  `schema.json` without a `ConfigError`.
- Every schema keyword is supported or benign (Q1); no unenforced keyword exists.
- All 8 error categories are emitted by the appropriate layer and were reproduced by probes
  (Q2), with one category-label mismatch on record count (O1).
- Unknown keys and credential keys are named in `detail` (Q3).
- No silent normalization of `difficulty`, `verdict`, `dayKey`, `origin`, or `extra` (Q4).
- `canonical_bytes` is `sort_keys=True`, fixed separators, `ensure_ascii=False`, UTF-8,
  trailing newline; no clock/locale dependence (Q5).
- Size constants are exact and string size is measured in UTF-8 bytes (Q6).
- NaN/Infinity, duplicate keys, and lone surrogates are rejected at decode (Q7).

**Non-claims (explicitly out of scope):**
- This review does **not** certify the golden corpus (`golden/`, `cases.json`), which is not
  present on this branch at `acf4f8c`; corpus/CI results belong to the controller's
  `docs/verification/phase-131-portable-contract.md`.
- This review does **not** assess platform import/export consumers (phases 132–135), Android
  build/test gates, or CI workflow correctness.
- This review does **not** claim the checker is a platform proof — it is a reference oracle
  (`README.md:3`, design spec `:140-141`).
- This review does **not** claim exhaustive fuzzing of all malformed inputs; probes were
  targeted at the seven review questions and the documented error categories.
- No fix was applied to any implementation file; findings are reported only.

---

Review disposition (controller, 2026-11-05)
------------------------------------------
The controller reviewed this report and hardened `check.py` on branch
`codex/phase-131-hardening`:

| ID | Disposition |
|---|---|
| F1 (minor) | **Fixed.** `validate_against_schema` now maps a `maxItems` violation to `SIZE_LIMIT` (see `SIZE_LIMIT_KEYWORDS`); the precedence docstring step 5 was aligned. `schema.json` was intentionally left unchanged so the published JSON Schema keeps standard ECMA-262 patterns and its `maxItems` guard. Regression: `selftest.py` check `F1/F3 size-overflow categories`. |
| F3 (observation) | **Fixed.** `maxLength` overflow likewise maps to `SIZE_LIMIT`; ASCII and multibyte overflows now agree with `README.md:62`. Regression: same selftest check. |
| F5 (observation) | **Fixed.** Module docstring step 4/5 rewritten to describe the real precedence after the F1/F3 mapping change. |
| F6 (minor) | **Fixed.** `_validate_string` now routes anchored patterns through `_pattern_matches`, which translates a terminal, unescaped `$` to Python's absolute-end `\\Z`. Prefix-only `^https?://` (sourceUrl) is untouched. Regression: `selftest.py` check `F6 trailing-newline pattern anchoring` (end-to-end package rejection, helper-agnostic). |
| F2, F4, F7 (observation) | **Accepted, no change.** F2 is harmless defense in depth (version gate + schema enum); F4 is deterministic for a fixed document (unknown key is first in document order) and the contract does not require byte-level key-choice stability across re-orderings; F7 is documented defense in depth for a version with no file fields (broaden only if a future phase adds path-bearing fields). |

These fixes are behavior-preserving for every conforming package: the golden corpus
(15 cases) and the canonical identity of `pw-p01.json` are unchanged. A negative control
running the updated `selftest.py` against the un-hardened `check.py` at `acf4f8c`
fails exactly the two new regression checks (F6, F1/F3), confirming they are not
vacuous. The independent review above remains an accurate record of the source as it
was read at `acf4f8c`; this section records the controller's subsequent disposition.

## Closing note

This review covers **source + contract text only** — `schema.json`, `check.py`, `README.md`,
the binding design spec and the plan's review focus. The golden corpus gate and CI are
measured separately by the controller in
`docs/verification/phase-131-portable-contract.md`. No implementation file was modified by
this review; the only artifact produced here is this report.
