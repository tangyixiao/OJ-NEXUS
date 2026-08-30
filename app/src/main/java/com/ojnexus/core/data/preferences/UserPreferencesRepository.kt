package com.ojnexus.core.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.ojnexus.core.designsystem.NexusThemeSlot
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
}
