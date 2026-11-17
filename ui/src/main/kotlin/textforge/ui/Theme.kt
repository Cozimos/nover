package textforge.ui
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

val AtkinsonFont = FontFamily(
    Font(R.font.atkinsonhyperlegible_regular, FontWeight.Normal),
    Font(R.font.atkinsonhyperlegible_bold, FontWeight.Bold),
    Font(R.font.atkinsonhyperlegible_italic, FontWeight.Normal, androidx.compose.ui.text.font.FontStyle.Italic),
    Font(R.font.atkinsonhyperlegible_bolditalic, FontWeight.Bold, androidx.compose.ui.text.font.FontStyle.Italic)
)

val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = AtkinsonFont),
        displayMedium = displayMedium.copy(fontFamily = AtkinsonFont),
        displaySmall = displaySmall.copy(fontFamily = AtkinsonFont),
        headlineLarge = headlineLarge.copy(fontFamily = AtkinsonFont),
        headlineMedium = headlineMedium.copy(fontFamily = AtkinsonFont),
        headlineSmall = headlineSmall.copy(fontFamily = AtkinsonFont),
        titleLarge = titleLarge.copy(fontFamily = AtkinsonFont),
        titleMedium = titleMedium.copy(fontFamily = AtkinsonFont),
        titleSmall = titleSmall.copy(fontFamily = AtkinsonFont),
        bodyLarge = bodyLarge.copy(fontFamily = AtkinsonFont),
        bodyMedium = bodyMedium.copy(fontFamily = AtkinsonFont),
        bodySmall = bodySmall.copy(fontFamily = AtkinsonFont),
        labelLarge = labelLarge.copy(fontFamily = AtkinsonFont),
        labelMedium = labelMedium.copy(fontFamily = AtkinsonFont),
        labelSmall = labelSmall.copy(fontFamily = AtkinsonFont)
    )
}


object NovelColors {
    val Background = Color(0xFFA39D8C)
    val MenuBackground = Color(0xFF8F8F7F)
}
