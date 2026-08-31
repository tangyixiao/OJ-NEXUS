package com.ojnexus.judge.luogu

import com.ojnexus.core.model.DifficultySource
import com.ojnexus.core.model.JudgeId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LuoguProblemsetDumpParserTest {
    @Test
    fun `gzip ndjson maps official catalog fields and ignores unknown fields`() {
        val input = gzip(
            """
            {"pid":"P1001","type":"P","difficulty":1,"tags":["模拟","math"],"title":"A+B Problem","description":"details"}
            {"pid":"P1002","type":"B","difficulty":2,"tags":[],"title":"Another Problem"}
            """.trimIndent(),
        )

        val rows = LuoguProblemsetDumpParser.parse(
            input = ByteArrayInputStream(input),
            updatedAt = 42L,
        ).toList()

        assertEquals(2, rows.size)
        assertEquals(JudgeId.LUOGU.id, rows[0].judge)
        assertEquals("P1001", rows[0].externalId)
        assertEquals("A+B Problem", rows[0].name)
        assertEquals(DifficultySource.OFFICIAL.name, rows[0].difficultySource)
        assertEquals("模拟\u001Fmath", rows[0].tags)
        assertEquals(42L, rows[0].updatedAt)
        assertTrue(rows.none { it.name == "details" })
    }

    @Test
    fun `malformed non blank ndjson fails instead of silently truncating catalog`() {
        val error = assertThrows(LuoguApiError.ParseError::class.java) {
            LuoguProblemsetDumpParser.parse(
                input = ByteArrayInputStream(gzip("not-json")),
                updatedAt = 42L,
            ).toList()
        }

        assertNotNull(error.cause)
    }

    @Test
    fun `catalog row without identity fails instead of silently truncating catalog`() {
        assertThrows(LuoguApiError.ParseError::class.java) {
            LuoguProblemsetDumpParser.parse(
                input = ByteArrayInputStream(gzip("{\"title\":\"Missing PID\"}")),
                updatedAt = 42L,
            ).toList()
        }
    }

    @Test
    fun `catalog row with a blank title keeps its pid as an honest display fallback`() {
        val rows = LuoguProblemsetDumpParser.parse(
            input = ByteArrayInputStream(
                gzip("""{"pid":"P3459","title":"","description":"public content"}"""),
            ),
            updatedAt = 42L,
        ).toList()

        assertEquals(1, rows.size)
        assertEquals("P3459", rows.single().externalId)
        assertEquals("P3459", rows.single().name)
    }

    private fun gzip(value: String): ByteArray = ByteArrayOutputStream().also { output ->
        GZIPOutputStream(output).bufferedWriter().use { writer -> writer.write(value) }
    }.toByteArray()
}
