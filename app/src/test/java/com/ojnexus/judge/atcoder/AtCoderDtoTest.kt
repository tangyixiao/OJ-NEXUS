package com.ojnexus.judge.atcoder

import com.ojnexus.judge.atcoder.api.dto.AtCoderSubmissionDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AtCoderDtoTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun `submission sample parses exact v3 fields and nullable execution time`() {
        val body = """{
          "execution_time":null,"point":100.0,"result":"AC","problem_id":"abc350_a",
          "user_id":"CaseUser","epoch_second":1714200000,"contest_id":"abc350",
          "id":123456789,"language":"C++ 23 (gcc 12.2)","length":512,"future":true
        }"""

        val dto = json.decodeFromString<AtCoderSubmissionDto>(body)

        assertEquals(123456789L, dto.id)
        assertEquals(1714200000L, dto.epochSecond)
        assertEquals("abc350_a", dto.problemId)
        assertEquals("CaseUser", dto.userId)
        assertNull(dto.executionTime)
    }
}
