package textforge.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
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
