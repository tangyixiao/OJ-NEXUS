package com.ojnexus.judge.luogu

import com.ojnexus.judge.luogu.api.dto.LuoguProblemContentDto
import com.ojnexus.judge.luogu.api.dto.LuoguProblemDetailData
import com.ojnexus.judge.luogu.api.dto.LuoguProblemDetailDto
import com.ojnexus.judge.luogu.api.dto.LuoguProblemDetailResponse
import com.ojnexus.judge.luogu.api.dto.LuoguProblemLimitsDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuoguProblemDetailTest {

    @Test
    fun `decodes Luogu nested sample pairs into ordered flat samples`() {
        val response = Json { ignoreUnknownKeys = true }.decodeFromString<LuoguProblemDetailResponse>(
            """
            {
              "data": {
                "problem": {
                  "pid": "B4132",
                  "name": "Sample",
                  "samples": [["1 2", "3"], ["4 5", "9"]]
                }
              }
            }
            """.trimIndent(),
        )

        val detail = LuoguProblemDetailMapper.toDomain(requireNotNull(response.data))

        assertEquals(listOf("1 2", "3", "4 5", "9"), detail.samples)
    }

    @Test
    fun `maps structured problem content and sample limits`() {
        val detail = LuoguProblemDetailMapper.toDomain(
            LuoguProblemDetailData(
                problem = LuoguProblemDetailDto(
                    pid = "P1001",
                    name = "A+B Problem",
                    difficulty = 1,
                    tags = listOf(1, 2),
                    totalSubmit = 12,
                    totalAccepted = 9,
                    contenu = LuoguProblemContentDto(
                        description = "输入两个整数。",
                        formatI = "一行输入。",
                        formatO = "输出答案。",
                        hint = "```cpp\nint main() {}\n```",
                    ),
                    samples = listOf("1 2\n", "3\n"),
                    limits = LuoguProblemLimitsDto(
                        time = listOf(1000),
                        memory = listOf(524288),
                    ),
                ),
            ),
        )

        assertEquals("P1001", detail.pid)
        assertEquals("A+B Problem", detail.title)
        assertEquals("输入两个整数。", detail.description)
        assertEquals(listOf("1 2\n", "3\n"), detail.samples)
        assertEquals(1000, detail.timeLimitMs)
        assertEquals(512, detail.memoryLimitMb)
        assertEquals(listOf(1, 2), detail.tags)
    }

    @Test
    fun `markdown parser exposes safe native blocks without html or urls`() {
        val blocks = LuoguMarkdownParser.parse(
            "# 题面\n\n[官方说明](https://example.com)\n\n- one\n- two\n\n```cpp\nint main() {}\n```\n\n---",
        )

        assertEquals(6, blocks.size)
        assertEquals(LuoguMarkdownBlock.Heading(1, "题面"), blocks[0])
        assertEquals(LuoguMarkdownBlock.Paragraph("官方说明"), blocks[1])
        assertEquals(LuoguMarkdownBlock.Bullet("one"), blocks[2])
        assertEquals(LuoguMarkdownBlock.Bullet("two"), blocks[3])
        assertEquals(LuoguMarkdownBlock.Code("cpp", "int main() {}"), blocks[4])
        assertEquals(LuoguMarkdownBlock.Divider, blocks[5])
        assertTrue(blocks.none { it is LuoguMarkdownBlock.Paragraph && it.text.contains("https://") })
    }
}
