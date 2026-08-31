package com.ojnexus.judge.luogu

import org.junit.Assert.assertEquals
import org.junit.Test

class LuoguUrlsTest {
    @Test
    fun `contest and problem URLs use canonical Luogu routes`() {
        assertEquals("https://www.luogu.com.cn/contest/123", LuoguUrls.contest("123"))
        assertEquals("https://www.luogu.com.cn/problem/P1001", LuoguUrls.problem("P1001"))
    }

    @Test
    fun `OpenApp documentation URL stays on the official docs host`() {
        assertEquals("https://docs.lgapi.cn/open/", LuoguUrls.openPlatformDocs())
    }
}
