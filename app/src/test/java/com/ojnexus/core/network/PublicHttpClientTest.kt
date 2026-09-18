package com.ojnexus.core.network

import okhttp3.CookieJar
import org.junit.Assert.assertSame
import org.junit.Test

class PublicHttpClientTest {
    @Test
    fun `builder disables cookie handling`() {
        val client = PublicHttpClient.builder().build()

        assertSame(CookieJar.NO_COOKIES, client.cookieJar)
    }
}
