package com.ojnexus.judge.luogu

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.network.DelayProvider
import com.ojnexus.core.network.MonotonicClock
import com.ojnexus.core.network.RateLimitedRequestGate
import com.ojnexus.judge.luogu.api.LuoguApi
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LuoguProblemDetailCacheTest {
    private lateinit var database: OjNexusDatabase
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OjNexusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
    }

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

    @Test
    fun `cache hit returns without public network`() = runBlocking {
        database.remoteProblemDetailDao().upsert(
            LuoguProblemDetailMapper.toCache(detail("Cached"), updatedAt = 10L),
        )

        val result = repository().fetch("P1001")

        assertEquals(LuoguProblemDetailSource.CACHE, result.source)
        assertEquals("Cached", result.detail.title)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `network miss persists public detail for later offline use`() = runBlocking {
        server.enqueue(jsonResponse(detailResponse("Network")))

        val result = repository().fetch("P1001")

        assertEquals(LuoguProblemDetailSource.NETWORK, result.source)
        assertEquals("Network", result.detail.title)
        assertEquals("Network", database.remoteProblemDetailDao().findByKey("luogu", "P1001")?.title)
    }

    @Test
    fun `explicit refresh replaces cached detail`() = runBlocking {
        database.remoteProblemDetailDao().upsert(
            LuoguProblemDetailMapper.toCache(detail("Old"), updatedAt = 10L),
        )
        server.enqueue(jsonResponse(detailResponse("New")))

        val result = repository().refresh("P1001")

        assertEquals(LuoguProblemDetailSource.NETWORK, result.source)
        assertEquals("New", result.detail.title)
        assertEquals("New", database.remoteProblemDetailDao().findByKey("luogu", "P1001")?.title)
    }

    @Test
    fun `network refresh failure returns cached detail`() = runBlocking {
        database.remoteProblemDetailDao().upsert(
            LuoguProblemDetailMapper.toCache(detail("Cached"), updatedAt = 10L),
        )
        server.shutdown()

        val result = repository().refresh("P1001")

        assertEquals(LuoguProblemDetailSource.CACHE_FALLBACK, result.source)
        assertEquals("Cached", result.detail.title)
    }

    @Test
    fun `server failure does not hide error or replace cached detail`() = runBlocking {
        database.remoteProblemDetailDao().upsert(
            LuoguProblemDetailMapper.toCache(detail("Cached"), updatedAt = 10L),
        )
        server.enqueue(MockResponse().setResponseCode(500))

        assertThrows(LuoguApiError.ServerError::class.java) {
            runBlocking { repository().refresh("P1001") }
        }
        assertEquals("Cached", database.remoteProblemDetailDao().findByKey("luogu", "P1001")?.title)
    }

    private fun repository(readTimeoutMs: Long = 500): LuoguProblemDetailRepository {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LuoguApi::class.java)
        val client = LuoguClient(
            api = api,
            gate = RateLimitedRequestGate(0, object : MonotonicClock {
                override fun nowMs() = 0L
            }, object : DelayProvider {
                override suspend fun delayMs(ms: Long) = Unit
            }),
            retryPolicy = LuoguRetryPolicy(maxAttempts = 1),
            delayProvider = object : DelayProvider {
                override suspend fun delayMs(ms: Long) = Unit
            },
        )
        return LuoguProblemDetailRepository(client, database.remoteProblemDetailDao(), Clock.systemUTC())
    }

    private fun detail(title: String) = LuoguProblemDetail(
        pid = "P1001",
        title = title,
        difficulty = 1,
        tags = listOf(1),
        totalSubmit = 2,
        totalAccepted = 1,
        background = "background",
        description = "description",
        inputFormat = "input",
        outputFormat = "output",
        hint = "hint",
        samples = listOf("1", "2"),
        timeLimitMs = 1000,
        memoryLimitMb = 128,
    )

    private fun detailResponse(title: String) =
        """{"status":200,"data":{"problem":{"pid":"P1001","name":"$title","difficulty":1,"tags":[1],"totalSubmit":2,"totalAccepted":1,"contenu":{"background":"background","description":"description","formatI":"input","formatO":"output","hint":"hint"},"samples":["1","2"],"limits":{"time":[1000],"memory":[131072]}}}}"""

    private fun jsonResponse(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
