package com.ojnexus.core.crash

import java.io.File

/**
 * Local-only crash capture. Opt-in via UserPreferences.crashLogEnabled; the handler is installed
 * from OjNexusApplication only when the preference is on. Nothing ever leaves the device — this
 * class intentionally has no network imports (asserted by CrashLogTest).
 */
object CrashLog {

    const val DIR_NAME = "crash"
    const val MAX_FILES = 10
    private const val PREFIX = "crash-"
    private const val SUFFIX = ".txt"

    /**
     * Write a crash report and prune old files. Returns the written file, or null when writing
     * failed (never throws — this runs inside an uncaught-exception path).
     */
    fun write(filesDir: File, thread: Thread, throwable: Throwable, nowMs: Long): File? = try {
        val dir = File(filesDir, DIR_NAME).apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss-SSS", java.util.Locale.US)
            .format(java.util.Date(nowMs))
        val file = File(dir, "$PREFIX$stamp$SUFFIX")
        file.writeText(buildBody(thread, throwable, nowMs))
        prune(dir)
        file
    } catch (_: Throwable) {
        null
    }

    /** Newest crash file, or null when none exists. */
    fun newest(filesDir: File): File? = crashFiles(filesDir).firstOrNull()

    /** All crash files, newest first. */
    fun crashFiles(filesDir: File): List<File> {
        val dir = File(filesDir, DIR_NAME)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }
            ?.sortedByDescending { it.name }
            ?: emptyList()
    }

    /** Delete every crash file; returns the number removed. */
    fun clear(filesDir: File): Int {
        val files = crashFiles(filesDir)
        files.forEach { it.delete() }
        return files.size
    }

    fun prune(dir: File) {
        crashFilesInDir(dir).drop(MAX_FILES).forEach { it.delete() }
    }

    private fun crashFilesInDir(dir: File): List<File> =
        dir.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }
            ?.sortedByDescending { it.name }
            ?: emptyList()

    internal fun buildBody(thread: Thread, throwable: Throwable, nowMs: Long): String = buildString {
        appendLine("OJ NEXUS LOCAL CRASH REPORT")
        appendLine("TIME_EPOCH_MS: $nowMs")
        appendLine("THREAD: ${thread.name}")
        appendLine("EXCEPTION: ${throwable.javaClass.name}")
        appendLine("MESSAGE: ${throwable.message ?: "<none>"}")
        appendLine("STACKTRACE:")
        appendLine(throwable.stackTraceToString())
        appendLine("LOCAL ONLY — this report never leaves the device.")
    }
}
