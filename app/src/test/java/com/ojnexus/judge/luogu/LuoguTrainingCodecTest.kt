package com.ojnexus.judge.luogu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LuoguTrainingCodecTest {

    /** Fixture mirrors the real probe of /training/list (2026-10-18), escaped quotes kept. */
    private val listHtml = """
        <!DOCTYPE html><html><head><title>训练</title></head><body>
        <script id="lentille-context" type="application/json">{"instance":"main","template":"training.list","status":200,"locale":"zh-CN","data":{"trainings":{"perPage":18,"count":18,"result":[{"id":100,"name":"【入门1】顺序结构","type":1,"provider":{"uid":3,"name":"洛谷","color":"Purple"},"createTime":1583016195,"deadline":null,"problemCount":15,"markCount":38862}]}}}</script>
        </body></html>
    """.trimIndent()

    /** Fixture mirrors the real probe of /training/100, problems array included. */
    private val detailHtml = """
        <!DOCTYPE html><html><body>
        <script id="lentille-context" type="application/json">{"instance":"main","template":"training.show","status":200,"data":{"training":{"id":100,"name":"【入门1】顺序结构","type":1,"provider":{"uid":3,"name":"洛谷"},"createTime":1583016195,"deadline":null,"problemCount":15,"markCount":38862,"description":"千里之行，始于足下。","problems":[{"pid":"B2002","type":"B","name":"Hello,World!","difficulty":1,"fullScore":100,"tags":[353],"totalSubmit":1218376,"totalAccepted":604801,"flag":5,"provider":{"uid":1308,"name":"信息学奥赛一本通"}}]}}}</script>
        </body></html>
    """.trimIndent()

    @Test
    fun `extract pulls the json between marker and script close`() {
        val json = LuoguTrainingCodec.extract(listHtml)
        assertEquals(true, json?.startsWith("{\"instance\":\"main\""))
        assertEquals(true, json?.endsWith("}"))
    }

    @Test
    fun `extract returns null on missing marker`() {
        assertNull(LuoguTrainingCodec.extract("<html><body>no payload</body></html>"))
    }

    @Test
    fun `list payload decodes and template must match`() {
        val payload = LuoguTrainingCodec.parseList(listHtml)
        val trainings = payload?.data?.trainings
        assertEquals(18, trainings?.count)
        assertEquals(1, trainings?.result?.size)
        val first = trainings?.result?.first()
        assertEquals(100L, first?.id)
        assertEquals("【入门1】顺序结构", first?.name)
        assertEquals(15, first?.problemCount)
        assertEquals(38862, first?.markCount)
        assertEquals("洛谷", first?.provider?.name)
    }

    @Test
    fun `wrong template is rejected`() {
        val wrong = listHtml.replace("training.list", "training.show")
        assertNull(LuoguTrainingCodec.parseList(wrong))
    }

    @Test
    fun `detail payload decodes with problems ladder`() {
        val payload = LuoguTrainingCodec.parseDetail(detailHtml)
        val training = payload?.data?.training
        assertEquals(100L, training?.id)
        assertEquals("千里之行，始于足下。", training?.description)
        assertEquals(1, training?.problems?.size)
        val problem = training?.problems?.first()
        assertEquals("B2002", problem?.pid)
        assertEquals("Hello,World!", problem?.name)
        assertEquals(1, problem?.difficulty)
        assertEquals(1218376L, problem?.totalSubmit)
        assertEquals(604801L, problem?.totalAccepted)
    }

    @Test
    fun `detail template mismatch is rejected`() {
        val wrong = detailHtml.replace("training.show", "training.list")
        assertNull(LuoguTrainingCodec.parseDetail(wrong))
    }

    @Test
    fun `garbage json yields null not a crash`() {
        assertNull(LuoguTrainingCodec.parseList("prefix<script id=\"lentille-context\" type=\"application/json\">not json</script>"))
        assertNull(LuoguTrainingCodec.parseDetail("<script id=\"lentille-context\" type=\"application/json\">{broken</script>"))
    }
}
