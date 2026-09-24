package com.ojnexus.core.data.restore

import android.content.ContentResolver
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import com.ojnexus.core.database.OjNexusDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
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

    fun restoreBeforeRoomOpen(): RestoreOutcome {
        val journalFileExists = journalFile().isFile
        val journal = readJournal()
        val pending = pendingFile()
        if (!journalFileExists && !pending.isFile) return RestoreOutcome.NothingToRestore
        if (journalFileExists && journal == null) {
            return RestoreOutcome.Rejected(RestoreFailure.INTEGRITY_FAILURE)
        }

        val effectiveJournal = journal ?: RestoreJournal(
            stage = RestoreStage.STAGED,
            generation = UUID.randomUUID().toString(),
            detail = null,
        )
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
        val pending = pendingFile()
        val originalTargetExisted = targetFile().isFile
        return try {
            validate(pending)
            writeJournal(journal.copy(stage = RestoreStage.SWAPPING, detail = null))
            moveLiveFilesToRollback()
            pending.copyTo(candidateFile(), overwrite = true)
            sync(candidateFile())
            atomicMove(candidateFile(), targetFile())
            validate(targetFile())
            writeJournal(journal.copy(stage = RestoreStage.APPLIED, detail = null))
            writeTextAtomically(generationFile(), journal.generation)
            cleanupRecoveryFiles()
            journalFile().delete()
            RestoreOutcome.Applied(journal.generation)
        } catch (failure: RestoreValidationException) {
            recoverAfterSwapFailure(journal, failure.category, originalTargetExisted)
        } catch (_: SQLiteException) {
            recoverAfterSwapFailure(journal, RestoreFailure.INVALID_SQLITE, originalTargetExisted)
        } catch (_: Throwable) {
            recoverAfterSwapFailure(journal, RestoreFailure.FILE_REPLACEMENT_FAILURE, originalTargetExisted)
        }
    }

    private fun validateSwappedTarget(journal: RestoreJournal): RestoreOutcome {
        return try {
            validate(targetFile())
            writeJournal(journal.copy(stage = RestoreStage.APPLIED, detail = null))
            writeTextAtomically(generationFile(), journal.generation)
            cleanupRecoveryFiles()
            journalFile().delete()
            RestoreOutcome.Applied(journal.generation)
        } catch (failure: RestoreValidationException) {
            restoreRollback(journal, failure.category)
        } catch (_: SQLiteException) {
            restoreRollback(journal, RestoreFailure.INVALID_SQLITE)
        } catch (_: Throwable) {
            restoreRollback(journal, RestoreFailure.FILE_REPLACEMENT_FAILURE)
        }
    }

    private fun recoverAfterSwapFailure(
        journal: RestoreJournal,
        failure: RestoreFailure,
        originalTargetExisted: Boolean,
    ): RestoreOutcome {
        if (rollbackFile().isFile) return restoreRollback(journal, failure)
        // A fresh install has no rollback copy. Never leave a failed candidate at the live path,
        // otherwise the subsequent Room open could crash instead of starting empty.
        if (!originalTargetExisted) removeLiveFiles()
        return rejectStaging(failure)
    }

    private fun restoreRollback(journal: RestoreJournal, failure: RestoreFailure): RestoreOutcome {
        return try {
            removeLiveFiles()
            restoreFile(rollbackFile(), targetFile())
            restoreFile(rollbackWalFile(), targetWalFile())
            restoreFile(rollbackShmFile(), targetShmFile())
            writeJournal(journal.copy(stage = RestoreStage.ROLLED_BACK, detail = failure))
            cleanupRecoveryFiles()
            journalFile().delete()
            RestoreOutcome.RolledBack(journal.generation, failure)
        } catch (_: Throwable) {
            writeJournal(journal.copy(stage = RestoreStage.ROLLED_BACK, detail = RestoreFailure.ROLLBACK_FAILURE))
            RestoreOutcome.Rejected(RestoreFailure.ROLLBACK_FAILURE)
        }
    }

    private fun rejectStaging(failure: RestoreFailure): RestoreOutcome {
        val generation = readJournal()?.generation ?: currentDataGeneration()
        writeJournal(RestoreJournal(RestoreStage.REJECTED, generation, failure))
        pendingFile().delete()
        candidateFile().delete()
        journalFile().delete()
        return RestoreOutcome.Rejected(failure)
    }

    private fun completeInterruptedJournal(journal: RestoreJournal): RestoreOutcome {
        cleanupRecoveryFiles()
        journalFile().delete()
        return when (journal.stage) {
            RestoreStage.APPLIED -> RestoreOutcome.Applied(journal.generation)
            RestoreStage.ROLLED_BACK -> RestoreOutcome.RolledBack(
                journal.generation,
                journal.detail ?: RestoreFailure.FILE_REPLACEMENT_FAILURE,
            )
            RestoreStage.REJECTED -> RestoreOutcome.Rejected(
                journal.detail ?: RestoreFailure.INTEGRITY_FAILURE,
            )
            else -> RestoreOutcome.NothingToRestore
        }
    }

    private fun fileState(): RestoreFileState = RestoreFileState(
        target = targetFile().isFile,
        candidate = candidateFile().isFile,
        rollback = rollbackFile().isFile,
        staged = pendingFile().isFile,
    )

    private fun moveLiveFilesToRollback() {
        moveIfExists(targetFile(), rollbackFile())
        moveIfExists(targetWalFile(), rollbackWalFile())
        moveIfExists(targetShmFile(), rollbackShmFile())
    }

    private fun removeLiveFiles() {
        targetFile().delete()
        targetWalFile().delete()
        targetShmFile().delete()
    }

    private fun restoreFile(source: File, target: File) {
        if (!source.isFile) return
        atomicMove(source, target)
    }

    private fun cleanupRecoveryFiles() {
        pendingFile().delete()
        pendingTempFile().delete()
        candidateFile().delete()
        rollbackFile().delete()
        rollbackWalFile().delete()
        rollbackShmFile().delete()
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
        temp.outputStream().use { properties.store(it, null) }
        sync(temp)
        atomicMove(temp, journalFile())
    }

    private fun writeTextAtomically(file: File, text: String) {
        val temp = File(file.path + ".tmp")
        temp.writeText(text)
        sync(temp)
        atomicMove(temp, file)
    }

    private fun atomicReplace(source: File, target: File, failure: RestoreFailure) {
        try {
            atomicMove(source, target)
        } catch (_: Throwable) {
            throw RestoreValidationException(failure)
        }
    }

    private fun atomicMove(source: File, target: File) {
        target.parentFile?.mkdirs()
        try {
            Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            throw RestoreValidationException(RestoreFailure.FILE_REPLACEMENT_FAILURE)
        }
    }

    private fun moveIfExists(source: File, target: File) {
        if (source.isFile) atomicMove(source, target)
    }

    private fun sync(file: File) {
        FileOutputStream(file, true).use { it.fd.sync() }
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
