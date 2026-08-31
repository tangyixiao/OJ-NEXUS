package com.ojnexus.judge.luogu

import java.net.URLEncoder

object LuoguUrls {
    const val API_BASE_URL = "https://www.luogu.com.cn/"
    const val OPEN_PLATFORM_BASE_URL = "https://open-v1.lgapi.cn/"

    fun user(uid: Long): String = "https://www.luogu.com.cn/user/${segment(uid.toString())}"

    fun contest(contestId: String): String = "https://www.luogu.com.cn/contest/${segment(contestId)}"

    fun problem(pid: String): String = "https://www.luogu.com.cn/problem/${segment(pid)}"

    private fun segment(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
