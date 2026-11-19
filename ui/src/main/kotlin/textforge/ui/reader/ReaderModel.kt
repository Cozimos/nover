package textforge.ui.reader

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import textforge.parser.ChapterIndex
import textforge.storage.NovelStore
import textforge.storage.ReadingProgress
import textforge.ui.dictionary.StarDictManager

/** All reader-facing values live in this one immutable snapshot. */
data class ReaderState(
    val novelId: String = "",
    val chapters: List<ChapterIndex> = emptyList(),
    val chapterBlocks: List<ChapterBlock> = emptyList(),
    val isLoadingChapter: Boolean = false,
    val chapter: Int = 0,
    val page: Int = 0,
    val pagesByChapter: Map<Int, Int> = emptyMap(),
    val layout: ReaderLayout = ReaderLayout(),
    val isLoading: Boolean = true,
    val showBars: Boolean = false,
    val showMenu: Boolean = false,
    val showContents: Boolean = false,
    val showSearch: Boolean = false,
    val showReplace: Boolean = false,
    val showDictionarySearch: Boolean = false,
    val selectedWord: String? = null,
    val dictionaryQuery: String = "",
    val dictionaryDefinition: String? = null,
    val dictionaryName: String? = null,
    val searchText: String = "",
    val replaceText: String = "",
    val tocPage: Int = 0,
    val searchResults: List<ReaderSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val error: String? = null
) {
    val lastChapter: Int get() = (chapters.size - 1).coerceAtLeast(0)
}

/** One layout gate: geometry and typography are always updated together. */
data class ReaderLayout(
    val widthPx: Int = 1,
    val heightPx: Int = 1,
    val density: Float = 1f,
    val fontScale: Float = 1f,
    val fontSizeSp: Float = 17f
)

data class ReaderSearchResult(
    val chapter: Int,
    val chapterTitle: String,
    val snippet: String
)

/** User actions and asynchronous results. The View never edits ReaderState directly. */
sealed interface ReaderIntent {
    data class Open(val novelId: String) : ReaderIntent
    data class NavigateToChapter(val chapter: Int) : ReaderIntent
    data class SetPage(val page: Int) : ReaderIntent
    data class SetLayout(val layout: ReaderLayout) : ReaderIntent
    data class SetFontSize(val sizeSp: Float) : ReaderIntent
    data object ToggleBars : ReaderIntent
    data class ShowMenu(val visible: Boolean) : ReaderIntent
    data class ShowContents(val visible: Boolean) : ReaderIntent
    data class ShowSearch(val visible: Boolean) : ReaderIntent
    data class ShowReplace(val visible: Boolean) : ReaderIntent
    data class ShowDictionarySearch(val visible: Boolean) : ReaderIntent
    data class SetSelectedWord(val word: String?) : ReaderIntent
    data class SetDictionaryQuery(val query: String) : ReaderIntent
    data class SetDictionaryDefinition(val definition: String?) : ReaderIntent
    data class SetDictionaryName(val name: String?) : ReaderIntent
    data class ImportDictionary(val uri: Uri) : ReaderIntent
    data class LookupWord(val word: String) : ReaderIntent
    data class SetSearchText(val text: String) : ReaderIntent
    data class SetReplaceText(val text: String) : ReaderIntent
    data class SetTocPage(val page: Int) : ReaderIntent
    data class Search(val query: String) : ReaderIntent
    data class SearchCompleted(val results: List<ReaderSearchResult>) : ReaderIntent
    data class SearchFailed(val message: String) : ReaderIntent
    data class Opened(val chapters: List<ChapterIndex>, val progress: ReadingProgress) : ReaderIntent
    data class ChapterLoaded(val chapter: Int, val blocks: List<ChapterBlock>) : ReaderIntent
    data class ChapterLoadFailed(val chapter: Int, val message: String) : ReaderIntent
    data class Failed(val message: String) : ReaderIntent
    data object ClearError : ReaderIntent
}

/** Pure reducer: a user intent or effect result produces the next full snapshot. */
fun reduceReaderState(state: ReaderState, intent: ReaderIntent): ReaderState = when (intent) {
    is ReaderIntent.Open -> ReaderState(novelId = intent.novelId, isLoading = true)
    is ReaderIntent.Opened -> {
        val chapter = intent.progress.chapterNumber.coerceIn(0, (intent.chapters.size - 1).coerceAtLeast(0))
        state.copy(
            chapters = intent.chapters,
            chapter = chapter,
            page = intent.progress.pageIndex.coerceAtLeast(0),
            isLoading = false,
            isLoadingChapter = true,
            chapterBlocks = emptyList(),
            error = null
        )
    }
    is ReaderIntent.NavigateToChapter -> {
        val target = intent.chapter.coerceIn(0, state.lastChapter)
        if (state.chapters.isEmpty() || target == state.chapter) state
        else state.copy(
            pagesByChapter = state.pagesByChapter + (state.chapter to state.page),
            chapter = target,
            page = state.pagesByChapter[target] ?: 0,
            chapterBlocks = emptyList(),
            isLoadingChapter = true
        )
    }
    is ReaderIntent.SetPage -> state.copy(page = intent.page.coerceAtLeast(0))
    is ReaderIntent.ChapterLoaded -> if (state.chapter == intent.chapter) state.copy(chapterBlocks = intent.blocks, isLoadingChapter = false, error = null) else state
    is ReaderIntent.ChapterLoadFailed -> if (state.chapter == intent.chapter) state.copy(chapterBlocks = emptyList(), isLoadingChapter = false, error = intent.message) else state
    is ReaderIntent.SetLayout -> if (state.layout == intent.layout) state else state.copy(layout = intent.layout)
    is ReaderIntent.SetFontSize -> state.copy(layout = state.layout.copy(fontSizeSp = intent.sizeSp.coerceIn(12f, 32f)))
    ReaderIntent.ToggleBars -> state.copy(showBars = !state.showBars)
    is ReaderIntent.ShowMenu -> state.copy(showMenu = intent.visible)
    is ReaderIntent.ShowContents -> state.copy(showContents = intent.visible, showMenu = false, tocPage = 0)
    is ReaderIntent.ShowSearch -> state.copy(showSearch = intent.visible, showMenu = false)
    is ReaderIntent.ShowReplace -> state.copy(showReplace = intent.visible, showMenu = false)
    is ReaderIntent.ShowDictionarySearch -> state.copy(showDictionarySearch = intent.visible)
    is ReaderIntent.SetSelectedWord -> state.copy(selectedWord = intent.word, dictionaryDefinition = null)
    is ReaderIntent.SetDictionaryQuery -> state.copy(dictionaryQuery = intent.query)
    is ReaderIntent.SetDictionaryDefinition -> state.copy(dictionaryDefinition = intent.definition)
    is ReaderIntent.SetDictionaryName -> state.copy(dictionaryName = intent.name)
    is ReaderIntent.ImportDictionary, is ReaderIntent.LookupWord -> state
    is ReaderIntent.SetSearchText -> state.copy(searchText = intent.text)
    is ReaderIntent.SetReplaceText -> state.copy(replaceText = intent.text)
    is ReaderIntent.SetTocPage -> state.copy(tocPage = intent.page.coerceAtLeast(0))
    is ReaderIntent.Search -> state.copy(searchText = intent.query, isSearching = true, searchResults = emptyList(), searchError = null)
    is ReaderIntent.SearchCompleted -> state.copy(isSearching = false, searchResults = intent.results)
    is ReaderIntent.SearchFailed -> state.copy(isSearching = false, searchError = intent.message)
    is ReaderIntent.Failed -> state.copy(isLoading = false, isLoadingChapter = false, error = intent.message)
    ReaderIntent.ClearError -> state.copy(error = null)
}

/**
 * MVI coordinator. It accepts intents, reduces state, and runs disk work as
 * effects. Effect results return through intents so the reducer stays central.
 */
class ReaderViewModel(private val appContext: Context) : ViewModel() {
    private val mutableState = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = mutableState.asStateFlow()
    private var searchJob: Job? = null
    private var openJob: Job? = null
    private val progressMutex = Mutex()
    private var progressVersion = 0L
//cleans trailing , . ' "
    private fun cleanWord(raw: String): String {
    return raw
        .trim()
        .trim(',', '.', '\'', '"', ':', ';', '!', '?', '(', ')', '[', ']', '{', '}',
            '“', '”', '‘', '’', '«', '»', '—', '-', '–')
        .trim()
}

    fun dispatch(intent: ReaderIntent) {
        when (intent) {
            is ReaderIntent.Open -> openNovel(intent.novelId)
            is ReaderIntent.NavigateToChapter -> {
                val old = mutableState.value
                reduce(intent)
                persistProgress(old)
                val updated = mutableState.value
                if (updated.chapter != old.chapter) loadChapter(updated.novelId, updated.chapter)
            }
            is ReaderIntent.SetPage -> {
                reduce(intent)
                persistProgress(mutableState.value)
            }
            is ReaderIntent.Search -> search(intent.query)
            is ReaderIntent.ImportDictionary -> importDictionary(intent.uri)
            // cleanWord
            is ReaderIntent.LookupWord -> lookupWord(cleanWord(intent.word))
            else -> reduce(intent)
        }
    }

    private fun reduce(intent: ReaderIntent) {
        mutableState.value = reduceReaderState(mutableState.value, intent)
    }

    private fun openNovel(id: String) {
        if (id.isBlank() || mutableState.value.novelId == id && !mutableState.value.isLoading) return
        reduce(ReaderIntent.Open(id))
        openJob?.cancel()
        openJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    NovelStore.getChapters(appContext, id) to NovelStore.getProgress(appContext, id)
                }
            }.onSuccess { (chapters, progress) ->
                if (mutableState.value.novelId == id) {
                    reduce(ReaderIntent.Opened(chapters, progress))
                    if (chapters.isNotEmpty()) loadChapter(id, mutableState.value.chapter)
                    else reduce(ReaderIntent.Failed("This novel has no readable chapters"))
                }
            }.onFailure { error ->
                if (mutableState.value.novelId == id) reduce(ReaderIntent.Failed(error.message ?: "Unable to open novel"))
            }
        }
    }

    private var chapterJob: Job? = null

    private fun loadChapter(novelId: String, chapter: Int) {
        chapterJob?.cancel()
        chapterJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    NovelStore.getChapterBlocks(appContext, novelId, chapter).toReaderBlocks()
                }
            }.onSuccess { blocks ->
                if (mutableState.value.novelId == novelId && mutableState.value.chapter == chapter) {
                    reduce(ReaderIntent.ChapterLoaded(chapter, blocks))
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (mutableState.value.novelId == novelId && mutableState.value.chapter == chapter) {
                    reduce(ReaderIntent.ChapterLoadFailed(chapter, error.message ?: "Chapter load failed"))
                }
            }
        }
    }

    private fun search(query: String) {
        val snapshot = mutableState.value
        if (query.isBlank() || snapshot.novelId.isBlank()) return
        reduce(ReaderIntent.Search(query))
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    buildList {
                        // Search scans sequentially and holds only the chapter currently being parsed.
                        for (chapter in snapshot.chapters) {
                            if (size >= 200) break
                            val blocks = NovelStore.getChapterBlocks(appContext, snapshot.novelId, chapter.number)
                            for (block in blocks) {
                                val body = when (block) {
                                    is textforge.parser.NovelBlock.Heading -> block.title
                                    is textforge.parser.NovelBlock.Paragraph -> block.segments.joinToString("") { it.text }
                                }
                                var from = 0
                                while (from < body.length && size < 200) {
                                    val found = body.indexOf(query, from, ignoreCase = true)
                                    if (found < 0) break
                                    val start = (found - 55).coerceAtLeast(0)
                                    val end = (found + query.length + 75).coerceAtMost(body.length)
                                    add(ReaderSearchResult(chapter.number, chapter.title, body.substring(start, end).replace('\n', ' ')))
                                    from = found + query.length.coerceAtLeast(1)
                                }
                            }
                        }
                    }
                }
            }.onSuccess { results ->
                if (mutableState.value.novelId == snapshot.novelId) reduce(ReaderIntent.SearchCompleted(results))
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                reduce(ReaderIntent.SearchFailed(error.message ?: "Search failed"))
            }
        }
    }

    private fun importDictionary(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { StarDictManager.installFromFolder(appContext, uri) }
            }.onSuccess { reduce(ReaderIntent.SetDictionaryName(it)) }
                .onFailure { reduce(ReaderIntent.SetDictionaryName("Import failed: ${it.message ?: "unknown error"}")) }
        }
    }

    private fun lookupWord(word: String) {
        if (word.isBlank()) return
        viewModelScope.launch {
            val definition = runCatching {
                withContext(Dispatchers.IO) {
                    val result = StarDictManager.lookupSmart(appContext, word)
                    result.entry?.definition
                        ?: if (result.suggestions.isNotEmpty()) {
                            "No exact entry. Suggestions: ${result.suggestions.joinToString()}"
                        } else {
                            "No dictionary entry found. Import a StarDict dictionary from Reader Tools."
                        }
                }
            }.getOrElse { "Dictionary lookup failed: ${it.message ?: "unknown error"}" }
            reduce(ReaderIntent.SetDictionaryDefinition(definition))
        }
    }

    private fun persistProgress(snapshot: ReaderState) {
        if (snapshot.novelId.isBlank() || snapshot.chapters.isEmpty()) return
        val chapter = snapshot.chapters.getOrNull(snapshot.chapter) ?: return
        val version = ++progressVersion
        viewModelScope.launch(Dispatchers.IO) {
            progressMutex.withLock {
                // A slow earlier disk write must never overwrite a newer page position.
                if (version != progressVersion) return@withLock
                NovelStore.saveProgress(
                    appContext,
                    snapshot.novelId,
                    ReadingProgress(snapshot.chapter, 0, chapter.startByte, snapshot.page)
                )
            }
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReaderViewModel::class.java))
            return ReaderViewModel(appContext) as T
        }
    }
}

/** Convert parser output once at the data boundary; no JSON serialization/cache layer. */
private fun List<textforge.parser.NovelBlock>.toReaderBlocks(): List<ChapterBlock> = map { block ->
    when (block) {
        is textforge.parser.NovelBlock.Heading -> ChapterBlock(
            heading = true,
            title = block.title,
            fullHighlight = false,
            segments = emptyList()
        )
        is textforge.parser.NovelBlock.Paragraph -> ChapterBlock(
            heading = false,
            title = "",
            fullHighlight = block.fullHighlight,
            segments = block.segments.map { segment ->
                Segment(
                    text = segment.text,
                    italic = segment.italic,
                    highlighted = segment.highlighted
                )
            }
        )
    }
}
