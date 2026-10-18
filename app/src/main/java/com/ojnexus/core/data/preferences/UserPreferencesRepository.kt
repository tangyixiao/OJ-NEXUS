package com.ojnexus.core.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ojnexus.core.data.backup.BackupCadence
import com.ojnexus.core.designsystem.NexusThemeSlot
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.NoteTemplate
import com.ojnexus.core.model.NoteTemplateCodec
import com.ojnexus.core.model.TrainingTarget
import com.ojnexus.core.model.TrainingTargetPolicy
import com.ojnexus.core.model.editorFields
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class UserPreferences(
    val reduceMotion: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val themeSlot: NexusThemeSlot = NexusThemeSlot.NEXUS_BLUE,
    val backupCadence: BackupCadence = BackupCadence.OFF,
    val backupTreeUri: String = "",
    val backupKeepCount: Int = BackupCadence.DEFAULT_KEEP_COUNT,
    val backupLastStatus: String = "",
)

internal val Context.userPreferencesDataStore by preferencesDataStore(
    name = "oj-nexus-preferences",
)

/** DataStore key for the note template payload; shared with tests that inject a corrupt value. */
const val NOTE_TEMPLATE_KEY: String = "note_template"

class UserPreferencesRepository(private val context: Context) {
    val preferences: Flow<UserPreferences> = context.userPreferencesDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { values ->
            UserPreferences(
                reduceMotion = values[Keys.REDUCE_MOTION] ?: false,
                hapticsEnabled = values[Keys.HAPTICS_ENABLED] ?: true,
                themeSlot = values[Keys.THEME_SLOT]?.let { value ->
                    runCatching { NexusThemeSlot.valueOf(value) }.getOrNull()
                } ?: NexusThemeSlot.NEXUS_BLUE,
                backupCadence = values[Keys.BACKUP_CADENCE]
                    ?.let(BackupCadence::fromStored)
                    ?: BackupCadence.OFF,
                backupTreeUri = values[Keys.BACKUP_TREE_URI] ?: "",
                backupKeepCount = (values[Keys.BACKUP_KEEP_COUNT]
                    ?: BackupCadence.DEFAULT_KEEP_COUNT)
                    .coerceIn(BackupCadence.MIN_KEEP_COUNT, BackupCadence.MAX_KEEP_COUNT),
                backupLastStatus = values[Keys.BACKUP_LAST_STATUS] ?: "",
            )
        }

    val trainingTargets: Flow<List<TrainingTarget>> = context.userPreferencesDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { values ->
            buildList {
                decodeTarget(values, judge = null)?.let(::add)
                JudgeId.entries.forEach { judge -> decodeTarget(values, judge)?.let(::add) }
            }
        }

    suspend fun setTrainingTarget(judge: JudgeId?, center: Int?, tolerance: Int): Boolean {
        val target = TrainingTarget(judge = judge, center = center, tolerance = tolerance)
        if (!TrainingTargetPolicy.isValid(target)) return false
        context.userPreferencesDataStore.edit { values ->
            val key = targetKey(judge)
            if (center == null) {
                values.remove(key.center)
                values.remove(key.tolerance)
            } else {
                values[key.center] = center
                values[key.tolerance] = tolerance
            }
        }
        return true
    }

    /**
     * The user's note layout. A corrupt or hand-edited value degrades to
     * [NoteTemplate.DEFAULT] instead of failing, so the editor always has a usable template.
     */
    val noteTemplate: Flow<NoteTemplate> = context.userPreferencesDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { values ->
            values[Keys.NOTE_TEMPLATE]
                ?.let { NoteTemplateCodec.decode(it) }
                ?: NoteTemplate.DEFAULT
        }

    /** Persists [template]; returns false (and writes nothing) when it is invalid. */
    suspend fun setNoteTemplate(template: NoteTemplate): Boolean {
        if (!template.validate()) return false
        context.userPreferencesDataStore.edit { values ->
            values[Keys.NOTE_TEMPLATE] = NoteTemplateCodec.encode(template)
        }
        return true
    }

    suspend fun clearTrainingTarget(judge: JudgeId?) {
        context.userPreferencesDataStore.edit { values ->
            val key = targetKey(judge)
            values.remove(key.center)
            values.remove(key.tolerance)
        }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.REDUCE_MOTION] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setThemeSlot(slot: NexusThemeSlot) {
        context.userPreferencesDataStore.edit { it[Keys.THEME_SLOT] = slot.name }
    }

    /** Persists the cadence; [treeUri] may be null to keep the stored folder unchanged. */
    suspend fun setBackupCadence(cadence: BackupCadence, treeUri: String? = null) {
        context.userPreferencesDataStore.edit { values ->
            values[Keys.BACKUP_CADENCE] = cadence.stored()
            if (treeUri != null) values[Keys.BACKUP_TREE_URI] = treeUri
        }
    }

    suspend fun setBackupTreeUri(uri: String) {
        context.userPreferencesDataStore.edit { it[Keys.BACKUP_TREE_URI] = uri }
    }

    suspend fun setBackupKeepCount(count: Int) {
        context.userPreferencesDataStore.edit { values ->
            values[Keys.BACKUP_KEEP_COUNT] = count
                .coerceIn(BackupCadence.MIN_KEEP_COUNT, BackupCadence.MAX_KEEP_COUNT)
        }
    }

    /** Written by the scheduled worker; `success:<epochMs>` or `failed:<epochMs>`. */
    suspend fun recordBackupStatus(success: Boolean, atEpochMs: Long) {
        context.userPreferencesDataStore.edit { values ->
            values[Keys.BACKUP_LAST_STATUS] = "${if (success) "success" else "failed"}:$atEpochMs"
        }
    }

    private object Keys {
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val THEME_SLOT = stringPreferencesKey("theme_slot")
        val NOTE_TEMPLATE = stringPreferencesKey(NOTE_TEMPLATE_KEY)
        val BACKUP_CADENCE = stringPreferencesKey("backup_cadence")
        val BACKUP_TREE_URI = stringPreferencesKey("backup_tree_uri")
        val BACKUP_KEEP_COUNT = intPreferencesKey("backup_keep_count")
        val BACKUP_LAST_STATUS = stringPreferencesKey("backup_last_status")
    }

    private data class TargetKeys(
        val center: Preferences.Key<Int>,
        val tolerance: Preferences.Key<Int>,
    )

    private fun targetKey(judge: JudgeId?): TargetKeys {
        val suffix = judge?.id ?: "default"
        return TargetKeys(
            center = intPreferencesKey("training_target_${suffix}_center"),
            tolerance = intPreferencesKey("training_target_${suffix}_tolerance"),
        )
    }

    private fun decodeTarget(
        values: Preferences,
        judge: JudgeId?,
    ): TrainingTarget? {
        val key = targetKey(judge)
        val center = values[key.center] ?: return null
        val target = TrainingTarget(
            judge = judge,
            center = center,
            tolerance = values[key.tolerance] ?: TrainingTargetPolicy.DEFAULT_TOLERANCE,
        )
        return target.takeIf(TrainingTargetPolicy::isValid)
    }
}
