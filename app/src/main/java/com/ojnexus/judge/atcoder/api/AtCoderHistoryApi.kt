package com.ojnexus.judge.atcoder.api

import com.ojnexus.judge.atcoder.api.dto.AtCoderRatingChangeDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Official AtCoder site JSON endpoints (public, no auth). Separate from the kenkoooo
 * community API because the base URL differs; both share the same request gate and
 * bounded-retry client semantics.
 */
interface AtCoderHistoryApi {
    /**
     * Full rating history for [handle]. `contestType` narrows the ladder
     * (`algo` or `heuristic`); an unknown handle returns HTTP 200 with an empty array —
     * never a 404.
     */
    @GET("users/{handle}/history/json")
    suspend fun ratingHistory(
        @Path("handle") handle: String,
        @Query("contestType") contestType: String,
    ): List<AtCoderRatingChangeDto>
}
