package com.ojnexus.feature.settings

import com.ojnexus.judge.luogu.open.LuoguOpenApiError
import com.ojnexus.judge.luogu.open.LuoguOpenQuotaSnapshot
import com.ojnexus.judge.luogu.open.OpenAppCredential
import com.ojnexus.judge.luogu.open.OpenAppCredentialStore
import com.ojnexus.judge.luogu.open.LuoguOpenQuotaReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAppCredentialVerificationTest {
    @Test
    fun `successful verification keeps credential and exposes quota`() = runBlocking {
        val store = RecordingCredentialStore()
        val quota = LuoguOpenQuotaSnapshot(emptyList())

        val result = verifyAndStoreOpenAppCredential(
            store = store,
            quotaReader = FakeQuotaReader(quota),
            credential = OpenAppCredential("user", "secret"),
        )

        assertTrue(result is OpenAppCredentialVerification.Verified)
        assertEquals(quota, (result as OpenAppCredentialVerification.Verified).quota)
        assertEquals(OpenAppCredential("user", "secret"), store.value)
        assertEquals(0, store.clearCount)
    }

    @Test
    fun `authorization rejection clears invalid credential`() = runBlocking {
        val store = RecordingCredentialStore()

        val result = verifyAndStoreOpenAppCredential(
            store = store,
            quotaReader = FakeQuotaReader(error = LuoguOpenApiError.Unauthorized),
            credential = OpenAppCredential("user", "bad-secret"),
        )

        assertTrue(result is OpenAppCredentialVerification.Rejected)
        assertEquals(OpenAppQuotaError.UNAUTHORIZED, (result as OpenAppCredentialVerification.Rejected).error)
        assertEquals(1, store.clearCount)
        assertEquals(null, store.value)
    }

    @Test
    fun `network failure preserves credential for retry`() = runBlocking {
        val store = RecordingCredentialStore()

        val result = verifyAndStoreOpenAppCredential(
            store = store,
            quotaReader = FakeQuotaReader(error = LuoguOpenApiError.Network(java.io.IOException("offline"))),
            credential = OpenAppCredential("user", "possibly-valid"),
        )

        assertTrue(result is OpenAppCredentialVerification.Unverified)
        assertEquals(OpenAppQuotaError.NETWORK, (result as OpenAppCredentialVerification.Unverified).error)
        assertEquals(0, store.clearCount)
        assertEquals(OpenAppCredential("user", "possibly-valid"), store.value)
    }

    private class RecordingCredentialStore : OpenAppCredentialStore {
        var value: OpenAppCredential? = null
        var clearCount = 0

        override suspend fun read(): OpenAppCredential? = value
        override suspend fun write(value: OpenAppCredential) {
            this.value = value
        }
        override suspend fun clear() {
            clearCount++
            value = null
        }
    }

    private class FakeQuotaReader(
        private val quota: LuoguOpenQuotaSnapshot? = null,
        private val error: LuoguOpenApiError? = null,
    ) : LuoguOpenQuotaReader {
        override suspend fun fetchQuota(): LuoguOpenQuotaSnapshot =
            error?.let { throw it } ?: requireNotNull(quota)
    }
}
