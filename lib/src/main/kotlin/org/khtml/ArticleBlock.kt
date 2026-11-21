package org.khtml

sealed interface ArticleBlock {
    data class Heading(
        val level: Int,
        val spans: List<InlineSpan>,
        val id: String? = null
    ) : ArticleBlock

    data class Paragraph(val spans: List<InlineSpan>) : ArticleBlock
    data class Blockquote(val spans: List<InlineSpan>) : ArticleBlock
    data class CodeBlock(val code: String, val language: String? = null) : ArticleBlock
    data class ListItem(val spans: List<InlineSpan>, val index: Int) : ArticleBlock
    data class HtmlList(val items: List<ListItem>, val isOrdered: Boolean) : ArticleBlock
    data class ImageBlock(val src: String, val alt: String? = null) : ArticleBlock
    data object Divider : ArticleBlock
    data object LineBreak : ArticleBlock
}

data class InlineSpan(
    val text: String,
    val styles: Set<InlineStyle> = emptySet(),
    val linkUrl: String? = null
)
