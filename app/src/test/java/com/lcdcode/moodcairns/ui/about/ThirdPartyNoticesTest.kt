package com.lcdcode.moodcairns.ui.about

import com.lcdcode.moodcairns.markdown.MarkdownParser
import com.lcdcode.moodcairns.markdown.MdBlock
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards THIRD_PARTY_NOTICES.md, which the build packages into the APK for the licenses page.
 * Which dependencies must be listed is enforced at build time by verifyThirdPartyNotices;
 * this test makes sure the notices that the licenses themselves require are never dropped.
 */
class ThirdPartyNoticesTest {

    private val notices: String = sequenceOf("../THIRD_PARTY_NOTICES.md", "THIRD_PARTY_NOTICES.md")
        .map(::File)
        .firstOrNull(File::isFile)
        ?.readText()
        ?: error("THIRD_PARTY_NOTICES.md not found from ${File(".").absolutePath}")

    private val flattened: String =
        notices.replace("\\.", ".").split(Regex("\\s+")).joinToString(" ")

    @Test
    fun `required notices are present`() {
        val missing = REQUIRED_NOTICES.filterNot { flattened.contains(it) }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `license section numbers stay literal text`() {
        val orderedLists = MarkdownParser.parse(notices).flatMap(::allBlocks)
            .filter { it is MdBlock.ListBlock && it.ordered }
        assertTrue("Unescaped 'N.' turned license text into a list", orderedLists.isEmpty())
    }

    private fun allBlocks(block: MdBlock): List<MdBlock> = listOf(block) + when (block) {
        is MdBlock.Quote -> block.blocks.flatMap(::allBlocks)
        is MdBlock.ListBlock -> block.items.flatMap { it.blocks }.flatMap(::allBlocks)
        else -> emptyList()
    }

    private companion object {
        val REQUIRED_NOTICES = listOf(
            // Apache License 2.0, section 4(a): a copy of the license.
            "TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION",
            "END OF TERMS AND CONDITIONS",
            // Apache License 2.0, section 4(d): NOTICE file contents.
            "kotlinx.coroutines library. Copyright 2016-2024 JetBrains s.r.o and contributors",
            "kotlinx.serialization library. Copyright 2017-2019 JetBrains s.r.o",
            "Copyright 2017 JetBrains s.r.o and respective authors and developers",
            "Jakarta Dependency Injection is a trademark of the Eclipse Foundation.",
            // BSD licenses: copyright line, conditions and disclaimer.
            "Copyright (c) 2015, Atlassian Pty Ltd",
            "Copyright (c) 2008-2023, ZETETIC LLC",
            "Copyright 2008 Google Inc. All rights reserved.",
            "Redistributions in binary form must reproduce the above copyright notice",
            // OpenSSL attribution.
            "Copyright (c) 1998-2024 The OpenSSL Project",
            "Boost Software License - Version 1.0",
        )
    }
}
