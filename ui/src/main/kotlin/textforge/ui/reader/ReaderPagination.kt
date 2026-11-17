package textforge.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import textforge.ui.components.appendSegment

// ---------------------------------------------------------------------------
// Tuning knobs (measurement only, they do not change how anything looks)
// ---------------------------------------------------------------------------

/** Free space kept at the bottom of every page. Raise if only the last line clips. */
internal val ReaderBottomSafety = 16.dp

/** Added to EVERY measured line. Raise (1.dp steps) if clipping grows toward the bottom. */
internal val ReaderLineExtra = 0.dp

/** Taken off the measuring width, so text wraps earlier. Raise if drawn text wraps more than measured. */
internal val ReaderWidthTrim = 0.dp

/** Space a card adds around its text (top + bottom) on EVERY page it appears on. */
internal val ReaderCardSpaceV = 16.dp

/** Width a card takes away from its text (left + right). */
internal val ReaderCardSpaceH = 0.dp


// ---------------------------------------------------------------------------
// Plain chapter blocks supplied by the Model after parsing on the IO dispatcher.
// ---------------------------------------------------------------------------

data class Segment(
    val text: String,
    val italic: Boolean,
    val highlighted: Boolean
)

data class ChapterBlock(
    val heading: Boolean,
    val title: String,
    val fullHighlight: Boolean,
    val segments: List<Segment>
)

// ---------------------------------------------------------------------------
// Page model and pagination entry point
// ---------------------------------------------------------------------------

internal data class ReaderPageBlock(
    val text: AnnotatedString,
    val heading: Boolean,
    val fullHighlight: Boolean
)

/** All geometry and typography values are grouped into one layout key. */
internal data class ReaderLayoutKey(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val fontScale: Float,
    val style: TextStyle
)

@Composable
internal fun rememberReaderPages(
    chapter: Int,
    blocks: List<ChapterBlock>,
    key: ReaderLayoutKey,
    density: Density
): List<List<ReaderPageBlock>> {
    val measurer = rememberTextMeasurer()
    var pages by remember(chapter, blocks, key) {
        mutableStateOf<List<List<ReaderPageBlock>>>(emptyList())
    }

    // Parsing is already complete in the Model; only CPU pagination happens here.
    LaunchedEffect(chapter, blocks, key) {
        pages = withContext(Dispatchers.Default) {
            calculateReaderPages(blocks, key, density, measurer)
        }
    }
    return pages
}

// ---------------------------------------------------------------------------
// Pagination
// ---------------------------------------------------------------------------

internal fun calculateReaderPages(
    blocks: List<ChapterBlock>,
    key: ReaderLayoutKey,
    density: Density,
    measurer: TextMeasurer
): List<List<ReaderPageBlock>> {

    val pages = mutableListOf<List<ReaderPageBlock>>()
    var page = mutableListOf<ReaderPageBlock>()
    var used = 0

    val widthPx = (key.widthPx - with(density) { ReaderWidthTrim.roundToPx() })
        .coerceAtLeast(1)
    val lineExtraPx = with(density) { ReaderLineExtra.roundToPx() }
    val pageHeight = (key.heightPx - with(density) { ReaderBottomSafety.roundToPx() }).coerceAtLeast(1)
    val paragraphPad = with(density) { 8.dp.roundToPx() } * 2
    val headingPad = with(density) { 10.dp.roundToPx() } * 2
    val cardPad = with(density) { ReaderCardSpaceV.roundToPx() }
    val cardWidth = (widthPx - with(density) { ReaderCardSpaceH.roundToPx() })
        .coerceAtLeast(1)

    fun finishPage() {
        if (page.isNotEmpty()) {
            pages.add(page)
            page = mutableListOf()
            used = 0
        }
    }

    for (block in blocks) {
        val heading = block.heading
        val fullHighlight = block.fullHighlight

        val text = if (heading) {
            AnnotatedString(block.title)
        } else {
            AnnotatedString.Builder().apply {
                for (segment in block.segments) {
                    appendSegment(
                        text = segment.text,
                        italic = segment.italic,
                        highlighted = segment.highlighted,
                        fullHighlight = fullHighlight
                    )
                }
            }.toAnnotatedString()
        }

        if (text.isEmpty()) continue

        // A card pays its own space on every page it appears on:
        // each fragment of a split card gets it again on the next page.
        val pad = when {
            heading -> headingPad
            fullHighlight -> cardPad
            else -> paragraphPad
        }
        val blockWidth = if (fullHighlight) cardWidth else widthPx

        // Measure the whole block once, then cut it on line boundaries.
        val layout = measurer.measure(
            text = text,
            style = key.style,
            constraints = Constraints(maxWidth = blockWidth)
        )
        val lineCount = layout.lineCount

        // The text that will really be drawn for lines [from..to].
        // Trailing hard newlines are cut: drawn alone they add a blank line.
        fun slice(from: Int, to: Int): AnnotatedString {
            val start = layout.getLineStart(from)
            var end = layout.getLineEnd(to)
            while (end > start && text[end - 1] == '\n') end--
            return text.subSequence(start, end)
        }

        // Exact height Text() will take for that fragment.
        fun heightOf(fragment: AnnotatedString): Int {
            val r = measurer.measure(
                text = fragment,
                style = key.style,
                constraints = Constraints(maxWidth = blockWidth)
            )
            return r.size.height + r.lineCount * lineExtraPx
        }

        var line = 0
        while (line < lineCount) {
            val room = pageHeight - used - pad
            val top = layout.getLineTop(line)

            // Estimate from the whole-block layout...
            var last = line - 1
            while (last + 1 < lineCount &&
                layout.getLineBottom(last + 1) - top +
                (last + 2 - line) * lineExtraPx <= room.toFloat()
            ) last++

            // ...then verify with the exact fragment and shrink until it fits.
            var fragment = AnnotatedString("")
            var h = 0
            while (last >= line) {
                fragment = slice(line, last)
                h = heightOf(fragment)
                if (h <= room) break
                last--
            }

            if (last < line) {
                if (page.isNotEmpty()) {
                    finishPage()
                    continue
                }
                last = line // empty page, still too small: force one line
                fragment = slice(line, last)
                h = heightOf(fragment)
            }

            page.add(
                ReaderPageBlock(
                    text = fragment,
                    heading = heading,
                    fullHighlight = fullHighlight
                )
            )

            used += h + pad
            line = last + 1

            if (line < lineCount) finishPage()
        }
    }

    finishPage()
    return pages
}
