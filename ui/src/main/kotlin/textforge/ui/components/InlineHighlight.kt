package textforge.ui.components

import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.text.font.FontStyle

/** One colour for every inline highlight (reader + renderer). */
val InlineHighlightColor = Color(0xFFC28487)

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
