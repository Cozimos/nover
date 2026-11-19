package textforge.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import textforge.ui.NovelColors
import textforge.ui.dictionary.StarDictManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import textforge.ui.dictionary.StarDictEntryView

/**
 * All reader chrome and secondary panels live outside the page renderer.
 * This file contains no reader business logic: every action is an Intent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.ReaderPanels(
    state: ReaderState,
    dispatch: (ReaderIntent) -> Unit,
    onBack: () -> Unit,
    onDictionaryImport: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val tocPageSize = 15



    AnimatedVisibility(
        visible = state.showBars,
        modifier = Modifier.align(Alignment.TopCenter)
    ) {
        Surface(
            color = NovelColors.MenuBackground,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.statusBarsPadding().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("← Library") }
                Spacer(Modifier.weight(1f))
                Text("${state.chapter + 1} / ${state.chapters.size}")
            }
        }
    }

    AnimatedVisibility(
        visible = state.showBars,
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Surface(color = NovelColors.MenuBackground, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(15.dp),
                contentAlignment = Alignment.Center
            ) {
                FilledTonalButton(
                    onClick = { dispatch(ReaderIntent.ShowMenu(true)) },
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Reader menu")
                    Spacer(Modifier.width(8.dp))
                    Text("Reader Menu")
                }
            }
        }
    }

    if (state.showMenu) {
        ModalBottomSheet(
            onDismissRequest = { dispatch(ReaderIntent.ShowMenu(false)) },
            containerColor = NovelColors.MenuBackground
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding()
            ) {
                Text("Reader Tools", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { dispatch(ReaderIntent.ShowContents(true)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Table of Contents") }
                OutlinedButton(
                    onClick = { dispatch(ReaderIntent.ShowSearch(true)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Search Word") }
                OutlinedButton(
                    onClick = {
                       dispatch(ReaderIntent.SetDictionaryQuery(
                            clipboard.getText()?.text.orEmpty()
                            .trim()
                            .trim(',', '.', '\'', '"', ':', ';', '!', '?', '(', ')', '[', ']', '“', '”', '‘', '’')
))
                        dispatch(ReaderIntent.ShowMenu(false))
                        dispatch(ReaderIntent.ShowDictionarySearch(true))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Dictionary Lookup") }
                OutlinedButton(onClick = onDictionaryImport, modifier = Modifier.fillMaxWidth()) {
                    Text(if (StarDictManager.installedName(context) == null) "Import Dictionary" else "Replace Dictionary")
                }
                Text(
                    state.dictionaryName
                        ?: StarDictManager.installedName(context)?.let { "Dictionary: $it" }
                        ?: "No dictionary imported",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Text size: ${state.layout.fontSizeSp.toInt()}", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        dispatch(ReaderIntent.SetFontSize(state.layout.fontSizeSp - 1f))
                    }) { Text("−") }
                    OutlinedButton(onClick = {
                        dispatch(ReaderIntent.SetFontSize(state.layout.fontSizeSp + 1f))
                    }) { Text("+") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { dispatch(ReaderIntent.NavigateToChapter(state.chapter - 1)) }
                    ) { Text("< Previous") }
                    OutlinedButton(
                        onClick = { dispatch(ReaderIntent.NavigateToChapter(state.chapter + 1)) }
                    ) { Text("Next >") }
                }
                OutlinedButton(
                    onClick = { dispatch(ReaderIntent.ShowReplace(true)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Find & Replace") }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (state.showContents) {
    // show the page the current chapter is in
        LaunchedEffect(state.showContents) {
        if (state.showContents) {
            val correctPage = state.chapter / tocPageSize
            if (state.tocPage != correctPage) {
                dispatch(ReaderIntent.SetTocPage(correctPage))
            }
        }
    }

    Dialog(onDismissRequest = { dispatch(ReaderIntent.ShowContents(false)) }) {
            Surface(
                color = NovelColors.MenuBackground,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text("Table of Contents", style = MaterialTheme.typography.titleLarge)
                    val pageCount = (state.chapters.size + tocPageSize - 1) / tocPageSize
                    val start = state.tocPage * tocPageSize
                    val end = minOf(start + tocPageSize, state.chapters.size)
                    LazyColumn(modifier = Modifier.heightIn(max = 700.dp)) {
                    itemsIndexed(state.chapters.subList(start, end)) { index, chapter ->
                    val chapterIndex = start + index
                    val isCurrent = chapterIndex == state.chapter
            TextButton(
            onClick = {
                dispatch(ReaderIntent.NavigateToChapter(chapterIndex))
                dispatch(ReaderIntent.ShowContents(false))
            },
            // FIX 1: No fixed height. Let it grow to 2-3 lines.
            // FIX 2: Align to start, give it vertical breathing room.
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = 40.dp),
            contentPadding = PaddingValues(
                horizontal = 10.dp,
                vertical = 10.dp
            )
        ) {
            Text(
                text = chapter.title,
                color = if (isCurrent) Color(0xFFFFCC80) else Color.Black,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                // FIX 3: This is the key part - allow wrapping
                softWrap = true,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                // FIX 4: Force left-align inside TextButton's Row
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
                lineHeight = 20.sp
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
                            enabled = state.tocPage > 0,
                            onClick = { dispatch(ReaderIntent.SetTocPage(state.tocPage - 1)) }
                        ) { Text("<") }
                        Text("${state.tocPage + 1} / ${pageCount.coerceAtLeast(1)}")
                        TextButton(
                            enabled = state.tocPage < pageCount - 1,
                            onClick = { dispatch(ReaderIntent.SetTocPage(state.tocPage + 1)) }
                        ) { Text(">") }
                    }
                    TextButton(
                        onClick = { dispatch(ReaderIntent.ShowContents(false)) },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Close") }
                }
            }
        }
    }

    if (state.showDictionarySearch) {
    AlertDialog(
        onDismissRequest = {
            dispatch(ReaderIntent.ShowDictionarySearch(false))
            dispatch(ReaderIntent.SetDictionaryDefinition(null))
        },
        containerColor = NovelColors.MenuBackground,
        title = { Text("Dictionary Lookup") },
        text = {
            Column {
                OutlinedTextField(
                    value = state.dictionaryQuery,
                    onValueChange = { dispatch(ReaderIntent.SetDictionaryQuery(it)) },
                    label = { Text("Word") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))

                // Result shows HERE, in same dialog. No 2nd dialog.
                if (state.dictionaryDefinition != null) {
                    // Keep long definitions inside the dialog and let the entry scroll.
                    // SelectionContainer remains inside StarDictEntryView so text can be copied.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        StarDictEntryView(
                            rawEntry = state.dictionaryDefinition.orEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Text("Type a word and tap Look Up.")
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    val word = state.dictionaryQuery.trim()
                    if (word.isNotEmpty()) {
                        dispatch(ReaderIntent.LookupWord(word))
                    }
                }) { Text("Look Up") }

                TextButton(onClick = {
                    dispatch(ReaderIntent.ShowDictionarySearch(false))
                    dispatch(ReaderIntent.SetDictionaryDefinition(null))
                }) { Text("Close") }
            }
        }
    )
}

    if (state.showSearch) {
        AlertDialog(
            onDismissRequest = { dispatch(ReaderIntent.ShowSearch(false)) },
            containerColor = NovelColors.MenuBackground,
            title = { Text("Search Novel") },
            text = {
                Column {
                    OutlinedTextField(
                        value = state.searchText,
                        onValueChange = { dispatch(ReaderIntent.SetSearchText(it)) },
                        label = { Text("Search word or phrase") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    if (state.isSearching) CircularProgressIndicator()
                    state.searchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        itemsIndexed(state.searchResults) { _, result ->
                            TextButton(
                                onClick = {
                                    dispatch(ReaderIntent.NavigateToChapter(result.chapter))
                                    dispatch(ReaderIntent.SetPage(0))
                                    dispatch(ReaderIntent.ShowSearch(false))
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
                    if (state.searchResults.isEmpty() && !state.isSearching && state.searchText.isNotBlank()) {
                        Text("No matches yet. Tap Search to scan the novel.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dispatch(ReaderIntent.Search(state.searchText)) }) {
                    Text("Search")
                }
            }
        )
    }

    if (state.showReplace) {
        AlertDialog(
            onDismissRequest = { dispatch(ReaderIntent.ShowReplace(false)) },
            containerColor = NovelColors.MenuBackground,
            title = { Text("Find & Replace") },
            text = {
                Column {
                    OutlinedTextField(
                        value = state.searchText,
                        onValueChange = { dispatch(ReaderIntent.SetSearchText(it)) },
                        label = { Text("Find") }
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.replaceText,
                        onValueChange = { dispatch(ReaderIntent.SetReplaceText(it)) },
                        label = { Text("Replace with") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { dispatch(ReaderIntent.ShowReplace(false)) }) { Text("Replace") }
            }
        )
    }
}
