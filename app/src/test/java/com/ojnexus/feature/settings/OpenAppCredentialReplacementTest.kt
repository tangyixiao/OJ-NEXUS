package com.ojnexus.feature.settings

import com.ojnexus.judge.luogu.open.LuoguOpenApiError
import com.ojnexus.judge.luogu.open.LuoguOpenCredentialVerifier
import com.ojnexus.judge.luogu.open.LuoguOpenQuotaSnapshot
import com.ojnexus.judge.luogu.open.OpenAppCredential
import com.ojnexus.judge.luogu.open.OpenAppCredentialStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAppCredentialReplacementTest {
    @Test
    fun `successful candidate is verified before it is written`() = runBlocking {
        val events = mutableListOf<String>()
        val store = RecordingCredentialStore(OpenAppCredential("old-user", "old-secret"), events)
        val candidate = OpenAppCredential("new-user", "new-secret")

        val result = verifyAndReplaceOpenAppCredential(
            store = store,
            verifier = RecordingVerifier(events, LuoguOpenQuotaSnapshot(emptyList())),
            credential = candidate,
        )

        assertTrue(result is OpenAppCredentialVerification.Verified)
        assertEquals(listOf("verify", "write"), events)
        assertEquals(candidate, store.value)
    }

    @Test
    fun `unauthorized candidate preserves the old credential`() = runBlocking {
        val old = OpenAppCredential("old-user", "old-secret")
        val store = RecordingCredentialStore(old)

        val result = verifyAndReplaceOpenAppCredential(
            store = store,
            verifier = RecordingVerifier(error = LuoguOpenApiError.Unauthorized),
            credential = OpenAppCredential("new-user", "bad-secret"),
        )

        assertTrue(result is OpenAppCredentialVerification.Rejected)
        assertEquals(old, store.value)
        assertEquals(0, store.writeCount)
    }

    @Test
    fun `network failure preserves the old credential`() = runBlocking {
        val old = OpenAppCredential("old-user", "old-secret")
        val store = RecordingCredentialStore(old)

        val result = verifyAndReplaceOpenAppCredential(
            store = store,
            verifier = RecordingVerifier(error = LuoguOpenApiError.Network(java.io.IOException("offline"))),
            credential = OpenAppCredential("new-user", "possibly-valid"),
        )

        assertTrue(result is OpenAppCredentialVerification.Unverified)
        assertEquals(old, store.value)
        assertEquals(0, store.writeCount)
    }

    private class RecordingCredentialStore(
        initial: OpenAppCredential,
        private val events: MutableList<String>? = null,
    ) : OpenAppCredentialStore {
        var value: OpenAppCredential? = initial
        var writeCount = 0

        override suspend fun read(): OpenAppCredential? = value

        override suspend fun write(value: OpenAppCredential) {
            events?.add("write")
            writeCount++
            this.value = value
        }

        override suspend fun clear() {
            value = null
        }
    }

    private class RecordingVerifier(
        private val events: MutableList<String> = mutableListOf(),
        private val quota: LuoguOpenQuotaSnapshot? = null,
        private val error: LuoguOpenApiError? = null,
    ) : LuoguOpenCredentialVerifier {
        override suspend fun verifyCredential(credential: OpenAppCredential): LuoguOpenQuotaSnapshot {
            events += "verify"
            error?.let { throw it }
            return requireNotNull(quota)
        }
    }
}
