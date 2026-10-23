package com.ojnexus.core.data.restore

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class RecoveryVectorCorpus(val format_version: Int, val synthetic: Boolean, val vectors: List<RecoveryVector>)
@Serializable
data class RecoveryVector(val id: String, val operation: VectorOperation, val fault: String, val checkpoint: String, val preconditions: List<String>, val atomic_unit: AtomicUnit, val write_semantics: WriteSemantics, val expected: VectorExpected, val retry: VectorRetry, val android: AndroidProjection? = null)
@Serializable
enum class VectorOperation { IMPORT, EXPORT, WORK, MIGRATE, OPEN, WRITE, SYNC }
@Serializable
enum class AtomicUnit { STORE, MODULE, WORKSPACE_FILE, WORK_ITEM }
@Serializable
enum class WriteSemantics { REPLACE, UPSERT, NONE }
@Serializable
enum class VectorPhase { PRECHECK, STAGING, STAGED, COMMITTING, COMMITTED, RECOVERING, ABORTED, BLOCKED }
@Serializable
enum class ActiveData { OLD, NEW, EMPTY, NONE_BLOCKED }
@Serializable
enum class GenerationOutcome { OLD, NEW, UNCHANGED }
@Serializable
enum class SafeVectorError { INVALID_INPUT, UNSUPPORTED_SCHEMA, INTEGRITY_FAILURE, STORAGE_FULL, STORAGE_IO, CANCELLED, CANCEL_TOO_LATE, STALE_GENERATION, GENERATION_UNKNOWN, RECOVERY_SOURCE_MISSING, RECOVERY_BLOCKED }
@Serializable
data class VectorExpected(val phase: VectorPhase, val active_data: ActiveData, val generation: GenerationOutcome, val error: SafeVectorError? = null, val preserve_artifacts: Boolean, val originating_work_may_write: Boolean, val next_action: String)
@Serializable
data class VectorRetry(val allowed: Boolean, val after: String)
@Serializable
enum class AndroidProjectionKind { RECOVERY_DECISION, GENERATION_DECISION }
@Serializable
data class AndroidProjection(val kind: AndroidProjectionKind, val journal: JournalProjection? = null, val files: FileProjection? = null, val expected_action: RestoreRecoveryAction? = null, val expected_generation: String? = null, val current_generation: String? = null, val expected_decision: RestoreWorkDecision? = null)
@Serializable
data class JournalProjection(val stage: RestoreStage, val generation: String, val detail: RestoreFailure? = null)
@Serializable
data class FileProjection(val target: Boolean, val candidate: Boolean, val rollback: Boolean, val staged: Boolean)

/** Strict test-only decoder for the shared synthetic protocol corpus. */
object RecoveryVectorFixtures {
    private val json = Json { ignoreUnknownKeys = false; isLenient = false; coerceInputValues = false }
    fun decode(text: String): RecoveryVectorCorpus {
        val corpus = json.decodeFromString<RecoveryVectorCorpus>(text)
        corpus.requireValid()
        return corpus
    }
    fun load(): RecoveryVectorCorpus = decode(loadTextForTest())
    internal fun loadTextForTest(): String = requireNotNull(javaClass.getResourceAsStream("/recovery/v1/vectors.json")) { "Missing classpath recovery vector corpus" }.bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun RecoveryVectorCorpus.requireValid() {
        require(format_version == 1) { "Unsupported recovery corpus version: $format_version" }
        require(synthetic) { "Recovery vectors must be synthetic" }
        require(vectors.size == 32) { "Expected exactly 32 recovery vectors" }
        val ids = (1..32).map { "RV%03d".format(it) }
        require(vectors.map { it.id }.distinct().size == vectors.size) { "Duplicate recovery vector ID" }
        require(vectors.map { it.id } == ids) { "Recovery vector IDs/order must be RV001 through RV032" }
        require(vectors.filter { it.android != null }.map { it.id }.toSet() == ANDROID_IDS) { "Android projections must match the nine declared IDs" }
        vectors.forEach { v ->
            require(v.fault.isNotBlank() && v.checkpoint.isNotBlank()) { "${v.id} needs fault and checkpoint" }
            require(v.preconditions.isNotEmpty() && v.preconditions.all(String::isNotBlank)) { "${v.id} needs explicit preconditions" }
            require(v.expected.next_action.isNotBlank()) { "${v.id} needs a concrete next action" }
            require(v.retry.after.isNotBlank()) { "${v.id} needs a concrete retry prerequisite" }
            require(v.retry.allowed || v.retry.after == "NONE") { "${v.id} denied retry must say NONE" }
            require(!v.retry.allowed || v.retry.after != "NONE") { "${v.id} retry needs a prerequisite" }
            require(v.expected.phase != VectorPhase.BLOCKED || v.expected.preserve_artifacts) { "${v.id} must preserve artifacts while blocked" }
            require(v.expected.originating_work_may_write == (v.id == "RV021")) { "Only matching-generation RV021 may allow writes" }
            if (v.operation == VectorOperation.IMPORT && v.expected.phase == VectorPhase.ABORTED) {
                require(v.expected.active_data == ActiveData.OLD && v.expected.generation == GenerationOutcome.OLD) { "${v.id} aborted import must preserve OLD/OLD" }
            }
            v.android?.let { projection ->
                when (projection.kind) {
                    AndroidProjectionKind.RECOVERY_DECISION -> {
                        require(projection.files != null && projection.expected_action != null) { "${v.id} incomplete recovery projection" }
                        require(projection.expected_generation == null && projection.current_generation == null && projection.expected_decision == null) { "${v.id} mixes projection kinds" }
                    }
                    AndroidProjectionKind.GENERATION_DECISION -> {
                        require(projection.journal == null && projection.files == null && projection.expected_action == null && projection.current_generation != null && projection.expected_decision != null) { "${v.id} incomplete generation projection" }
                    }
                }
            }
        }
        val replace = vectors.single { it.id == "RV029" }
        val upsert = vectors.single { it.id == "RV030" }
        require(replace.write_semantics == WriteSemantics.REPLACE && replace.expected.active_data == ActiveData.EMPTY) { "RV029 must model valid empty REPLACE" }
        require(upsert.write_semantics == WriteSemantics.UPSERT && upsert.expected.active_data == ActiveData.OLD) { "RV030 must model valid empty UPSERT" }
        require(replace.expected.active_data != upsert.expected.active_data) { "Empty REPLACE and UPSERT must differ" }
    }
    private val ANDROID_IDS = setOf("RV010", "RV011", "RV012", "RV013", "RV014", "RV016", "RV020", "RV021", "RV022")
}
