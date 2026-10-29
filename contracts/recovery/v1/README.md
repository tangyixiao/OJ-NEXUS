# Recovery vectors v1

`vectors.json` is the sole machine-readable corpus. All names such as OLD, NEW, generation symbols, checkpoints, and faults describe synthetic logical state; the corpus contains no database bytes, credentials, judge responses, host paths, or native schema versions.

Every vector includes an explicit non-empty `preconditions` list. These are semantic setup conditions, not a claim that a platform fixture or fault injector has produced the state. RV010 specifically requires that user confirmation to replace the active store was obtained and durably recorded before restart; Android's STAGED journal is treated as authorized apply.

Preconditions state the symbolic facts needed to derive the expected result, including which OLD/NEW image validates, which generation is durable, what remains available for rollback, and whether authorization or the commit boundary has been recorded. They do not merely repeat the vector's fault/checkpoint labels. These facts remain protocol-level setup statements, not native bytes or evidence that a platform executed the fault.

The 32 entries are normative scenarios, not 32 end-to-end fault proofs. Future platform phases 127–130 must bind each applicable ID to native setup, fault injection, and an independent end-state oracle before claiming that platform passed it. The nine Android projections in this phase only exercise production decision routing/classification. They do not prove storage effects, fault handling, or final recovery state.

Fields: `format_version` fixes the schema contract; `synthetic` marks the data boundary; `vectors` contains stable RV001–RV032 IDs. Each vector's `operation`, `fault`, and `checkpoint` name the scenario; `preconditions` records prerequisites; `atomic_unit` and `write_semantics` define the logical write boundary; `expected` records phase, active data, generation, safe error, artifact preservation, whether the originating work may continue ordinary writes, and the next action; `retry` gives permission and a concrete prerequisite, using NONE when denied. Nullable `android` contains either a recovery decision input/output or a generation classification projection.

`expected.originating_work_may_write` is a work-item permission, not a permission for the recovery coordinator to complete an already-authorized atomic commit. Only matching-generation WORK vector RV021 sets it true; stale and unknown work remain denied.

Nullable contract values still require their keys: every vector includes `android` (null when unprojected), every `expected` includes `error` (null when no safe error is expected), every recovery projection includes `journal` (which may be null), and every generation projection includes `expected_generation` (null represents the existing LEGACY classifier). Projection objects contain only the keys belonging to their `kind`.

`generation` OLD/NEW refers to the old/new logical generation for store replacement; on WORK scenarios it distinguishes stale from current workspace generation. Export vectors contain no candidate database bytes, and OLD means the active store is unchanged. EMPTY is a valid empty logical value, distinct from missing or invalid input. An unset Android expected generation intentionally yields the existing LEGACY classifier; it is not evidence that unknown-generation writes are safe.
