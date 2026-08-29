package com.ojnexus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import com.ojnexus.app.NexusApp
import com.ojnexus.core.designsystem.NexusTheme

/**
 * Single-activity entry point. All navigation and theming live in the Compose layer.
 * The app is dark-first, so system bars are forced to the dark style regardless of the
 * system setting.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            NexusTheme {
                NexusApp()
            }
        }
    }
}
