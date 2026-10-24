package com.ojnexus.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ojnexus.core.designsystem.NexusTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoveryBlockedScreenComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun blockedCopyRemainsReachableOnCompactTallFontViewport() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                NexusTheme { Box(Modifier.size(width = 320.dp, height = 320.dp)) { RecoveryBlockedScreen() } }
            }
        }
        val title = compose.onNodeWithText("LOCAL RECOVERY BLOCKED")
        val preserved = compose.onNodeWithText("LOCAL FILES PRESERVED")
        val body = compose.onNodeWithText("AUTOMATIC RECOVERY STOPPED. CLOSE THE APP AND KEEP THE LOCAL FILES FOR INSPECTION.")
        title.assertIsDisplayed()
        preserved.performScrollTo().assertIsDisplayed()
        body.performScrollTo().assertIsDisplayed()
    }

    @Test fun startingCopyIsVisibleOnCompactTallFontViewport() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                NexusTheme { Box(Modifier.size(width = 320.dp, height = 320.dp)) { RecoveryStartingScreen() } }
            }
        }
        compose.onNodeWithText("INITIALIZING LOCAL STORAGE").assertIsDisplayed()
    }
}
