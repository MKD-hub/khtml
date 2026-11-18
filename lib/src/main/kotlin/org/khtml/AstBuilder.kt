package org.khtml

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
