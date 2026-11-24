package org.khtml

/**
 * Utility for decoding HTML character references into standard Unicode characters.
 *
 * Supports named typography entities (e.g. `&rsquo;`, `&mdash;`), standard XML escapes (`&amp;`, `&lt;`),
 * and numeric code point references in decimal (`&#8217;`) and hex (`&#x2019;`).
 */
object HtmlEntities {
    private val NAMED_ENTITIES: Map<String, String> = mapOf(
        // Core HTML
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",

        // Typography & Punctuation
        "rsquo" to "’",
        "lsquo" to "‘",
        "rdquo" to "”",
        "ldquo" to "“",
        "sbquo" to "‚",
        "bdquo" to "„",
        "mdash" to "—",
        "ndash" to "–",
        "hellip" to "…",
        "prime" to "′",
        "Prime" to "″",
        "bull" to "•",
        "middot" to "·",
        "para" to "¶",
        "sect" to "§",
        "dagger" to "†",
        "Dagger" to "‡",

        // Spaces
        "nbsp" to "\u00A0",
        "thinsp" to "\u2009",
        "ensp" to "\u2002",
        "emsp" to "\u2003",
        "zwnj" to "\u200C",
        "zwj" to "\u200D",

        // Symbols & Currency
        "copy" to "©",
        "reg" to "®",
        "trade" to "™",
        "deg" to "°",
        "cent" to "¢",
        "pound" to "£",
        "yen" to "¥",
        "euro" to "€",

        // Math & Logic
        "plusmn" to "±",
        "times" to "×",
        "divide" to "÷",
        "ne" to "≠",
        "le" to "≤",
        "ge" to "≥",
        "infin" to "∞",
        "approx" to "≈",
        "frac14" to "¼",
        "frac12" to "½",
        "frac34" to "¾",

        // Common accented characters
        "aacute" to "á", "Aacute" to "Á",
        "eacute" to "é", "Eacute" to "É",
        "iacute" to "í", "Iacute" to "Í",
        "oacute" to "ó", "Oacute" to "Ó",
        "uacute" to "ú", "Uacute" to "Ú",
        "agrave" to "à", "Agrave" to "À",
        "egrave" to "è", "Egrave" to "È",
        "igrave" to "ì", "Igrave" to "Ì",
        "ograve" to "ò", "Ograve" to "Ò",
        "ugrave" to "ù", "Ugrave" to "Ù",
        "auml" to "ä", "Auml" to "Ä",
        "euml" to "ë", "Euml" to "Ë",
        "iuml" to "ï", "Iuml" to "Ï",
        "ouml" to "ö", "Ouml" to "Ö",
        "uuml" to "ü", "Uuml" to "Ü",
        "ccedil" to "ç", "Ccedil" to "Ç",
        "ntilde" to "ñ", "Ntilde" to "Ñ"
    )

    /**
     * Decodes all recognized HTML character entities in [input] to Unicode.
     *
     * If [input] contains no `&` character, it is returned immediately without allocations.
     * Unrecognized or malformed entities are preserved verbatim.
     *
     * @param input Raw text potentially containing HTML entities.
     * @return String with all recognized entities replaced.
     */
    fun decode(input: String): String {
        if (!input.contains('&')) return input

        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '&') {
                val semi = input.indexOf(';', startIndex = i + 1)
                // Look for entity: must terminate with ';' within 12 characters, and no whitespace or '<'
                if (semi != -1 && semi - i in 2..12) {
                    val candidate = input.substring(i + 1, semi)
                    if (candidate.all { !it.isWhitespace() && it != '<' && it != '&' }) {
                        val replacement = decodeEntity(candidate)
                        if (replacement != null) {
                            sb.append(replacement)
                            i = semi + 1
                            continue
                        }
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun decodeEntity(nameOrCode: String): String? {
        if (nameOrCode.startsWith("#")) {
            val isHex = nameOrCode.length > 2 && (nameOrCode[1] == 'x' || nameOrCode[1] == 'X')
            val codeStr = if (isHex) nameOrCode.substring(2) else nameOrCode.substring(1)
            val radix = if (isHex) 16 else 10
            val codePoint = codeStr.toIntOrNull(radix) ?: return null
            if (codePoint in 0..0x10FFFF) {
                return try {
                    String(Character.toChars(codePoint))
                } catch (e: Exception) {
                    null
                }
            }
            return null
        }
        return NAMED_ENTITIES[nameOrCode]
    }
}
