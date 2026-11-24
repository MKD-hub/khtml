package org.khtml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
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
    fun `empty or whitespace-only paragraph should be discarded`() {
        val pos = SourcePosition(1, 1)
        val builder = AstBuilder()

        // 1. Completely empty paragraph: <p></p>
        val emptyTokens = listOf(
            HtmlToken.TagOpen(name = "p", position = pos),
            HtmlToken.TagClose(name = "p", position = pos)
        )
        val emptyResult = builder.build(emptyTokens)
        assertTrue(emptyResult.isEmpty(), "Expected empty paragraph to be discarded")

        // 2. Whitespace-only paragraph: <p>   \n\t  </p>
        val whitespaceTokens = listOf(
            HtmlToken.TagOpen(name = "p", position = pos),
            HtmlToken.Text(content = "   \n\t  ", position = pos),
            HtmlToken.TagClose(name = "p", position = pos)
        )
        val whitespaceResult = builder.build(whitespaceTokens)
        assertTrue(whitespaceResult.isEmpty(), "Expected whitespace-only paragraph to be discarded")
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
            ),

            // 9. Generic inline span tags pass through cleanly without splitting
            InlineTestCase(
                name = "generic inline span <span class>",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.Text(content = "Text with ", position = pos),
                    HtmlToken.TagOpen(
                        name = "span",
                        attributes = mapOf("class" to "highlight"),
                        position = pos
                    ),
                    HtmlToken.Text(content = "span content", position = pos),
                    HtmlToken.TagClose(name = "span", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedSpans = listOf(
                    InlineSpan(text = "Text with "),
                    InlineSpan(text = "span content")
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

    @Test
    fun `should skip unopened inline tags`() {
        val pos = SourcePosition(1, 1)

        val testCases = listOf(
            // 1. Single stray closing tag after text
            InlineTestCase(
                name = "stray </b> after unstyled text",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.Text(content = "Plain Text", position = pos),
                    HtmlToken.TagClose(name = "b", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedSpans = listOf(
                    InlineSpan(text = "Plain Text")
                )
            ),

            // 2. Stray closing tag before any text
            InlineTestCase(
                name = "stray </i> at paragraph start",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.TagClose(name = "i", position = pos),
                    HtmlToken.Text(content = "Leading text", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedSpans = listOf(
                    InlineSpan(text = "Leading text")
                )
            ),

            // 3. Stray closing </a> tag when no link is active
            InlineTestCase(
                name = "stray </a> without open <a>",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.Text(content = "No link ", position = pos),
                    HtmlToken.TagClose(name = "a", position = pos),
                    HtmlToken.Text(content = "here", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedSpans = listOf(
                    InlineSpan(text = "No link "),
                    InlineSpan(text = "here")
                )
            ),

            // 4. Critical: A stray tag must NOT remove an unrelated active style!
            InlineTestCase(
                name = "stray </i> does not corrupt active bold style",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.TagOpen(name = "b", position = pos),
                    HtmlToken.Text(content = "Still ", position = pos),
                    HtmlToken.TagClose(name = "i", position = pos), // stray </i>
                    HtmlToken.Text(content = "Bold", position = pos),
                    HtmlToken.TagClose(name = "b", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedSpans = listOf(
                    InlineSpan(text = "Still ", styles = setOf(InlineStyle.BOLD)),
                    InlineSpan(text = "Bold", styles = setOf(InlineStyle.BOLD))
                )
            ),

            // 5. Multiple consecutive stray tags
            InlineTestCase(
                name = "cascade of unopened closing tags",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.Text(content = "Safe", position = pos),
                    HtmlToken.TagClose(name = "em", position = pos),
                    HtmlToken.TagClose(name = "strong", position = pos),
                    HtmlToken.TagClose(name = "code", position = pos),
                    HtmlToken.TagClose(name = "mark", position = pos),
                    HtmlToken.Text(content = " text", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedSpans = listOf(
                    InlineSpan(text = "Safe"),
                    InlineSpan(text = " text")
                )
            ),

            // 6. Stray tag between two valid styled spans
            InlineTestCase(
                name = "stray tag between two bold spans",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.TagOpen(name = "b", position = pos),
                    HtmlToken.Text(content = "First", position = pos),
                    HtmlToken.TagClose(name = "b", position = pos),
                    HtmlToken.TagClose(name = "code", position = pos), // stray </code>
                    HtmlToken.TagOpen(name = "b", position = pos),
                    HtmlToken.Text(content = "Second", position = pos),
                    HtmlToken.TagClose(name = "b", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedSpans = listOf(
                    InlineSpan(text = "First", styles = setOf(InlineStyle.BOLD)),
                    InlineSpan(text = "Second", styles = setOf(InlineStyle.BOLD))
                )
            )
        )

        val builder = AstBuilder()
        for ((name, tokens, expectedSpans) in testCases) {
            val blocks = builder.build(tokens)
            assertEquals(1, blocks.size, "Failed on '${name}': Expected 1 block")
            val paragraph = blocks[0]
            assertIs<ArticleBlock.Paragraph>(paragraph, "Failed on '${name}'")
            assertEquals(expectedSpans, paragraph.spans, "Failed on '${name}'")
        }
    }

    @Test
    fun `orphaned inline tags should be turned into paragraphs`() {
        val pos = SourcePosition(1, 1)
        val testCases = listOf(
            // 1. Bold tag <b>
            InlineTestCase(
                name = "bold tag <b>",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "b", position = pos),
                    HtmlToken.Text(content = " World ", position = pos),
                    HtmlToken.TagClose(name = "b", position = pos),
                ),
                expectedSpans = listOf(
                    InlineSpan(text = " World ", styles = setOf(InlineStyle.BOLD))
                )
            ),

            // 2. Italic tag <i>
            InlineTestCase(
                name = "italic tag <i>",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "i", position = pos),
                    HtmlToken.Text(content = " World ", position = pos),
                    HtmlToken.TagClose(name = "i", position = pos),
                ),
                expectedSpans = listOf(
                    InlineSpan(text = " World ", styles = setOf(InlineStyle.ITALIC))
                )
            ),

            // 3. Strong tag <strong>
            InlineTestCase(
                name = "strong tag <strong>",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "strong", position = pos),
                    HtmlToken.Text(content = "Important", position = pos),
                    HtmlToken.TagClose(name = "strong", position = pos),
                ),
                expectedSpans = listOf(
                    InlineSpan(text = "Important", styles = setOf(InlineStyle.BOLD))
                )
            ),

            // 4. Emphasis tag <em>
            InlineTestCase(
                name = "emphasis tag <em>",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "em", position = pos),
                    HtmlToken.Text(content = "Notice", position = pos),
                    HtmlToken.TagClose(name = "em", position = pos),
                ),
                expectedSpans = listOf(
                    InlineSpan(text = "Notice", styles = setOf(InlineStyle.ITALIC))
                )
            ),

            // 5. Code tag <code>
            InlineTestCase(
                name = "code tag <code>",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "code", position = pos),
                    HtmlToken.Text(content = "git status", position = pos),
                    HtmlToken.TagClose(name = "code", position = pos),
                ),
                expectedSpans = listOf(
                    InlineSpan(text = "git status", styles = setOf(InlineStyle.CODE))
                )
            ),

            // 6. Highlight tag <mark>
            InlineTestCase(
                name = "mark tag <mark>",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "mark", position = pos),
                    HtmlToken.Text(content = "Highlighted", position = pos),
                    HtmlToken.TagClose(name = "mark", position = pos),
                ),
                expectedSpans = listOf(
                    InlineSpan(text = "Highlighted", styles = setOf(InlineStyle.HIGHLIGHT))
                )
            ),

            // 7. Link tag <a href>
            InlineTestCase(
                name = "link tag <a href>",
                tokens = listOf(
                    HtmlToken.TagOpen(
                        name = "a",
                        attributes = mapOf("href" to "https://example.com"),
                        position = pos
                    ),
                    HtmlToken.Text(content = "Documentation", position = pos),
                    HtmlToken.TagClose(name = "a", position = pos),
                ),
                expectedSpans = listOf(
                    InlineSpan(text = "Documentation", linkUrl = "https://example.com")
                )
            ),

            // 8. Nested orphaned tags <b><i>
            InlineTestCase(
                name = "nested orphaned <b><i>",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "b", position = pos),
                    HtmlToken.TagOpen(name = "i", position = pos),
                    HtmlToken.Text(content = "Bold Italic", position = pos),
                    HtmlToken.TagClose(name = "i", position = pos),
                    HtmlToken.TagClose(name = "b", position = pos),
                ),
                expectedSpans = listOf(
                    InlineSpan(
                        text = "Bold Italic",
                        styles = setOf(InlineStyle.BOLD, InlineStyle.ITALIC)
                    )
                )
            ),

            // 9. Orphaned inline tag mixed with plain text
            InlineTestCase(
                name = "orphaned <b> followed by plain text",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "b", position = pos),
                    HtmlToken.Text(content = "Prefix", position = pos),
                    HtmlToken.TagClose(name = "b", position = pos),
                    HtmlToken.Text(content = " Suffix", position = pos),
                ),
                expectedSpans = listOf(
                    InlineSpan(text = "Prefix", styles = setOf(InlineStyle.BOLD)),
                    InlineSpan(text = " Suffix")
                )
            ),
        )

        val builder = AstBuilder()
        for ((name, tokens, expectedSpans) in testCases) {
            val blocks = builder.build(tokens)
            assertEquals(1, blocks.size, "Failed on '${name}': Expected 1 block")
            val paragraph = blocks[0]
            assertIs<ArticleBlock.Paragraph>(paragraph, "Failed on '${name}'")
            assertEquals(expectedSpans, paragraph.spans, "Failed on '${name}'")
        }
    }

    @Test
    fun `non-inline tag inside paragraph should close the paragraph`() {
        val pos = SourcePosition(1, 1)

        // Case 1: <p> hello <h2> -> paragraph[hello]
        val tokens1 = listOf(
            HtmlToken.TagOpen(name = "p", position = pos),
            HtmlToken.Text(content = "hello", position = pos),
            HtmlToken.TagOpen(name = "h2", position = pos),
        )

        val builder = AstBuilder()
        val got1 = builder.build(tokens1)

        assertEquals(1, got1.size, "Expected 1 paragraph block when <h2> interrupts <p>")
        val p1 = got1[0]
        assertIs<ArticleBlock.Paragraph>(p1)
        assertEquals(listOf(InlineSpan(text = "hello")), p1.spans)

        // Case 2: <p> First <p> Second </p> -> auto-close first paragraph
        val tokens2 = listOf(
            HtmlToken.TagOpen(name = "p", position = pos),
            HtmlToken.Text(content = "First", position = pos),
            HtmlToken.TagOpen(name = "p", position = pos),
            HtmlToken.Text(content = "Second", position = pos),
            HtmlToken.TagClose(name = "p", position = pos)
        )

        val got2 = builder.build(tokens2)
        assertEquals(2, got2.size, "Expected 2 paragraph blocks when <p> interrupts <p>")
        val first = got2[0]
        val second = got2[1]
        assertIs<ArticleBlock.Paragraph>(first)
        assertIs<ArticleBlock.Paragraph>(second)
        assertEquals(listOf(InlineSpan(text = "First")), first.spans)
        assertEquals(listOf(InlineSpan(text = "Second")), second.spans)
    }

    private data class HeadingTestCase(
        val name: String,
        val tokens: List<HtmlToken>,
        val expectedLevel: Int,
        val expectedSpans: List<InlineSpan>,
        val expectedId: String? = null
    )

    @Test
    fun `happy - parse headings with levels correctly`() {
        val pos = SourcePosition(1, 1)

        val testCases = listOf(
            // 1. Level 1 Heading
            HeadingTestCase(
                name = "h1 heading",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "h1", position = pos),
                    HtmlToken.Text(content = "Title", position = pos),
                    HtmlToken.TagClose(name = "h1", position = pos)
                ),
                expectedLevel = 1,
                expectedSpans = listOf(InlineSpan(text = "Title"))
            ),

            // 2. Level 2 Heading
            HeadingTestCase(
                name = "h2 heading",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "h2", position = pos),
                    HtmlToken.Text(content = "Subtitle", position = pos),
                    HtmlToken.TagClose(name = "h2", position = pos)
                ),
                expectedLevel = 2,
                expectedSpans = listOf(InlineSpan(text = "Subtitle"))
            ),

            // 3. Level 3 Heading
            HeadingTestCase(
                name = "h3 heading",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "h3", position = pos),
                    HtmlToken.Text(content = "Section", position = pos),
                    HtmlToken.TagClose(name = "h3", position = pos)
                ),
                expectedLevel = 3,
                expectedSpans = listOf(InlineSpan(text = "Section"))
            ),

            // 4. Level 4 Heading
            HeadingTestCase(
                name = "h4 heading",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "h4", position = pos),
                    HtmlToken.Text(content = "Sub-section", position = pos),
                    HtmlToken.TagClose(name = "h4", position = pos)
                ),
                expectedLevel = 4,
                expectedSpans = listOf(InlineSpan(text = "Sub-section"))
            ),

            // 5. Level 5 Heading
            HeadingTestCase(
                name = "h5 heading",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "h5", position = pos),
                    HtmlToken.Text(content = "Minor Heading", position = pos),
                    HtmlToken.TagClose(name = "h5", position = pos)
                ),
                expectedLevel = 5,
                expectedSpans = listOf(InlineSpan(text = "Minor Heading"))
            ),

            // 6. Level 6 Heading
            HeadingTestCase(
                name = "h6 heading",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "h6", position = pos),
                    HtmlToken.Text(content = "Lowest Heading", position = pos),
                    HtmlToken.TagClose(name = "h6", position = pos)
                ),
                expectedLevel = 6,
                expectedSpans = listOf(InlineSpan(text = "Lowest Heading"))
            ),

            // 7. Heading with formatted inline spans (code and bold)
            HeadingTestCase(
                name = "h2 with code and bold spans",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "h2", position = pos),
                    HtmlToken.Text(content = "The ", position = pos),
                    HtmlToken.TagOpen(name = "code", position = pos),
                    HtmlToken.Text(content = "Result", position = pos),
                    HtmlToken.TagClose(name = "code", position = pos),
                    HtmlToken.Text(content = " Type in ", position = pos),
                    HtmlToken.TagOpen(name = "b", position = pos),
                    HtmlToken.Text(content = "Kotlin", position = pos),
                    HtmlToken.TagClose(name = "b", position = pos),
                    HtmlToken.TagClose(name = "h2", position = pos)
                ),
                expectedLevel = 2,
                expectedSpans = listOf(
                    InlineSpan(text = "The "),
                    InlineSpan(text = "Result", styles = setOf(InlineStyle.CODE)),
                    InlineSpan(text = " Type in "),
                    InlineSpan(text = "Kotlin", styles = setOf(InlineStyle.BOLD))
                )
            ),

            // 8. Heading containing a link
            HeadingTestCase(
                name = "h3 with link",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "h3", position = pos),
                    HtmlToken.TagOpen(
                        name = "a",
                        attributes = mapOf("href" to "https://example.com/docs"),
                        position = pos
                    ),
                    HtmlToken.Text(content = "Documentation", position = pos),
                    HtmlToken.TagClose(name = "a", position = pos),
                    HtmlToken.TagClose(name = "h3", position = pos)
                ),
                expectedLevel = 3,
                expectedSpans = listOf(
                    InlineSpan(text = "Documentation", linkUrl = "https://example.com/docs")
                )
            )
        )

        val builder = AstBuilder()
        for ((name, tokens, expectedLevel, expectedSpans, expectedId) in testCases) {
            val blocks = builder.build(tokens)
            assertEquals(1, blocks.size, "Failed on '$name': Expected 1 block")
            val heading = blocks[0]
            assertIs<ArticleBlock.Heading>(heading, "Failed on '$name': Expected Heading block")
            assertEquals(expectedLevel, heading.level, "Failed on '$name': Level mismatch")
            assertEquals(expectedSpans, heading.spans, "Failed on '$name': Spans mismatch")
            assertEquals(expectedId, heading.id, "Failed on '$name': Id mismatch")
        }
    }

    private data class HeadingSadTestCase(
        val name: String,
        val tokens: List<HtmlToken>,
        val expectedBlocks: List<ArticleBlock>
    )

    @Test
    fun `sad path - headings edge cases and recovery`() {
        val pos = SourcePosition(1, 1)

        val testCases = listOf(
            // 1. Invalid heading level <h7> falls back to paragraph
            HeadingSadTestCase(
                name = "invalid heading <h7> becomes paragraph",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "h7", position = pos),
                    HtmlToken.Text(content = "Not a real heading", position = pos),
                    HtmlToken.TagClose(name = "h7", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.Paragraph(spans = listOf(InlineSpan(text = "Not a real heading")))
                )
            ),

            // 2. Invalid heading level <h0> falls back to paragraph
            HeadingSadTestCase(
                name = "invalid heading <h0> becomes paragraph",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "h0", position = pos),
                    HtmlToken.Text(content = "Zero level", position = pos),
                    HtmlToken.TagClose(name = "h0", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.Paragraph(spans = listOf(InlineSpan(text = "Zero level")))
                )
            ),

            // 3. Unclosed heading at EOF flushes safely
            HeadingSadTestCase(
                name = "unclosed heading at EOF flushes",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "h1", position = pos),
                    HtmlToken.Text(content = "Never Closed Title", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.Heading(
                        level = 1,
                        spans = listOf(InlineSpan(text = "Never Closed Title"))
                    )
                )
            ),

            // 4. Stray closing </h1> without opening tag
            HeadingSadTestCase(
                name = "stray closing </h1> without opening tag",
                tokens = listOf(
                    HtmlToken.Text(content = "Orphaned text", position = pos),
                    HtmlToken.TagClose(name = "h1", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.Paragraph(spans = listOf(InlineSpan(text = "Orphaned text")))
                )
            ),

            // 5. Mismatched closing tag <h2> closed by </h3>
            HeadingSadTestCase(
                name = "mismatched closing tag closes active heading",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "h2", position = pos),
                    HtmlToken.Text(content = "Mismatched", position = pos),
                    HtmlToken.TagClose(name = "h3", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.Heading(
                        level = 2,
                        spans = listOf(InlineSpan(text = "Mismatched"))
                    )
                )
            ),

            // 6. Unclosed heading interrupted by a paragraph
            HeadingSadTestCase(
                name = "unclosed heading interrupted by paragraph",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "h2", position = pos),
                    HtmlToken.Text(content = "Heading Title", position = pos),
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.Text(content = "Paragraph Body", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.Heading(
                        level = 2,
                        spans = listOf(InlineSpan(text = "Heading Title"))
                    ),
                    ArticleBlock.Paragraph(
                        spans = listOf(InlineSpan(text = "Paragraph Body"))
                    )
                )
            ),

            // 7. Unclosed heading interrupted by another heading
            HeadingSadTestCase(
                name = "unclosed heading interrupted by another heading",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "h1", position = pos),
                    HtmlToken.Text(content = "First Heading", position = pos),
                    HtmlToken.TagOpen(name = "h2", position = pos),
                    HtmlToken.Text(content = "Second Heading", position = pos),
                    HtmlToken.TagClose(name = "h2", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.Heading(
                        level = 1,
                        spans = listOf(InlineSpan(text = "First Heading"))
                    ),
                    ArticleBlock.Heading(
                        level = 2,
                        spans = listOf(InlineSpan(text = "Second Heading"))
                    )
                )
            ),

            // 8. Empty heading tag is discarded
            HeadingSadTestCase(
                name = "empty heading is discarded",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "h1", position = pos),
                    HtmlToken.TagClose(name = "h1", position = pos)
                ),
                expectedBlocks = emptyList()
            ),

            // 9. Stray unopened heading tag inside paragraph does not corrupt active bold style
            HeadingSadTestCase(
                name = "stray </h3> inside paragraph does not corrupt bold style",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.TagOpen(name = "b", position = pos),
                    HtmlToken.Text(content = "Bold", position = pos),
                    HtmlToken.TagClose(name = "h3", position = pos),
                    HtmlToken.Text(content = " Text", position = pos),
                    HtmlToken.TagClose(name = "b", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.Paragraph(
                        spans = listOf(
                            InlineSpan(text = "Bold", styles = setOf(InlineStyle.BOLD)),
                            InlineSpan(text = " Text", styles = setOf(InlineStyle.BOLD))
                        )
                    )
                )
            )
        )

        val builder = AstBuilder()
        for ((name, tokens, expectedBlocks) in testCases) {
            val blocks = builder.build(tokens)
            assertEquals(expectedBlocks.size, blocks.size, "Failed on '$name': Block count mismatch")
            assertEquals(expectedBlocks, blocks, "Failed on '$name': Block content mismatch")
        }
    }

    private data class ListTestCase(
        val name: String,
        val tokens: List<HtmlToken>,
        val expectedBlocks: List<ArticleBlock>
    )

    @Test
    fun `happy - list table tests`() {
        val pos = SourcePosition(1, 1)

        val testCases = listOf(
            // 1. Basic ordered list with explicit closing tags
            ListTestCase(
                name = "basic ordered list with two items and explicit closing tags",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "ol", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Bread", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Coffee", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagClose(name = "ol", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.HtmlList(
                        isOrdered = true,
                        items = listOf(
                            ArticleBlock.ListItem(
                                spans = listOf(InlineSpan(text = "Bread")),
                                index = 1
                            ),
                            ArticleBlock.ListItem(
                                spans = listOf(InlineSpan(text = "Coffee")),
                                index = 2
                            )
                        )
                    )
                )
            ),

            // 2. Auto-closing consecutive <li> items without </li> tags
            ListTestCase(
                name = "auto-closing consecutive li items without closing tag",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "ol", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Bread", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Coffee", position = pos),
                    HtmlToken.TagClose(name = "ol", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.HtmlList(
                        isOrdered = true,
                        items = listOf(
                            ArticleBlock.ListItem(
                                spans = listOf(InlineSpan(text = "Bread")),
                                index = 1
                            ),
                            ArticleBlock.ListItem(
                                spans = listOf(InlineSpan(text = "Coffee")),
                                index = 2
                            )
                        )
                    )
                )
            ),

            // 3. Basic unordered list (ul) with items
            ListTestCase(
                name = "basic unordered list with two items",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "ul", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Apples", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Oranges", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagClose(name = "ul", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.HtmlList(
                        isOrdered = false,
                        items = listOf(
                            ArticleBlock.ListItem(
                                spans = listOf(InlineSpan(text = "Apples")),
                                index = 1
                            ),
                            ArticleBlock.ListItem(
                                spans = listOf(InlineSpan(text = "Oranges")),
                                index = 2
                            )
                        )
                    )
                )
            ),

            // 4. List items with inline formatted tags
            ListTestCase(
                name = "list items with inline formatted tags",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "ul", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Plain ", position = pos),
                    HtmlToken.TagOpen(name = "b", position = pos),
                    HtmlToken.Text(content = "Bold", position = pos),
                    HtmlToken.TagClose(name = "b", position = pos),
                    HtmlToken.Text(content = " and More", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.TagOpen(name = "i", position = pos),
                    HtmlToken.Text(content = "Italic", position = pos),
                    HtmlToken.TagClose(name = "i", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagClose(name = "ul", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.HtmlList(
                        isOrdered = false,
                        items = listOf(
                            ArticleBlock.ListItem(
                                spans = listOf(
                                    InlineSpan(text = "Plain "),
                                    InlineSpan(
                                        text = "Bold",
                                        styles = setOf(InlineStyle.BOLD)
                                    ),
                                    InlineSpan(text = " and More")
                                ),
                                index = 1
                            ),
                            ArticleBlock.ListItem(
                                spans = listOf(
                                    InlineSpan(
                                        text = "Italic",
                                        styles = setOf(InlineStyle.ITALIC)
                                    )
                                ),
                                index = 2
                            )
                        )
                    )
                )
            ),

            // 5. Nested list inside list item
            ListTestCase(
                name = "nested list inside list item",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "ul", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Parent item", position = pos),
                    HtmlToken.TagOpen(name = "ul", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Sub item 1", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Sub item 2", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagClose(name = "ul", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Second parent item", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagClose(name = "ul", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.HtmlList(
                        isOrdered = false,
                        items = listOf(
                            ArticleBlock.ListItem(
                                spans = listOf(InlineSpan(text = "Parent item")),
                                index = 1,
                                children = listOf(
                                    ArticleBlock.HtmlList(
                                        isOrdered = false,
                                        items = listOf(
                                            ArticleBlock.ListItem(
                                                spans = listOf(InlineSpan(text = "Sub item 1")),
                                                index = 1
                                            ),
                                            ArticleBlock.ListItem(
                                                spans = listOf(InlineSpan(text = "Sub item 2")),
                                                index = 2
                                            )
                                        )
                                    )
                                )
                            ),
                            ArticleBlock.ListItem(
                                spans = listOf(InlineSpan(text = "Second parent item")),
                                index = 2
                            )
                        )
                    )
                )
            ),

            // 6. List items wrapping text in paragraph tags
            ListTestCase(
                name = "list items with paragraph tags",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "ul", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.Text(content = "Paragraph item 1", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.Text(content = "Paragraph item 2", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagClose(name = "ul", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.HtmlList(
                        isOrdered = false,
                        items = listOf(
                            ArticleBlock.ListItem(
                                spans = listOf(InlineSpan(text = "Paragraph item 1")),
                                index = 1
                            ),
                            ArticleBlock.ListItem(
                                spans = listOf(InlineSpan(text = "Paragraph item 2")),
                                index = 2
                            )
                        )
                    )
                )
            )
        )

        val builder = AstBuilder()
        for ((name, tokens, expectedBlocks) in testCases) {
            val blocks = builder.build(tokens)
            assertEquals(expectedBlocks.size, blocks.size, "Failed on '$name': Block count mismatch")
            assertEquals(expectedBlocks, blocks, "Failed on '$name': Block content mismatch")
        }
    }

    @Test
    fun `sad path - list edge cases and recovery`() {
        val pos = SourcePosition(1, 1)

        val testCases = listOf(
            // 1. Stray text directly inside list is preserved as a list item
            ListTestCase(
                name = "stray text directly inside list is preserved as list item",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "ul", position = pos),
                    HtmlToken.Text(content = "Stray text", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Normal item", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagClose(name = "ul", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.HtmlList(
                        isOrdered = false,
                        items = listOf(
                            ArticleBlock.ListItem(
                                spans = listOf(InlineSpan(text = "Stray text")),
                                index = 1
                            ),
                            ArticleBlock.ListItem(
                                spans = listOf(InlineSpan(text = "Normal item")),
                                index = 2
                            )
                        )
                    )
                )
            ),

            // 2. Whitespace indentation between tags is ignored
            ListTestCase(
                name = "whitespace between list tags is ignored",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "ul", position = pos),
                    HtmlToken.Text(content = "\n  \t  ", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Item", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.Text(content = "\n", position = pos),
                    HtmlToken.TagClose(name = "ul", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.HtmlList(
                        isOrdered = false,
                        items = listOf(
                            ArticleBlock.ListItem(
                                spans = listOf(InlineSpan(text = "Item")),
                                index = 1
                            )
                        )
                    )
                )
            ),

            // 3. Orphaned li tag at root level degrades to paragraph
            ListTestCase(
                name = "orphaned li at root level degrades to paragraph",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Orphan item", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.Paragraph(
                        spans = listOf(InlineSpan(text = "Orphan item"))
                    )
                )
            ),

            // 4. Unclosed paragraph inside list item
            ListTestCase(
                name = "unclosed paragraph inside list item",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "ul", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.Text(content = "Unclosed paragraph item", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagClose(name = "ul", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.HtmlList(
                        isOrdered = false,
                        items = listOf(
                            ArticleBlock.ListItem(
                                spans = listOf(InlineSpan(text = "Unclosed paragraph item")),
                                index = 1
                            )
                        )
                    )
                )
            ),

            // 5. Paragraph followed by text in same list item
            ListTestCase(
                name = "paragraph followed by text inside same list item",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "ul", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.Text(content = "Hello", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos),
                    HtmlToken.Text(content = " World", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagClose(name = "ul", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.HtmlList(
                        isOrdered = false,
                        items = listOf(
                            ArticleBlock.ListItem(
                                spans = listOf(
                                    InlineSpan(text = "Hello"),
                                    InlineSpan(text = " World")
                                ),
                                index = 1
                            )
                        )
                    )
                )
            )
        )

        val builder = AstBuilder()
        for ((name, tokens, expectedBlocks) in testCases) {
            val blocks = builder.build(tokens)
            assertEquals(expectedBlocks.size, blocks.size, "Failed on '$name': Block count mismatch")
            assertEquals(expectedBlocks, blocks, "Failed on '$name': Block content mismatch")
        }
    }

    private data class VoidTagTestCase(
        val name: String,
        val tokens: List<HtmlToken>,
        val expectedBlocks: List<ArticleBlock>
    )

    @Test
    fun `void tags - hr and br handling`() {
        val pos = SourcePosition(1, 1)

        val testCases = listOf(
            // 1. Standalone <hr> at root emits Divider
            VoidTagTestCase(
                name = "standalone hr at root emits Divider",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "hr", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.Divider
                )
            ),

            // 2. <hr> between paragraphs emits Divider
            VoidTagTestCase(
                name = "hr between paragraphs emits Divider",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.Text(content = "Before", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos),
                    HtmlToken.TagOpen(name = "hr", position = pos),
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.Text(content = "After", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.Paragraph(spans = listOf(InlineSpan(text = "Before"))),
                    ArticleBlock.Divider,
                    ArticleBlock.Paragraph(spans = listOf(InlineSpan(text = "After")))
                )
            ),

            // 3. <hr> inside a paragraph forces flush, emits Divider, and starts new paragraph
            VoidTagTestCase(
                name = "hr inside paragraph splits paragraph with Divider",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.Text(content = "Hello", position = pos),
                    HtmlToken.TagOpen(name = "hr", position = pos),
                    HtmlToken.Text(content = "World", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.Paragraph(spans = listOf(InlineSpan(text = "Hello"))),
                    ArticleBlock.Divider,
                    ArticleBlock.Paragraph(spans = listOf(InlineSpan(text = "World")))
                )
            ),

            // 4. <hr> inside list is skipped
            VoidTagTestCase(
                name = "hr inside list is skipped",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "ul", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Item 1", position = pos),
                    HtmlToken.TagOpen(name = "hr", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Item 2", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagClose(name = "ul", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.HtmlList(
                        isOrdered = false,
                        items = listOf(
                            ArticleBlock.ListItem(spans = listOf(InlineSpan(text = "Item 1")), index = 1),
                            ArticleBlock.ListItem(spans = listOf(InlineSpan(text = "Item 2")), index = 2)
                        )
                    )
                )
            ),

            // 5. Standalone <br> at root emits LineBreak
            VoidTagTestCase(
                name = "standalone br at root emits LineBreak",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "br", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.LineBreak
                )
            ),

            // 6. <br> between paragraphs emits LineBreak
            VoidTagTestCase(
                name = "br between paragraphs emits LineBreak",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.Text(content = "Before", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos),
                    HtmlToken.TagOpen(name = "br", position = pos),
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.Text(content = "After", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.Paragraph(spans = listOf(InlineSpan(text = "Before"))),
                    ArticleBlock.LineBreak,
                    ArticleBlock.Paragraph(spans = listOf(InlineSpan(text = "After")))
                )
            ),

            // 7. <br> inside list is skipped
            VoidTagTestCase(
                name = "br inside list is skipped",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "ul", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Item 1", position = pos),
                    HtmlToken.TagOpen(name = "br", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Item 2", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagClose(name = "ul", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.HtmlList(
                        isOrdered = false,
                        items = listOf(
                            ArticleBlock.ListItem(spans = listOf(InlineSpan(text = "Item 1")), index = 1),
                            ArticleBlock.ListItem(spans = listOf(InlineSpan(text = "Item 2")), index = 2)
                        )
                    )
                )
            ),

            // 8. <br> inside paragraph emits newline span within same paragraph
            VoidTagTestCase(
                name = "br inside paragraph emits newline span within same paragraph",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.Text(content = "Line 1", position = pos),
                    HtmlToken.TagOpen(name = "br", position = pos),
                    HtmlToken.Text(content = "Line 2", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.Paragraph(
                        spans = listOf(
                            InlineSpan(text = "Line 1"),
                            InlineSpan(text = "\n"),
                            InlineSpan(text = "Line 2")
                        )
                    )
                )
            )
        )

        val builder = AstBuilder()
        for ((name, tokens, expectedBlocks) in testCases) {
            val blocks = builder.build(tokens)
            assertEquals(expectedBlocks.size, blocks.size, "Failed on '$name': Block count mismatch")
            assertEquals(expectedBlocks, blocks, "Failed on '$name': Block content mismatch")
        }
    }

    private data class BlockquoteTestCase(
        val name: String,
        val tokens: List<HtmlToken>,
        val expectedBlocks: List<ArticleBlock>
    )

    @Test
    fun `blockquote table tests`() {
        val pos = SourcePosition(1, 1)

        val testCases = listOf(
            // 1. Simple blockquote
            BlockquoteTestCase(
                name = "simple blockquote with text",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "blockquote", position = pos),
                    HtmlToken.Text(content = "Simplicity is prerequisite for reliability.", position = pos),
                    HtmlToken.TagClose(name = "blockquote", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.Blockquote(
                        spans = listOf(InlineSpan(text = "Simplicity is prerequisite for reliability."))
                    )
                )
            ),

            // 2. Blockquote with inline styles
            BlockquoteTestCase(
                name = "blockquote with inline styles and links",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "blockquote", position = pos),
                    HtmlToken.Text(content = "Chef's Tip: ", position = pos),
                    HtmlToken.TagOpen(name = "b", position = pos),
                    HtmlToken.Text(content = "Do not", position = pos),
                    HtmlToken.TagClose(name = "b", position = pos),
                    HtmlToken.Text(content = " overmix the batter! Visit ", position = pos),
                    HtmlToken.TagOpen(name = "a", attributes = mapOf("href" to "https://recipes.com"), position = pos),
                    HtmlToken.Text(content = "Recipe Site", position = pos),
                    HtmlToken.TagClose(name = "a", position = pos),
                    HtmlToken.TagClose(name = "blockquote", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.Blockquote(
                        spans = listOf(
                            InlineSpan(text = "Chef's Tip: "),
                            InlineSpan(text = "Do not", styles = setOf(InlineStyle.BOLD)),
                            InlineSpan(text = " overmix the batter! Visit "),
                            InlineSpan(text = "Recipe Site", linkUrl = "https://recipes.com")
                        )
                    )
                )
            ),

            // 3. Blockquote wrapping paragraph tags
            BlockquoteTestCase(
                name = "blockquote wrapping paragraph tags",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "blockquote", position = pos),
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.Text(content = "Wrapped quote text", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos),
                    HtmlToken.TagClose(name = "blockquote", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.Blockquote(
                        spans = listOf(InlineSpan(text = "Wrapped quote text"))
                    )
                )
            ),

            // 4. Empty blockquote is discarded
            BlockquoteTestCase(
                name = "empty blockquote is discarded",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "blockquote", position = pos),
                    HtmlToken.TagClose(name = "blockquote", position = pos)
                ),
                expectedBlocks = emptyList()
            ),

            // 5. Whitespace-only blockquote is discarded
            BlockquoteTestCase(
                name = "whitespace-only blockquote is discarded",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "blockquote", position = pos),
                    HtmlToken.Text(content = "   \n\t  ", position = pos),
                    HtmlToken.TagClose(name = "blockquote", position = pos)
                ),
                expectedBlocks = emptyList()
            ),

            // 6. Unclosed blockquote at EOF
            BlockquoteTestCase(
                name = "unclosed blockquote flushes at EOF",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "blockquote", position = pos),
                    HtmlToken.Text(content = "Quote at end of file", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.Blockquote(
                        spans = listOf(InlineSpan(text = "Quote at end of file"))
                    )
                )
            ),

            // 7. Blockquote inside list item children
            BlockquoteTestCase(
                name = "blockquote nested inside list item",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "ul", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Step 1", position = pos),
                    HtmlToken.TagOpen(name = "blockquote", position = pos),
                    HtmlToken.Text(content = "Important note for step 1", position = pos),
                    HtmlToken.TagClose(name = "blockquote", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagClose(name = "ul", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.HtmlList(
                        isOrdered = false,
                        items = listOf(
                            ArticleBlock.ListItem(
                                spans = listOf(InlineSpan(text = "Step 1")),
                                index = 1,
                                children = listOf(
                                    ArticleBlock.Blockquote(
                                        spans = listOf(InlineSpan(text = "Important note for step 1"))
                                    )
                                )
                            )
                        )
                    )
                )
            ),

            // 8. <br> inside blockquote emits newline span within same blockquote
            BlockquoteTestCase(
                name = "br inside blockquote emits newline span within same blockquote",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "blockquote", position = pos),
                    HtmlToken.Text(content = "Verse 1", position = pos),
                    HtmlToken.TagOpen(name = "br", position = pos),
                    HtmlToken.Text(content = "Verse 2", position = pos),
                    HtmlToken.TagClose(name = "blockquote", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.Blockquote(
                        spans = listOf(
                            InlineSpan(text = "Verse 1"),
                            InlineSpan(text = "\n"),
                            InlineSpan(text = "Verse 2")
                        )
                    )
                )
            )
        )

        val builder = AstBuilder()
        for ((name, tokens, expectedBlocks) in testCases) {
            val blocks = builder.build(tokens)
            assertEquals(expectedBlocks.size, blocks.size, "Failed on '$name': Block count mismatch")
            assertEquals(expectedBlocks, blocks, "Failed on '$name': Block content mismatch")
        }
    }

    data class CodeBlockTestCase(
        val name: String,
        val tokens: List<HtmlToken>,
        val expectedBlocks: List<ArticleBlock>
    )

    @Test
    fun `build code block test cases`() {
        val pos = SourcePosition(1, 1)
        val testCases = listOf(
            // 1. Standalone <pre>
            CodeBlockTestCase(
                name = "standalone pre without code tag",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "pre", position = pos),
                    HtmlToken.Text(content = "val x = 1\nval y = 2", position = pos),
                    HtmlToken.TagClose(name = "pre", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.CodeBlock(
                        code = "val x = 1\nval y = 2",
                        language = null
                    )
                )
            ),

            // 2. <pre><code> without language
            CodeBlockTestCase(
                name = "pre with code tag without language",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "pre", position = pos),
                    HtmlToken.TagOpen(name = "code", position = pos),
                    HtmlToken.Text(content = "echo hello", position = pos),
                    HtmlToken.TagClose(name = "code", position = pos),
                    HtmlToken.TagClose(name = "pre", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.CodeBlock(
                        code = "echo hello",
                        language = null
                    )
                )
            ),

            // 3. <pre><code class="language-kotlin">
            CodeBlockTestCase(
                name = "pre with language- prefix",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "pre", position = pos),
                    HtmlToken.TagOpen(
                        name = "code",
                        attributes = mapOf("class" to "language-kotlin"),
                        position = pos
                    ),
                    HtmlToken.Text(content = "fun main() {\n    println(\"hi\")\n}", position = pos),
                    HtmlToken.TagClose(name = "code", position = pos),
                    HtmlToken.TagClose(name = "pre", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.CodeBlock(
                        code = "fun main() {\n    println(\"hi\")\n}",
                        language = "kotlin"
                    )
                )
            ),

            // 4. <pre><code class="lang-python">
            CodeBlockTestCase(
                name = "pre with lang- prefix",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "pre", position = pos),
                    HtmlToken.TagOpen(
                        name = "code",
                        attributes = mapOf("class" to "lang-python"),
                        position = pos
                    ),
                    HtmlToken.Text(content = "print(\"hello\")", position = pos),
                    HtmlToken.TagClose(name = "code", position = pos),
                    HtmlToken.TagClose(name = "pre", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.CodeBlock(
                        code = "print(\"hello\")",
                        language = "python"
                    )
                )
            ),

            // 5. Preserving exact spaces, tabs, and indentation
            CodeBlockTestCase(
                name = "preserve indentation and formatting",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "pre", position = pos),
                    HtmlToken.TagOpen(name = "code", position = pos),
                    HtmlToken.Text(content = "    indented line\n\t\ttabbed line", position = pos),
                    HtmlToken.TagClose(name = "code", position = pos),
                    HtmlToken.TagClose(name = "pre", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.CodeBlock(
                        code = "    indented line\n\t\ttabbed line",
                        language = null
                    )
                )
            ),

            // 6. Absorbs inner tags (such as syntax highlighting spans)
            CodeBlockTestCase(
                name = "absorbs syntax highlighting inner tags",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "pre", position = pos),
                    HtmlToken.TagOpen(
                        name = "code",
                        attributes = mapOf("class" to "language-js"),
                        position = pos
                    ),
                    HtmlToken.TagOpen(
                        name = "span",
                        attributes = mapOf("class" to "token-keyword"),
                        position = pos
                    ),
                    HtmlToken.Text(content = "const", position = pos),
                    HtmlToken.TagClose(name = "span", position = pos),
                    HtmlToken.Text(content = " x = 1;", position = pos),
                    HtmlToken.TagClose(name = "code", position = pos),
                    HtmlToken.TagClose(name = "pre", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.CodeBlock(
                        code = "const x = 1;",
                        language = "js"
                    )
                )
            ),

            // 7. Unclosed pre tag flushes at EOF
            CodeBlockTestCase(
                name = "unclosed pre tag flushes at EOF",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "pre", position = pos),
                    HtmlToken.TagOpen(
                        name = "code",
                        attributes = mapOf("class" to "language-rust"),
                        position = pos
                    ),
                    HtmlToken.Text(content = "fn main() {", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.CodeBlock(
                        code = "fn main() {",
                        language = "rust"
                    )
                )
            ),

            // 8. Code block nested inside list item
            CodeBlockTestCase(
                name = "code block nested inside list item",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "ul", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Installation:", position = pos),
                    HtmlToken.TagOpen(name = "pre", position = pos),
                    HtmlToken.TagOpen(
                        name = "code",
                        attributes = mapOf("class" to "language-bash"),
                        position = pos
                    ),
                    HtmlToken.Text(content = "npm install khtml", position = pos),
                    HtmlToken.TagClose(name = "code", position = pos),
                    HtmlToken.TagClose(name = "pre", position = pos),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagClose(name = "ul", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.HtmlList(
                        isOrdered = false,
                        items = listOf(
                            ArticleBlock.ListItem(
                                spans = listOf(InlineSpan(text = "Installation:")),
                                index = 1,
                                children = listOf(
                                    ArticleBlock.CodeBlock(
                                        code = "npm install khtml",
                                        language = "bash"
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val builder = AstBuilder()
        for ((name, tokens, expectedBlocks) in testCases) {
            val blocks = builder.build(tokens)
            assertEquals(expectedBlocks.size, blocks.size, "Failed on '$name': Block count mismatch")
            assertEquals(expectedBlocks, blocks, "Failed on '$name': Block content mismatch")
        }
    }

    data class ImageBlockTestCase(
        val name: String,
        val tokens: List<HtmlToken>,
        val expectedBlocks: List<ArticleBlock>
    )

    @Test
    fun `build image block test cases`() {
        val pos = SourcePosition(1, 1)
        val testCases = listOf(
            // 1. Standalone <img> with src and alt
            ImageBlockTestCase(
                name = "standalone img with src and alt",
                tokens = listOf(
                    HtmlToken.TagOpen(
                        name = "img",
                        attributes = mapOf("src" to "https://example.com/cat.png", "alt" to "A cute cat"),
                        position = pos
                    )
                ),
                expectedBlocks = listOf(
                    ArticleBlock.ImageBlock(
                        src = "https://example.com/cat.png",
                        alt = "A cute cat"
                    )
                )
            ),

            // 2. Standalone <img> with only src
            ImageBlockTestCase(
                name = "standalone img with only src",
                tokens = listOf(
                    HtmlToken.TagOpen(
                        name = "img",
                        attributes = mapOf("src" to "/images/banner.jpg"),
                        position = pos
                    )
                ),
                expectedBlocks = listOf(
                    ArticleBlock.ImageBlock(
                        src = "/images/banner.jpg",
                        alt = null
                    )
                )
            ),

            // 3. <img> followed by optional closing tag </img>
            ImageBlockTestCase(
                name = "img with explicit closing tag is absorbed",
                tokens = listOf(
                    HtmlToken.TagOpen(
                        name = "img",
                        attributes = mapOf("src" to "logo.svg", "alt" to "Logo"),
                        position = pos
                    ),
                    HtmlToken.TagClose(name = "img", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.ImageBlock(
                        src = "logo.svg",
                        alt = "Logo"
                    )
                )
            ),

            // 4. <img> inside paragraph splits paragraph into before and after
            ImageBlockTestCase(
                name = "img inside paragraph splits paragraph",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "p", position = pos),
                    HtmlToken.Text(content = "Before image", position = pos),
                    HtmlToken.TagOpen(
                        name = "img",
                        attributes = mapOf("src" to "inline.jpg", "alt" to "Diagram"),
                        position = pos
                    ),
                    HtmlToken.Text(content = "After image", position = pos),
                    HtmlToken.TagClose(name = "p", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.Paragraph(spans = listOf(InlineSpan(text = "Before image"))),
                    ArticleBlock.ImageBlock(src = "inline.jpg", alt = "Diagram"),
                    ArticleBlock.Paragraph(spans = listOf(InlineSpan(text = "After image")))
                )
            ),

            // 5. <img> nested inside list item
            ImageBlockTestCase(
                name = "img nested inside list item",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "ul", position = pos),
                    HtmlToken.TagOpen(name = "li", position = pos),
                    HtmlToken.Text(content = "Recipe step:", position = pos),
                    HtmlToken.TagOpen(
                        name = "img",
                        attributes = mapOf("src" to "step1.jpg", "alt" to "Chop onions"),
                        position = pos
                    ),
                    HtmlToken.TagClose(name = "li", position = pos),
                    HtmlToken.TagClose(name = "ul", position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.HtmlList(
                        isOrdered = false,
                        items = listOf(
                            ArticleBlock.ListItem(
                                spans = listOf(InlineSpan(text = "Recipe step:")),
                                index = 1,
                                children = listOf(
                                    ArticleBlock.ImageBlock(
                                        src = "step1.jpg",
                                        alt = "Chop onions"
                                    )
                                )
                            )
                        )
                    )
                )
            ),

            // 6. Multiple consecutive images at root
            ImageBlockTestCase(
                name = "multiple consecutive images at root",
                tokens = listOf(
                    HtmlToken.TagOpen(name = "img", attributes = mapOf("src" to "1.png"), position = pos),
                    HtmlToken.TagOpen(name = "img", attributes = mapOf("src" to "2.png"), position = pos)
                ),
                expectedBlocks = listOf(
                    ArticleBlock.ImageBlock(src = "1.png", alt = null),
                    ArticleBlock.ImageBlock(src = "2.png", alt = null)
                )
            )
        )

        val builder = AstBuilder()
        for ((name, tokens, expectedBlocks) in testCases) {
            val blocks = builder.build(tokens)
            assertEquals(expectedBlocks.size, blocks.size, "Failed on '$name': Block count mismatch")
            assertEquals(expectedBlocks, blocks, "Failed on '$name': Block content mismatch")
        }
    }

    @Test
    fun `logger records unhandled tags across scopes`() {
        val pos = SourcePosition(1, 1)
        val logs = mutableListOf<String>()
        val logger = AstLogger { scope, message ->
            logs.add("[$scope] $message")
        }

        val tokens = listOf(
            // 1. Unhandled tag at root: <marquee>
            HtmlToken.TagOpen(name = "marquee", position = pos),
            // 2. A normal paragraph with an unhandled closing tag: <p>Hello</unknown></p>
            HtmlToken.TagOpen(name = "p", position = pos),
            HtmlToken.Text(content = "Hello", position = pos),
            HtmlToken.TagClose(name = "unknown", position = pos),
            HtmlToken.TagClose(name = "p", position = pos),
            // 3. Unhandled tag at root: </blink>
            HtmlToken.TagClose(name = "blink", position = pos)
        )

        val builder = AstBuilder(logger = logger)
        val blocks = builder.build(tokens)

        assertEquals(1, blocks.size)
        assertEquals(listOf(ArticleBlock.Paragraph(listOf(InlineSpan("Hello")))), blocks)

        assertEquals(3, logs.size)
        assertEquals("[root] UNHANDLED: <marquee>", logs[0])
        assertEquals("[paragraph] UNHANDLED: </unknown>", logs[1])
        assertEquals("[root] UNHANDLED: </blink>", logs[2])
    }
}
