package com.ojnexus.core.network

import okhttp3.CookieJar
import okhttp3.OkHttpClient

/** Shared transport builder for anonymous public judge endpoints. */
object PublicHttpClient {
    fun builder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
}
