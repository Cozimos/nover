package textforge.ui.dictionary

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.sp

/**
 * Converts raw StarDict HTML into compact, clean text that is easier to scan.
 *
 * StarDict dictionaries commonly contain presentation tags that are noisy in
 * a reader. This formatter keeps their useful text while normalizing the
 * headword, examples, references, and nested quotations.
 */
fun formatStarDictFastRead(raw: String, hideHeadword: Boolean = false): String {
    var html = raw

    // Remove color/style wrappers while preserving their textual contents.
    html = html.replace(
        Regex("</?(font|span|c)\\b[^>]*>", RegexOption.IGNORE_CASE),
        ""
    )

    // Normalize the dictionary headword.
    html = if (hideHeadword) {
        html.replace(Regex("<k>.*?</k>\\s*", RegexOption.IGNORE_CASE), "")
    } else {
        html.replace(
            Regex("<k>(.*?)</k>\\s*", RegexOption.IGNORE_CASE),
            "<b>$1</b><br>"
        )
    }

    // Normalize common StarDict-specific tags.
    html = html
        .replace(
            Regex("<kref[^>]*>(.*?)</kref>", RegexOption.IGNORE_CASE),
            "<b>$1</b>"
        )
        .replace(
            Regex("<ex[^>]*>(.*?)</ex>", RegexOption.IGNORE_CASE),
            "<i>$1</i>"
        )
        .replace(
            Regex("</?(dtrn|def|co|abr)\\b[^>]*>", RegexOption.IGNORE_CASE),
            ""
        )

    // Make nested blockquotes readable without preserving excessive markup.
    html = html
        .replace(
            Regex("<blockquote>\\s*<blockquote>\\s*", RegexOption.IGNORE_CASE),
            "&nbsp;&nbsp;"
        )
        .replace(
            Regex("\\s*</blockquote>\\s*</blockquote>\\s*", RegexOption.IGNORE_CASE),
            "<br>"
        )
        .replace(
            Regex("<blockquote>\\s*", RegexOption.IGNORE_CASE),
            "<br><b>"
        )
        .replace(
            Regex("\\s*</blockquote>\\s*", RegexOption.IGNORE_CASE),
            "</b><br>"
        )

    // Remove redundant leading, trailing, and repeated blank lines.
    return html
        .replace(Regex("^(<br>\\s*)+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("(<br>\\s*){3,}", RegexOption.IGNORE_CASE), "<br><br>")
        .replace(Regex("(<br>\\s*)+$", RegexOption.IGNORE_CASE), "")
        .trim()
}

/**
 * Displays one StarDict definition using Compose text.
 *
 * SelectionContainer intentionally allows users to select and copy dictionary
 * text without making the dictionary content itself a WebView.
 */
@Composable
fun StarDictEntryView(
    rawEntry: String,
    modifier: Modifier = Modifier,
    hideHeadword: Boolean = false,
    style: TextStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
) {
    val formattedText = remember(rawEntry, hideHeadword) {
        AnnotatedString.fromHtml(formatStarDictFastRead(rawEntry, hideHeadword))
    }

    SelectionContainer {
        Text(
            text = formattedText,
            style = style,
            modifier = modifier
        )
    }
}
