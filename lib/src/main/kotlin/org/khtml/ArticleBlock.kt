package org.khtml

sealed interface ArticleBlock {
    data class Heading(val level: Int, val text: String) : ArticleBlock
    data class Paragraph(val spans: List<InlineSpan>) : ArticleBlock
    data class Blockquote(val spans: List<InlineSpan>) : ArticleBlock
    data class CodeBlock(val code: String, val language: String? = null) : ArticleBlock
    data class ListItem(val spans: List<InlineSpan>, val isOrdered: Boolean, val index: Int) : ArticleBlock
    data class ImageBlock(val src: String, val alt: String? = null) : ArticleBlock
    data object Divider : ArticleBlock
}

sealed interface InlineSpan {
    data class Text(val content: String) : InlineSpan
    data class Bold(val content: String) : InlineSpan
    data class Italic(val content: String) : InlineSpan
    data class Code(val content: String) : InlineSpan
    data class Link(val text: String, val url: String) : InlineSpan
}
