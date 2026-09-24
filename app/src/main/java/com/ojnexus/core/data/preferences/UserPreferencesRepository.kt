package com.ojnexus.core.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.ojnexus.core.designsystem.NexusThemeSlot
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.TrainingTarget
import com.ojnexus.core.model.TrainingTargetPolicy
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class UserPreferences(
    val reduceMotion: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val themeSlot: NexusThemeSlot = NexusThemeSlot.NEXUS_BLUE,
)

private val Context.userPreferencesDataStore by preferencesDataStore(name = "oj-nexus-preferences")

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

    private object Keys {
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val THEME_SLOT = androidx.datastore.preferences.core.stringPreferencesKey("theme_slot")
    }

    private data class TargetKeys(
        val center: androidx.datastore.preferences.core.Preferences.Key<Int>,
        val tolerance: androidx.datastore.preferences.core.Preferences.Key<Int>,
    )

    private fun targetKey(judge: JudgeId?): TargetKeys {
        val suffix = judge?.id ?: "default"
        return TargetKeys(
            center = androidx.datastore.preferences.core.intPreferencesKey("training_target_${suffix}_center"),
            tolerance = androidx.datastore.preferences.core.intPreferencesKey("training_target_${suffix}_tolerance"),
        )
    }

    private fun decodeTarget(
        values: androidx.datastore.preferences.core.Preferences,
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
