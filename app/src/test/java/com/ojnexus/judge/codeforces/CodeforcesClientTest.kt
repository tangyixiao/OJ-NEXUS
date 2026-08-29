package com.ojnexus.judge.codeforces

import com.ojnexus.core.model.Verdict
import com.ojnexus.core.network.DelayProvider
import com.ojnexus.core.network.MonotonicClock
import com.ojnexus.judge.codeforces.api.CodeforcesApi
import com.ojnexus.judge.codeforces.api.dto.CfContestDto
import com.ojnexus.judge.codeforces.api.dto.CfRatingChangeDto
import com.ojnexus.judge.codeforces.api.dto.CfUserDto
import com.ojnexus.judge.codeforces.mapper.CfMappers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

private class NoDelayProvider : DelayProvider {
    val delays = mutableListOf<Long>()
    override suspend fun delayMs(ms: Long) {
        delays += ms
    }
}

private class SteadyClock : MonotonicClock {
    override fun nowMs(): Long = 0L
}

/**
 * Wire-level client tests against MockWebServer — fully offline, CI-repeatable.
 * Verifies envelope handling (HTTP 200 + FAILED is never success), error mapping,
 * bounded retry and JSON compatibility rules.
 */
class CodeforcesClientTest {

    private lateinit var server: MockWebServer
    private lateinit var api: CodeforcesApi
    private val delays = NoDelayProvider()
    private val gateDelays = NoDelayProvider()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CodeforcesApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(maxAttempts: Int = 3) = CodeforcesClient(
        api = api,
        gate = CodeforcesRequestGate(2_100, SteadyClock(), gateDelays),
        retryPolicy = CodeforcesRetryPolicy(maxAttempts = maxAttempts, backoffBaseMs = 2_100),
        delayProvider = delays,
    )

    private fun enqueueBody(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse().setResponseCode(code).setBody(body)
                .setHeader("Content-Type", "application/json"),
        )
    }

    @Test
    fun `user info OK envelope returns the typed result`() {
        enqueueBody(
            """{"status":"OK","result":[{"handle":"tourist","rating":3979,"maxRating":3986,
                "rank":"legendary grandmaster","someFutureField":123}]}""",
        )
        val users = runBlocking { client().call { userInfo("tourist") } }
        assertEquals(1, users.size)
        assertEquals("tourist", users[0].handle)
        assertEquals(3979, users[0].rating)
        assertEquals(3986, users[0].maxRating)
    }

    @Test
    fun `HTTP 200 with FAILED envelope is never treated as success`() {
        enqueueBody("""{"status":"FAILED","comment":"not found"}""")
        assertThrows(CodeforcesApiError.UserNotFound::class.java) {
            runBlocking { client(maxAttempts = 1).call<List<CfUserDto>> { userInfo("nobody") } }
        }
    }

    @Test
    fun `rate limit comment maps to RateLimited and retries with backoff`() {
        enqueueBody("""{"status":"FAILED","comment":"Call limit exceeded"}""")
        enqueueBody("""{"status":"FAILED","comment":"Call limit exceeded"}""")
        enqueueBody("""{"status":"OK","result":[]}""")
        val result = runBlocking { client().call { userInfo("tourist") } }
        assertTrue(result.isEmpty())
        assertEquals(3, server.requestCount)
        // Two backoff waits after the two rejected attempts (2.1s then 4.2s).
        assertEquals(listOf(2_100L, 4_200L), delays.delays)
    }

    @Test
    fun `rate limit that never recovers exhausts bounded attempts`() {
        repeat(4) { enqueueBody("""{"status":"FAILED","comment":"Call limit exceeded"}""") }
        assertThrows(CodeforcesApiError.RateLimited::class.java) {
            runBlocking { client(maxAttempts = 3).call<List<CfRatingChangeDto>> { userRating("tourist") } }
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `unknown handle is not retried`() {
        enqueueBody("""{"status":"FAILED","comment":"handles: User with handle 'nobody' not found"}""")
        assertThrows(CodeforcesApiError.UserNotFound::class.java) {
            runBlocking { client().call<List<CfUserDto>> { userInfo("nobody") } }
        }
        assertEquals("no retry for permanent errors", 1, server.requestCount)
    }

    @Test
    fun `other FAILED comments map to ApiFailed with the raw comment preserved`() {
        enqueueBody("""{"status":"FAILED","comment":"something: weird happened"}""")
        val error = assertThrows(CodeforcesApiError.ApiFailed::class.java) {
            runBlocking { client(maxAttempts = 1).call<List<CfUserDto>> { userInfo("x") } }
        }
        assertEquals("something: weird happened", error.rawComment)
    }

    @Test
    fun `HTTP 500 retries and then maps to ServerError`() {
        enqueueBody("{}", code = 500)
        enqueueBody("{}", code = 500)
        assertThrows(CodeforcesApiError.ServerError::class.java) {
            runBlocking { client(maxAttempts = 2).call<List<CfContestDto>> { contestList(gym = false) } }
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `malformed JSON maps to ParseError without retry`() {
        enqueueBody("""{"status":"OK","result": [this is not json]}""")
        assertThrows(CodeforcesApiError.ParseError::class.java) {
            runBlocking { client().call<List<CfUserDto>> { userInfo("x") } }
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `missing optional fields parse as null and unknown JSON fields are ignored`() {
        enqueueBody(
            """{"status":"OK","result":[
                {"id":1,"creationTimeSeconds":1756400000,
                 "problem":{"name":"Mystery","index":"X1","contestId":9999},
                 "verdict":"SECURITY_VIOLATED","aFutureField":true}]}""",
        )
        val submissions = runBlocking { client().call { userStatus(handle = "tourist", from = 1, count = 10) } }
        assertEquals(1, submissions.size)
        assertEquals(null, submissions[0].contestId)
        assertEquals(null, submissions[0].participantType)
        // Unknown verdict string maps to OTHER, raw string preserved on the DTO.
        assertEquals(Verdict.OTHER, CfMappers.submissionVerdict(submissions[0].verdict))
        assertEquals("SECURITY_VIOLATED", submissions[0].verdict)
    }
}
