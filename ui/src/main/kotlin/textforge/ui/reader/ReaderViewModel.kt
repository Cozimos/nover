package textforge.ui.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import textforge.parser.ChapterIndex
import textforge.storage.NovelStore

/** One layout gate: UI geometry and typography are captured together. */
internal data internal class ReaderLayoutState(
    val widthPx: Int = 1,
    val heightPx: Int = 1,
    val density: Float = 1f,
    val fontScale: Float = 1f,
    val fontSizeSp: Float = 17f
)

internal data internal class ReaderSearchResult(
    val chapter: Int,
    val chapterTitle: String,
    val snippet: String
)

/**
 * The single reader-data state. Compose renders this snapshot and does not
 * independently own chapter, page, layout, loading, or search truth. Transient
 * dialog visibility remains presentation-only state in the screen.
 */
internal data internal class ReaderState(
    val novelId: String = "",
    val chapters: List<ChapterIndex> = emptyList(),
    val chapterBlocks: List<ChapterBlock> = emptyList(),
    val currentChapter: Int = 0,
    val currentPage: Int = 0,
    val pageByChapter: Map<Int, Int> = emptyMap(),
    val layout: ReaderLayoutState = ReaderLayoutState(),
    val isStarting: Boolean = true,
    val isLoadingChapter: Boolean = false,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<ReaderSearchResult> = emptyList(),
    val error: String? = null
) {
    val chapterCount: Int get() = chapters.size
    val lastChapter: Int get() = (chapters.size - 1).coerceAtLeast(0)
}

/** MVI events accepted by the reader. */
internal sealed interface ReaderIntent {
    data class OpenNovel(val novelId: String) : ReaderIntent
    data class GoToChapter(val chapter: Int) : ReaderIntent
    data class SetPage(val page: Int) : ReaderIntent
    data class SetLayout(val layout: ReaderLayoutState) : ReaderIntent
    data class Search(val query: String) : ReaderIntent
    data class UpdateSearchQuery(val query: String) : ReaderIntent
    data object ClearError : ReaderIntent
}

internal class ReaderViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(ReaderState())
    private var chapterLoadJob: Job? = null
    private var searchJob: Job? = null
    val state: StateFlow<ReaderState> = mutableState.asStateFlow()

    fun dispatch(intent: ReaderIntent) {
        when (intent) {
            is ReaderIntent.OpenNovel -> open(intent.novelId)
            is ReaderIntent.GoToChapter -> goToChapter(intent.chapter)
            is ReaderIntent.SetPage -> {
                update { it.copy(currentPage = intent.page.coerceAtLeast(0)) }
                persistProgress()
            }
            is ReaderIntent.SetLayout -> update { it.copy(layout = intent.layout) }
            is ReaderIntent.Search -> search(intent.query)
            is ReaderIntent.UpdateSearchQuery -> update { it.copy(searchQuery = intent.query) }
            ReaderIntent.ClearError -> update { it.copy(error = null) }
        }
    }

    private fun open(id: String) {
        if (id.isBlank() || mutableState.value.novelId == id && !mutableState.value.isStarting) return
        mutableState.value = ReaderState(novelId = id, isStarting = true)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val chapters = NovelStore.getChapters(appContext, id)
                    val progress = NovelStore.getProgress(appContext, id)
                    chapters to progress
                }
            }.onSuccess { (chapters, progress) ->
                val chapter = progress.chapterNumber.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
                mutableState.value = mutableState.value.copy(
                    chapters = chapters,
                    currentChapter = chapter,
                    currentPage = progress.pageIndex.coerceAtLeast(0),
                    isStarting = false
                )
                loadChapter(chapter)
            }.onFailure { error ->
                update { it.copy(isStarting = false, error = error.message ?: "Unable to open novel") }
            }
        }
    }

    private fun goToChapter(requested: Int) {
        val old = mutableState.value
        if (old.chapters.isEmpty()) return
        val target = requested.coerceIn(0, old.lastChapter)
        if (target == old.currentChapter) return
        // Save the chapter being left before switching the single active chapter.
        persistProgress()
        val pages = old.pageByChapter + (old.currentChapter to old.currentPage)
        mutableState.value = old.copy(
            currentChapter = target,
            currentPage = pages[target] ?: 0,
            pageByChapter = pages,
            chapterBlocks = emptyList(),
            isLoadingChapter = true
        )
        loadChapter(target)
    }

    /** Load and parse one chapter only; no neighbour prefetch or parsed disk cache. */
    private fun loadChapter(chapter: Int) {
        val snapshot = mutableState.value
        val id = snapshot.novelId
        chapterLoadJob?.cancel()
        chapterLoadJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    NovelStore.getChapterBlocks(appContext, id, chapter).toBlocks()
                }
            }.onSuccess { blocks ->
                // Ignore a result if the user navigated while disk I/O was running.
                if (mutableState.value.novelId == id && mutableState.value.currentChapter == chapter) {
                    update { it.copy(isLoadingChapter = false, chapterBlocks = blocks) }
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (mutableState.value.novelId == id && mutableState.value.currentChapter == chapter) {
                    update { it.copy(isLoadingChapter = false, chapterBlocks = emptyList(), error = error.message ?: "Chapter load failed") }
                }
            }
        }
    }

    private fun search(query: String) {
        if (query.isBlank()) return
        val snapshot = mutableState.value
        mutableState.value = snapshot.copy(isSearching = true, searchQuery = query, searchResults = emptyList(), error = null)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    buildList {
                        // Search is deliberately sequential: at most one chapter is parsed at a time.
                        for (chapter in snapshot.chapters) {
                            if (size >= 200) break
                            val blocks = NovelStore.getChapterBlocks(appContext, snapshot.novelId, chapter.number).toBlocks()
                            for (block in blocks) {
                                val body = if (block.heading) block.title else block.segments.joinToString("") { it.text }
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
                update { it.copy(isSearching = false, searchResults = results) }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                update { it.copy(isSearching = false, error = error.message ?: "Search failed") }
            }
        }
    }

    fun persistProgress() {
        persistSnapshot(mutableState.value)
    }

    private fun persistSnapshot(s: ReaderState) {
        if (s.novelId.isBlank() || s.chapters.isEmpty()) return
        val chapter = s.chapters.getOrNull(s.currentChapter) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            NovelStore.saveProgress(
                appContext,
                s.novelId,
                textforge.storage.ReadingProgress(s.currentChapter, 0, chapter.startByte, s.currentPage)
            )
        }
    }

    private inline fun update(transform: (ReaderState) -> ReaderState) {
        mutableState.value = transform(mutableState.value)
    }
}
