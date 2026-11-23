package org.khtml

fun interface AstLogger {
    fun log(scope: String, message: String)
}

class AstBuilder(
    private val logger: AstLogger? = null
) {
    fun build(tokens: List<HtmlToken>): List<ArticleBlock> {

        val blocks = mutableListOf<ArticleBlock>()
        val stack = ArrayDeque<BuilderContext>()

        stack.addLast(RootContext())

        if (tokens.isEmpty()) {
            return emptyList()
        }

        for (token in tokens) {
            stack.last().handleToken(token, stack, blocks, logger)
        }

        while (stack.isNotEmpty()) {
            stack.removeLast().onEof(blocks)
        }

        return blocks
    }
}
