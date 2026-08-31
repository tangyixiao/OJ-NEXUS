package com.ojnexus.judge.luogu

import org.junit.Assert.assertEquals
import org.junit.Test

class LuoguProblemDetailCacheTest {
    @Test
    fun `detail mapper round trips every native detail field`() {
        val original = LuoguProblemDetail(
            pid = "P1001",
            title = "A+B Problem",
            difficulty = 1,
            tags = listOf(1, 2, 3),
            totalSubmit = 120,
            totalAccepted = 100,
            background = "background",
            description = "description",
            inputFormat = "input",
            outputFormat = "output",
            hint = "hint",
            samples = listOf("1 2", "3"),
            timeLimitMs = 1000,
            memoryLimitMb = 128,
        )

        val cached = LuoguProblemDetailMapper.toCache(original, updatedAt = 42L)
        val restored = LuoguProblemDetailMapper.fromCache(cached)

        assertEquals(original, restored)
        assertEquals("luogu", cached.judge)
        assertEquals("P1001", cached.externalId)
        assertEquals(42L, cached.updatedAt)
    }
}
