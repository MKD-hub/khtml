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

    // 2. Build Article AST
    val builder = AstBuilder()
    val blocks = builder.build(tokens)

    println(blocks)
}
```

## Data Model

### Article Blocks
- `ArticleBlock.Paragraph(spans)`
- `ArticleBlock.Heading(level, text)`
- `ArticleBlock.Blockquote(spans)`
- `ArticleBlock.CodeBlock(code, language)`
- `ArticleBlock.ListItem(spans, isOrdered, index)`
- `ArticleBlock.ImageBlock(src, alt)`
- `ArticleBlock.Divider`

### Inline Spans
- `InlineSpan.Text(content)`
- `InlineSpan.Bold(content)`
- `InlineSpan.Italic(content)`
- `InlineSpan.Code(content)`
- `InlineSpan.Link(text, url)`

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
