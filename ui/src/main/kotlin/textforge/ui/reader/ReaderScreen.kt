package textforge.ui.reader

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import textforge.ui.NovelColors
import textforge.ui.dictionary.StarDictManager

@Composable
@OptIn(ExperimentalMaterial3Api::class)
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

    // MVI boundary: all reader data is owned by the ViewModel's single StateFlow.
    val readerViewModel: ReaderViewModel = viewModel(
        key = "reader-$novelId",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ReaderViewModel(context.applicationContext) as T
            }
        }
    )
    val readerState by readerViewModel.state.collectAsState()
    val chapters = readerState.chapters
    val currentChapter = readerState.currentChapter
    val currentPage = readerState.currentPage
    val lastChapter = readerState.lastChapter

    LaunchedEffect(novelId) {
        readerViewModel.dispatch(ReaderIntent.OpenNovel(novelId))
    }
    DisposableEffect(readerViewModel) {
        onDispose { readerViewModel.persistProgress() }
    }

    var showBars by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showTOC by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showReplace by remember { mutableStateOf(false) }
    var selectedWord by remember { mutableStateOf<String?>(null) }
    var showDictionarySearch by remember { mutableStateOf(false) }
    var dictionaryQuery by remember { mutableStateOf("") }
    var dictionaryDefinition by remember { mutableStateOf<String?>(null) }
    var dictionaryName by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

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
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { StarDictManager.installFromFolder(context, uri) }
                }
                result.onSuccess { dictionaryName = it }
                    .onFailure { dictionaryName = "Import failed: ${it.message ?: "unknown error"}" }
            }
        }
    }

    val textLayouts = remember(currentChapter, currentPage) {
        mutableStateMapOf<Int, TextLayoutResult>()
    }

    val textCoordinates = remember(currentChapter, currentPage) {
        mutableStateMapOf<Int, LayoutCoordinates>()
    }

    var readerCoordinates by remember {
        mutableStateOf<LayoutCoordinates?>(null)
    }

    var replaceText by remember { mutableStateOf("") }

    val tocPageSize = 13

    var tocPage by remember(novelId) {
        mutableIntStateOf(0)
    }

    fun goTo(chapter: Int) {
        readerViewModel.dispatch(ReaderIntent.GoToChapter(chapter))
    }

    fun runNovelSearch(query: String) {
        readerViewModel.dispatch(ReaderIntent.Search(query))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NovelColors.Background)
    ) {

        // READER PAGE AREA
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val density = LocalDensity.current

            val pageTextStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = readerState.layout.fontSizeSp.sp,
                lineHeight = (readerState.layout.fontSizeSp * 1.0f).sp
            )

            // Must match the Column padding below exactly.
            val pagePad = 7.dp

            val widthPx = with(density) {
                (maxWidth - pagePad * 2).roundToPx()
            }

            val heightPx = with(density) {
                (maxHeight - pagePad * 2 - 4.dp).roundToPx()
            }

            // Pagination reads dimensions and typography from the same state
            // snapshot, rather than maintaining separate "screen" and "font" knobs.
            val layoutKey = remember(readerState.layout, pageTextStyle) {
                ReaderLayoutKey(
                    widthPx = readerState.layout.widthPx.coerceAtLeast(1),
                    heightPx = readerState.layout.heightPx.coerceAtLeast(1),
                    density = readerState.layout.density,
                    fontScale = readerState.layout.fontScale,
                    style = pageTextStyle
                )
            }

            LaunchedEffect(widthPx, heightPx, density.density, density.fontScale) {
                readerViewModel.dispatch(
                    ReaderIntent.SetLayout(
                        ReaderLayoutState(
                            widthPx = widthPx.coerceAtLeast(1),
                            heightPx = heightPx.coerceAtLeast(1),
                            density = density.density,
                            fontScale = density.fontScale,
                            fontSizeSp = readerState.layout.fontSizeSp
                        )
                    )
                )
            }

            val pages = rememberReaderPages(
                chapter = currentChapter,
                blocks = readerState.chapterBlocks,
                key = layoutKey,
                density = density
            )

            LaunchedEffect(currentChapter, pages.size) {
                if (pages.isNotEmpty()) {
                    readerViewModel.dispatch(ReaderIntent.SetPage(currentPage.coerceIn(0, pages.lastIndex)))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        readerCoordinates = it
                    }
                    .pointerInput(currentChapter, currentPage, pages.size) {
                        detectTapGestures(
                            onTap = { offset ->
                                when {
                                    offset.x < size.width * 0.40f -> {
                                        if (currentPage > 0) {
                                            readerViewModel.dispatch(ReaderIntent.SetPage(currentPage - 1))
                                        } else if (currentChapter > 0) {
                                            goTo(currentChapter - 1)
                                        }
                                    }

                                    offset.x > size.width * 0.70f -> {
                                        if (currentPage < pages.lastIndex) {
                                            readerViewModel.dispatch(ReaderIntent.SetPage(currentPage + 1))
                                        } else if (currentChapter < lastChapter) {
                                            goTo(currentChapter + 1)
                                        }
                                    }

                                    else -> showBars = !showBars
                                }
                            },
                        )
                    }
                    .padding(pagePad)
            ) {
                SelectionContainer {
                    Column {
                        pages.getOrNull(currentPage)?.forEachIndexed { index, pageBlock ->
                    val onLayout: (TextLayoutResult) -> Unit = {
                        textLayouts[index] = it
                    }

                    val onPositioned: (LayoutCoordinates) -> Unit = {
                        textCoordinates[index] = it
                    }

                    when {
                        pageBlock.heading -> {
                            Text(
                                text = pageBlock.text,
                                style = pageTextStyle,
                                modifier = Modifier
                                    .padding(vertical = 10.dp)
                                    .onGloballyPositioned(onPositioned),
                                onTextLayout = onLayout
                            )
                        }

                        pageBlock.fullHighlight -> {
                            textforge.ui.components.HighlightedParagraph(
                                text = pageBlock.text,
                                onTextLayout = onLayout,
                                onTextPositioned = onPositioned
                            )
                        }

                        else -> {
                            textforge.ui.components.InlineHighlight(
                                text = pageBlock.text,
                                style = pageTextStyle,
                                modifier = Modifier.padding(vertical = 8.dp),
                                onTextLayout = onLayout,
                                onTextPositioned = onPositioned
                            )
                        }
                    }
                        }
                    }
                }
            }
        }

        // Startup and chapter reads are asynchronous; render their state instead
        // of blocking the first frame while the byte range is read and parsed.
        if (readerState.isStarting ||
            (readerState.isLoadingChapter && readerState.chapterBlocks.isEmpty())
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        readerState.error?.takeIf { !showSearch }?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }

        selectedWord?.let { word ->
            AlertDialog(
                onDismissRequest = { selectedWord = null; dictionaryDefinition = null },
                title = { Text(word) },
                text = { Text(dictionaryDefinition ?: "Choose an action for this selection.") },
                confirmButton = {
                    Row {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(word))
                            selectedWord = null
                            dictionaryDefinition = null
                        }) { Text("Copy") }
                        TextButton(onClick = {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    StarDictManager.lookupSmart(context, word)
                                }
                                dictionaryDefinition = result.entry?.definition
                                    ?: if (result.suggestions.isNotEmpty()) "No exact entry. Suggestions: ${result.suggestions.joinToString()}" else "No dictionary entry found. Import a StarDict dictionary from Reader Tools."
                            }
                        }) { Text("Look Up") }
                        TextButton(onClick = { selectedWord = null; dictionaryDefinition = null }) { Text("Close") }
                    }
                }
            )
        }

        // TOP READER BAR
        AnimatedVisibility(
            visible = showBars,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = NovelColors.MenuBackground,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) {
                        Text("← Library")
                    }

                    Spacer(Modifier.weight(1f))

                    Text("${currentChapter + 1} / ${chapters.size}")
                }
            }
        }

        // BOTTOM READER BAR
        AnimatedVisibility(
            visible = showBars,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = NovelColors.MenuBackground,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    FilledTonalButton(
                        onClick = { showMenu = true },
                        shape = RoundedCornerShape(50.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Reader menu"
                        )

                        Spacer(Modifier.width(8.dp))

                        Text("Reader Menu")
                    }
                }
            }
        }
    }

    // READER MENU
    if (showMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            containerColor = NovelColors.MenuBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    "Reader Tools",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        showMenu = false
                        showTOC = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Table of Contents")
                }

                OutlinedButton(
                    onClick = {
                        showMenu = false
                        showSearch = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Search Word")
                }

                OutlinedButton(
                    onClick = { dictionaryQuery = clipboard.getText()?.text.orEmpty(); showMenu = false; showDictionarySearch = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Dictionary Lookup") }

                OutlinedButton(
                    onClick = { dictionaryPicker.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (StarDictManager.installedName(context) == null) "Import Dictionary" else "Replace Dictionary")
                }
                Text(
                    dictionaryName ?: StarDictManager.installedName(context)?.let { "Dictionary: $it" } ?: "No dictionary imported",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { goTo(currentChapter - 1) }
                    ) {
                        Text("< Previous")
                    }

                    OutlinedButton(
                        onClick = { goTo(currentChapter + 1) }
                    ) {
                        Text("Next >")
                    }
                }

                OutlinedButton(
                    onClick = {
                        showMenu = false
                        showReplace = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Find & Replace")
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // TABLE OF CONTENTS
    if (showTOC) {
        Dialog(
            onDismissRequest = { showTOC = false }
        ) {
            Surface(
                color = NovelColors.MenuBackground,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 21.dp, vertical = 5.dp)
                ) {
                    Text(
                        "Table of Contents",
                        style = MaterialTheme.typography.titleLarge
                    )

                    val tocPageCount =
                        (chapters.size + tocPageSize - 1) / tocPageSize

                    val start = tocPage * tocPageSize
                    val end = minOf(start + tocPageSize, chapters.size)

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 880.dp)
                    ) {
                        itemsIndexed(chapters.subList(start, end)) { index, chapter ->
                            val chapterIndex = start + index

                            TextButton(
                                onClick = {
                                    goTo(chapterIndex)
                                    showTOC = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp),
                                contentPadding = PaddingValues(
                                    horizontal = 9.dp,
                                    vertical = 11.dp
                                )
                            ) {
                                Text(
                                    chapter.title,
                                    color = if (chapterIndex == currentChapter) {
                                        Color(0xFFFFCC80)
                                    } else {
                                        Color.Black
                                    },
                                    fontWeight = if (chapterIndex == currentChapter) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            enabled = tocPage > 0,
                            onClick = { tocPage-- },
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Text("<")
                        }

                        Text(
                            "${tocPage + 1} / ${tocPageCount.coerceAtLeast(1)}"
                        )

                        TextButton(
                            enabled = tocPage < tocPageCount - 1,
                            onClick = { tocPage++ },
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            Text(">")
                        }
                    }

                    TextButton(
                        onClick = { showTOC = false },
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(
                            horizontal = 8.dp,
                            vertical = 0.dp
                        )
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    if (showDictionarySearch) {
        AlertDialog(
            onDismissRequest = { showDictionarySearch = false },
            containerColor = NovelColors.MenuBackground,
            title = { Text("Dictionary Lookup") },
            text = {
                OutlinedTextField(
                    value = dictionaryQuery,
                    onValueChange = { dictionaryQuery = it },
                    label = { Text("Word") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedWord = dictionaryQuery.trim().takeIf { it.isNotEmpty() }
                    dictionaryDefinition = null
                    showDictionarySearch = false
                }) { Text("Look Up") }
            }
        )
    }

    // SEARCH
    if (showSearch) {
        AlertDialog(
            onDismissRequest = { showSearch = false },
            containerColor = NovelColors.MenuBackground,
            title = {
                Text("Search Novel")
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = readerState.searchQuery,
                        onValueChange = { readerViewModel.dispatch(ReaderIntent.UpdateSearchQuery(it)) },
                        label = { Text("Search word or phrase") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    if (readerState.isSearching) CircularProgressIndicator()
                    readerState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        itemsIndexed(readerState.searchResults) { _, result ->
                            TextButton(
                                onClick = {
                                    goTo(result.chapter)
                                    readerViewModel.dispatch(ReaderIntent.SetPage(0))
                                    showSearch = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text("${result.chapterTitle} · Chapter ${result.chapter + 1}", fontWeight = FontWeight.Bold)
                                    Text(result.snippet, maxLines = 3)
                                }
                            }
                        }
                    }
                    if (readerState.searchResults.isEmpty() && !readerState.isSearching && readerState.searchQuery.isNotBlank()) {
                        Text("No matches yet. Tap Search to scan the novel.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { runNovelSearch(readerState.searchQuery) }) {
                    Text("Search")
                }
            }
        )
    }

    // FIND AND REPLACE
    if (showReplace) {
        AlertDialog(
            onDismissRequest = { showReplace = false },
            containerColor = NovelColors.MenuBackground,
            title = {
                Text("Find & Replace")
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = readerState.searchQuery,
                        onValueChange = { readerViewModel.dispatch(ReaderIntent.UpdateSearchQuery(it)) },
                        label = { Text("Find") }
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        label = { Text("Replace with") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showReplace = false }) {
                    Text("Replace")
                }
            }
        )
    }
}


