package org.khtml

enum class TokenResult {
    HANDLED,
    SKIPPED,
    UNHANDLED
}

internal object HtmlTags {
    val INLINE = setOf(
        "b", "strong", "i", "em", "a", "code",
        "kbd", "samp", "mark", "span", "br", "cite", "dfn"
    )

    val VOID = setOf(
        "br", "hr", "img", "meta", "link"
    )

    val HEADINGS = setOf(
        "h1", "h2", "h3", "h4", "h5", "h6"
    )
}

enum class InlineStyle {
    BOLD, ITALIC, CODE, HIGHLIGHT
}

interface BuilderContext {
    fun handleToken(
        input: HtmlToken,
        stack: ArrayDeque<BuilderContext>,
        out: MutableList<ArticleBlock>
    ): TokenResult

    fun onEof(out: MutableList<ArticleBlock>) {}
}

class RootContext : BuilderContext {
    override fun handleToken(
        input: HtmlToken,
        stack: ArrayDeque<BuilderContext>,
        out: MutableList<ArticleBlock>
    ): TokenResult {
        return when (input) {
            is HtmlToken.TagOpen -> {
                if (input.name in HtmlTags.INLINE) {
                    val p = ParagraphContext()
                    stack.addLast(p)
                    p.handleToken(input, stack, out)
                }
                when (input.name) {
                    "p" -> {
                        stack.addLast(ParagraphContext())
                        TokenResult.HANDLED
                    }

                    "ol", "ul" -> {
                        val ordered = input.name == "ol"
                        stack.addLast(ListContext(ordered))
                        TokenResult.HANDLED
                    }

                    in HtmlTags.HEADINGS -> {
                        val headingLevel = input.name.drop(1).toInt()
                        stack.addLast(HeadingContext(headingLevel))
                        TokenResult.HANDLED
                    }

                    else -> TokenResult.UNHANDLED
                }
            }

            is HtmlToken.Text -> {
                if (input.content.isBlank()) {
                    return TokenResult.SKIPPED
                }
                val p = ParagraphContext()
                stack.addLast(p)
                p.handleToken(input, stack, out) // returns its own TokenResult.HANDLED/UNHANDLED
            }

            else -> TokenResult.UNHANDLED
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
        out: MutableList<ArticleBlock>
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

                    else -> TokenResult.UNHANDLED
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
                        stack.last().handleToken(input, stack, out)
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
        out: MutableList<ArticleBlock>
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

                    else -> TokenResult.UNHANDLED
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
                        stack.last().handleToken(input, stack, out)
                    }
                }
            }
        }
    }

    override fun onEof(out: MutableList<ArticleBlock>) {
        flush(out)
    }
}

class ListContext(val isOrdered: Boolean) : BuilderContext {
    private val spans = mutableListOf<InlineSpan>()
    private val listItems = mutableListOf<ArticleBlock.ListItem>()
    private var index = 0

    // private fun nextIndex(): Int = ++index
    // private fun getIndex(): Int = index

    private fun flush(out: MutableList<ArticleBlock>) {
        if (listItems.isNotEmpty()) {
            out.add(ArticleBlock.HtmlList(items = listItems.toList(), isOrdered = isOrdered))
            listItems.clear()
        }
    }

    override fun handleToken(
        input: HtmlToken,
        stack: ArrayDeque<BuilderContext>,
        out: MutableList<ArticleBlock>
    ): TokenResult {
        return when (input) {
            is HtmlToken.Text -> {
                spans.add(
                    InlineSpan(
                        text = input.content
                    )
                )
                TokenResult.HANDLED
            }

            is HtmlToken.TagOpen -> {
                when (input.name) {
                    "li" -> {
                        val l = ListItemContext(listItems, ++index)
                        stack.addLast(l)
                        TokenResult.HANDLED
                    }

                    else -> TokenResult.UNHANDLED
                }
            }

            is HtmlToken.TagClose -> {
                when (input.name) {
                    "ol", "ul" -> {
                        flush(out)
                        stack.removeLast()
                        TokenResult.HANDLED
                    }

                    else -> TokenResult.UNHANDLED
                    // else -> {
                    //     flush(out)
                    //     stack.removeLast()
                    //     stack.last().handleToken(input, stack, out)
                    // }
                }
            }


        }
    }


    override fun onEof(out: MutableList<ArticleBlock>) {
        flush(out)
    }
}

class ListItemContext(
    val items: MutableList<ArticleBlock.ListItem>,
    val index: Int
) : BuilderContext {
    private val spans: MutableList<InlineSpan> = mutableListOf<InlineSpan>()
    private val activeStyles: MutableList<String> = mutableListOf<String>()
    private var link: String? = null

    private fun flush(out: MutableList<ArticleBlock.ListItem>) {
        if (spans.isNotEmpty()) {
            out.add(ArticleBlock.ListItem(spans = spans.toList(), index = index))
            spans.clear()
            activeStyles.clear()
            link = null
        }
    }

    override fun handleToken(
        input: HtmlToken,
        stack: ArrayDeque<BuilderContext>,
        out: MutableList<ArticleBlock>
    ): TokenResult {
        return when (input) {
            is HtmlToken.TagOpen -> {
                // in cases where we have: <li> [first] <li> [second]
                when (input.name) {
                    "li" -> {
                        flush(items)
                        stack.removeLast()
                        stack.last().handleToken(input, stack, out)
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

                    else -> TokenResult.UNHANDLED
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

                    else -> {
                        flush(items)
                        stack.removeLast()
                        stack.last().handleToken(input, stack, out)
                    }
                }
            }
        }
    }

    override fun onEof(out: MutableList<ArticleBlock>) {
        flush(items)
    }
}
