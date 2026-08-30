package com.ojnexus.judge.luogu

import com.ojnexus.core.network.CoroutineDelayProvider
import com.ojnexus.core.network.DelayProvider
import com.ojnexus.core.network.RateLimitedRequestGate
import com.ojnexus.judge.luogu.api.LuoguApi
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

sealed class LuoguApiError(message: String?, cause: Throwable? = null) : Exception(message, cause) {
    class RateLimited : LuoguApiError("Luogu rate limited")
    class ServerError(val statusCode: Int) : LuoguApiError("Luogu server error $statusCode")
    class HttpError(val statusCode: Int) : LuoguApiError("Luogu HTTP error $statusCode")
    class Network(cause: Throwable) : LuoguApiError(cause.message, cause)
    class Timeout(cause: Throwable) : LuoguApiError(cause.message, cause)
    class ParseError(cause: Throwable) : LuoguApiError(cause.message, cause)
}

data class LuoguRetryPolicy(
    val maxAttempts: Int = 3,
    val backoffBaseMs: Long = 1_100L,
) {
    init {
        require(maxAttempts in 1..3)
    }

    fun backoffMs(retryIndex: Int): Long = backoffBaseMs shl retryIndex.coerceIn(0, 2)
}

/** Single gated, bounded-retry transport boundary for Luogu's public endpoint. */
class LuoguClient(
    private val api: LuoguApi,
    private val gate: RateLimitedRequestGate,
    private val retryPolicy: LuoguRetryPolicy = LuoguRetryPolicy(),
    private val delayProvider: DelayProvider = CoroutineDelayProvider(),
) {
    suspend fun searchUsers(keyword: String) = call { api.searchUsers(keyword) }

    private suspend fun <T> call(block: suspend () -> T): T {
        var last: LuoguApiError? = null
        repeat(retryPolicy.maxAttempts) { attempt ->
            try {
                return gate.execute { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                val mapped = when {
                    e.code() == 429 -> LuoguApiError.RateLimited()
                    e.code() in 500..599 -> LuoguApiError.ServerError(e.code())
                    else -> LuoguApiError.HttpError(e.code())
                }
                if (!mapped.retryable || attempt == retryPolicy.maxAttempts - 1) throw mapped
                last = mapped
            } catch (e: SocketTimeoutException) {
                val mapped = LuoguApiError.Timeout(e)
                if (attempt == retryPolicy.maxAttempts - 1) throw mapped
                last = mapped
            } catch (e: IOException) {
                val mapped = LuoguApiError.Network(e)
                if (attempt == retryPolicy.maxAttempts - 1) throw mapped
                last = mapped
            } catch (e: SerializationException) {
                throw LuoguApiError.ParseError(e)
            } catch (e: IllegalArgumentException) {
                throw LuoguApiError.ParseError(e)
            }
            delayProvider.delayMs(retryPolicy.backoffMs(attempt))
        }
        throw last ?: LuoguApiError.ParseError(IllegalStateException("unreachable"))
    }

    private val LuoguApiError.retryable: Boolean
        get() = this is LuoguApiError.RateLimited ||
            this is LuoguApiError.ServerError ||
            this is LuoguApiError.Network ||
            this is LuoguApiError.Timeout
}
