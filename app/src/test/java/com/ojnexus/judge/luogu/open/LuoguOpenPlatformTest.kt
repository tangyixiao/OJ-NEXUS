package com.ojnexus.judge.luogu.open

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
        client = LuoguOpenPlatformClient(api, FakeOpenAppCredentialStore(OpenAppCredential("u", "s")))
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
