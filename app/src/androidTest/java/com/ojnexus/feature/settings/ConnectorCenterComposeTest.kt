package com.ojnexus.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ojnexus.core.data.sync.SyncPhase
import com.ojnexus.core.database.entity.JudgeAccountEntity
import com.ojnexus.core.database.entity.SyncStateEntity
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.JudgeCapability
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectorCenterComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun connectedCenterShowsCoverageAndQueuesSyncAll() {
        var clicked = 0
        composeRule.setContent {
            NexusTheme(reduceMotion = true) {
                ConnectorCenterSection(
                    summary = deriveConnectorCenter(
                        listOf(
                            connection(
                                JudgeId.CODEFORCES,
                                account = account(JudgeId.CODEFORCES),
                                sync = SyncStateEntity(
                                    judge = JudgeId.CODEFORCES.id,
                                    state = SyncPhase.SUCCESS.name,
                                    profileSyncedAt = 1L,
                                    submissionsSyncedAt = 2L,
                                ),
                                capabilities = setOf(
                                    JudgeCapability.PROFILE,
                                    JudgeCapability.SUBMISSIONS,
                                    JudgeCapability.BACKGROUND_SYNC,
                                ),
                            ),
                        ),
                    ),
                    syncAllInFlight = false,
                    onSyncAll = { clicked++ },
                )
            }
        }

        composeRule.onNodeWithText("OJ CONNECTOR CENTER").assertIsDisplayed()
        composeRule.onNodeWithText("CODEFORCES").assertIsDisplayed()
        composeRule.onNodeWithText("2/2 RECEIPTS").assertIsDisplayed()
        composeRule
            .onNode(
                hasContentDescription("Queue a foreground refresh for every connected judge") and hasClickAction(),
            )
            .performClick()
        composeRule.runOnIdle { assertEquals(1, clicked) }
    }

    @Test
    fun emptyCenterDisablesSyncAll() {
        composeRule.setContent {
            NexusTheme(reduceMotion = true) {
                ConnectorCenterSection(
                    summary = deriveConnectorCenter(emptyList()),
                    syncAllInFlight = false,
                    onSyncAll = {},
                )
            }
        }

        composeRule.onNodeWithText("NO JUDGE ADAPTERS REGISTERED").assertIsDisplayed()
        composeRule.onNodeWithText("SYNC ALL").assertIsDisplayed()
        composeRule.onNodeWithText("SYNC ALL").assertIsNotEnabled()
    }

    private fun connection(
        judge: JudgeId,
        account: JudgeAccountEntity? = null,
        sync: SyncStateEntity? = null,
        capabilities: Set<JudgeCapability> = emptySet(),
    ) = JudgeConnectionUi(
        judge = judge,
        account = account,
        profile = null,
        syncState = sync,
        capabilities = capabilities,
        reliability = com.ojnexus.judge.DataSourceReliability.OFFICIAL,
    )

    private fun account(judge: JudgeId) = JudgeAccountEntity(
        id = 7L,
        judge = judge.id,
        handle = "raw_handle",
        canonicalHandle = "handle",
        connectedAt = 1L,
        updatedAt = 1L,
    )
}
