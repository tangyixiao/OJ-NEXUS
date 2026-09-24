package com.ojnexus.judge.luogu

import java.net.URLEncoder

object LuoguUrls {
    const val API_BASE_URL = "https://www.luogu.com.cn/"
    const val OPEN_PLATFORM_BASE_URL = "https://open-v1.lgapi.cn/"
    const val OPEN_PLATFORM_WEBSOCKET_URL = "wss://open-ws.lgapi.cn/ws"
    const val PROBLEMSET_DUMP_URL = "https://cdn.luogu.com.cn/problemset-open/latest.ndjson.gz"

    fun openPlatformDocs(): String = "https://docs.lgapi.cn/open/"

    fun user(uid: Long): String = "https://www.luogu.com.cn/user/${segment(uid.toString())}"

    fun contest(contestId: String): String = "https://www.luogu.com.cn/contest/${segment(contestId)}"

    fun problem(pid: String): String = "https://www.luogu.com.cn/problem/${segment(pid)}"

    private fun segment(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
