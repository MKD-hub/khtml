package org.khtml

/**
 * Logger interface for diagnostic reporting during AST construction.
 */
fun interface AstLogger {
    /**
     * Records a diagnostic message from a specific builder [scope].
     */
    fun log(scope: String, message: String)
}

/**
 * Builds a structured semantic document of [ArticleBlock]s from a stream of [HtmlToken]s.
 *
 * Uses an [ArrayDeque] state machine of contexts to manage nesting, unclosed tags,
 * and orphan inline text without constructing a full browser DOM.
 *
 * @param logger Optional diagnostic logger for tracking unhandled tags.
 */
class AstBuilder(
    private val logger: AstLogger? = null
) {
    /**
     * Converts a flat sequence of [tokens] into a list of top-level [ArticleBlock]s.
     *
     * Empty or whitespace-only blocks are automatically discarded.
     *
     * @param tokens The stream of tokens produced by [Tokenizer.tokenize].
     * @return A list of structured [ArticleBlock]s ready for rendering.
     */
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
