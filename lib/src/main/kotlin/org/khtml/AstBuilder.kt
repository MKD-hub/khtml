package org.khtml

import kotlin.reflect.typeOf

interface BuilderContext {
    // class HeadingContext(val level: Int) : BuilderContext
    // class ListContext(val isOrdered: Boolean) : BuilderContext
    fun handleToken(
        input: HtmlToken,
        stack: ArrayDeque<BuilderContext>,
        out: MutableList<ArticleBlock>
    ): Boolean = false

    fun onEof(out: MutableList<ArticleBlock>) {}
}

class ParapgraphContext : BuilderContext {
    private val spans = mutableListOf<InlineSpan>()

    private fun flush(out: MutableList<ArticleBlock>) {
        if (spans.isNotEmpty()) {
            out.add(ArticleBlock.Paragraph(spans.toList()))
            spans.clear()
        }
    }

    override fun handleToken(
        input: HtmlToken,
        stack: ArrayDeque<BuilderContext>,
        out: MutableList<ArticleBlock>
    ): Boolean {
        return when (input) {

            is HtmlToken.Text -> {
                spans.add(InlineSpan.Text(input.content))
                true
            }

            is HtmlToken.TagClose -> {
                if (input.name == "p") {
                    flush(out)
                    stack.removeLast()
                    true
                } else {
                    false
                }
            }

            else -> false
        }
    }

    override fun onEof(out: MutableList<ArticleBlock>) {
        flush(out)
    }
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
                    stack.addLast(ParapgraphContext())
                    true
                } else {
                    false
                }
            }

            is HtmlToken.Text -> {
                val p = ParapgraphContext()
                stack.addLast(p)
                p.handleToken(input, stack, out)
                true
            }

            else -> false
        }
    }
}

class AstBuilder {
    fun build(tokens: List<HtmlToken>): List<ArticleBlock> {

        val blocks = mutableListOf<ArticleBlock>()
        val stack = ArrayDeque<BuilderContext>()

        stack.addLast(RootContext())

        if (tokens.isEmpty()) {
            return emptyList()
        }

        for (token in tokens) {
            stack.last().handleToken(token, stack, blocks)
        }

        while (stack.isNotEmpty()) {
            stack.removeLast().onEof(blocks)
        }

        return blocks
    }
}
