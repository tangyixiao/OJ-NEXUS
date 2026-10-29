package com.ojnexus.core.data.restore

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Real durable file operations; tests may decorate this boundary while retaining real SQLite files. */
internal interface RestoreFileOperations {
    fun copyAndSync(source: File, target: File)
    fun writeAndSync(target: File, bytes: ByteArray)
    fun atomicReplace(source: File, target: File)
    fun deleteChecked(file: File)

    /** Creates the import staging temp file; injectable for staging allocation faults. */
    fun createStagingTemp(directory: File): File =
        File.createTempFile("oj-nexus-import-", ".db", directory)

    /** Copies an input stream to [target] and syncs it; injectable for staged-write faults. */
    fun copyStreamAndSync(source: InputStream, target: File) {
        target.parentFile?.mkdirs()
        FileOutputStream(target, false).use { output ->
            source.use { it.copyTo(output) }
            output.fd.sync()
        }
    }
}

internal object SystemRestoreFileOperations : RestoreFileOperations {
    override fun copyAndSync(source: File, target: File) {
        target.parentFile?.mkdirs()
        FileOutputStream(target, false).use { output ->
            source.inputStream().use { it.copyTo(output) }
            output.fd.sync()
        }
    }

    override fun writeAndSync(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        FileOutputStream(target, false).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
    }

    override fun atomicReplace(source: File, target: File) {
        target.parentFile?.mkdirs()
        try {
            Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            throw IllegalStateException("Atomic file replacement is unavailable")
        }
    }

    override fun deleteChecked(file: File) {
        if (file.exists() && !file.delete()) throw IllegalStateException("Could not delete recovery file")
    }
}
