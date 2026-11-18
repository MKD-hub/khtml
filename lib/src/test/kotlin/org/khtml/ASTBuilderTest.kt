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
        assertEquals(
            listOf(
                InlineSpan(
                    text = "Hello, World!"
                )
            ), paragraph.spans
        )
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
        assertEquals(listOf(InlineSpan(text = " Hello ")), paragraph.spans)
    }

    @Test
    fun `discard new line or white space between two paragraphs`() {
        val paragraphs = listOf<HtmlToken>(
            HtmlToken.TagOpen(name = "p", position = SourcePosition(1, 2)),
            HtmlToken.Text(content = " Hello ", position = SourcePosition(1, 5)),
            HtmlToken.TagClose(name = "p", position = SourcePosition(1, 8)),
            HtmlToken.Text(content = "\n", position = SourcePosition(1, 10)),
            HtmlToken.TagOpen(name = "p", position = SourcePosition(1, 12)),
            HtmlToken.Text(content = " World ", position = SourcePosition(1, 14)),
            HtmlToken.TagClose(name = "p", position = SourcePosition(1, 17)),
        )
        val paragraphs2 = listOf<HtmlToken>(
            HtmlToken.TagOpen(name = "p", position = SourcePosition(1, 2)),
            HtmlToken.Text(content = " Hello ", position = SourcePosition(1, 5)),
            HtmlToken.TagClose(name = "p", position = SourcePosition(1, 8)),
            HtmlToken.Text(content = "  \t", position = SourcePosition(1, 10)),
            HtmlToken.TagOpen(name = "p", position = SourcePosition(1, 12)),
            HtmlToken.Text(content = " World ", position = SourcePosition(1, 14)),
            HtmlToken.TagClose(name = "p", position = SourcePosition(1, 17)),
        )

        val builder = AstBuilder()
        val got = builder.build(paragraphs)
        val got2 = builder.build(paragraphs2)

        val paragraph = got[1]
        val paragraph2 = got2[1]

        assertIs<ArticleBlock.Paragraph>(paragraph)
        assertIs<ArticleBlock.Paragraph>(paragraph2)
        assertEquals(listOf(InlineSpan(text = " World ")), paragraph.spans)
        assertEquals(listOf(InlineSpan(text = " World ")), paragraph2.spans)
    }

    @Test
    fun `happy - return paragraph with proper inline tags`() {
        val paragraph = listOf<HtmlToken>(
            HtmlToken.TagOpen(name = "p", position = SourcePosition(1, 2)),
            HtmlToken.Text(content = " Hello ", position = SourcePosition(1, 5)),
            HtmlToken.TagOpen(name = "b", position = SourcePosition(1, 12)),
            HtmlToken.Text(content = " World ", position = SourcePosition(1, 14)),
            HtmlToken.TagClose(name = "b", position = SourcePosition(1, 22)),
            HtmlToken.TagClose(name = "p", position = SourcePosition(1, 24)),
        )

        val builder = AstBuilder()
        val got = builder.build(paragraph)
        val p = got[0]

        assertIs<ArticleBlock.Paragraph>(p)
        assertEquals(
            listOf(
                InlineSpan(text = " Hello "),
                InlineSpan(text = " World ", styles = setOf(InlineStyle.BOLD)),
            ), p.spans
        )

    }
}
