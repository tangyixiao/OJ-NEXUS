package com.ojnexus.judge.atcoder

import com.ojnexus.core.network.CoroutineDelayProvider
import com.ojnexus.core.network.DelayProvider
import com.ojnexus.core.network.RateLimitedRequestGate
import com.ojnexus.judge.atcoder.api.AtCoderProblemsApi
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

sealed class AtCoderApiError(message: String?, cause: Throwable? = null) : Exception(message, cause) {
    class RateLimited : AtCoderApiError("AtCoder Problems rate limited")
    class ServerError(val statusCode: Int) : AtCoderApiError("AtCoder Problems server error $statusCode")
    class HttpError(val statusCode: Int) : AtCoderApiError("AtCoder Problems HTTP $statusCode")
    class Network(cause: Throwable) : AtCoderApiError(cause.message, cause)
    class Timeout(cause: Throwable) : AtCoderApiError(cause.message, cause)
    class ParseError(cause: Throwable) : AtCoderApiError(cause.message, cause)
}

data class AtCoderRetryPolicy(
    val maxAttempts: Int = 3,
    val backoffBaseMs: Long = 1_100,
) {
    init {
        require(maxAttempts in 1..3)
    }

    fun backoffMs(retryIndex: Int): Long = backoffBaseMs shl retryIndex.coerceIn(0, 2)
}

/** Single gated, bounded-retry transport boundary for all AtCoder Problems requests. */
class AtCoderProblemsClient(
    private val api: AtCoderProblemsApi,
    private val gate: RateLimitedRequestGate,
    private val retryPolicy: AtCoderRetryPolicy = AtCoderRetryPolicy(),
    private val delayProvider: DelayProvider = CoroutineDelayProvider(),
) {
    suspend fun <T> call(call: suspend AtCoderProblemsApi.() -> T): T {
        var last: AtCoderApiError? = null
        repeat(retryPolicy.maxAttempts) { attempt ->
            try {
                return gate.execute { api.call() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                val mapped = when {
                    e.code() == 429 -> AtCoderApiError.RateLimited()
                    e.code() in 500..599 -> AtCoderApiError.ServerError(e.code())
                    else -> AtCoderApiError.HttpError(e.code())
                }
                if (!mapped.retryable || attempt == retryPolicy.maxAttempts - 1) throw mapped
                last = mapped
            } catch (e: SocketTimeoutException) {
                val mapped = AtCoderApiError.Timeout(e)
                if (attempt == retryPolicy.maxAttempts - 1) throw mapped
                last = mapped
            } catch (e: IOException) {
                val mapped = AtCoderApiError.Network(e)
                if (attempt == retryPolicy.maxAttempts - 1) throw mapped
                last = mapped
            } catch (e: SerializationException) {
                throw AtCoderApiError.ParseError(e)
            } catch (e: IllegalArgumentException) {
                throw AtCoderApiError.ParseError(e)
            }
            delayProvider.delayMs(retryPolicy.backoffMs(attempt))
        }
        throw last ?: AtCoderApiError.ParseError(IllegalStateException("unreachable"))
    }

    private val AtCoderApiError.retryable: Boolean
        get() = this is AtCoderApiError.RateLimited ||
            this is AtCoderApiError.ServerError ||
            this is AtCoderApiError.Network ||
            this is AtCoderApiError.Timeout
}
