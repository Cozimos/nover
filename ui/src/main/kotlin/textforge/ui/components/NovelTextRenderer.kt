package textforge.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import textforge.parser.NovelBlock

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
    val annotatedText = buildAnnotatedString {
        block.segments.forEach { segment ->
            val start = length
            append(segment.text)
            val end = length

            if (segment.italic) {
                addStyle(
                    SpanStyle(fontStyle = FontStyle.Italic),
                    start,
                    end
                )
            }
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
            modifier = modifier.padding(vertical = 9.dp)
        )
    }
}
    }
}
