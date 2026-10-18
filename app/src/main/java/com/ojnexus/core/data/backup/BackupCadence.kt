package com.ojnexus.core.data.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Opt-in schedule for the local snapshot backup. Local only — never any cloud upload. */
enum class BackupCadence(val days: Long) {
    OFF(0L),
    DAILY(1L),
    WEEKLY(7L),
    ;

    fun stored(): String = name

    companion object {
        const val MIN_KEEP_COUNT = 1
        const val MAX_KEEP_COUNT = 20
        const val DEFAULT_KEEP_COUNT = 5

        /** Unknown or legacy values degrade to [OFF] instead of throwing. */
        fun fromStored(value: String): BackupCadence =
            entries.firstOrNull { it.name == value } ?: OFF
    }
}

/** Enqueues or cancels the periodic local-backup work. Purely local constraints. */
object ScheduledBackupScheduler {
    const val UNIQUE_NAME = "oj-nexus-scheduled-backup"

    fun update(context: Context, cadence: BackupCadence) {
        val manager = WorkManager.getInstance(context)
        if (cadence == BackupCadence.OFF) {
            manager.cancelUniqueWork(UNIQUE_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<ScheduledBackupWorker>(cadence.days, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        manager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
