package com.ojnexus.core.resources

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeTest {

    @Test
    fun `app theme is an AppCompat theme for locale support`() {
        val file = listOf(
            File("src/main/res/values/themes.xml"),
            File("app/src/main/res/values/themes.xml"),
        ).first { it.exists() }
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val styles = document.getElementsByTagName("style")
        val appTheme = (0 until styles.length)
            .map { styles.item(it) }
            .first { it.attributes.getNamedItem("name")?.nodeValue == "Theme.OJNexus" }
        val parent = appTheme.attributes.getNamedItem("parent")?.nodeValue.orEmpty()

        assertTrue("Theme.OJNexus must inherit an AppCompat theme", parent.startsWith("Theme.AppCompat"))
    }
}
