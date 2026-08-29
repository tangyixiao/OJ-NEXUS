package com.ojnexus.judge.codeforces

import java.net.URLEncoder

/**
 * Official Codeforces URL builder — the ONLY place Codeforces URLs are constructed.
 * Path segments are URL-encoded.
 */
object CodeforcesUrls {

    const val API_BASE_URL = "https://codeforces.com/api/"
    private const val SITE_BASE = "https://codeforces.com"

    fun profile(handle: String): String =
        "$SITE_BASE/profile/${encode(handle)}"

    /** e.g. contestId=2134, index=C → /problemset/problem/2134/C */
    fun problem(contestId: Long, index: String): String =
        "$SITE_BASE/problemset/problem/${contestId}/${encode(index)}"

    fun contest(contestId: Long): String =
        "$SITE_BASE/contest/${contestId}"

    private fun encode(segment: String): String =
        URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
}
