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

    state.selectedWord?.let { word ->
        AlertDialog(
            onDismissRequest = {
                dispatch(ReaderIntent.SetSelectedWord(null))
                dispatch(ReaderIntent.SetDictionaryDefinition(null))
            },
            title = { Text(word) },
            text = { Text(state.dictionaryDefinition ?: "Choose an action for this selection.") },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(word))
                        dispatch(ReaderIntent.SetSelectedWord(null))
                        dispatch(ReaderIntent.SetDictionaryDefinition(null))
                    }) { Text("Copy") }
                    TextButton(onClick = { dispatch(ReaderIntent.LookupWord(word)) }) {
                        Text("Look Up")
                    }
                    TextButton(onClick = {
                        dispatch(ReaderIntent.SetSelectedWord(null))
                        dispatch(ReaderIntent.SetDictionaryDefinition(null))
                    }) { Text("Close") }
                }
            }
        )
    }

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
                        dispatch(ReaderIntent.SetDictionaryQuery(clipboard.getText()?.text.orEmpty()))
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
        Dialog(onDismissRequest = { dispatch(ReaderIntent.ShowContents(false)) }) {
            Surface(
                color = NovelColors.MenuBackground,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 21.dp, vertical = 5.dp)
                ) {
                    Text("Table of Contents", style = MaterialTheme.typography.titleLarge)
                    val pageCount = (state.chapters.size + tocPageSize - 1) / tocPageSize
                    val start = state.tocPage * tocPageSize
                    val end = minOf(start + tocPageSize, state.chapters.size)
                    LazyColumn(modifier = Modifier.heightIn(max = 888.dp)) {
                        itemsIndexed(state.chapters.subList(start, end)) { index, chapter ->
                            val chapterIndex = start + index
                            TextButton(
                                onClick = {
                                    dispatch(ReaderIntent.NavigateToChapter(chapterIndex))
                                    dispatch(ReaderIntent.ShowContents(false))
                                },
                                modifier = Modifier.fillMaxWidth().height(32.dp),
                                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    chapter.title,
                                    color = if (chapterIndex == state.chapter) Color(0xFFFFCC80) else Color.Black,
                                    fontWeight = if (chapterIndex == state.chapter) FontWeight.Bold else FontWeight.Normal,
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
            onDismissRequest = { dispatch(ReaderIntent.ShowDictionarySearch(false)) },
            containerColor = NovelColors.MenuBackground,
            title = { Text("Dictionary Lookup") },
            text = {
                OutlinedTextField(
                    value = state.dictionaryQuery,
                    onValueChange = { dispatch(ReaderIntent.SetDictionaryQuery(it)) },
                    label = { Text("Word") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dispatch(ReaderIntent.SetSelectedWord(state.dictionaryQuery.trim().takeIf { it.isNotEmpty() }))
                    dispatch(ReaderIntent.ShowDictionarySearch(false))
                }) { Text("Look Up") }
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
