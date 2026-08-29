package com.ojnexus.judge.codeforces

import com.ojnexus.core.network.DelayProvider
import com.ojnexus.core.network.CoroutineDelayProvider
import com.ojnexus.judge.codeforces.api.CodeforcesApi
import com.ojnexus.judge.codeforces.api.CodeforcesEnvelope
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/** Bounded retry configuration — the gate already enforces base spacing between calls. */
data class CodeforcesRetryPolicy(
    /** 1 initial attempt + 2 retries. Never an unbounded loop. */
    val maxAttempts: Int = 3,
    val backoffBaseMs: Long = 2_100,
) {
    fun backoffMs(retryIndex: Int): Long = backoffBaseMs shl retryIndex.coerceIn(0, 3)
}

/**
 * The single choke point between OJ NEXUS and the Codeforces API:
 *  1. every call passes the [CodeforcesRequestGate] (official >= 2 s limit),
 *  2. HTTP 200 + `status: FAILED` is mapped to typed [CodeforcesApiError]s (never success),
 *  3. only transient failures retry, at most [CodeforcesRetryPolicy.maxAttempts] with
 *     exponential backoff; CancellationException always propagates.
 */
class CodeforcesClient(
    private val api: CodeforcesApi,
    private val gate: CodeforcesRequestGate,
    private val retryPolicy: CodeforcesRetryPolicy = CodeforcesRetryPolicy(),
    private val delayProvider: DelayProvider = CoroutineDelayProvider(),
) {

    suspend fun <T> call(call: suspend CodeforcesApi.() -> CodeforcesEnvelope<T>): T {
        var lastError: CodeforcesApiError? = null
        repeat(retryPolicy.maxAttempts) { attempt ->
            try {
                val envelope: CodeforcesEnvelope<T> = gate.execute { api.call() }
                if (!envelope.isOk) {
                    throw mapEnvelopeFailure(envelope.comment)
                }
                return envelope.result
                    ?: throw CodeforcesApiError.ParseError(
                        IllegalStateException("OK envelope without result"),
                    )
            } catch (e: CodeforcesApiError) {
                if (!e.isRetryable || attempt == retryPolicy.maxAttempts - 1) throw e
                lastError = e
            } catch (e: CancellationException) {
                throw e
            } catch (e: SocketTimeoutException) {
                val error = CodeforcesApiError.Timeout(e)
                if (attempt == retryPolicy.maxAttempts - 1) throw error
                lastError = error
            } catch (e: IOException) {
                val error = CodeforcesApiError.NetworkUnavailable(e)
                if (attempt == retryPolicy.maxAttempts - 1) throw error
                lastError = error
            } catch (e: HttpException) {
                val error = if (e.code() in 500..599) {
                    CodeforcesApiError.ServerError(e.code())
                } else {
                    CodeforcesApiError.HttpError(e.code())
                }
                if (!error.isRetryable || attempt == retryPolicy.maxAttempts - 1) throw error
                lastError = error
            } catch (e: SerializationException) {
                throw CodeforcesApiError.ParseError(e)
            }
            delayProvider.delayMs(retryPolicy.backoffMs(attempt))
        }
        throw lastError ?: CodeforcesApiError.ApiFailed("unreachable")
    }

    private fun mapEnvelopeFailure(comment: String?): CodeforcesApiError {
        val raw = comment ?: "FAILED"
        val lowered = raw.lowercase()
        return when {
            "limit exceeded" in lowered -> CodeforcesApiError.RateLimited(raw)
            "not found" in lowered || "invalid handle" in lowered || "handles:" in lowered ->
                CodeforcesApiError.UserNotFound(raw)
            else -> CodeforcesApiError.ApiFailed(raw)
        }
    }

    private val CodeforcesApiError.isRetryable: Boolean
        get() = this is CodeforcesApiError.RateLimited ||
            this is CodeforcesApiError.ServerError ||
            this is CodeforcesApiError.NetworkUnavailable ||
            this is CodeforcesApiError.Timeout
}
