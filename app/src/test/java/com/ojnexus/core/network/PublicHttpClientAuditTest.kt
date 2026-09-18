package com.ojnexus.core.network

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the public HTTP boundary: production sources must build OkHttp clients through
 * [PublicHttpClient] so cookie handling stays disabled everywhere.
 */
class PublicHttpClientAuditTest {

    @Test
    fun mainSourcesBuildClientsThroughPublicHttpClient() {
        val sourceRoot = findMainSourceRoot()

        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != PUBLIC_CLIENT_FILE }
            .flatMap { file ->
                file.readLines()
                    .mapIndexed { index, line -> index + 1 to line }
                    .filter { (_, line) -> FORBIDDEN_CONSTRUCTIONS.any { line.contains(it) } }
                    .map { (number, _) -> "${file.relativeTo(sourceRoot).invariantSeparatorsPath}:$number" }
            }
            .toList()

        assertEquals(
            "OkHttpClient must only be referenced from $PUBLIC_CLIENT_FILE",
            emptyList<String>(),
            offenders,
        )
    }

    private fun findMainSourceRoot(): File {
        var directory = File(System.getProperty(USER_DIR_PROPERTY))
        while (directory.parentFile != null) {
            val candidate = File(directory, MAIN_SOURCE_RELATIVE_PATH)
            if (candidate.isDirectory) {
                return candidate
            }
            directory = directory.parentFile
        }
        throw AssertionError(
            "Unable to locate $MAIN_SOURCE_RELATIVE_PATH from ${System.getProperty(USER_DIR_PROPERTY)}",
        )
    }

    private companion object {
        const val MAIN_SOURCE_RELATIVE_PATH = "src/main/java"
        const val PUBLIC_CLIENT_FILE = "PublicHttpClient.kt"
        val FORBIDDEN_CONSTRUCTIONS = listOf("OkHttpClient.Builder(", "OkHttpClient(")
        const val USER_DIR_PROPERTY = "user.dir"
    }
}
