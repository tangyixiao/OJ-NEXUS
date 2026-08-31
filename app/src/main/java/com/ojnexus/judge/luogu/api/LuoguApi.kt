package com.ojnexus.judge.luogu.api

import com.ojnexus.judge.luogu.api.dto.LuoguUserSearchResponse
import com.ojnexus.judge.luogu.api.dto.LuoguContestListResponse
import com.ojnexus.judge.luogu.api.dto.LuoguProblemListResponse
import com.ojnexus.judge.luogu.api.dto.LuoguProblemDetailResponse
import com.ojnexus.judge.luogu.api.dto.LuoguRecordPageResponse
import com.ojnexus.judge.luogu.api.dto.LuoguUserPageResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface LuoguApi {
    @GET("api/user/search")
    suspend fun searchUsers(
        @Query("keyword") keyword: String,
    ): LuoguUserSearchResponse

    @Headers(
        "Accept: application/json",
        "X-Requested-With: XMLHttpRequest",
        "x-lentille-request: content-only",
    )
    @GET("user/{uid}")
    suspend fun userPage(@Path("uid") uid: Long): LuoguUserPageResponse

    @Headers(
        "Accept: application/json",
        "X-Requested-With: XMLHttpRequest",
        "x-lentille-request: content-only",
    )
    @GET("user/{uid}/practice")
    suspend fun practicePage(@Path("uid") uid: Long): LuoguUserPageResponse

    @Headers(
        "Accept: application/json",
        "X-Requested-With: XMLHttpRequest",
        "x-lentille-request: content-only",
    )
    @GET("problem/list")
    suspend fun problemPage(@Query("page") page: Int): LuoguProblemListResponse

    @Headers(
        "Accept: application/json",
        "X-Requested-With: XMLHttpRequest",
        "x-lentille-request: content-only",
    )
    @GET("problem/{pid}")
    suspend fun problem(@Path("pid") pid: String): LuoguProblemDetailResponse

    @Headers(
        "Accept: application/json",
        "X-Requested-With: XMLHttpRequest",
        "x-lentille-request: content-only",
    )
    @GET("contest/list")
    suspend fun contestPage(@Query("page") page: Int): LuoguContestListResponse

    @Headers(
        "Accept: application/json",
        "X-Requested-With: XMLHttpRequest",
        "x-lentille-request: content-only",
    )
    @GET("record/list")
    suspend fun recordPage(
        @Query("user") uid: Long,
        @Query("page") page: Int,
    ): LuoguRecordPageResponse
}
