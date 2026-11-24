package org.khtml

/**
 * Represents a high-level semantic block extracted from an HTML document.
 *
 * Blocks are top-level structural elements (paragraphs, headings, lists, code blocks)
 * designed to be directly rendered into UI containers such as Compose `LazyColumn`.
 */
sealed interface ArticleBlock {
    /**
     * A heading block (e.g. `<h1>` through `<h6>`).
     *
     * @property level The heading level from 1 (`<h1>`) to 6 (`<h6>`).
     * @property spans The styled inline text segments inside this heading.
     * @property id Optional HTML element id or anchor for deep linking and scrolling.
     */
    data class Heading(
        val level: Int,
        val spans: List<InlineSpan>,
        val id: String? = null
    ) : ArticleBlock

    /**
     * A standard text paragraph (`<p>`).
     *
     * @property spans The styled inline text segments inside this paragraph.
     */
    data class Paragraph(val spans: List<InlineSpan>) : ArticleBlock

    /**
     * A blockquote container (`<blockquote>`).
     *
     * @property spans The styled inline text segments inside this quotation.
     */
    data class Blockquote(val spans: List<InlineSpan>) : ArticleBlock

    /**
     * A preformatted monospace code block (`<pre><code>`).
     *
     * @property code The raw, unformatted code text.
     * @property language The programming language extracted from CSS classes (e.g. `language-kotlin`), or null.
     */
    data class CodeBlock(val code: String, val language: String? = null) : ArticleBlock

    /**
     * A single item within an ordered or unordered list (`<li>`).
     *
     * @property spans The styled inline text segments of this item.
     * @property index The 0-based index of this item in the parent list.
     * @property children Nested sub-blocks contained within this list item (e.g. nested lists or sub-paragraphs).
     */
    data class ListItem(
        val spans: List<InlineSpan>,
        val index: Int,
        val children: List<ArticleBlock> = emptyList()
    ) : ArticleBlock

    /**
     * An ordered (`<ol>`) or unordered (`<ul>`) list container.
     *
     * @property items The list of [ListItem]s in this list.
     * @property isOrdered `true` for numbered lists (`<ol>`), `false` for bulleted lists (`<ul>`).
     */
    data class HtmlList(val items: List<ListItem>, val isOrdered: Boolean) : ArticleBlock

    /**
     * An embedded image element (`<img>`).
     *
     * @property src The image URL or data URI.
     * @property alt Optional alternative text description.
     */
    data class ImageBlock(val src: String, val alt: String? = null) : ArticleBlock

    /**
     * A horizontal divider or thematic break (`<hr>`).
     */
    data object Divider : ArticleBlock

    /**
     * An explicit structural line break (`<br>`).
     */
    data object LineBreak : ArticleBlock
}

/**
 * A styled segment of inline text within a block.
 *
 * @property text The raw text content of this span with HTML entities already decoded.
 * @property styles The set of [InlineStyle]s applied to this span (e.g. [InlineStyle.BOLD], [InlineStyle.CODE]).
 * @property linkUrl The destination URL if this span is enclosed in an `<a>` anchor, or `null`.
 */
data class InlineSpan(
    val text: String,
    val styles: Set<InlineStyle> = emptySet(),
    val linkUrl: String? = null
)
