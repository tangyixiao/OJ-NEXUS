package com.ojnexus.core.data.restore

import android.content.ContentResolver
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import com.ojnexus.core.database.OjNexusDatabase
import java.io.File
import java.io.FileOutputStream
import java.util.Properties
import java.util.UUID

/**
 * Owns the on-disk restore protocol. It is intentionally synchronous: callers invoke it from an
 * IO coroutine, and application startup can finish recovery before constructing Room.
 */
class DatabaseRestoreCoordinator(
    private val context: Context,
    private val resolver: ContentResolver = context.contentResolver,
) {
    private var files: RestoreFileOperations = SystemRestoreFileOperations

    internal constructor(context: Context, resolver: ContentResolver, files: RestoreFileOperations) : this(context, resolver) {
        this.files = files
    }
    fun prepareImport(source: Uri): RestoreOutcome {
        val generation = UUID.randomUUID().toString()
        val input = File.createTempFile("oj-nexus-import-", ".db", context.cacheDir)
        return try {
            resolver.openInputStream(source)?.use { stream ->
                FileOutputStream(input).use { output -> stream.copyTo(output) }
            } ?: return RestoreOutcome.Rejected(RestoreFailure.INVALID_SQLITE)
            validate(input)
            atomicReplace(input, pendingTempFile(), RestoreFailure.FILE_REPLACEMENT_FAILURE)
            atomicReplace(pendingTempFile(), pendingFile(), RestoreFailure.FILE_REPLACEMENT_FAILURE)
            writeJournal(RestoreJournal(RestoreStage.STAGED, generation, null))
            RestoreOutcome.Staged(generation)
        } catch (failure: RestoreValidationException) {
            RestoreOutcome.Rejected(failure.category)
        } catch (_: SQLiteException) {
            RestoreOutcome.Rejected(RestoreFailure.INVALID_SQLITE)
        } catch (_: Throwable) {
            RestoreOutcome.Rejected(RestoreFailure.FILE_REPLACEMENT_FAILURE)
        } finally {
            input.delete()
        }
    }

    fun restoreBeforeRoomOpen(): RestoreOutcome = restorePass()

    fun resolveBeforeRoomOpen(): RestoreReadiness = try {
        val outcome = restorePass()
        if (outcome is RestoreOutcome.Blocked) {
            RestoreReadiness.Blocked(outcome.failure, outcome.recovery)
        } else {
            val generation = when (outcome) {
                is RestoreOutcome.Applied -> outcome.generation
                is RestoreOutcome.RolledBack -> readGeneration() ?: return RestoreReadiness.Blocked(
                    RestoreFailure.ROLLBACK_FAILURE, RecoveryDisposition.INSPECTION_REQUIRED,
                )
                else -> currentDataGeneration()
            }
            RestoreReadiness.Ready(outcome, generation)
        }
    } catch (_: Throwable) {
        RestoreReadiness.Blocked(RestoreFailure.FILE_REPLACEMENT_FAILURE, RecoveryDisposition.INSPECTION_REQUIRED)
    }

    private fun restorePass(): RestoreOutcome {
        val journalFileExists = journalFile().isFile
        val journal = readJournal()
        val pending = pendingFile()
        if (!journalFileExists) {
            if (pending.isFile) return blocked(RestoreFailure.INTEGRITY_FAILURE)
            if (recoveryEvidence().any { it.exists() }) return blocked(RestoreFailure.ROLLBACK_FAILURE)
            return RestoreOutcome.NothingToRestore
        }
        if (journalFileExists && journal == null) {
            return blocked(RestoreFailure.INTEGRITY_FAILURE)
        }

        val effectiveJournal = journal ?: RestoreJournal(
            stage = RestoreStage.STAGED,
            generation = UUID.randomUUID().toString(),
            detail = null,
        )
        if (journal != null) {
            when (journal.stage) {
                RestoreStage.SAVING_ROLLBACK -> return abortSaving(journal, RestoreFailure.FILE_REPLACEMENT_FAILURE)
                RestoreStage.ROLLING_BACK -> return blocked(RestoreFailure.ROLLBACK_FAILURE)
                RestoreStage.INSTALLING -> {
                    if (!rollbackFile().isFile) return blocked(RestoreFailure.ROLLBACK_FAILURE)
                    if (pendingFile().isFile) return applyCandidate(journal)
                    return blocked(RestoreFailure.FILE_REPLACEMENT_FAILURE, RecoveryDisposition.RECONCILE_FORWARD_ON_NEXT_START)
                }
                else -> Unit
            }
            if (journal.stage == RestoreStage.SWAPPING) {
                if (splitLegacySources() || !rollbackFile().isFile) return blocked(RestoreFailure.ROLLBACK_FAILURE)
            }
            if (journal.stage == RestoreStage.ROLLED_BACK || journal.stage == RestoreStage.ROLLING_BACK) {
                return blocked(RestoreFailure.ROLLBACK_FAILURE)
            }
        }
        val action = decideRestoreRecovery(effectiveJournal, fileState())
        return when (action) {
            RestoreRecoveryAction.APPLY_CANDIDATE -> applyCandidate(effectiveJournal)
            RestoreRecoveryAction.VALIDATE_TARGET -> validateSwappedTarget(effectiveJournal)
            RestoreRecoveryAction.RESTORE_ROLLBACK -> restoreRollback(
                effectiveJournal,
                effectiveJournal.detail ?: RestoreFailure.FILE_REPLACEMENT_FAILURE,
            )
            RestoreRecoveryAction.REJECT_STAGING -> rejectStaging(effectiveJournal.detail ?: RestoreFailure.INTEGRITY_FAILURE)
            RestoreRecoveryAction.CLEAR_COMPLETED_ARTIFACTS -> completeInterruptedJournal(effectiveJournal)
            RestoreRecoveryAction.NO_ACTION -> RestoreOutcome.NothingToRestore
            RestoreRecoveryAction.BLOCK_RECOVERY -> RestoreOutcome.Blocked(
                RestoreFailure.ROLLBACK_FAILURE,
                RecoveryDisposition.INSPECTION_REQUIRED,
            )
        }
    }

    @Synchronized
    fun currentDataGeneration(): String {
        val file = generationFile()
        val existing = file.takeIf { it.isFile }?.readText()?.trim().orEmpty()
        if (existing.isNotEmpty()) return existing
        val generation = UUID.randomUUID().toString()
        writeTextAtomically(generationFile(), generation)
        return generation
    }

    private fun applyCandidate(journal: RestoreJournal): RestoreOutcome {
        val originalTargetExisted = targetFile().isFile
        var savingRollback = false
        return try {
            validate(pendingFile())
            if (journal.stage == RestoreStage.STAGED || journal.stage == RestoreStage.SWAPPING) {
                if (candidateFile().exists() || listOf(
                        backupTemp(rollbackFile()), backupTemp(rollbackWalFile()), backupTemp(rollbackShmFile()),
                        rollbackInstallTemp(targetFile()), rollbackInstallTemp(targetWalFile()), rollbackInstallTemp(targetShmFile()),
                        File(journalFile().path + ".tmp"), File(generationFile().path + ".tmp"),
                    ).any(File::exists)
                ) return blocked(RestoreFailure.ROLLBACK_FAILURE)
                if (!targetFile().isFile && (targetWalFile().exists() || targetShmFile().exists())) {
                    return blocked(RestoreFailure.ROLLBACK_FAILURE)
                }
                if (rollbackFile().exists() || rollbackWalFile().exists() || rollbackShmFile().exists()) {
                    return blocked(RestoreFailure.ROLLBACK_FAILURE)
                }
                writeJournal(journal.copy(stage = RestoreStage.SAVING_ROLLBACK, detail = null))
                savingRollback = true
                saveLiveFilesToRollback()
                writeJournal(journal.copy(stage = RestoreStage.INSTALLING, detail = null))
                savingRollback = false
            }
            pendingFile().let { files.copyAndSync(it, candidateFile()) }
            files.atomicReplace(candidateFile(), targetFile())
            validate(targetFile())
            finalizeForward(journal)
        } catch (failure: RestoreValidationException) {
            if (savingRollback) abortSaving(journal, failure.category)
            else recoverAfterSwapFailure(journal, failure.category, originalTargetExisted)
        } catch (_: SQLiteException) {
            if (savingRollback) abortSaving(journal, RestoreFailure.INVALID_SQLITE)
            else recoverAfterSwapFailure(journal, RestoreFailure.INVALID_SQLITE, originalTargetExisted)
        } catch (_: Throwable) {
            if (savingRollback) abortSaving(journal, RestoreFailure.FILE_REPLACEMENT_FAILURE)
            else blocked(RestoreFailure.FILE_REPLACEMENT_FAILURE, RecoveryDisposition.RECONCILE_FORWARD_ON_NEXT_START)
        }
    }

    private fun validateSwappedTarget(journal: RestoreJournal): RestoreOutcome {
        return try {
            validate(targetFile())
            finalizeForward(journal)
        } catch (failure: RestoreValidationException) {
            if (journal.stage == RestoreStage.SWAPPING) restoreRollback(journal, failure.category)
            else blocked(failure.category)
        } catch (_: SQLiteException) {
            if (journal.stage == RestoreStage.SWAPPING) restoreRollback(journal, RestoreFailure.INVALID_SQLITE)
            else blocked(RestoreFailure.INVALID_SQLITE)
        } catch (_: Throwable) {
            if (journal.stage == RestoreStage.SWAPPING) restoreRollback(journal, RestoreFailure.FILE_REPLACEMENT_FAILURE)
            else blocked(RestoreFailure.FILE_REPLACEMENT_FAILURE, RecoveryDisposition.RECONCILE_FORWARD_ON_NEXT_START)
        }
    }

    private fun recoverAfterSwapFailure(
        journal: RestoreJournal,
        failure: RestoreFailure,
        originalTargetExisted: Boolean,
    ): RestoreOutcome {
        if (rollbackFile().isFile) return restoreRollback(journal, failure)
        if (rollbackWalFile().exists() || rollbackShmFile().exists()) return blocked(RestoreFailure.ROLLBACK_FAILURE)
        // A fresh install has no rollback copy. Never leave a failed candidate at the live path,
        // otherwise the subsequent Room open could crash instead of starting empty.
        if (!originalTargetExisted) {
            try {
                removeLiveFiles()
            } catch (_: Throwable) {
                return blocked(RestoreFailure.FILE_REPLACEMENT_FAILURE)
            }
        }
        return rejectStaging(failure)
    }

    private fun restoreRollback(journal: RestoreJournal, failure: RestoreFailure): RestoreOutcome {
        return try {
            if (!rollbackFile().isFile) return blocked(RestoreFailure.ROLLBACK_FAILURE)
            val oldGeneration = readGeneration() ?: return blocked(RestoreFailure.ROLLBACK_FAILURE)
            if (oldGeneration == journal.generation) return blocked(RestoreFailure.ROLLBACK_FAILURE)
            writeJournal(journal.copy(stage = RestoreStage.ROLLING_BACK, detail = failure))
            val pair = listOf(rollbackFile() to targetFile(), rollbackWalFile() to targetWalFile(), rollbackShmFile() to targetShmFile())
            pair.forEach { (source, destination) ->
                if (source.isFile) files.copyAndSync(source, rollbackInstallTemp(destination))
            }
            pair.forEach { (source, destination) ->
                if (source.isFile) files.atomicReplace(rollbackInstallTemp(destination), destination)
                else files.deleteChecked(destination)
            }
            validate(targetFile())
            writeJournal(journal.copy(stage = RestoreStage.ROLLED_BACK, detail = failure))
            cleanupRecoveryFiles()
            files.deleteChecked(journalFile())
            RestoreOutcome.RolledBack(oldGeneration, failure)
        } catch (_: Throwable) {
            blocked(RestoreFailure.ROLLBACK_FAILURE)
        }
    }

    private fun rejectStaging(failure: RestoreFailure): RestoreOutcome {
        return try {
            if (rollbackFile().exists() || rollbackWalFile().exists() || rollbackShmFile().exists()) {
                return blocked(RestoreFailure.ROLLBACK_FAILURE)
            }
            if (targetFile().isFile) validate(targetFile())
            val generation = readJournal()?.generation ?: currentDataGeneration()
            writeJournal(RestoreJournal(RestoreStage.REJECTED, generation, failure))
            files.deleteChecked(pendingFile())
            files.deleteChecked(candidateFile())
            files.deleteChecked(journalFile())
            RestoreOutcome.Rejected(failure)
        } catch (validation: RestoreValidationException) {
            blocked(validation.category)
        } catch (_: Throwable) {
            blocked(RestoreFailure.FILE_REPLACEMENT_FAILURE)
        }
    }

    private fun completeInterruptedJournal(journal: RestoreJournal): RestoreOutcome {
        return when (journal.stage) {
            RestoreStage.APPLIED -> finalizeForward(journal)
            RestoreStage.ROLLED_BACK -> blocked(RestoreFailure.ROLLBACK_FAILURE)
            RestoreStage.REJECTED -> try {
                cleanupRecoveryFiles()
                files.deleteChecked(journalFile())
                RestoreOutcome.Rejected(journal.detail ?: RestoreFailure.INTEGRITY_FAILURE)
            } catch (_: Throwable) { blocked(RestoreFailure.FILE_REPLACEMENT_FAILURE) }
            else -> RestoreOutcome.NothingToRestore
        }
    }

    private fun fileState(): RestoreFileState = RestoreFileState(
        target = targetFile().isFile,
        candidate = candidateFile().isFile,
        rollback = rollbackFile().isFile,
        staged = pendingFile().isFile,
    )

    private fun finalizeForward(journal: RestoreJournal): RestoreOutcome = try {
        validate(targetFile())
        writeJournal(journal.copy(stage = RestoreStage.APPLIED, detail = null))
        writeTextAtomically(generationFile(), journal.generation)
        if (readGeneration() != journal.generation) throw IllegalStateException("Generation readback failed")
        cleanupRecoveryFiles()
        files.deleteChecked(journalFile())
        RestoreOutcome.Applied(journal.generation)
    } catch (failure: RestoreValidationException) {
        blocked(failure.category, RecoveryDisposition.RECONCILE_FORWARD_ON_NEXT_START)
    } catch (_: Throwable) {
        blocked(RestoreFailure.FILE_REPLACEMENT_FAILURE, RecoveryDisposition.RECONCILE_FORWARD_ON_NEXT_START)
    }

    private fun abortSaving(journal: RestoreJournal, failure: RestoreFailure): RestoreOutcome = try {
        validate(targetFile())
        val existingGeneration = readGeneration()
        if (existingGeneration == null || existingGeneration == journal.generation) {
            return blocked(RestoreFailure.ROLLBACK_FAILURE)
        }
        listOf(rollbackFile(), rollbackWalFile(), rollbackShmFile(), backupTemp(rollbackFile()), backupTemp(rollbackWalFile()), backupTemp(rollbackShmFile()))
            .forEach(files::deleteChecked)
        writeJournal(journal.copy(stage = RestoreStage.REJECTED, detail = failure))
        files.deleteChecked(pendingFile())
        files.deleteChecked(journalFile())
        RestoreOutcome.Rejected(failure)
    } catch (_: Throwable) {
        blocked(RestoreFailure.ROLLBACK_FAILURE)
    }

    private fun blocked(
        failure: RestoreFailure,
        recovery: RecoveryDisposition = RecoveryDisposition.INSPECTION_REQUIRED,
    ) = RestoreOutcome.Blocked(failure, recovery)

    private fun readGeneration(): String? = generationFile().takeIf { it.isFile }
        ?.let { runCatching { it.readText().trim().takeIf(String::isNotEmpty) }.getOrNull() }

    private fun recoveryEvidence(): List<File> = listOf(
        pendingFile(), pendingTempFile(), candidateFile(), rollbackFile(), rollbackWalFile(), rollbackShmFile(),
        File(rollbackFile().path + ".tmp"), File(rollbackWalFile().path + ".tmp"), File(rollbackShmFile().path + ".tmp"),
        rollbackInstallTemp(targetFile()), rollbackInstallTemp(targetWalFile()), rollbackInstallTemp(targetShmFile()),
        File(journalFile().path + ".tmp"), File(generationFile().path + ".tmp"),
    )

    private fun backupTemp(file: File) = File(file.path + ".tmp")
    private fun rollbackInstallTemp(file: File) = File(file.path + ".restore-rollback-install")

    private fun splitLegacySources(): Boolean =
        (targetFile().isFile && !rollbackFile().isFile && (rollbackWalFile().isFile || rollbackShmFile().isFile)) ||
            (!targetFile().isFile && rollbackFile().isFile && (targetWalFile().isFile || targetShmFile().isFile))

    private fun saveLiveFilesToRollback() {
        listOf(targetFile() to rollbackFile(), targetWalFile() to rollbackWalFile(), targetShmFile() to rollbackShmFile())
            .forEach { (source, destination) -> if (source.isFile) files.copyAndSync(source, backupTemp(destination)).also { files.atomicReplace(backupTemp(destination), destination) } }
    }

    private fun removeLiveFiles() {
        files.deleteChecked(targetFile())
        files.deleteChecked(targetWalFile())
        files.deleteChecked(targetShmFile())
    }

    private fun cleanupRecoveryFiles() {
        recoveryEvidence().forEach(files::deleteChecked)
    }

    private fun validate(file: File) {
        if (!file.isFile || file.length() == 0L) throw RestoreValidationException(RestoreFailure.INVALID_SQLITE)
        try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
                database.rawQuery("PRAGMA user_version", null).use { cursor ->
                    if (!cursor.moveToFirst() || cursor.getInt(0) != OjNexusDatabase.CURRENT_SCHEMA_VERSION) {
                        throw RestoreValidationException(RestoreFailure.SCHEMA_MISMATCH)
                    }
                }
                database.rawQuery("PRAGMA quick_check", null).use { cursor ->
                    if (!cursor.moveToFirst() || cursor.getString(0) != "ok") {
                        throw RestoreValidationException(RestoreFailure.INTEGRITY_FAILURE)
                    }
                }
                database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    if (!cursor.moveToFirst() || cursor.getString(0) != "ok") {
                        throw RestoreValidationException(RestoreFailure.INTEGRITY_FAILURE)
                    }
                }
                val placeholders = REQUIRED_TABLES.joinToString(",") { "?" }
                database.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ($placeholders)",
                    REQUIRED_TABLES.toTypedArray(),
                ).use { cursor ->
                    val found = buildSet {
                        while (cursor.moveToNext()) add(cursor.getString(0))
                    }
                    if (!found.containsAll(REQUIRED_TABLES)) {
                        throw RestoreValidationException(RestoreFailure.REQUIRED_TABLE_MISSING)
                    }
                }
            }
        } catch (failure: RestoreValidationException) {
            throw failure
        } catch (_: SQLiteException) {
            throw RestoreValidationException(RestoreFailure.INVALID_SQLITE)
        }
    }

    private fun readJournal(): RestoreJournal? {
        if (!journalFile().isFile) return null
        return runCatching {
            val properties = Properties()
            journalFile().inputStream().use(properties::load)
            RestoreJournal(
                stage = RestoreStage.valueOf(properties.getProperty("stage")),
                generation = properties.getProperty("generation").takeIf { !it.isNullOrBlank() }
                    ?: error("generation missing"),
                detail = properties.getProperty("detail")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(RestoreFailure::valueOf),
            )
        }.getOrNull()
    }

    private fun writeJournal(journal: RestoreJournal) {
        val temp = File(journalFile().path + ".tmp")
        val properties = Properties().apply {
            setProperty("stage", journal.stage.name)
            setProperty("generation", journal.generation)
            journal.detail?.let { setProperty("detail", it.name) }
        }
        val out = java.io.ByteArrayOutputStream().apply { properties.store(this, null) }.toByteArray()
        files.writeAndSync(temp, out)
        files.atomicReplace(temp, journalFile())
    }

    private fun writeTextAtomically(file: File, text: String) {
        val temp = File(file.path + ".tmp")
        files.writeAndSync(temp, text.toByteArray(Charsets.UTF_8))
        files.atomicReplace(temp, file)
    }

    private fun atomicReplace(source: File, target: File, failure: RestoreFailure) {
        try {
            files.atomicReplace(source, target)
        } catch (_: Throwable) {
            throw RestoreValidationException(failure)
        }
    }

    private fun targetFile(): File = context.getDatabasePath(OjNexusDatabase.DATABASE_NAME)
    private fun targetWalFile(): File = File(targetFile().path + "-wal")
    private fun targetShmFile(): File = File(targetFile().path + "-shm")
    private fun rollbackFile(): File = File(targetFile().path + ".restore-rollback")
    private fun rollbackWalFile(): File = File(targetWalFile().path + ".restore-rollback")
    private fun rollbackShmFile(): File = File(targetShmFile().path + ".restore-rollback")
    private fun candidateFile(): File = File(targetFile().path + ".restore-candidate")
    private fun pendingFile(): File = File(context.filesDir, PENDING_RESTORE_NAME)
    private fun pendingTempFile(): File = File(context.filesDir, PENDING_RESTORE_TEMP_NAME)
    private fun journalFile(): File = File(context.filesDir, JOURNAL_NAME)
    private fun generationFile(): File = File(context.filesDir, GENERATION_NAME)

    private class RestoreValidationException(val category: RestoreFailure) : Exception()

    companion object {
        private const val PENDING_RESTORE_NAME = "oj-nexus-pending-restore.db"
        private const val PENDING_RESTORE_TEMP_NAME = "oj-nexus-pending-restore.db.tmp"
        private const val JOURNAL_NAME = "oj-nexus-restore.journal"
        private const val GENERATION_NAME = "oj-nexus-data-generation"

        private val REQUIRED_TABLES = setOf(
            "problems",
            "problem_tags",
            "problem_tag_cross_ref",
            "attempts",
            "failure_entries",
            "problem_notes",
            "reviews",
            "review_log",
            "training_tasks",
            "training_sessions",
            "training_session_problems",
            "judge_accounts",
            "judge_profiles",
            "rating_changes",
            "remote_problems",
            "remote_problem_details",
            "contests",
            "contest_problem_markers",
            "problem_knowledge",
            "sync_states",
            "submission_jobs",
            "workspace_drafts",
        )
    }
}
