package com.ojnexus.judge.luogu

import com.ojnexus.core.network.DelayProvider
import com.ojnexus.core.network.MonotonicClock
import com.ojnexus.core.network.RateLimitedRequestGate
import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.luogu.api.LuoguApi
import com.ojnexus.judge.luogu.api.dto.LuoguContestListResponse
import com.ojnexus.judge.luogu.api.dto.LuoguProblemListResponse
import com.ojnexus.judge.luogu.api.dto.LuoguRecordPageResponse
import com.ojnexus.judge.luogu.api.dto.LuoguUserPageResponse
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LuoguPublicTransportTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `content-only user page decodes public profile and rating history`() {
        val response = """
            {
              "status": 200,
              "template": "user.show",
              "instance": "main",
              "locale": "zh-CN",
              "data": {
                "user": {
                  "uid": 7, "name": "alice", "avatar": "https://cdn/avatar.png",
                  "slogan": "hello", "introduction": "bio", "eloValue": 1200,
                  "ranking": 42, "followerCount": 8, "followingCount": 9,
                  "passedProblemCount": 10, "submittedProblemCount": 12,
                  "ccfLevel": 6, "xcpcLevel": 3, "badge": "verified"
                },
                "gu": {"rating": 1200, "time": 1700000000,
                  "scores": {"rating": 1200, "practice": 80}},
                "elo": [{"rating": 1200, "time": 1700000000,
                  "contest": {"id": 99, "startTime": 1699990000,
                    "endTime": 1700000000, "name": "Round"},
                  "userCount": 20, "prevDiff": 100,
                  "previous": {"rating": 1100}}]
              }
            }
        """.trimIndent()

        server.enqueue(jsonResponse(response))
        val result = runBlocking { client().fetchUserPage(7) }

        assertEquals("alice", result.data?.user?.name)
        assertEquals(1200, result.data?.gu?.rating)
        assertEquals(99L, result.data?.elo?.single()?.contest?.id)
    }

    @Test
    fun `problem and contest pages expose server counts and rows`() {
        server.enqueue(jsonResponse(
            """{"status":200,"template":"problem.list","data":{"problems":{"perPage":50,"count":1,"result":[{"pid":"P1000","name":"A+B Problem","difficulty":1,"tags":["math"],"totalAccepted":3,"totalSubmit":4}]}}}""",
        ))
        server.enqueue(jsonResponse(
            """{"status":200,"template":"contest.list","data":{"contests":{"perPage":20,"count":1,"result":[{"id":12,"startTime":1700000000,"endTime":1700003600,"name":"Contest","method":2,"rated":1,"problemCount":5}]}}}""",
        ))

        val client = client()
        val problemPage = runBlocking { client.fetchProblemPage(1) }
        val contestPage = runBlocking { client.fetchContestPage(1) }

        assertEquals(1, problemPage.data?.problems?.count)
        assertEquals("P1000", problemPage.data?.problems?.result?.single()?.pid)
        assertEquals(12L, contestPage.data?.contests?.result?.single()?.id)
        assertEquals(3600L, contestPage.data?.contests?.result?.single()?.endTime?.minus(
            contestPage.data?.contests?.result?.single()?.startTime ?: 0L,
        ))
    }

    @Test
    fun `public requests carry Luogu content-only transport headers`() {
        server.enqueue(jsonResponse("""{"status":200,"template":"user.show","data":{}}"""))

        runBlocking { client().fetchUserPage(7) }
        val request = server.takeRequest(1, TimeUnit.SECONDS)

        assertEquals("content-only", request?.getHeader("x-lentille-request"))
        assertEquals("XMLHttpRequest", request?.getHeader("X-Requested-With"))
        assertEquals("application/json", request?.getHeader("Accept"))
    }

    @Test
    fun `auth envelope is not treated as empty public submission data`() {
        server.enqueue(jsonResponse(
            """{"status":200,"instance":"auth","template":"login","data":{"webauthn":true}}""",
        ))

        val error = assertThrows(LuoguApiError.AuthenticationRequired::class.java) {
            runBlocking { client().fetchRecordPage(7, 1) }
        }

        assertTrue(error.message!!.contains("authentication", ignoreCase = true))
    }

    @Test
    fun `malformed public JSON maps to a non-retryable parse error`() {
        server.enqueue(jsonResponse("not-json"))

        assertThrows(LuoguApiError.ParseError::class.java) {
            runBlocking { client(maxAttempts = 3).fetchUserPage(7) }
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `keyword search requests the matching page and maps rows for the local cache`() {
        server.enqueue(jsonResponse(
            """{"status":200,"data":{"problems":{"perPage":50,"count":1,"result":[{"pid":"P1001","name":"A+B Problem","difficulty":1,"tags":["math"],"totalAccepted":3}]}}}""",
        ))

        val repository = LuoguProblemSearchRepository(
            client(),
            Clock.fixed(Instant.ofEpochMilli(42L), ZoneOffset.UTC),
        )
        val rows = runBlocking {
            repository.fetch(JudgeId.LUOGU, " P1001 ", limit = 50, offset = 50)
        }

        assertEquals("P1001", rows.single().externalId)
        assertEquals(3, rows.single().solvedCount)
        assertEquals("/problem/list?page=2&keyword=P1001", server.takeRequest().path)
    }

    private fun client(maxAttempts: Int = 1): LuoguClient {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LuoguApi::class.java)
        return LuoguClient(
            api = api,
            gate = RateLimitedRequestGate(0, object : MonotonicClock {
                override fun nowMs() = 0L
            }, object : DelayProvider {
                override suspend fun delayMs(ms: Long) = Unit
            }),
            retryPolicy = LuoguRetryPolicy(maxAttempts = maxAttempts, backoffBaseMs = 1),
            delayProvider = object : DelayProvider {
                override suspend fun delayMs(ms: Long) = Unit
            },
        )
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
