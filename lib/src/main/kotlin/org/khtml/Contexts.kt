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

    private fun flush(out: MutableList<ArticleBlock>) {
        if (spans.isNotEmpty()) {
            out.add(ArticleBlock.Paragraph(spans.toList()))
            spans.clear()
        }
        activeStyles.clear()
    }

    override fun handleToken(
        input: HtmlToken,
        stack: ArrayDeque<BuilderContext>,
        out: MutableList<ArticleBlock>
    ): Boolean {
        return when (input) {

            is HtmlToken.Text -> {
                // check activeStyles
                for (style in activeStyles) {
                    when (style) {
                        "b" -> {
                            spans.add(
                                InlineSpan(
                                    text = input.content,
                                    styles = setOf(InlineStyle.BOLD)
                                )
                            )
                        }

                        else -> Unit
                    }
                }
                if (activeStyles.isEmpty()) {
                    // this is for unstyled text
                    spans.add(InlineSpan(text = input.content))
                }
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

                    else -> false
                }
            }

            is HtmlToken.TagOpen -> {
                when (input.name) {
                    "b" -> {
                        activeStyles.add(input.name)
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
