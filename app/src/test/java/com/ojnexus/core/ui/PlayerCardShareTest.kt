package com.ojnexus.core.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerCardShareTest {
    @Test
    fun `player card renderer writes a valid png artifact`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = PlayerCardShare.renderToCache(
            context,
            PlayerCardImageData(
                title = "OJ NEXUS",
                role = "COMPETITIVE PROGRAMMER",
                cardLabel = "PLAYER CARD",
                achievementsLabel = "ACHIEVEMENTS",
                solvedLabel = "SOLVED",
                solvedValue = "12",
                attemptsLabel = "SUBMISSIONS",
                attemptsValue = "20",
                activeDaysLabel = "ACTIVE DAYS",
                activeDaysValue = "8",
                streakLabel = "STREAK",
                streakValue = "7",
                maxDifficultyLabel = "MAX DIFF",
                maxDifficultyValue = "1800",
                achievements = listOf("FIRST BLOOD"),
            ),
        )!!

        assertTrue(file.exists())
        assertTrue(file.length() > 100)
        assertArrayEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
            file.inputStream().use { it.readNBytes(4) },
        )
        file.delete()
    }
}
