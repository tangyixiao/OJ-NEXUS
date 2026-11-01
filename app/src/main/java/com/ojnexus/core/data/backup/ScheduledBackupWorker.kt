package com.ojnexus.core.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ojnexus.OjNexusApplication
import com.ojnexus.app.NexusStartupState
import com.ojnexus.core.data.repository.BackupRepository
import com.ojnexus.core.data.restore.RestoreWorkDecision
import com.ojnexus.core.data.restore.decideRestoreWorkGeneration
import com.ojnexus.core.data.preferences.UserPreferencesRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

/**
 * Runs the opt-in local snapshot backup into the user-picked SAF folder and prunes the
 * oldest archives beyond the keep count. Local only: no network, no credentials.
 */
class ScheduledBackupWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? OjNexusApplication ?: return Result.failure()
        val container = when (val state = application.startupState) {
            NexusStartupState.Starting -> return Result.retry()
            is NexusStartupState.Blocked -> return Result.failure(androidx.work.workDataOf("result" to "RECOVERY_BLOCKED"))
            is NexusStartupState.Ready -> state.container
        }
        // Phase 127B: refuse queued backups captured under another (or no) generation before
        // any preference read or export; the next ready start re-stamps the periodic entry.
        val stamp = inputData.getString(DATA_GENERATION)?.trim()?.takeIf { it.isNotEmpty() }
        when (decideRestoreWorkGeneration(stamp, container.currentDataGeneration())) {
            RestoreWorkDecision.STALE ->
                return Result.success(androidx.work.workDataOf(RESULT_KEY to STALE_RESULT))
            RestoreWorkDecision.LEGACY ->
                return Result.success(androidx.work.workDataOf(RESULT_KEY to UNSTAMPED_RESULT))
            RestoreWorkDecision.PROCEED -> Unit
        }
        val repository = UserPreferencesRepository(applicationContext)
        val preferences = repository.preferences.first()
        val treeUri = preferences.backupTreeUri
        if (preferences.backupCadence == BackupCadence.OFF || treeUri.isBlank()) {
            // The schedule was cancelled after this run was already queued.
            return Result.success()
        }
        val startedAt = System.currentTimeMillis()
        val exported = runCatching {
            exportToTree(
                context = applicationContext,
                treeUri = treeUri,
                keepCount = preferences.backupKeepCount,
                backupRepository = container.backupRepository,
            )
        }.getOrElse { false }
        repository.recordBackupStatus(exported, startedAt)
        return if (exported) Result.success() else Result.failure()
    }

    companion object {
        private val NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        const val NAME_PREFIX = "oj-nexus-auto-"
        const val NAME_SUFFIX = ".zip"
        const val DATA_GENERATION = "data_generation"
        const val RESULT_KEY = "result"
        const val STALE_RESULT = "STALE_DATA_GENERATION"
        const val UNSTAMPED_RESULT = "UNSTAMPED_DATA_GENERATION"

        /** Dated archive name; the timestamp sorts lexically for pruning. */
        fun autoBackupName(atEpochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
            "$NAME_PREFIX${NAME_FORMAT.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(atEpochMs), zone))}$NAME_SUFFIX"

        /**
         * Pure keep-N selection: archives matching the auto-backup name shape, newest first,
         * returns the display names to delete (everything past [keep]).
         */
        fun pruneSelection(names: List<String>, keep: Int): List<String> {
            val archives = names
                .filter { it.startsWith(NAME_PREFIX) && it.endsWith(NAME_SUFFIX) }
                .sortedDescending()
            val effectiveKeep = keep.coerceAtLeast(BackupCadence.MIN_KEEP_COUNT)
            return archives.drop(effectiveKeep)
        }

        /** Creates a dated document in [treeUri], exports the snapshot, then prunes. */
        suspend fun exportToTree(
            context: Context,
            treeUri: String,
            keepCount: Int,
            backupRepository: BackupRepository,
        ): Boolean {
            val resolver = context.contentResolver
            val parsed = Uri.parse(treeUri)
            val tree = DocumentsContract.buildDocumentUriUsingTree(
                parsed,
                DocumentsContract.getTreeDocumentId(parsed),
            )
            val created = DocumentsContract.createDocument(
                resolver,
                tree,
                "application/zip",
                autoBackupName(System.currentTimeMillis()),
            ) ?: return false
            val exported = backupRepository.exportSnapshot(resolver, created)
            if (!exported) {
                // A failed export can leave an empty document behind; remove it so the
                // folder never accumulates broken archives.
                runCatching { DocumentsContract.deleteDocument(resolver, created) }
                return false
            }
            pruneTree(context, treeUri, keepCount)
            return true
        }

        private fun pruneTree(context: Context, treeUri: String, keepCount: Int) {
            val resolver = context.contentResolver
            val parsed = Uri.parse(treeUri)
            val treeDocId = DocumentsContract.getTreeDocumentId(parsed)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parsed, treeDocId)
            val names = mutableListOf<String>()
            val idsByName = mutableMapOf<String, String>()
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    names += name
                    idsByName[name] = cursor.getString(0)
                }
            }
            pruneSelection(names, keepCount).forEach { name ->
                idsByName[name]?.let { documentId ->
                    runCatching {
                        DocumentsContract.deleteDocument(
                            resolver,
                            DocumentsContract.buildDocumentUriUsingTree(parsed, documentId),
                        )
                    }
                }
            }
        }
    }
}
