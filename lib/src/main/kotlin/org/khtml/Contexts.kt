package org.khtml

internal object HtmlTags {
    val INLINE = setOf(
        "b", "strong", "i", "em", "a", "code",
        "kbd", "samp", "mark", "span", "br", "cite", "dfn"
    )

    val VOID = setOf(
        "br", "hr", "img", "meta", "link"
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
                if (input.name == "p") {
                    stack.addLast(ParagraphContext())
                    true
                } else {
                    false
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

                    else -> false
                }
            }
        }
    }

    override fun onEof(out: MutableList<ArticleBlock>) {
        flush(out)
    }
}
