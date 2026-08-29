package com.ojnexus.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun formatCountdownUsesHoursMinutesAndSeconds() {
        assertEquals("01:01:01", formatCountdown(3_661))
    }

    @Test
    fun formatCountdownClampsNegativeValues() {
        assertEquals("00:00:00", formatCountdown(-1))
    }
}
