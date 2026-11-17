package textforge.ui.reader

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import textforge.ui.NovelColors

@Composable
fun ReaderScreen(
    novelId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity

    // Immersive reader: hide system bars.
    DisposableEffect(activity, view) {
        val window = activity?.window

        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)

            val controller = WindowInsetsControllerCompat(window, view)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            if (window != null) {
                val controller = WindowInsetsControllerCompat(window, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
    }

    val viewModel: ReaderViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = novelId,
        factory = ReaderViewModel.Factory(context)
    )
    val state by viewModel.state.collectAsState()
    val currentChapter = state.chapter
    val currentPage = state.page
    val lastChapter = state.lastChapter
    val send: (ReaderIntent) -> Unit = viewModel::dispatch

    LaunchedEffect(novelId) {
        send(ReaderIntent.Open(novelId))
    }

    val dictionaryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            send(ReaderIntent.ImportDictionary(uri))
        }
    }

    fun goTo(chapter: Int) = send(ReaderIntent.NavigateToChapter(chapter))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NovelColors.Background)
    ) {

        if (state.isLoading || state.isLoadingChapter) {
            CircularProgressIndicator(Modifier.align(androidx.compose.ui.Alignment.Center))
        }
        state.error?.let { message ->
            Text(
                text = message,
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center).padding(24.dp),
                color = MaterialTheme.colorScheme.error
            )
        }

        // READER PAGE AREA
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val density = LocalDensity.current

            // One layout snapshot is the only gate for every size-related change.
            // Screen geometry and typography travel together through the Model.
            val pagePad = 7.dp
            val measuredLayout = ReaderLayout(
                widthPx = with(density) { (maxWidth - pagePad * 2).roundToPx().coerceAtLeast(1) },
                heightPx = with(density) { (maxHeight - pagePad * 2 - 4.dp).roundToPx().coerceAtLeast(1) },
                density = density.density,
                fontScale = density.fontScale,
                fontSizeSp = state.layout.fontSizeSp
            )
            LaunchedEffect(measuredLayout) {
                send(ReaderIntent.SetLayout(measuredLayout))
            }

            val pageTextStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = state.layout.fontSizeSp.sp,
                lineHeight = state.layout.fontSizeSp.sp
            )
            val layoutKey = ReaderLayoutKey(
                widthPx = state.layout.widthPx,
                heightPx = state.layout.heightPx,
                density = state.layout.density,
                fontScale = state.layout.fontScale,
                style = pageTextStyle
            )

            val pages = rememberReaderPages(
                chapter = currentChapter,
                blocks = state.chapterBlocks,
                key = layoutKey,
                density = density
            )

            LaunchedEffect(currentChapter, pages.size) {
                if (pages.isNotEmpty()) {
                    send(ReaderIntent.SetPage(currentPage.coerceIn(0, pages.lastIndex)))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentChapter, currentPage, pages.size) {
                        awaitEachGesture {
                            // Do not consume the long-press/drag sequence: SelectionContainer
                            // owns it. Page navigation only reacts to an unconsumed tap.
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val up = waitForUpOrCancellation()
                            if (up != null && !up.isConsumed) {
                                val x = down.position.x
                                when {
                                    x < size.width * 0.40f -> {
                                        if (currentPage > 0) send(ReaderIntent.SetPage(currentPage - 1))
                                        else if (currentChapter > 0) goTo(currentChapter - 1)
                                    }
                                    x > size.width * 0.70f -> {
                                        if (currentPage < pages.lastIndex) send(ReaderIntent.SetPage(currentPage + 1))
                                        else if (currentChapter < lastChapter) goTo(currentChapter + 1)
                                    }
                                    else -> send(ReaderIntent.ToggleBars)
                                }
                            }
                        }
                    }
                    .padding(pagePad)
            ) {
                SelectionContainer {
                    Column {
                        pages.getOrNull(currentPage)?.forEach { pageBlock ->
                    when {
                        pageBlock.heading -> Text(
                            text = pageBlock.text,
                            style = pageTextStyle,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        pageBlock.fullHighlight -> textforge.ui.components.HighlightedParagraph(pageBlock.text)
                        else -> textforge.ui.components.InlineHighlight(
                            text = pageBlock.text,
                            style = pageTextStyle,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                        }
                    }
                }
            }
        }

        ReaderPanels(
            state = state,
            dispatch = send,
            onBack = onBack,
            onDictionaryImport = { dictionaryPicker.launch(null) }
        )
    }
}
