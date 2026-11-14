package textforge.ui.components

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun DictionaryHtmlView(
    html: String,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false
) {
    // Colors
    val bg = if (isDarkTheme) "#121214" else "#FFFFFF"
    val text = if (isDarkTheme) "#E8E8EA" else "#1F1F22"
    val muted = if (isDarkTheme) "#A8A8AD" else "#5F5F66"
    val quoteBg = if (isDarkTheme) "#1E1E22" else "#F4F4F5"
    val quoteBorder = if (isDarkTheme) "#5A5A63" else "#C9C9CF"
    val link = if (isDarkTheme) "#7AB8FF" else "#2563EB"
    val line = if (isDarkTheme) "#2E2E33" else "#E5E5E8"

    val document = remember(html, bg, text, muted, quoteBg, quoteBorder, link, line) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <style>
            body {
              margin: 0;
              padding: 10px 12px;
              background: $bg;
              color: $text;
              font-family: sans-serif;
              font-size: 14.5px;
              line-height: 1.45;
              overflow-wrap: anywhere;
            }

            h1, h2, h3 { margin: 10px 0 4px; line-height: 1.25; }
            h1 { font-size: 19px; }
            h2 { font-size: 17px; }
            h3 { font-size: 15.5px; }
            h1:first-child, h2:first-child, h3:first-child { margin-top: 0; }

            p { margin: 0 0 6px; }

            ul, ol { margin: 0 0 6px; padding-left: 18px; }
            li { margin: 2px 0; }
            li::marker { color: $muted; }

            blockquote {
              margin: 8px 0;
              padding: 7px 10px;
              border-left: 3px solid $quoteBorder;
              background: $quoteBg;
              border-radius: 0 8px 8px 0;
            }
            blockquote p:last-child { margin-bottom: 0; }

            a { color: $link; }

            img { max-width: 100%; height: auto; display: block; margin: 6px 0; }

            table { width: 100%; border-collapse: collapse; margin: 6px 0 8px; font-size: 13.5px; }
            th, td { text-align: left; padding: 6px 8px; border-bottom: 1px solid $line; }
            th { background: $quoteBg; }
            tr:last-child td { border-bottom: none; }

            code { font-size: 13px; background: $quoteBg; padding: 1px 5px; border-radius: 5px; }
            pre { background: $quoteBg; padding: 8px 10px; border-radius: 8px; overflow-x: auto; font-size: 13px; line-height: 1.4; }
            pre code { background: transparent; padding: 0; }

            hr { border: none; border-top: 1px solid $line; margin: 10px 0; }
          </style>
        </head>
        <body>$html</body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false

                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = false
                overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
                isNestedScrollingEnabled = true

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = true
                }
            }
        },
        update = { webView ->
            if (webView.tag != document.hashCode()) {
                webView.tag = document.hashCode()
                webView.loadDataWithBaseURL(
                    "https://textforge.invalid/",
                    document,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        }
    )
}
