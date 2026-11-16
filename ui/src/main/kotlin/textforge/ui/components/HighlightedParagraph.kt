package textforge.ui.components

import androidx.compose.foundation.BorderStroke
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
