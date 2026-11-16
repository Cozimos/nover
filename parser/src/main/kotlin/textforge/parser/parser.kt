package textforge.parser

data class NovelDocument(
    val blocks: List<NovelBlock>,
    val toc: List<TocEntry>
)

sealed class NovelBlock {

    data class Heading(
        val level: Int,
        val title: String
    ) : NovelBlock()

    data class Paragraph(
        val segments: List<Segment>,
        val fullHighlight: Boolean = false
    ) : NovelBlock()
}

data class TocEntry(
    val title: String,
    val blockIndex: Int
)

data class Segment(
    val text: String,
    val highlighted: Boolean = false,
    val italic: Boolean = false
)

object NovelMarkdownParser {

    private val HEADING = Regex("^(#{1,6})\\s+(.+)$")

    fun parse(markdown: String): NovelDocument {

        val blocks = mutableListOf<NovelBlock>()
        val toc = mutableListOf<TocEntry>()

        val lines = markdown
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n")

        var i = 0

        while (i < lines.size) {

            val trimmed = lines[i].trim()

            // Skip empty lines
            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // Heading
            val heading = HEADING.matchEntire(trimmed)

            if (heading != null) {
                val level = heading.groupValues[1].length
                val title = heading.groupValues[2].trim()
                val index = blocks.size

                blocks.add(NovelBlock.Heading(level = level, title = title))
                toc.add(TocEntry(title = title, blockIndex = index))

                i++
                continue
            }

            // Explicit multiline card: "[" alone on a line ... "]" alone on a line
            if (trimmed == "[") {

                i++

                val content = mutableListOf<String>()

                while (i < lines.size && lines[i].trim() != "]") {
                    content.add(lines[i])
                    i++
                }

                // Consume closing bracket
                if (i < lines.size && lines[i].trim() == "]") {
                    i++
                }

                blocks.add(
                    NovelBlock.Paragraph(
                        segments = parseSegments(
                            content.joinToString("\n"),
                            highlighted = true
                        ),
                        fullHighlight = true
                    )
                )

                continue
            }

            // Normal paragraph (until blank line, heading or lone "[")
            val paragraph = mutableListOf<String>()

            while (i < lines.size) {
                val current = lines[i]

                if (current.isBlank()) break

                val currentTrimmed = current.trim()

                if (currentTrimmed == "[" || HEADING.matches(currentTrimmed)) {
                    break
                }

                paragraph.add(current)
                i++
            }

            if (paragraph.isNotEmpty()) {
                val joined = paragraph.joinToString("\n").trim()

                if (isWholeBracket(joined)) {
                    // The whole paragraph is one [ ... ] group -> card
                    blocks.add(
                        NovelBlock.Paragraph(
                            segments = parseSegments(
                                joined.substring(1, joined.length - 1).trim(),
                                highlighted = true
                            ),
                            fullHighlight = true
                        )
                    )
                } else {
                    // Brackets inside a sentence -> inline highlight only
                    blocks.add(
                        NovelBlock.Paragraph(
                            segments = parseSegments(joined),
                            fullHighlight = false
                        )
                    )
                }
            }
        }

        return NovelDocument(
            blocks = blocks,
            toc = toc
        )
    }

    /** True only when one single [ ... ] group spans the entire paragraph. */
    private fun isWholeBracket(s: String): Boolean =
        s.length > 2 &&
            s.first() == '[' &&
            s.last() == ']' &&
            s.indexOf(']') == s.length - 1

    private fun findItalicEnd(source: String, open: Int): Int {
        var j = open + 2

        while (j < source.length) {
            if (source[j] == '\'') {
                val before = source[j - 1]
                val after = source.getOrNull(j + 1)

                if (!before.isWhitespace() &&
                    (after == null || !after.isLetterOrDigit())
                ) {
                    return j
                }
            }
            j++
        }

        return -1
    }

    private fun parseSegments(
        text: String,
        highlighted: Boolean = false
    ): List<Segment> {

        val result = mutableListOf<Segment>()
        val buf = StringBuilder()
        var curHighlight = highlighted
        var curItalic = false

        fun flush() {
            if (buf.isNotEmpty()) {
                result.add(
                    Segment(
                        buf.toString(),
                        curHighlight,
                        curItalic
                    )
                )
                buf.setLength(0)
            }
        }

        fun add(value: Char, highlight: Boolean, italic: Boolean) {
            if (highlight != curHighlight || italic != curItalic) {
                flush()
                curHighlight = highlight
                curItalic = italic
            }
            buf.append(value)
        }

        fun parseRange(
            source: String,
            highlight: Boolean,
            italic: Boolean
        ) {
            var i = 0

            while (i < source.length) {

                if (source[i] == '[') {
                    val end = source.indexOf(']', i + 1)

                    if (end != -1) {
                        parseRange(
                            source.substring(i + 1, end),
                            highlight = true,
                            italic = italic
                        )
                        i = end + 1
                        continue
                    }
                }

                if (source[i] == '\'') {
                    val prev = if (i > 0) source[i - 1] else null
                    val next = source.getOrNull(i + 1)

                    val canOpen =
                        (prev == null || !prev.isLetterOrDigit()) &&
                            next != null &&
                            !next.isWhitespace()

                    if (canOpen) {
                        val end = findItalicEnd(source, i)

                        if (end != -1) {
                            parseRange(
                                source.substring(i + 1, end),
                                highlight = highlight,
                                italic = true
                            )
                            i = end + 1
                            continue
                        }
                    }
                }

                add(source[i], highlight, italic)
                i++
            }
        }

        parseRange(
            text,
            highlight = highlighted,
            italic = false
        )

        flush()

        return result
    }
}
