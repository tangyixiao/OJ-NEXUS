package com.ojnexus.judge.codeforces

/**
 * Unified error surface for the Codeforces adapter. Raw API comments are preserved for
 * debugging; UI maps these to short telemetry-style messages and never shows stack traces.
 */
sealed class CodeforcesApiError(message: String) : Exception(message) {
    val rawComment: String? = message

    /** No connectivity (IOException from the transport). */
    class NetworkUnavailable(cause: Throwable) :
        CodeforcesApiError(cause.message ?: "network unavailable")

    /** Socket/read timeout. */
    class Timeout(cause: Throwable) : CodeforcesApiError(cause.message ?: "timeout")

    /** Official rate limit hit (`FAILED: Call limit exceeded`) — retryable with backoff. */
    class RateLimited(comment: String) : CodeforcesApiError(comment)

    /** Handle does not exist (or historic handle no longer resolves) — not retryable. */
    class UserNotFound(comment: String) : CodeforcesApiError(comment)

    /** Any other `status: FAILED` envelope. */
    class ApiFailed(comment: String) : CodeforcesApiError(comment)

    /** HTTP 5xx from the server — retryable. */
    class ServerError(code: Int) : CodeforcesApiError("HTTP $code")

    /** Unexpected HTTP status (4xx etc.) — not retryable by default. */
    class HttpError(code: Int) : CodeforcesApiError("HTTP $code")

    /** Response body could not be parsed into the typed DTOs. */
    class ParseError(cause: Throwable) : CodeforcesApiError(cause.message ?: "parse error")
}
