package com.ojnexus.core.data

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DataResultTest {

    @Test
    fun `success wraps the block value`() {
        val result = dataResult { 42 }
        assertEquals(42, (result as DataResult.Success).value)
    }

    @Test
    fun `SQLException maps to Storage failure`() {
        val result = dataResult { throw android.database.sqlite.SQLiteConstraintException("dup") }
        assertTrue(result is DataResult.Failure)
        assertTrue((result as DataResult.Failure).error is DataError.Storage)
    }

    @Test
    fun `IllegalStateException maps to Storage failure`() {
        val result = dataResult { throw IllegalStateException("A session is already active") }
        assertTrue(result is DataResult.Failure)
        assertEquals(
            "A session is already active",
            (result as DataResult.Failure).error.message,
        )
    }

    @Test
    fun `cancellation is rethrown, never mapped to a DataError`() {
        // Regression for WorkManager cancellation: CancellationException extends
        // IllegalStateException; without the explicit rethrow branch it would be swallowed
        // and surface as a bogus storage error.
        assertThrows(CancellationException::class.java) {
            dataResult { throw CancellationException("worker stopped") }
        }
    }
}
