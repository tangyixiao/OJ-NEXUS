package com.ojnexus.judge.luogu

import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.DataSourceReliability
import com.ojnexus.judge.JudgeCapability
import com.ojnexus.judge.luogu.api.dto.LuoguUserSearchResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LuoguAdapterTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `public search response parses the current user summary shape`() {
        val response = json.decodeFromString<LuoguUserSearchResponse>(
            """{
              "users": [{
                "uid": 1,
                "name": "kkksc03",
                "avatar": "https://cdn.luogu.com.cn/upload/usericon/1.png",
                "slogan": "洛谷吉祥物 DA✩ZE",
                "badge": "吉祥物",
                "isAdmin": true,
                "isBanned": false,
                "color": "Purple",
                "ccfLevel": 6,
                "xcpcLevel": 0,
                "background": null,
                "isRoot": true
              }]
            }""",
        )

        val user = requireNotNull(response.users.single())
        assertEquals(1L, user.uid)
        assertEquals("kkksc03", user.name)
        assertEquals("Purple", user.color)
    }

    @Test
    fun `adapter exposes only public account binding`() = runBlocking {
        val adapter = FakeLuoguAdapter(LuoguUserSearchResponse(emptyList()))

        assertEquals(JudgeId.LUOGU, adapter.id)
        assertEquals(DataSourceReliability.EXPERIMENTAL, adapter.reliability)
        assertEquals(setOf(JudgeCapability.ACCOUNT_BINDING), adapter.capabilities)
        assertNull(adapter.searchUser("missing"))
    }

    private class FakeLuoguAdapter(
        private val response: LuoguUserSearchResponse,
    ) : LuoguAdapter {
        override suspend fun searchUser(handle: String) = response.users.firstOrNull()
    }
}
