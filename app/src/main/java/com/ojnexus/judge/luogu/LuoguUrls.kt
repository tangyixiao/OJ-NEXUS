package com.ojnexus.judge.luogu

import java.net.URLEncoder

object LuoguUrls {
    const val API_BASE_URL = "https://www.luogu.com.cn/"

    fun user(uid: Long): String = "https://www.luogu.com.cn/user/${segment(uid.toString())}"

    private fun segment(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
