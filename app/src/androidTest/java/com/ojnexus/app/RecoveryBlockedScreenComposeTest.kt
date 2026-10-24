package com.ojnexus.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ojnexus.core.designsystem.NexusTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoveryBlockedScreenComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun blockedCopyIsVisibleAtScaledFontSize() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.4f)) {
                NexusTheme { RecoveryBlockedScreen() }
            }
        }
        compose.onNodeWithText("LOCAL RECOVERY BLOCKED").assertIsDisplayed()
        compose.onNodeWithText("LOCAL FILES PRESERVED").assertIsDisplayed()
        compose.onNodeWithText("AUTOMATIC RECOVERY STOPPED. CLOSE THE APP AND KEEP THE LOCAL FILES FOR INSPECTION.").assertIsDisplayed()
    }
}
