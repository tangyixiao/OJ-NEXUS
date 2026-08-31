package com.ojnexus.judge.luogu.open

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LuoguOpenPlatformValidationTest {

    @Test
    fun `problem requests require pid language and code`() {
        assertEquals(
            LuoguOpenRequestValidation.PidRequired,
            LuoguOpenRequestValidator.validateProblem(
                LuoguProblemJudgeRequest(pid = "", lang = "cxx/14/gcc", o2 = false, code = "int main() {}"),
            ),
        )
        assertEquals(
            LuoguOpenRequestValidation.LanguageRequired,
            LuoguOpenRequestValidator.validateProblem(
                LuoguProblemJudgeRequest(pid = "P1001", lang = "", o2 = false, code = "int main() {}"),
            ),
        )
        assertEquals(
            LuoguOpenRequestValidation.CodeRequired,
            LuoguOpenRequestValidator.validateProblem(
                LuoguProblemJudgeRequest(pid = "P1001", lang = "cxx/14/gcc", o2 = false, code = ""),
            ),
        )
    }

    @Test
    fun `run input obeys official size limit and track id is bounded`() {
        val valid = LuoguRunRequest(
            input = "x",
            lang = "cxx/14/gcc",
            o2 = false,
            code = "int main() {}",
            trackId = "a".repeat(64),
        )
        assertEquals(LuoguOpenRequestValidation.Valid, LuoguOpenRequestValidator.validateRun(valid))
        assertEquals(
            LuoguOpenRequestValidation.InputTooLarge,
            LuoguOpenRequestValidator.validateRun(valid.copy(input = "x".repeat(7169))),
        )
        assertEquals(
            LuoguOpenRequestValidation.TrackIdTooLong,
            LuoguOpenRequestValidator.validateRun(valid.copy(trackId = "a".repeat(65))),
        )
    }

    @Test
    fun `basic authorization encodes the OpenApp pair and does not expose raw secret`() {
        val header = OpenAppBasicAuth.header(OpenAppCredential("app-user", "app-secret"))
        assertTrue(header.startsWith("Basic "))
        assertFalse(header.contains("app-secret"))
        assertEquals(
            "Basic YXBwLXVzZXI6YXBwLXNlY3JldA==",
            header,
        )
    }
}

class LuoguOpenPlatformClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: LuoguOpenPlatformClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(
                Json { ignoreUnknownKeys = true }
                    .asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(LuoguOpenPlatformApi::class.java)
        client = LuoguOpenPlatformClient(
            api,
            FakeOpenAppCredentialStore(OpenAppCredential("u", "s")),
            webSocketUrl = server.url("/ws").toString(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `problem submission uses official path and Basic authorization`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"requestId\":\"req-1\"}"))

        val response = client.submitProblem(
            LuoguProblemJudgeRequest(
                pid = "P1001",
                lang = "cxx/14/gcc",
                o2 = true,
                code = "int main() {}",
            ),
        )

        assertEquals("req-1", response.requestId)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/judge/problem", request.path)
        assertEquals("Basic dTpz", request.getHeader("Authorization"))
    }

    @Test
    fun `result endpoint maps 204 to pending`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        assertEquals(LuoguOpenResult.Pending, client.fetchResult("req-1"))
        assertEquals("/judge/result/req-1", server.takeRequest().path)
    }

    @Test
    fun `result endpoint maps non terminal 200 to in progress`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "requestId": "req-partial",
                      "type": "judge",
                      "data": {
                        "compile": {"success": true, "message": "compiled"},
                        "judge": {"status": 0, "score": 0}
                      }
                    }
                    """.trimIndent(),
                ),
        )

        val result = client.fetchResult("req-partial")

        assertTrue(result is LuoguOpenResult.InProgress)
        assertEquals(0, (result as LuoguOpenResult.InProgress).evaluation.status)
        assertEquals(true, result.evaluation.compileSuccess)
    }

    @Test
    fun `result signal filters request id and channel payload`() = runBlocking {
        val closed = CountDownLatch(1)
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                        webSocket.send("judge.result\u0000{\"requestId\":\"other\"}")
                        webSocket.send("judge.result\u0000{\"requestId\":\"req-1\"}")
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        closed.countDown()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                        closed.countDown()
                    }
                },
            ),
        )

        assertTrue(client.awaitResultSignal("req-1", 1_000L))
        assertTrue(closed.await(1, TimeUnit.SECONDS))
        val request = server.takeRequest()
        assertEquals("/ws", request.requestUrl?.encodedPath)
        assertEquals("u:s", request.requestUrl?.queryParameter("token"))
        assertEquals("judge.result", request.requestUrl?.queryParameter("channel"))
    }

    @Test
    fun `result signal timeout returns false and closes socket`() = runBlocking {
        val closed = CountDownLatch(1)
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        closed.countDown()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                        closed.countDown()
                    }
                },
            ),
        )

        assertFalse(client.awaitResultSignal("req-timeout", 100L))
        assertTrue(closed.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun `quota endpoint maps available points and sends Basic authorization`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "quotas": [
                        {
                          "availablePoints": 30000,
                          "createTime": 1697620471,
                          "validAfter": 1713542400,
                          "expireTime": 1721491199,
                          "points": {"max": 30000, "used": 0}
                        },
                        {
                          "availablePoints": 120,
                          "createTime": 1697620472,
                          "validAfter": 1713542400,
                          "expireTime": 1721491199,
                          "points": {"max": 500, "used": 380}
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )

        val quota = client.fetchQuota()

        assertEquals(2, quota.quotas.size)
        assertEquals(30120L, quota.totalAvailablePoints)
        assertEquals(30000L, quota.quotas.first().maxPoints)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/judge/quotaAvailable", request.path)
        assertEquals("Basic dTpz", request.getHeader("Authorization"))
    }

    @Test
    fun `quota exhaustion is typed and is never retried`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(402))

        try {
            client.submitProblem(
                LuoguProblemJudgeRequest(
                    pid = "P1001",
                    lang = "cxx/14/gcc",
                    o2 = false,
                    code = "int main() {}",
                ),
            )
            throw AssertionError("expected quota error")
        } catch (error: LuoguOpenApiError.QuotaExceeded) {
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `custom input execution is rejected before any network request`() = runBlocking {
        assertEquals(false, client.supportsCustomInputRun)
        try {
            client.run(
                LuoguRunRequest(
                    input = "1 2",
                    lang = "cxx/14/gcc",
                    o2 = false,
                    code = "int main() {}",
                ),
            )
            throw AssertionError("expected unsupported operation")
        } catch (error: LuoguOpenApiError.UnsupportedOperation) {
            assertEquals("Luogu Open Platform does not expose custom-input execution", error.message)
            assertEquals(0, server.requestCount)
        }
    }
}

private class FakeOpenAppCredentialStore(
    private val credential: OpenAppCredential?,
) : OpenAppCredentialStore {
    override suspend fun read(): OpenAppCredential? = credential
    override suspend fun write(value: OpenAppCredential) = Unit
    override suspend fun clear() = Unit
}
