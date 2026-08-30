package com.ojnexus.judge.luogu.api

import com.ojnexus.judge.luogu.api.dto.LuoguUserSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface LuoguApi {
    @GET("api/user/search")
    suspend fun searchUsers(
        @Query("keyword") keyword: String,
    ): LuoguUserSearchResponse
}
