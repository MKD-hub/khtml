package org.khtml

private const val TOKEN_HANDLED = true
private const val TOKEN_UNHANDLED = false

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
    ): Boolean = false

    fun onEof(out: MutableList<ArticleBlock>) {}
}

class RootContext : BuilderContext {
    override fun handleToken(
        input: HtmlToken,
        stack: ArrayDeque<BuilderContext>,
        out: MutableList<ArticleBlock>
    ): Boolean {
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
                        TOKEN_HANDLED
                    }

                    "ol", "ul" -> {
                        val ordered = input.name == "ol"
                        stack.addLast(ListContext(ordered))
                        TOKEN_HANDLED
                    }

                    in HtmlTags.HEADINGS -> {
                        val headingLevel = input.name.drop(1).toInt()
                        stack.addLast(HeadingContext(headingLevel))
                        TOKEN_HANDLED
                    }

                    else -> TOKEN_UNHANDLED
                }
            }

            is HtmlToken.Text -> {
                if (input.content.isBlank()) {
                    return TOKEN_HANDLED
                }
                val p = ParagraphContext()
                stack.addLast(p)
                p.handleToken(input, stack, out) // returns its own TOKEN_HANDLED/UNHANDLED
            }

            else -> false
        }
    }
}

class ParagraphContext : BuilderContext {
    private val spans = mutableListOf<InlineSpan>()
    private val activeStyles = ArrayDeque<String>()
    private var link: String? = null

    private fun flush(out: MutableList<ArticleBlock>) {
        if (spans.isNotEmpty()) {
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
    ): Boolean {
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
                TOKEN_HANDLED
            }

            is HtmlToken.TagClose -> {
                when (input.name) {
                    "p" -> {
                        flush(out)
                        stack.removeLast()
                        TOKEN_HANDLED
                    }

                    "b" -> {
                        activeStyles.remove("b")
                        TOKEN_HANDLED
                    }

                    "strong" -> {
                        activeStyles.remove("strong")
                        TOKEN_HANDLED
                    }

                    "i" -> {
                        activeStyles.remove("i")
                        TOKEN_HANDLED
                    }

                    "em" -> {
                        activeStyles.remove("em")
                        TOKEN_HANDLED
                    }

                    "code" -> {
                        activeStyles.remove("code")
                        TOKEN_HANDLED
                    }

                    "mark" -> {
                        activeStyles.remove("mark")
                        TOKEN_HANDLED
                    }

                    "a" -> {
                        link = null
                        TOKEN_HANDLED
                    }

                    else -> TOKEN_UNHANDLED
                }
            }

            is HtmlToken.TagOpen -> {
                when (input.name) {
                    "b", "strong" -> {
                        activeStyles.add(input.name)
                        TOKEN_HANDLED
                    }

                    "i", "em" -> {
                        activeStyles.add(input.name)
                        TOKEN_HANDLED
                    }

                    "code" -> {
                        activeStyles.add(input.name)
                        TOKEN_HANDLED
                    }

                    "mark" -> {
                        activeStyles.add(input.name)
                        TOKEN_HANDLED
                    }

                    "a" -> {
                        link = input.attributes["href"]
                        TOKEN_HANDLED
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
    ): Boolean {
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
                TOKEN_HANDLED
            }

            is HtmlToken.TagClose -> {
                when (input.name) {
                    in HtmlTags.HEADINGS -> {
                        flush(out)
                        stack.removeLast()
                        TOKEN_HANDLED
                    }

                    "b" -> {
                        activeStyles.remove("b")
                        TOKEN_HANDLED
                    }

                    "strong" -> {
                        activeStyles.remove("strong")
                        TOKEN_HANDLED
                    }

                    "i" -> {
                        activeStyles.remove("i")
                        TOKEN_HANDLED
                    }

                    "em" -> {
                        activeStyles.remove("em")
                        TOKEN_HANDLED
                    }

                    "code" -> {
                        activeStyles.remove("code")
                        TOKEN_HANDLED
                    }

                    "mark" -> {
                        activeStyles.remove("mark")
                        TOKEN_HANDLED
                    }

                    "a" -> {
                        link = null
                        TOKEN_HANDLED
                    }

                    else -> TOKEN_UNHANDLED
                }
            }

            is HtmlToken.TagOpen -> {
                when (input.name) {
                    "b", "strong" -> {
                        activeStyles.add(input.name)
                        TOKEN_HANDLED
                    }

                    "i", "em" -> {
                        activeStyles.add(input.name)
                        TOKEN_HANDLED
                    }

                    "code" -> {
                        activeStyles.add(input.name)
                        TOKEN_HANDLED
                    }

                    "mark" -> {
                        activeStyles.add(input.name)
                        TOKEN_HANDLED
                    }

                    "a" -> {
                        link = input.attributes["href"]
                        TOKEN_HANDLED
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
    ): Boolean {
        return when (input) {
            is HtmlToken.Text -> {
                spans.add(
                    InlineSpan(
                        text = input.content
                    )
                )
                TOKEN_HANDLED
            }

            is HtmlToken.TagOpen -> {
                when (input.name) {
                    "li" -> {
                        val l = ListItemContext(listItems, ++index)
                        stack.addLast(l)
                        TOKEN_HANDLED
                    }

                    else -> TOKEN_UNHANDLED
                }
            }

            is HtmlToken.TagClose -> {
                when (input.name) {
                    "ol", "ul" -> {
                        flush(out)
                        stack.removeLast()
                        TOKEN_HANDLED
                    }

                    else -> TOKEN_UNHANDLED
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
    ): Boolean {
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
                        TOKEN_HANDLED
                    }

                    "i", "em" -> {
                        activeStyles.add(input.name)
                        TOKEN_HANDLED
                    }

                    "code" -> {
                        activeStyles.add(input.name)
                        TOKEN_HANDLED
                    }

                    "mark" -> {
                        activeStyles.add(input.name)
                        TOKEN_HANDLED
                    }

                    "a" -> {
                        link = input.attributes["href"]
                        TOKEN_HANDLED
                    }

                    else -> TOKEN_UNHANDLED
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
                TOKEN_HANDLED
            }

            is HtmlToken.TagClose -> {
                when (input.name) {
                    "li" -> {
                        flush(items)
                        stack.removeLast()
                        TOKEN_HANDLED
                    }

                    "b" -> {
                        activeStyles.remove("b")
                        TOKEN_HANDLED
                    }

                    "strong" -> {
                        activeStyles.remove("strong")
                        TOKEN_HANDLED
                    }

                    "i" -> {
                        activeStyles.remove("i")
                        TOKEN_HANDLED
                    }

                    "em" -> {
                        activeStyles.remove("em")
                        TOKEN_HANDLED
                    }

                    "code" -> {
                        activeStyles.remove("code")
                        TOKEN_HANDLED
                    }

                    "mark" -> {
                        activeStyles.remove("mark")
                        TOKEN_HANDLED
                    }

                    "a" -> {
                        link = null
                        TOKEN_HANDLED
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
