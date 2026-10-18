package com.ojnexus.core.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashLogTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun filesDir() = temp.newFolder("files")

    @Test
    fun writeProducesReadableReportWithThreadAndException() {
        val dir = filesDir()
        val throwable = IllegalStateException("boom")
        val file = CrashLog.write(dir, Thread.currentThread(), throwable, 1_700_000_000_000)
        assertNotNull(file)
        val body = file!!.readText()
        assertTrue(body.contains("THREAD: "))
        assertTrue(body.contains("EXCEPTION: java.lang.IllegalStateException"))
        assertTrue(body.contains("MESSAGE: boom"))
        assertTrue(body.contains("never leaves the device"))
    }

    @Test
    fun writePrunesBeyondTenFilesKeepingNewest() {
        val dir = filesDir()
        repeat(12) { index ->
            CrashLog.write(dir, Thread.currentThread(), RuntimeException("c$index"), index.toLong() * 1000L)
        }
        val files = CrashLog.crashFiles(dir)
        assertEquals(CrashLog.MAX_FILES, files.size)
        // Newest first, and the two oldest are gone.
        assertTrue(files.first().name > files.last().name)
        assertNull(files.firstOrNull { it.name.contains("c0") })
    }

    @Test
    fun newestReturnsNewestOrNone() {
        val dir = filesDir()
        assertNull(CrashLog.newest(dir))
        val first = CrashLog.write(dir, Thread.currentThread(), RuntimeException("a"), 1_000L)!!
        val second = CrashLog.write(dir, Thread.currentThread(), RuntimeException("b"), 2_000L)!!
        assertEquals(second.name, CrashLog.newest(dir)!!.name)
        assertTrue(second.name > first.name)
    }

    @Test
    fun clearRemovesEverything() {
        val dir = filesDir()
        CrashLog.write(dir, Thread.currentThread(), RuntimeException("a"), 1_000L)
        CrashLog.write(dir, Thread.currentThread(), RuntimeException("b"), 2_000L)
        assertEquals(2, CrashLog.clear(dir))
        assertEquals(0, CrashLog.crashFiles(dir).size)
    }

    @Test
    fun crashCaptureHasNoNetworkImports() {
        // Constraint: nothing leaves the device. The capture class must not even carry network
        // machinery in its source.
        val source = java.io.File(
            "src/main/java/com/ojnexus/core/crash/CrashLog.kt",
        ).readText()
        listOf("okhttp", "retrofit", "HttpURL", "java.net.URL", "Socket").forEach { banned ->
            assertTrue(
                "CrashLog must not reference $banned",
                !source.contains(banned),
            )
        }
    }
}
