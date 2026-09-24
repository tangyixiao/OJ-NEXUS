package com.ojnexus.judge.atcoder

import com.ojnexus.core.network.DelayProvider
import com.ojnexus.core.network.MonotonicClock
import com.ojnexus.core.network.RateLimitedRequestGate
import com.ojnexus.judge.atcoder.api.AtCoderProblemsApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private class AtCoderTestClock : MonotonicClock {
    override fun nowMs(): Long = 0
}

private class AtCoderTestDelay : DelayProvider {
    val delays = mutableListOf<Long>()
    override suspend fun delayMs(ms: Long) {
        delays += ms
    }
}

class AtCoderClientTest {
    private lateinit var server: MockWebServer
    private lateinit var api: AtCoderProblemsApi
    private val backoff = AtCoderTestDelay()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(
                Json { ignoreUnknownKeys = true; coerceInputValues = true }
                    .asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(AtCoderProblemsApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun client(attempts: Int = 3) = AtCoderProblemsClient(
        api,
        RateLimitedRequestGate(1_100, AtCoderTestClock(), AtCoderTestDelay()),
        AtCoderRetryPolicy(maxAttempts = attempts, backoffBaseMs = 1_100),
        backoff,
    )

    @Test
    fun `submission call uses current v3 path and query parameters`() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))

        assertEquals(0, client().call { submissions("CaseUser", 123) }.size)
        val request = server.takeRequest()
        assertEquals("/atcoder-api/v3/user/submissions?user=CaseUser&from_second=123", request.path)
    }

    @Test
    fun `server errors retry within bounded policy`() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(500).setBody("{}")) }
        assertThrows(AtCoderApiError.ServerError::class.java) {
            runBlocking { client(2).call { contests() } }
        }
        assertEquals(2, server.requestCount)
        assertEquals(listOf(1_100L), backoff.delays)
    }

    @Test
    fun `schema errors do not retry or erase through client`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("not-json"))
        assertThrows(AtCoderApiError.ParseError::class.java) {
            runBlocking { client().call { contests() } }
        }
        assertEquals(1, server.requestCount)
    }
}
