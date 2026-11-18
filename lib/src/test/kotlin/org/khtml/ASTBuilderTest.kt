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

    private data class InlineTestCase(
        val name: String,
        val tokens: List<HtmlToken>,
        val expectedSpans: List<InlineSpan>
    )

    @Test
    fun `table test - return paragraph with various inline tags`() {
        val pos = SourcePosition(1, 1)

        val testCases = listOf(
            // 1. Bold tag
            InlineTestCase(
                name = "bold tag <b>",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.Text(content = " Hello ", position = pos),
                    HtmlToken.TagOpen(name = "b", position = pos),
                    HtmlToken.Text(content = " World ", position = pos),
                    HtmlToken.TagClose(name = "b", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedSpans = listOf(
                    InlineSpan(text = " Hello "),
                    InlineSpan(text = " World ", styles = setOf(InlineStyle.BOLD))
                )
            ),

            // 2. Strong tag (maps to BOLD)
            InlineTestCase(
                name = "strong tag <strong>",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.TagOpen(name = "strong", position = pos),
                    HtmlToken.Text(content = "Important", position = pos),
                    HtmlToken.TagClose(name = "strong", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedSpans = listOf(
                    InlineSpan(text = "Important", styles = setOf(InlineStyle.BOLD))
                )
            ),

            // 3. Italic and emphasis tags (map to ITALIC)
            InlineTestCase(
                name = "italic tag <i> and <em>",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.TagOpen(name = "i", position = pos),
                    HtmlToken.Text(content = "Slanted", position = pos),
                    HtmlToken.TagClose(name = "i", position = pos),
                    HtmlToken.Text(content = " and ", position = pos),
                    HtmlToken.TagOpen(name = "em", position = pos),
                    HtmlToken.Text(content = "Emphasized", position = pos),
                    HtmlToken.TagClose(name = "em", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedSpans = listOf(
                    InlineSpan(text = "Slanted", styles = setOf(InlineStyle.ITALIC)),
                    InlineSpan(text = " and "),
                    InlineSpan(text = "Emphasized", styles = setOf(InlineStyle.ITALIC))
                )
            ),

            // 4. Monospace code tag
            InlineTestCase(
                name = "code tag <code>",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.TagOpen(name = "code", position = pos),
                    HtmlToken.Text(content = "val x = 10", position = pos),
                    HtmlToken.TagClose(name = "code", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedSpans = listOf(
                    InlineSpan(text = "val x = 10", styles = setOf(InlineStyle.CODE))
                )
            ),

            // 5. Highlight mark tag
            InlineTestCase(
                name = "mark tag <mark>",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.TagOpen(name = "mark", position = pos),
                    HtmlToken.Text(content = "Highlighted", position = pos),
                    HtmlToken.TagClose(name = "mark", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedSpans = listOf(
                    InlineSpan(text = "Highlighted", styles = setOf(InlineStyle.HIGHLIGHT))
                )
            ),

            // 6. Nested styles: bold and italic together
            InlineTestCase(
                name = "nested bold and italic <b><i>",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.TagOpen(name = "b", position = pos),
                    HtmlToken.TagOpen(name = "i", position = pos),
                    HtmlToken.Text(content = "Bold and Italic", position = pos),
                    HtmlToken.TagClose(name = "i", position = pos),
                    HtmlToken.TagClose(name = "b", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedSpans = listOf(
                    InlineSpan(
                        text = "Bold and Italic",
                        styles = setOf(InlineStyle.BOLD, InlineStyle.ITALIC)
                    )
                )
            ),

            // 7. Interactive link tag with URL attribute
            InlineTestCase(
                name = "link tag <a href>",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.TagOpen(
                        name = "a",
                        attributes = mapOf("href" to "https://example.com"),
                        position = pos
                    ),
                    HtmlToken.Text(content = "Website", position = pos),
                    HtmlToken.TagClose(name = "a", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedSpans = listOf(
                    InlineSpan(
                        text = "Website",
                        linkUrl = "https://example.com"
                    )
                )
            ),

            // 8. Styled link: bold tag wrapping a link
            InlineTestCase(
                name = "bold link <b><a href>",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.TagOpen(name = "b", position = pos),
                    HtmlToken.TagOpen(
                        name = "a",
                        attributes = mapOf("href" to "https://example.com"),
                        position = pos
                    ),
                    HtmlToken.Text(content = "Bold Link", position = pos),
                    HtmlToken.TagClose(name = "a", position = pos),
                    HtmlToken.TagClose(name = "b", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedSpans = listOf(
                    InlineSpan(
                        text = "Bold Link",
                        styles = setOf(InlineStyle.BOLD),
                        linkUrl = "https://example.com"
                    )
                )
            )
        )

        val builder = AstBuilder()
        for (case in testCases) {
            val blocks = builder.build(case.tokens)
            assertEquals(1, blocks.size, "Failed on '${case.name}': Expected 1 block")
            val paragraph = blocks[0]
            assertIs<ArticleBlock.Paragraph>(paragraph, "Failed on '${case.name}'")
            assertEquals(case.expectedSpans, paragraph.spans, "Failed on '${case.name}'")
        }
    }

    // @Test
    // fun `should skip unopened inline tags`() {
    //     TODO()
    // }
}
