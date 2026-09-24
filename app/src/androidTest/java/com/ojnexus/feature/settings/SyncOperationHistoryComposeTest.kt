package com.ojnexus.feature.settings

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ojnexus.core.data.sync.SyncStage
import com.ojnexus.core.database.dao.SyncOperationWithModules
import com.ojnexus.core.database.entity.SyncOperationEntity
import com.ojnexus.core.database.entity.SyncOperationModuleEntity
import com.ojnexus.core.database.entity.SyncOperationStatus
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.model.JudgeId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncOperationHistoryComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun largeFontHistoryShowsFailureAndFullRetryAction() {
        var clicked = 0
        composeRule.setContent {
            Box(Modifier.width(360.dp)) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    NexusTheme(reduceMotion = true) {
                        SyncOperationHistorySection(
                            judge = JudgeId.CODEFORCES,
                            operations = listOf(failedOperation()),
                            capabilities = emptySet(),
                            retryingOperationId = null,
                            onRetry = { clicked++ },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("RECENT SYNC HISTORY").assertIsDisplayed()
        composeRule.onNodeWithText("PROFILE ERROR 3/2 IMPORTED · 1 UPDATED").assertIsDisplayed()
        composeRule.onNodeWithText("RETRY FULL SYNC").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, clicked) }
    }

    @Test
    fun emptyHistoryShowsTextualEmptyState() {
        composeRule.setContent {
            NexusTheme(reduceMotion = true) {
                SyncOperationHistorySection(
                    judge = JudgeId.ATCODER,
                    operations = emptyList(),
                    capabilities = emptySet(),
                    retryingOperationId = null,
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("NO RECORDED SYNC RUNS").assertIsDisplayed()
    }

    private fun failedOperation() = SyncOperationWithModules(
        operation = SyncOperationEntity(
            id = 7,
            judge = JudgeId.CODEFORCES.id,
            accountId = 4,
            dataGeneration = "generation",
            startedAt = 100,
            status = SyncOperationStatus.PARTIAL.name,
        ),
        modules = listOf(
            SyncOperationModuleEntity(
                id = 1,
                operationId = 7,
                stage = SyncStage.PROFILE.name,
                status = "ERROR",
                attemptedCount = 3,
                importedCount = 2,
                updatedCount = 1,
                failureType = "Network",
            ),
        ),
    )
}
