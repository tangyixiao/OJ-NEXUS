package com.ojnexus.judge.luogu

import com.ojnexus.judge.AccountBindingError
import com.ojnexus.judge.AccountVerificationState
import com.ojnexus.judge.DataSourceReliability
import com.ojnexus.judge.luogu.api.dto.LuoguUserSummary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LuoguAccountConnectorTest {
    @Test
    fun `bind trims input and returns the exact public account`() = runBlocking {
        val connector = LuoguAccountConnector(
            adapter = FakeLuoguAdapter(LuoguUserSummary(uid = 7, name = "user_7")),
        )

        val binding = connector.bind("  user_7 ")

        assertEquals("user_7", binding.storedHandle)
        assertEquals("user_7", binding.canonicalHandle)
        assertEquals(AccountVerificationState.VERIFIED, binding.verificationState)
        assertEquals(DataSourceReliability.EXPERIMENTAL, binding.reliability)
    }

    @Test
    fun `bind rejects blank and malformed handles before network access`() = runBlocking {
        val connector = LuoguAccountConnector(FakeLuoguAdapter(null))

        assertThrows(AccountBindingError.InvalidHandle::class.java) { runBlocking { connector.bind(" ") } }
        assertThrows(AccountBindingError.InvalidHandle::class.java) { runBlocking { connector.bind("用户") } }
    }

    @Test
    fun `bind rejects a missing exact account`() = runBlocking {
        val connector = LuoguAccountConnector(FakeLuoguAdapter(null))

        assertThrows(AccountBindingError.NotFound::class.java) {
            runBlocking { connector.bind("missing") }
        }
    }

    @Test
    fun `bind does not accept a fuzzy search result`() = runBlocking {
        val connector = LuoguAccountConnector(
            FakeLuoguAdapter(LuoguUserSummary(uid = 8, name = "other_user")),
        )

        assertThrows(AccountBindingError.NotFound::class.java) {
            runBlocking { connector.bind("user_7") }
        }
    }

    private class FakeLuoguAdapter(
        private val user: LuoguUserSummary?,
    ) : LuoguAdapter {
        override suspend fun searchUser(handle: String): LuoguUserSummary? = user
    }
}
