package com.ojnexus.feature.settings

import com.ojnexus.core.database.entity.SyncStateEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.JudgeCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncReceiptTest {
    @Test
    fun `receipt follows capability order and selects entity timestamps`() {
        val state = SyncStateEntity(
            judge = JudgeId.LUOGU.id,
            profileSyncedAt = 1_000L,
            ratingSyncedAt = 2_000L,
            submissionsSyncedAt = 3_000L,
            contestsSyncedAt = 4_000L,
            problemsetSyncedAt = 5_000L,
        )

        assertEquals(
            listOf(
                SyncReceiptItem(SyncReceiptModule.PROFILE, 1_000L),
                SyncReceiptItem(SyncReceiptModule.RATING, 2_000L),
                SyncReceiptItem(SyncReceiptModule.SUBMISSIONS, 3_000L),
                SyncReceiptItem(SyncReceiptModule.CONTESTS, 4_000L),
                SyncReceiptItem(SyncReceiptModule.PROBLEMSET, 5_000L),
            ),
            syncReceiptItems(
                capabilities = setOf(
                    JudgeCapability.PROFILE,
                    JudgeCapability.RATING_HISTORY,
                    JudgeCapability.SUBMISSIONS,
                    JudgeCapability.CONTESTS,
                    JudgeCapability.PROBLEM_CATALOG,
                ),
                state = state,
            ),
        )
    }

    @Test
    fun `public Luogu capabilities do not invent private submissions`() {
        val items = syncReceiptItems(
            capabilities = setOf(
                JudgeCapability.PROFILE,
                JudgeCapability.RATING_HISTORY,
                JudgeCapability.CONTESTS,
                JudgeCapability.PROBLEM_CATALOG,
            ),
            state = SyncStateEntity(judge = JudgeId.LUOGU.id),
        )

        assertEquals(
            listOf(
                SyncReceiptModule.PROFILE,
                SyncReceiptModule.RATING,
                SyncReceiptModule.CONTESTS,
                SyncReceiptModule.PROBLEMSET,
            ),
            items.map(SyncReceiptItem::module),
        )
    }

    @Test
    fun `missing state keeps supported modules never synced`() {
        assertTrue(
            syncReceiptItems(setOf(JudgeCapability.PROFILE), state = null)
                .single().syncedAt == null,
        )
    }

    @Test
    fun `sync age covers never recent minutes hours days and clock skew`() {
        assertEquals(SyncAge.NEVER, formatSyncAge(now = 100_000L, syncedAt = null))
        assertEquals(SyncAge.JUST_NOW, formatSyncAge(now = 100_000L, syncedAt = 99_999L))
        assertEquals(SyncAge.MINUTES_AGO(2), formatSyncAge(now = 220_000L, syncedAt = 100_000L))
        assertEquals(SyncAge.HOURS_AGO(2), formatSyncAge(now = 7_300_000L, syncedAt = 100_000L))
        assertEquals(SyncAge.DAYS_AGO(2), formatSyncAge(now = 172_900_000L, syncedAt = 100_000L))
        assertEquals(SyncAge.JUST_NOW, formatSyncAge(now = 100_000L, syncedAt = 120_000L))
    }
}
