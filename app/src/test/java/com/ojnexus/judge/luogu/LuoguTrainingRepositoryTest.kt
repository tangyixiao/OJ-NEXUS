package com.ojnexus.judge.luogu

import com.ojnexus.core.network.RateLimitedRequestGate
import com.ojnexus.core.network.DelayProvider
import com.ojnexus.core.network.MonotonicClock
import com.ojnexus.judge.luogu.api.LuoguApi
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/** Fake api that returns fixture HTML pages, counting calls like the CF adapter fakes. */
private class FakeLuoguTrainingApi(
    private val listPage: () -> Response<okhttp3.ResponseBody> = {
        Response.success(listHtmlBody())
    },
    private val trainingPage: (Long) -> Response<okhttp3.ResponseBody> = {
        Response.success(detailHtmlBody())
    },
    val calls: MutableList<String> = mutableListOf(),
) : LuoguApi {
    override suspend fun searchUsers(keyword: String) = throw UnsupportedOperationException()
    override suspend fun userPage(uid: Long) = throw UnsupportedOperationException()
    override suspend fun practicePage(uid: Long) = throw UnsupportedOperationException()
    override suspend fun problemPage(page: Int, keyword: String?) =
        throw UnsupportedOperationException()
    override suspend fun problem(pid: String) = throw UnsupportedOperationException()
    override suspend fun contestPage(page: Int) = throw UnsupportedOperationException()
    override suspend fun contest(contestId: String) = throw UnsupportedOperationException()
    override suspend fun recordPage(uid: Long, page: Int) = throw UnsupportedOperationException()
    override suspend fun problemsetDump(url: String) = throw UnsupportedOperationException()

    override suspend fun trainingListHtml(): Response<okhttp3.ResponseBody> {
        calls += "list"
        return listPage()
    }

    override suspend fun trainingHtml(id: Long): Response<okhttp3.ResponseBody> {
        calls += "training:$id"
        return trainingPage(id)
    }
}

private fun listHtmlBody(): okhttp3.ResponseBody =
    """
        <script id="lentille-context" type="application/json">{"template":"training.list","status":200,"data":{"trainings":{"perPage":18,"count":18,"result":[{"id":100,"name":"【入门1】顺序结构","type":1,"provider":{"uid":3,"name":"洛谷","color":"Purple"},"createTime":1583016195,"deadline":null,"problemCount":15,"markCount":38862}]}}}</script>
    """.trimIndent().toResponseBody("text/html".toMediaType())

private fun detailHtmlBody(): okhttp3.ResponseBody =
    """
        <script id="lentille-context" type="application/json">{"template":"training.show","status":200,"data":{"training":{"id":100,"name":"【入门1】顺序结构","provider":{"uid":3,"name":"洛谷"},"createTime":1583016195,"deadline":null,"problemCount":15,"markCount":38862,"description":"千里之行。","problems":[{"pid":"B2002","type":"B","name":"Hello,World!","difficulty":1,"fullScore":100,"tags":[353],"totalSubmit":1218376,"totalAccepted":604801}]}}}</script>
    """.trimIndent().toResponseBody("text/html".toMediaType())

private fun errorBody(status: Int): okhttp3.ResponseBody =
    "not found".toResponseBody("text/html".toMediaType())

class LuoguTrainingRepositoryTest {

    private fun repository(api: FakeLuoguTrainingApi): LuoguTrainingRepository =
        LuoguTrainingRepository(
            LuoguClient(
                api = api,
                gate = RateLimitedRequestGate(0, object : MonotonicClock {
                    override fun nowMs() = 0L
                }, object : DelayProvider {
                    override suspend fun delayMs(ms: Long) = Unit
                }),
                retryPolicy = LuoguRetryPolicy(maxAttempts = 1),
            ),
        )

    @Test
    fun `list decodes the public catalog from the embedded payload`() = runBlocking {
        val repo = repository(FakeLuoguTrainingApi())
        val trainings = repo.list()

        assertEquals(1, trainings.size)
        with(trainings.first()) {
            assertEquals(100L, id)
            assertEquals("【入门1】顺序结构", name)
            assertEquals(15, problemCount)
            assertEquals("洛谷", provider?.name)
        }
    }

    @Test
    fun `training loads the ordered problem ladder`() = runBlocking {
        val repo = repository(FakeLuoguTrainingApi())
        val detail = repo.training(100)

        assertEquals(100L, detail.id)
        assertEquals("千里之行。", detail.description)
        assertEquals(1, detail.problems.size)
        assertEquals("B2002", detail.problems.first().pid)
    }

    @Test
    fun `ghost training surfaces a typed http error`() = runBlocking {
        val repo = repository(
            FakeLuoguTrainingApi(
                trainingPage = { Response.error(404, errorBody(404)) },
            ),
        )
        val error = runCatching { repo.training(99999999) }.exceptionOrNull()

        assertTrue(error is LuoguApiError.HttpError)
        assertEquals(404, (error as LuoguApiError.HttpError).statusCode)
    }

    @Test
    fun `missing lentille payload surfaces a typed parse error`() = runBlocking {
        val repo = repository(
            FakeLuoguTrainingApi(
                listPage = {
                    Response.success(
                        "<html><body>no payload</body></html>".toResponseBody("text/html".toMediaType()),
                    )
                },
            ),
        )
        val error = runCatching { repo.list() }.exceptionOrNull()

        assertTrue(error is LuoguApiError.ParseError)
    }
}
