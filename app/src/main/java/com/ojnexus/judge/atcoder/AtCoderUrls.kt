package com.ojnexus.judge.atcoder

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object AtCoderUrls {
    const val API_BASE_URL = "https://kenkoooo.com/"

    fun contest(contestId: String): String = "https://atcoder.jp/contests/${segment(contestId)}"

    fun problem(contestId: String, problemId: String): String =
        "${contest(contestId)}/tasks/${segment(problemId)}"

    fun user(handle: String): String = "https://atcoder.jp/users/${segment(handle)}"

    private fun segment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}
