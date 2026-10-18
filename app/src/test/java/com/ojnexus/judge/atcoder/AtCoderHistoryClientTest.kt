package com.ojnexus.judge.atcoder

import com.ojnexus.core.network.DelayProvider
import com.ojnexus.core.network.MonotonicClock
import com.ojnexus.core.network.RateLimitedRequestGate
import com.ojnexus.judge.atcoder.api.AtCoderHistoryApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private class HistoryTestClock : MonotonicClock {
    override fun nowMs(): Long = 0
}

private class HistoryTestDelay : DelayProvider {
    override suspend fun delayMs(ms: Long) = Unit
}

/**
 * Fixture shape captured live from `GET https://atcoder.jp/users/<handle>/history/json`
 * (2026-10-18 probe): PascalCase fields, `ContestNameEn` present-but-blank, `EndTime`
 * carrying a `+09:00`-style offset.
 */
class AtCoderHistoryClientTest {
    private lateinit var server: MockWebServer
    private lateinit var api: AtCoderHistoryApi

    private val fixture = """
        [
          {"IsRated":true,"Place":2,"OldRating":0,"NewRating":2720,"Performance":3920,
           "InnerPerformance":3920,"ContestScreenName":"agc004.contest.atcoder.jp",
           "ContestName":"AtCoder Grand Contest 004","ContestNameEn":"",
           "EndTime":"2016-09-04T22:50:00+09:00"},
          {"IsRated":false,"Place":42,"OldRating":2720,"NewRating":0,"Performance":1800,
           "InnerPerformance":1600,"ContestScreenName":"ahc001.contest.atcoder.jp",
           "ContestName":"AHC001","ContestNameEn":"","EndTime":"2020-01-19T21:00:00+09:00"}
        ]
    """.trimIndent()

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
            .create(AtCoderHistoryApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun client() = AtCoderProblemsClient(
        api,
        RateLimitedRequestGate(1_100, HistoryTestClock(), HistoryTestDelay()),
        AtCoderRetryPolicy(maxAttempts = 3, backoffBaseMs = 1_100),
        HistoryTestDelay(),
    )

    @Test
    fun `history call hits official path with contestType query`() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody("[]"),
        )

        assertEquals(0, client().call { ratingHistory("tourist", "algo") }.size)
        val request = server.takeRequest()
        assertEquals("/users/tourist/history/json?contestType=algo", request.path)
    }

    @Test
    fun `fixture decodes rated and unrated rows with offsets intact`() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(fixture),
        )

        val rows = client().call { ratingHistory("tourist", "algo") }

        assertEquals(2, rows.size)
        val rated = rows.first()
        assertTrue(rated.isRated)
        assertEquals(2, rated.place)
        assertEquals(0, rated.oldRating)
        assertEquals(2720, rated.newRating)
        assertEquals("agc004.contest.atcoder.jp", rated.contestScreenName)
        assertEquals("AtCoder Grand Contest 004", rated.contestName)
        assertEquals("", rated.contestNameEn)
        val unrated = rows.last()
        assertFalse(unrated.isRated)
        assertEquals(0, unrated.newRating)
    }
}
