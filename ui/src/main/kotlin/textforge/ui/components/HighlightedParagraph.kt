package textforge.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

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
            .padding(vertical = 8.dp),
        // Card fill, not text highlight: fills full width even on multiline
        color = Color(0xFF009494),
        shape = RoundedCornerShape(3.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier
                .padding(4.dp)
                .onGloballyPositioned(onTextPositioned),
            // White for contrast on #009494, old #252525 was unreadable on cyan
            color = Color.White,
            fontSize = 17.sp,
            lineHeight = 18.sp,
            onTextLayout = onTextLayout
        )
    }
}
