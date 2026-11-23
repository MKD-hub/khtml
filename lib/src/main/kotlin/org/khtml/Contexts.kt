package org.khtml

enum class TokenResult {
    HANDLED,
    SKIPPED,
    UNHANDLED
}

internal object HtmlTags {
    val INLINE = setOf(
        "b", "strong", "i", "em", "a", "code",
        "kbd", "samp", "mark", "span", "cite", "dfn"
    )

    val HEADINGS = setOf(
        "h1", "h2", "h3", "h4", "h5", "h6"
    )
}

enum class InlineStyle {
    BOLD, ITALIC, CODE, HIGHLIGHT
}

internal fun HtmlToken.describe(): String = when (this) {
    is HtmlToken.TagOpen -> "<$name>"
    is HtmlToken.TagClose -> "</$name>"
    is HtmlToken.Text -> "text: \"${content.take(30)}${if (content.length > 30) "..." else ""}\""
}

interface BuilderContext {
    val scope: String get() = this::class.simpleName?.removeSuffix("Context")?.lowercase() ?: "root"

    fun handleToken(
        input: HtmlToken,
        stack: ArrayDeque<BuilderContext>,
        out: MutableList<ArticleBlock>,
        logger: AstLogger? = null
    ): TokenResult

    fun logUnhandled(logger: AstLogger?, input: HtmlToken): TokenResult {
        logger?.log(scope, "UNHANDLED: ${input.describe()}")
        return TokenResult.UNHANDLED
    }

    fun onEof(out: MutableList<ArticleBlock>) {}
}

class RootContext : BuilderContext {
    override fun handleToken(
        input: HtmlToken,
        stack: ArrayDeque<BuilderContext>,
        out: MutableList<ArticleBlock>,
        logger: AstLogger?
    ): TokenResult {
        return when (input) {
            is HtmlToken.TagOpen -> {
                if (input.name in HtmlTags.INLINE) {
                    val p = ParagraphContext()
                    stack.addLast(p)
                    p.handleToken(input, stack, out, logger)
                }
                when (input.name) {
                    "p" -> {
                        stack.addLast(ParagraphContext())
                        TokenResult.HANDLED
                    }

                    "ol", "ul" -> {
                        val isOrdered = input.name == "ol"
                        stack.addLast(ListContext(isOrdered, out))
                        TokenResult.HANDLED
                    }

                    "li" -> {
                        val p = ParagraphContext()
                        stack.addLast(p)
                        TokenResult.HANDLED
                    }

                    "hr" -> {
                        out.add(
                            ArticleBlock.Divider
                        )
                        TokenResult.HANDLED
                    }

                    "br" -> {
                        out.add(
                            ArticleBlock.LineBreak
                        )
                        TokenResult.HANDLED
                    }

                    "blockquote" -> {
                        stack.addLast(BlockquoteContext())
                        TokenResult.HANDLED
                    }

                    "pre" -> {
                        val openedBy = input.name
                        stack.addLast(CodeblockContext(openedBy))
                        TokenResult.HANDLED
                    }

                    "img" -> {
                        val src = input.attributes["src"] ?: ""
                        val alt = input.attributes["alt"]
                        out.add(ArticleBlock.ImageBlock(src = src, alt = alt))
                        TokenResult.HANDLED
                    }

                    in HtmlTags.HEADINGS -> {
                        val headingLevel = input.name.drop(1).toInt()
                        stack.addLast(HeadingContext(headingLevel))
                        TokenResult.HANDLED
                    }

                    else -> logUnhandled(logger, input)
                }
            }

            is HtmlToken.Text -> {
                if (input.content.isBlank()) {
                    return TokenResult.SKIPPED
                }
                val p = ParagraphContext()
                stack.addLast(p)
                p.handleToken(input, stack, out, logger) // returns its own TokenResult.HANDLED/UNHANDLED
            }

            else -> logUnhandled(logger, input)
        }
    }
}

class ParagraphContext : BuilderContext {
    private val spans = mutableListOf<InlineSpan>()
    private val activeStyles = ArrayDeque<String>()
    private var link: String? = null

    private fun flush(out: MutableList<ArticleBlock>) {
        if (spans.any { it.text.isNotBlank() }) {
            out.add(ArticleBlock.Paragraph(spans.toList()))
            spans.clear()
        }
        activeStyles.clear()
        link = null
    }

    override fun handleToken(
        input: HtmlToken,
        stack: ArrayDeque<BuilderContext>,
        out: MutableList<ArticleBlock>,
        logger: AstLogger?
    ): TokenResult {
        return when (input) {

            is HtmlToken.Text -> {
                val inlineStyle = mutableSetOf<InlineStyle>()
                // check activeStyles
                for (style in activeStyles) {
                    when (style) {
                        "b", "strong" -> {
                            inlineStyle.add(InlineStyle.BOLD)
                        }

                        "i", "em" -> {
                            inlineStyle.add(InlineStyle.ITALIC)
                        }

                        "code" -> {
                            inlineStyle.add(InlineStyle.CODE)
                        }

                        "mark" -> {
                            inlineStyle.add(InlineStyle.HIGHLIGHT)
                        }

                        else -> Unit
                    }
                }
                spans.add(
                    InlineSpan(
                        text = input.content,
                        styles = inlineStyle,
                        linkUrl = link
                    )
                )
                TokenResult.HANDLED
            }

            is HtmlToken.TagClose -> {
                when (input.name) {
                    "p" -> {
                        flush(out)
                        stack.removeLast()
                        TokenResult.HANDLED
                    }

                    "b" -> {
                        activeStyles.remove("b")
                        TokenResult.HANDLED
                    }

                    "strong" -> {
                        activeStyles.remove("strong")
                        TokenResult.HANDLED
                    }

                    "i" -> {
                        activeStyles.remove("i")
                        TokenResult.HANDLED
                    }

                    "em" -> {
                        activeStyles.remove("em")
                        TokenResult.HANDLED
                    }

                    "code" -> {
                        activeStyles.remove("code")
                        TokenResult.HANDLED
                    }

                    "mark" -> {
                        activeStyles.remove("mark")
                        TokenResult.HANDLED
                    }

                    "a" -> {
                        link = null
                        TokenResult.HANDLED
                    }

                    "img" -> TokenResult.SKIPPED

                    else -> logUnhandled(logger, input)
                }
            }

            is HtmlToken.TagOpen -> {
                when (input.name) {
                    "hr" -> {
                        flush(out)
                        out.add(ArticleBlock.Divider)
                        TokenResult.HANDLED
                    }

                    "br" -> {
                        flush(out)
                        out.add(ArticleBlock.LineBreak)
                        TokenResult.HANDLED
                    }

                    "img" -> {
                        flush(out)
                        val src = input.attributes["src"] ?: ""
                        val alt = input.attributes["alt"]
                        out.add(ArticleBlock.ImageBlock(src = src, alt = alt))
                        TokenResult.HANDLED
                    }

                    "b", "strong" -> {
                        activeStyles.add(input.name)
                        TokenResult.HANDLED
                    }

                    "i", "em" -> {
                        activeStyles.add(input.name)
                        TokenResult.HANDLED
                    }

                    "code" -> {
                        activeStyles.add(input.name)
                        TokenResult.HANDLED
                    }

                    "mark" -> {
                        activeStyles.add(input.name)
                        TokenResult.HANDLED
                    }

                    "a" -> {
                        link = input.attributes["href"]
                        TokenResult.HANDLED
                    }

                    else -> {
                        flush(out)
                        stack.removeLast()
                        stack.last().handleToken(input, stack, out, logger)
                    }
                }
            }
        }
    }

    override fun onEof(out: MutableList<ArticleBlock>) {
        flush(out)
    }
}

class BlockquoteContext(private val targetOut: MutableList<ArticleBlock>? = null) : BuilderContext {
    private val spans = mutableListOf<InlineSpan>()
    private val activeStyles = ArrayDeque<String>()
    private var link: String? = null

    private fun flush(out: MutableList<ArticleBlock>) {
        val destination = targetOut ?: out
        if (spans.any { it.text.isNotBlank() }) {
            destination.add(ArticleBlock.Blockquote(spans.toList()))
            spans.clear()
        }
        activeStyles.clear()
        link = null
    }

    override fun handleToken(
        input: HtmlToken,
        stack: ArrayDeque<BuilderContext>,
        out: MutableList<ArticleBlock>,
        logger: AstLogger?
    ): TokenResult {
        return when (input) {
            is HtmlToken.Text -> {
                val inlineStyle = mutableSetOf<InlineStyle>()
                for (style in activeStyles) {
                    when (style) {
                        "b", "strong" -> inlineStyle.add(InlineStyle.BOLD)
                        "i", "em" -> inlineStyle.add(InlineStyle.ITALIC)
                        "code" -> inlineStyle.add(InlineStyle.CODE)
                        "mark" -> inlineStyle.add(InlineStyle.HIGHLIGHT)
                        else -> Unit
                    }
                }
                spans.add(
                    InlineSpan(
                        text = input.content,
                        styles = inlineStyle,
                        linkUrl = link
                    )
                )
                TokenResult.HANDLED
            }

            is HtmlToken.TagClose -> {
                when (input.name) {
                    "blockquote" -> {
                        flush(out)
                        stack.removeLast()
                        TokenResult.HANDLED
                    }

                    "p" -> TokenResult.SKIPPED

                    "b" -> {
                        activeStyles.remove("b")
                        TokenResult.HANDLED
                    }

                    "strong" -> {
                        activeStyles.remove("strong")
                        TokenResult.HANDLED
                    }

                    "i" -> {
                        activeStyles.remove("i")
                        TokenResult.HANDLED
                    }

                    "em" -> {
                        activeStyles.remove("em")
                        TokenResult.HANDLED
                    }

                    "code" -> {
                        activeStyles.remove("code")
                        TokenResult.HANDLED
                    }

                    "mark" -> {
                        activeStyles.remove("mark")
                        TokenResult.HANDLED
                    }

                    "a" -> {
                        link = null
                        TokenResult.HANDLED
                    }

                    "img" -> TokenResult.SKIPPED

                    else -> logUnhandled(logger, input)
                }
            }

            is HtmlToken.TagOpen -> {
                when (input.name) {
                    "p" -> TokenResult.SKIPPED

                    "hr" -> {
                        flush(out)
                        val destination = targetOut ?: out
                        destination.add(ArticleBlock.Divider)
                        TokenResult.HANDLED
                    }

                    "br" -> {
                        flush(out)
                        val destination = targetOut ?: out
                        destination.add(ArticleBlock.LineBreak)
                        TokenResult.HANDLED
                    }

                    "b", "strong" -> {
                        activeStyles.add(input.name)
                        TokenResult.HANDLED
                    }

                    "i", "em" -> {
                        activeStyles.add(input.name)
                        TokenResult.HANDLED
                    }

                    "code" -> {
                        activeStyles.add(input.name)
                        TokenResult.HANDLED
                    }

                    "mark" -> {
                        activeStyles.add(input.name)
                        TokenResult.HANDLED
                    }

                    "a" -> {
                        link = input.attributes["href"]
                        TokenResult.HANDLED
                    }

                    else -> {
                        flush(out)
                        stack.removeLast()
                        stack.last().handleToken(input, stack, out, logger)
                    }
                }
            }
        }
    }

    override fun onEof(out: MutableList<ArticleBlock>) {
        flush(out)
    }
}

class HeadingContext(val level: Int) : BuilderContext {
    private val spans = mutableListOf<InlineSpan>()
    private val activeStyles = ArrayDeque<String>()
    private var link: String? = null

    private fun flush(out: MutableList<ArticleBlock>) {
        if (spans.isNotEmpty()) {
            out.add(ArticleBlock.Heading(level = level, spans = spans.toList()))
            spans.clear()
        }
        activeStyles.clear()
        link = null
    }

    override fun handleToken(
        input: HtmlToken,
        stack: ArrayDeque<BuilderContext>,
        out: MutableList<ArticleBlock>,
        logger: AstLogger?
    ): TokenResult {
        return when (input) {

            is HtmlToken.Text -> {
                val inlineStyle = mutableSetOf<InlineStyle>()
                // check activeStyles
                for (style in activeStyles) {
                    when (style) {
                        "b", "strong" -> {
                            inlineStyle.add(InlineStyle.BOLD)
                        }

                        "i", "em" -> {
                            inlineStyle.add(InlineStyle.ITALIC)
                        }

                        "code" -> {
                            inlineStyle.add(InlineStyle.CODE)
                        }

                        "mark" -> {
                            inlineStyle.add(InlineStyle.HIGHLIGHT)
                        }

                        else -> Unit
                    }
                }
                spans.add(
                    InlineSpan(
                        text = input.content,
                        styles = inlineStyle,
                        linkUrl = link
                    )
                )
                TokenResult.HANDLED
            }

            is HtmlToken.TagClose -> {
                when (input.name) {
                    in HtmlTags.HEADINGS -> {
                        flush(out)
                        stack.removeLast()
                        TokenResult.HANDLED
                    }

                    "b" -> {
                        activeStyles.remove("b")
                        TokenResult.HANDLED
                    }

                    "strong" -> {
                        activeStyles.remove("strong")
                        TokenResult.HANDLED
                    }

                    "i" -> {
                        activeStyles.remove("i")
                        TokenResult.HANDLED
                    }

                    "em" -> {
                        activeStyles.remove("em")
                        TokenResult.HANDLED
                    }

                    "code" -> {
                        activeStyles.remove("code")
                        TokenResult.HANDLED
                    }

                    "mark" -> {
                        activeStyles.remove("mark")
                        TokenResult.HANDLED
                    }

                    "a" -> {
                        link = null
                        TokenResult.HANDLED
                    }

                    "img" -> TokenResult.SKIPPED

                    else -> logUnhandled(logger, input)
                }
            }

            is HtmlToken.TagOpen -> {
                when (input.name) {
                    "b", "strong" -> {
                        activeStyles.add(input.name)
                        TokenResult.HANDLED
                    }

                    "i", "em" -> {
                        activeStyles.add(input.name)
                        TokenResult.HANDLED
                    }

                    "code" -> {
                        activeStyles.add(input.name)
                        TokenResult.HANDLED
                    }

                    "mark" -> {
                        activeStyles.add(input.name)
                        TokenResult.HANDLED
                    }

                    "a" -> {
                        link = input.attributes["href"]
                        TokenResult.HANDLED
                    }

                    else -> {
                        flush(out)
                        stack.removeLast()
                        stack.last().handleToken(input, stack, out, logger)
                    }
                }
            }
        }
    }

    override fun onEof(out: MutableList<ArticleBlock>) {
        flush(out)
    }
}

class ListContext(val isOrdered: Boolean, val targetOut: MutableList<ArticleBlock>) : BuilderContext {
    private val listItems = mutableListOf<ArticleBlock.ListItem>()
    private var index = 0

    private fun flush() {
        if (listItems.isNotEmpty()) {
            targetOut.add(ArticleBlock.HtmlList(items = listItems.toList(), isOrdered = isOrdered))
            listItems.clear()
        }
    }

    override fun handleToken(
        input: HtmlToken,
        stack: ArrayDeque<BuilderContext>,
        out: MutableList<ArticleBlock>,
        logger: AstLogger?
    ): TokenResult {
        return when (input) {
            is HtmlToken.Text -> {
                if (input.content.isBlank()) {
                    TokenResult.SKIPPED
                } else {
                    val l = ListItemContext(listItems, ++index)
                    stack.addLast(l)
                    l.handleToken(input, stack, out, logger)
                }
            }

            is HtmlToken.TagOpen -> {
                when (input.name) {
                    "li" -> {
                        val l = ListItemContext(listItems, ++index)
                        stack.addLast(l)
                        TokenResult.HANDLED
                    }

                    else -> logUnhandled(logger, input)
                }
            }

            is HtmlToken.TagClose -> {
                when (input.name) {
                    "ol", "ul" -> {
                        flush()
                        stack.removeLast()
                        TokenResult.HANDLED
                    }

                    else -> logUnhandled(logger, input)
                }
            }


        }
    }


    override fun onEof(out: MutableList<ArticleBlock>) {
        flush()
    }
}

class ListItemContext(
    val items: MutableList<ArticleBlock.ListItem>,
    val index: Int
) : BuilderContext {
    private val spans: MutableList<InlineSpan> = mutableListOf<InlineSpan>()
    private val activeStyles: MutableList<String> = mutableListOf<String>()
    private var link: String? = null

    // for recursive lists
    private val listItemBlocks = mutableListOf<ArticleBlock>()

    private fun flush(out: MutableList<ArticleBlock.ListItem>) {
        if (spans.isNotEmpty()) {
            out.add(ArticleBlock.ListItem(spans = spans.toList(), index = index, children = listItemBlocks.toList()))
            spans.clear()
            activeStyles.clear()
            link = null
        }
    }

    override fun handleToken(
        input: HtmlToken,
        stack: ArrayDeque<BuilderContext>,
        out: MutableList<ArticleBlock>,
        logger: AstLogger?
    ): TokenResult {
        return when (input) {
            is HtmlToken.TagOpen -> {
                // in cases where we have: <li> [first] <li> [second]
                when (input.name) {
                    "li" -> {
                        flush(items)
                        stack.removeLast()
                        stack.last().handleToken(input, stack, out, logger)
                    }

                    "p" -> {
                        // we just want the text from the paragraph, we'll―
                        // treat is as a standard list item
                        TokenResult.SKIPPED
                    }

                    "b", "strong" -> {
                        activeStyles.add(input.name)
                        TokenResult.HANDLED
                    }

                    "i", "em" -> {
                        activeStyles.add(input.name)
                        TokenResult.HANDLED
                    }

                    "code" -> {
                        activeStyles.add(input.name)
                        TokenResult.HANDLED
                    }

                    "mark" -> {
                        activeStyles.add(input.name)
                        TokenResult.HANDLED
                    }

                    "a" -> {
                        link = input.attributes["href"]
                        TokenResult.HANDLED
                    }

                    "ol", "ul" -> {
                        val isOrdered = input.name == "ol"
                        stack.addLast(ListContext(isOrdered, targetOut = listItemBlocks))
                        TokenResult.HANDLED
                    }

                    "blockquote" -> {
                        stack.addLast(BlockquoteContext(targetOut = listItemBlocks))
                        TokenResult.HANDLED
                    }

                    "pre" -> {
                        val openedBy = input.name
                        stack.addLast(CodeblockContext(openedBy, targetOut = listItemBlocks))
                        TokenResult.HANDLED
                    }

                    "img" -> {
                        val src = input.attributes["src"] ?: ""
                        val alt = input.attributes["alt"]
                        listItemBlocks.add(ArticleBlock.ImageBlock(src = src, alt = alt))
                        TokenResult.HANDLED
                    }

                    "hr", "br" -> {
                        // personal opinion:
                        // line breaks and horizontal rules in lists
                        // have no semantic meaning and add nothing to the lists
                        // avoid them.
                        TokenResult.SKIPPED
                    }

                    else -> logUnhandled(logger, input)
                }
            }

            is HtmlToken.Text -> {
                val inlineStyle = mutableSetOf<InlineStyle>()
                for (style in activeStyles) {
                    when (style) {
                        "b", "strong" -> {
                            inlineStyle.add(InlineStyle.BOLD)
                        }

                        "i", "em" -> {
                            inlineStyle.add(InlineStyle.ITALIC)
                        }

                        "code" -> {
                            inlineStyle.add(InlineStyle.CODE)
                        }

                        "mark" -> {
                            inlineStyle.add(InlineStyle.HIGHLIGHT)
                        }

                        else -> Unit
                    }
                }
                spans.add(
                    InlineSpan(
                        text = input.content,
                        styles = inlineStyle,
                        linkUrl = link
                    )
                )
                TokenResult.HANDLED
            }

            is HtmlToken.TagClose -> {
                when (input.name) {
                    "li" -> {
                        flush(items)
                        stack.removeLast()
                        TokenResult.HANDLED
                    }

                    "b" -> {
                        activeStyles.remove("b")
                        TokenResult.HANDLED
                    }

                    "p" -> {
                        TokenResult.SKIPPED
                    }

                    "strong" -> {
                        activeStyles.remove("strong")
                        TokenResult.HANDLED
                    }

                    "i" -> {
                        activeStyles.remove("i")
                        TokenResult.HANDLED
                    }

                    "em" -> {
                        activeStyles.remove("em")
                        TokenResult.HANDLED
                    }

                    "code" -> {
                        activeStyles.remove("code")
                        TokenResult.HANDLED
                    }

                    "mark" -> {
                        activeStyles.remove("mark")
                        TokenResult.HANDLED
                    }

                    "a" -> {
                        link = null
                        TokenResult.HANDLED
                    }

                    "img" -> TokenResult.SKIPPED

                    else -> {
                        flush(items)
                        stack.removeLast()
                        stack.last().handleToken(input, stack, out, logger)
                    }
                }
            }
        }
    }

    override fun onEof(out: MutableList<ArticleBlock>) {
        flush(items)
    }
}

class CodeblockContext(val openedBy: String, val targetOut: MutableList<ArticleBlock>? = null) : BuilderContext {
    private val codeBuilder = StringBuilder()
    private var language: String? = null

    private fun flush(out: MutableList<ArticleBlock>) {
        val destination = targetOut ?: out
        if (codeBuilder.isNotEmpty()) {
            destination.add(ArticleBlock.CodeBlock(code = codeBuilder.toString(), language = language))
        }
        language = null
    }

    override fun handleToken(
        input: HtmlToken,
        stack: ArrayDeque<BuilderContext>,
        out: MutableList<ArticleBlock>,
        logger: AstLogger?
    ): TokenResult {
        return when (input) {
            is HtmlToken.Text -> {
                codeBuilder.append(input.content)
                TokenResult.HANDLED
            }

            is HtmlToken.TagOpen -> {
                when (input.name) {
                    "code" -> {

                        val rawClass = input.attributes["class"] ?: ""
                        language = rawClass
                            .split("\\s+".toRegex())
                            .firstNotNullOfOrNull { cls ->
                                when {
                                    cls.startsWith("language-") -> cls.removePrefix("language-")
                                    cls.startsWith("lang-") -> cls.removePrefix("lang-")
                                    else -> null
                                }
                            }

                        TokenResult.HANDLED
                    }

                    "span" -> TokenResult.HANDLED

                    else -> logUnhandled(logger, input)
                }
            }

            is HtmlToken.TagClose -> {
                when (input.name) {
                    openedBy -> {
                        flush(out)
                        stack.removeLast()
                        TokenResult.HANDLED
                    }

                    "code", "span" -> TokenResult.HANDLED

                    else -> logUnhandled(logger, input)
                }
            }
        }
    }

    override fun onEof(out: MutableList<ArticleBlock>) {
        flush(out)
    }
}
