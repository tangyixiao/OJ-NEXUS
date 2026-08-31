package com.ojnexus.core.resources

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizationResourceTest {

    @Test
    fun `simplified Chinese resources mirror default keys and format placeholders`() {
        val english = readStrings("values")
        val chinese = readStrings("values-zh-rCN")

        assertEquals(english.keys, chinese.keys)
        english.forEach { (name, value) ->
            assertEquals(
                "format placeholders differ for $name",
                formatTokens(value),
                formatTokens(chinese.getValue(name)),
            )
        }
    }

    @Test
    fun `remote catalog empty state invites keyword search without requiring an account`() {
        val english = readStrings("values")
        val chinese = readStrings("values-zh-rCN")

        assertEquals("NO CACHED RESULTS — ENTER A KEYWORD", english.getValue("problems_remote_empty"))
        assertEquals("暂无缓存结果——请输入关键词", chinese.getValue("problems_remote_empty"))
    }

    private fun readStrings(directory: String): Map<String, String> {
        val file = listOf(
            File("src/main/res/$directory/strings.xml"),
            File("app/src/main/res/$directory/strings.xml"),
        ).firstOrNull { it.exists() }
            ?: throw AssertionError("resource file not found for $directory")
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(file)
        val values = linkedMapOf<String, String>()
        val nodes = document.getElementsByTagName("string")
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            val name = node.attributes?.getNamedItem("name")?.nodeValue
                ?: throw AssertionError("string resource is missing a name in $file")
            check(name !in values) { "duplicate string resource: $name in $file" }
            values[name] = node.textContent
        }
        return values
    }

    private fun formatTokens(value: String): List<String> =
        FORMAT_TOKEN.findAll(value).map { it.value }.toList()

    private companion object {
        val FORMAT_TOKEN = Regex("%(?:\\d+\\$)?[-+0-9.]*[a-zA-Z]")
    }
}
