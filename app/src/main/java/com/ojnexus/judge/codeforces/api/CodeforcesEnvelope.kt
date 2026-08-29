package com.ojnexus.judge.codeforces.api

import kotlinx.serialization.Serializable

/**
 * Official Codeforces API response envelope. HTTP 200 does NOT mean success — the caller
 * must check [status]; `FAILED` carries a `comment` and maps to a [com.ojnexus.judge.codeforces.CodeforcesApiError].
 */
@Serializable
data class CodeforcesEnvelope<T>(
    val status: String,
    val comment: String? = null,
    val result: T? = null,
) {
    val isOk: Boolean get() = status == "OK"
}
