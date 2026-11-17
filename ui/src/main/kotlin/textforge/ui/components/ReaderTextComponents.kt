package textforge.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import textforge.parser.NovelBlock

@Composable
fun HeadingComponent(
    title: String,
    level: Int
) {

    val fontSize = when (level) {
        1 -> 28.sp
        2 -> 24.sp
        3 -> 21.sp
        else -> 19.sp
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        color = Color(0xFFDCEEFF),
        shape = RoundedCornerShape(10.dp)
    ) {

        Text(
            text = title,
            modifier = Modifier.padding(14.dp),
            color = Color(0xFF174B80),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            lineHeight = fontSize * 1.4
        )
    }
}

@Composable
fun HighlightedParagraph(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    onTextPositioned: (LayoutCoordinates) -> Unit = {}
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        color = Color(0xFF818890),
        contentColor = Color(0xFF171A1D),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            Color(0xFF626B73)
        )
    ) {
        Text(
            text = text,
            modifier = Modifier
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .onGloballyPositioned(onTextPositioned),
            color = Color(0xFF171A1D),
            fontSize = 17.sp,
            lineHeight = 17.sp,
            onTextLayout = onTextLayout
        )
    }
}

/** One colour for every inline highlight (reader + renderer). */
val InlineHighlightColor = Color(0x7FAC97FF)

/**
 * Single place that styles a segment, used by both pagination and
 * NovelTextRenderer so measured text and drawn text cannot drift apart.
 */
fun AnnotatedString.Builder.appendSegment(
    text: String,
    italic: Boolean,
    highlighted: Boolean,
    fullHighlight: Boolean
) {
    pushStyle(
        SpanStyle(
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            background = if (highlighted && !fullHighlight) {
                InlineHighlightColor
            } else {
                Color.Transparent
            }
        )
    )
    append(text)
    pop()
}

/**
 * Plain text; highlighted spans use the span background only
 * (no rounded box, no outline). Pass the same style used for measuring.
 */
@Composable
fun InlineHighlight(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    onTextPositioned: (LayoutCoordinates) -> Unit = {}
) {
    Text(
        text = text,
        style = style,
        color = Color(0xFF171A1D),
        modifier = modifier.onGloballyPositioned(onTextPositioned),
        onTextLayout = onTextLayout
    )
}

@Composable
fun NovelTextRenderer(
    block: NovelBlock,
    modifier: Modifier = Modifier
) {
    when (block) {
        is NovelBlock.Heading -> {
            HeadingComponent(
                title = block.title,
                level = block.level
            )
        }

        is NovelBlock.Paragraph -> {
            // Same styling function the paginator uses.
            val annotatedText = buildAnnotatedString {
                block.segments.forEach { segment ->
                    appendSegment(
                        text = segment.text,
                        italic = segment.italic,
                        highlighted = segment.highlighted,
                        fullHighlight = block.fullHighlight
                    )
                }
            }

            if (block.fullHighlight) {
                HighlightedParagraph(
                    text = annotatedText,
                    modifier = modifier
                )
            } else {
                InlineHighlight(
                    text = annotatedText,
                    modifier = modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}
