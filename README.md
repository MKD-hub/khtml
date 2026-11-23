# khtml

An opinionated, lightweight Kotlin library for HTML tokenization and article AST extraction.

`khtml` extracts clean article content from HTML markup (for RSS readers, read-it-later tools, and content scrapers) without heavyweight browser engines or full DOM trees.

## Features

- **HTML Tokenizer**: Scans tags, attributes, and text nodes with source position tracking (`SourcePosition`).
- **AST Builder**: Converts HTML token streams into structured article models (`ArticleBlock`) and inline spans (`InlineSpan`).
- **Lightweight**: Minimal dependencies, fast execution, and pure JVM / Kotlin design.
- **Continuous Integration**: GitHub Actions test runner on every push and pull request.

## Requirements

- JDK 17 or higher
- Gradle 8+ (or included `./gradlew`)

## Quick Start

```kotlin
import org.khtml.Tokenizer
import org.khtml.AstBuilder

fun main() {
    val html = "<p>Hello <b>world</b>!</p>"

    // 1. Tokenize HTML
    val tokenizer = Tokenizer()
    val tokens = tokenizer.tokenize(html)

    // 2. Build Article AST (optionally inject logger for unhandled tokens)
    val builder = AstBuilder(logger = { scope, message ->
        println("[$scope] $message")
    })
    val blocks = builder.build(tokens)

    println(blocks)
}
```

## Data Model

### Article Blocks
- `ArticleBlock.Paragraph(spans)` - Text paragraphs with inline styling and links.
- `ArticleBlock.Heading(level, spans, id)` - Headings `h1` through `h6`.
- `ArticleBlock.Blockquote(spans)` - Quotations and callouts.
- `ArticleBlock.CodeBlock(code, language)` - Preformatted code blocks (`<pre>`, `<pre><code>`) with extracted language metadata.
- `ArticleBlock.HtmlList(items, isOrdered)` - Ordered (`<ol>`) and unordered (`<ul>`) lists.
- `ArticleBlock.ListItem(spans, index, children)` - List items supporting recursive child blocks (nested sub-lists, blockquotes, code blocks, and images).
- `ArticleBlock.ImageBlock(src, alt)` - Images (`<img>`) with source and alternative text.
- `ArticleBlock.Divider` - Horizontal rule divider (`<hr>`).
- `ArticleBlock.LineBreak` - Standalone line break (`<br>`).

### Inline Spans
- `InlineSpan(text, styles, linkUrl)` - Formatted text runs supporting combined styles:
  - `InlineStyle.BOLD` (`<b>`, `<strong>`)
  - `InlineStyle.ITALIC` (`<i>`, `<em>`)
  - `InlineStyle.CODE` (`<code>`)
  - `InlineStyle.HIGHLIGHT` (`<mark>`)
  - Hyperlinks (`<a href="...">`)

## Roadmap & Considerations

- **Tables**: Tables (`<table>`, `<tr>`, `<td>`, `<th>`) are complex to render on mobile screens and are currently under active design for a dedicated upcoming milestone.
- **Extended Lists**: Custom / non-standard `<list>` tags and description lists (`<dl>`, `<dt>`, `<dd>`) are being considered for future parser revisions.

## Build and Test

Run all unit tests:

```bash
./gradlew test
```

Build the library JAR:

```bash
./gradlew build
```

## License

This project is licensed under the [MIT License](LICENSE).
