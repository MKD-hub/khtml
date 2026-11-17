package org.khtml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ASTBuilderTest {
    @Test
    fun `empty token list emits empty block list`() {
        val builder = AstBuilder()
        val got = builder.build(emptyList())
        assertTrue(got.isEmpty())
    }

    @Test
    fun `orphaned plain text is registered as a paragraph`() {
        val text = listOf<HtmlToken>(
            HtmlToken.Text(content = "Hello, World!", position = SourcePosition(1, 1))
        )
        val builder = AstBuilder()
        val got = builder.build(text)

        assertEquals(1, got.size)

        val paragraph = got[0]

        assertIs<ArticleBlock.Paragraph>(paragraph)
        assertEquals(listOf(InlineSpan.Text("Hello, World!")), paragraph.spans)
    }

    @Test
    fun `build simple paragraph`() {
        val paragraphs = listOf<HtmlToken>(
            HtmlToken.TagOpen(name = "p", position = SourcePosition(1, 2)),
            HtmlToken.Text(content = " Hello ", position = SourcePosition(1, 5)),
            HtmlToken.TagClose(name = "p", position = SourcePosition(1, 8))
        )

        val builder = AstBuilder()
        val got = builder.build(paragraphs)

        val paragraph = got[0]

        assertEquals(1, got.size)
        assertIs<ArticleBlock.Paragraph>(paragraph)
        assertEquals(listOf(InlineSpan.Text(" Hello ")), paragraph.spans)
    }
}
