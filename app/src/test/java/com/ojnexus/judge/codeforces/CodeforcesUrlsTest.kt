package com.ojnexus.judge.codeforces

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * URL builder exactness for the only place Codeforces URLs are constructed
 * (docs/OJ_ADAPTERS.md; Phase 92 DoD: contest/submission/protocol edge cases).
 */
class CodeforcesUrlsTest {

    @Test
    fun `problem url joins contest id and encoded index`() {
        assertEquals(
            "https://codeforces.com/problemset/problem/2134/C",
            CodeforcesUrls.problem(contestId = 2134L, index = "C"),
        )
    }

    @Test
    fun `problem url encodes multi-character index`() {
        assertEquals(
            "https://codeforces.com/problemset/problem/1900/C1",
            CodeforcesUrls.problem(contestId = 1900L, index = "C1"),
        )
    }

    @Test
    fun `problem url percent-encodes space in index`() {
        assertEquals(
            "https://codeforces.com/problemset/problem/1/A%20B",
            CodeforcesUrls.problem(contestId = 1L, index = "A B"),
        )
    }

    @Test
    fun `submission url joins contest id and submission id`() {
        assertEquals(
            "https://codeforces.com/contest/2134/submission/281234567",
            CodeforcesUrls.submission(contestId = 2134L, submissionId = 281_234_567L),
        )
    }

    @Test
    fun `submission url is stable for minimal ids`() {
        assertEquals(
            "https://codeforces.com/contest/1/submission/1",
            CodeforcesUrls.submission(contestId = 1L, submissionId = 1L),
        )
    }

    @Test
    fun `contest url is exact`() {
        assertEquals(
            "https://codeforces.com/contest/1900",
            CodeforcesUrls.contest(contestId = 1900L),
        )
    }

    @Test
    fun `profile url percent-encodes handle without plus signs`() {
        assertEquals(
            "https://codeforces.com/profile/some%20handle",
            CodeforcesUrls.profile(handle = "some handle"),
        )
        assertEquals(
            "https://codeforces.com/profile/tourist",
            CodeforcesUrls.profile(handle = "tourist"),
        )
    }
}
