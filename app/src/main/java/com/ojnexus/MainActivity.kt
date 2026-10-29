package com.ojnexus

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import com.ojnexus.app.NexusApp
import com.ojnexus.app.NexusStartupState
import com.ojnexus.app.RecoveryBlockedScreen
import com.ojnexus.app.RecoveryStartingScreen
import com.ojnexus.core.data.preferences.UserPreferences
import com.ojnexus.core.designsystem.NexusTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Single-activity entry point. All navigation and theming live in the Compose layer.
 * The app is dark-first, so system bars are forced to the dark style regardless of the
 * system setting.
 */
class MainActivity : AppCompatActivity() {

    override fun onResume() {
        super.onResume()
        ((application as OjNexusApplication).startupState as? NexusStartupState.Ready)?.container?.localDaySource?.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as OjNexusApplication
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            when (val state = app.startupState) {
                NexusStartupState.Starting -> NexusTheme { RecoveryStartingScreen() }
                is NexusStartupState.Blocked -> NexusTheme { RecoveryBlockedScreen() }
                is NexusStartupState.Ready -> {
                    val preferences by state.container.userPreferencesRepository.preferences
                        .collectAsStateWithLifecycle(initialValue = UserPreferences())
                    NexusTheme(reduceMotion = preferences.reduceMotion, hapticsEnabled = preferences.hapticsEnabled,
                        themeSlot = preferences.themeSlot) { NexusApp() }
                }
            }
        }
    }
}
