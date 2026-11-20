package org.khtml

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
                        true
                    }

                    "ol", "ul" -> {
                        val ordered = input.name == "ol"
                        stack.addLast(ListContext(ordered))
                        true
                    }

                    in HtmlTags.HEADINGS -> {
                        val headingLevel = input.name.drop(1).toInt()
                        stack.addLast(HeadingContext(headingLevel))
                        true
                    }

                    else -> false
                }
            }

            is HtmlToken.Text -> {
                if (input.content.isBlank()) {
                    return true
                }
                val p = ParagraphContext()
                stack.addLast(p)
                p.handleToken(input, stack, out)
                true
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
                true
            }

            is HtmlToken.TagClose -> {
                when (input.name) {
                    "p" -> {
                        flush(out)
                        stack.removeLast()
                        true
                    }

                    "b" -> {
                        activeStyles.remove("b")
                        true
                    }

                    "strong" -> {
                        activeStyles.remove("strong")
                        true
                    }

                    "i" -> {
                        activeStyles.remove("i")
                        true
                    }

                    "em" -> {
                        activeStyles.remove("em")
                        true
                    }

                    "code" -> {
                        activeStyles.remove("code")
                        true
                    }

                    "mark" -> {
                        activeStyles.remove("mark")
                        true
                    }

                    "a" -> {
                        link = null
                        true
                    }

                    else -> false
                }
            }

            is HtmlToken.TagOpen -> {
                when (input.name) {
                    "b", "strong" -> {
                        activeStyles.add(input.name)
                        true
                    }

                    "i", "em" -> {
                        activeStyles.add(input.name)
                        true
                    }

                    "code" -> {
                        activeStyles.add(input.name)
                        true
                    }

                    "mark" -> {
                        activeStyles.add(input.name)
                        true
                    }

                    "a" -> {
                        link = input.attributes["href"]
                        true
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
                true
            }

            is HtmlToken.TagClose -> {
                when (input.name) {
                    in HtmlTags.HEADINGS -> {
                        flush(out)
                        stack.removeLast()
                        true
                    }

                    "b" -> {
                        activeStyles.remove("b")
                        true
                    }

                    "strong" -> {
                        activeStyles.remove("strong")
                        true
                    }

                    "i" -> {
                        activeStyles.remove("i")
                        true
                    }

                    "em" -> {
                        activeStyles.remove("em")
                        true
                    }

                    "code" -> {
                        activeStyles.remove("code")
                        true
                    }

                    "mark" -> {
                        activeStyles.remove("mark")
                        true
                    }

                    "a" -> {
                        link = null
                        true
                    }

                    else -> false
                }
            }

            is HtmlToken.TagOpen -> {
                when (input.name) {
                    "b", "strong" -> {
                        activeStyles.add(input.name)
                        true
                    }

                    "i", "em" -> {
                        activeStyles.add(input.name)
                        true
                    }

                    "code" -> {
                        activeStyles.add(input.name)
                        true
                    }

                    "mark" -> {
                        activeStyles.add(input.name)
                        true
                    }

                    "a" -> {
                        link = input.attributes["href"]
                        true
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
                true
            }

            is HtmlToken.TagOpen -> {
                when (input.name) {
                    "li" -> {
                        val l = ListItemContext(listItems, ++index)
                        stack.addLast(l)
                        l.handleToken(input, stack, out)
                    }

                    else -> false
                }
            }

            is HtmlToken.TagClose -> {
                when (input.name) {
                    "ol", "ul" -> {
                        flush(out)
                        stack.removeLast()
                        true
                    }

                    else -> false
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

    private fun flush(out: MutableList<ArticleBlock.ListItem>) {
        if (spans.isNotEmpty()) {
            out.add(ArticleBlock.ListItem(spans = spans.toList(), index = index))
            spans.clear()
        }
    }

    override fun handleToken(
        input: HtmlToken,
        stack: ArrayDeque<BuilderContext>,
        out: MutableList<ArticleBlock>
    ): Boolean {
        return when (input) {
            // is HtmlToken.TagOpen -> {
            //     TODO()
            // }

            is HtmlToken.Text -> {
                spans.add(
                    InlineSpan(text = input.content)
                )
                true
            }

            is HtmlToken.TagClose -> {
                when (input.name) {
                    "li" -> {
                        flush(items)
                        stack.removeLast()
                        true
                    }

                    else -> {
                        flush(items)
                        stack.removeLast()
                        stack.last().handleToken(input, stack, out)
                    }
                }
            }

            else -> true
        }
    }

    override fun onEof(out: MutableList<ArticleBlock>) {
        flush(items)
    }
}
