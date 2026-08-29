package com.ojnexus.core.data

/**
 * Domain-level error surfaced by repositories. SQLite/IO exceptions are mapped here and never
 * leak raw storage errors into the UI.
 */
sealed interface DataError {
    val message: String

    data class DuplicateProblem(val key: String) : DataError {
        override val message: String = "Problem already exists: $key"
    }

    data class NotFound(val what: String) : DataError {
        override val message: String = "Not found: $what"
    }

    data class Storage(override val message: String) : DataError
}

/** Result type for one-shot repository mutations. */
sealed interface DataResult<out T> {
    data class Success<T>(val value: T) : DataResult<T>
    data class Failure(val error: DataError) : DataResult<Nothing>

    fun getOrNull(): T? = (this as? Success)?.value
}

inline fun <T> dataResult(block: () -> T): DataResult<T> = try {
    DataResult.Success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    // CancellationException extends IllegalStateException — it MUST propagate so WorkManager
    // and structured concurrency observe cancellation instead of a storage error.
    throw e
} catch (e: android.database.SQLException) {
    DataResult.Failure(DataError.Storage(e.message ?: "Storage error"))
} catch (e: IllegalStateException) {
    DataResult.Failure(DataError.Storage(e.message ?: "State error"))
}
