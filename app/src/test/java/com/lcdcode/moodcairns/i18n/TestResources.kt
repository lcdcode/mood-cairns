package com.lcdcode.moodcairns.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Read access to the app's resource files from JVM unit tests. */
object TestResources {

    /** src/main/res, whether tests run from the module or the project directory. */
    val resDir: File = sequenceOf("src/main/res", "app/src/main/res")
        .map(::File)
        .firstOrNull(File::isDirectory)
        ?: error("src/main/res not found from ${File(".").absolutePath}")

    /** The English text of <string name="[name]">, with XML escapes resolved. */
    fun englishString(name: String): String {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resDir, "values/strings.xml"))
        val strings = doc.getElementsByTagName("string")
        return (0 until strings.length).map { strings.item(it) as Element }
            .firstOrNull { it.getAttribute("name") == name }
            ?.textContent
            ?: error("No <string name=\"$name\"> in values/strings.xml")
    }
}
