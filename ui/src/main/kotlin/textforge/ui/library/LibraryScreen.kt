package textforge.ui.library

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import textforge.storage.NovelMetadata
import textforge.storage.NovelStore


/** One immutable library snapshot, including the state of the single import flow. */
data class LibraryState(
    val novels: List<NovelMetadata> = emptyList(),
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val deleteTarget: NovelMetadata? = null,
    val importedNovelId: String? = null,
    val error: String? = null
)

sealed interface LibraryIntent {
    data object Refresh : LibraryIntent
    data class Import(val uri: Uri) : LibraryIntent
    data class RequestDelete(val novel: NovelMetadata) : LibraryIntent
    data object DismissDelete : LibraryIntent
    data class Delete(val id: String) : LibraryIntent
    data class Loaded(val novels: List<NovelMetadata>) : LibraryIntent
    data class Imported(val id: String, val novels: List<NovelMetadata>) : LibraryIntent
    data class Failed(val message: String) : LibraryIntent
    data object Deleted : LibraryIntent
    data object ConsumeImported : LibraryIntent
}

/** Library side effects are centralized here; the composable only renders state. */
class LibraryViewModel(private val appContext: Context) : ViewModel() {
    private val mutableState = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = mutableState.asStateFlow()
    private var libraryJob: kotlinx.coroutines.Job? = null

    fun dispatch(intent: LibraryIntent) {
        when (intent) {
            LibraryIntent.Refresh -> refresh()
            is LibraryIntent.Import -> import(intent.uri)
            is LibraryIntent.Delete -> delete(intent.id)
            else -> reduce(intent)
        }
    }

    private fun reduce(intent: LibraryIntent) {
        val old = mutableState.value
        mutableState.value = when (intent) {
            is LibraryIntent.RequestDelete -> old.copy(deleteTarget = intent.novel)
            LibraryIntent.DismissDelete -> old.copy(deleteTarget = null)
            is LibraryIntent.Loaded -> old.copy(novels = intent.novels, isLoading = false, error = null)
            is LibraryIntent.Imported -> old.copy(
                novels = intent.novels,
                isLoading = false,
                isImporting = false,
                importedNovelId = intent.id,
                error = null
            )
            is LibraryIntent.Failed -> old.copy(isLoading = false, isImporting = false, error = intent.message)
            LibraryIntent.Deleted -> old.copy(deleteTarget = null)
            LibraryIntent.ConsumeImported -> old.copy(importedNovelId = null)
            LibraryIntent.Refresh, is LibraryIntent.Import, is LibraryIntent.Delete -> old
        }
    }

    private fun refresh() {
        libraryJob?.cancel()
        libraryJob = viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { NovelStore.getLibrary(appContext) } }
                .onSuccess { reduce(LibraryIntent.Loaded(it)) }
                .onFailure { reduce(LibraryIntent.Failed(it.message ?: "Unable to load library")) }
        }
    }

    private fun import(uri: Uri) {
        libraryJob?.cancel()
        mutableState.value = mutableState.value.copy(isImporting = true, error = null)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val id = NovelStore.importNovel(appContext, uri)
                    id to NovelStore.getLibrary(appContext)
                }
            }.onSuccess { (id, novels) -> reduce(LibraryIntent.Imported(id, novels)) }
                .onFailure { reduce(LibraryIntent.Failed("Import failed: ${it.message ?: "unknown error"}")) }
        }
    }

    private fun delete(id: String) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { NovelStore.deleteNovel(appContext, id) } }
                .onSuccess {
                    reduce(LibraryIntent.Deleted)
                    refresh()
                }
                .onFailure { reduce(LibraryIntent.Failed(it.message ?: "Delete failed")) }
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LibraryViewModel::class.java))
            return LibraryViewModel(appContext) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onOpenNovel: (String) -> Unit,
    incomingUri: Uri? = null,
    onIncomingUriConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val model: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory(context))
    val state by model.state.collectAsState()
    val dispatch: (LibraryIntent) -> Unit = model::dispatch
    fun importUri(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        dispatch(LibraryIntent.Import(uri))
    }

    LaunchedEffect(Unit) { dispatch(LibraryIntent.Refresh) }
    LaunchedEffect(incomingUri) {
        incomingUri?.let {
            importUri(it)
            onIncomingUriConsumed()
        }
    }
    LaunchedEffect(state.importedNovelId) {
        state.importedNovelId?.let { id ->
            dispatch(LibraryIntent.ConsumeImported)
            onOpenNovel(id)
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            "Your Library",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Your personal reading space.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        when {
            state.isLoading -> Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.novels.isEmpty() -> Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Your library is waiting", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text("Add your first novel to start reading.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(22.dp))
                    NovelFilePicker(
                        importing = state.isImporting,
                        onSelected = ::importUri
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.novels, key = { it.id }) { novel ->
                        Card(
                            modifier = Modifier.fillMaxWidth().combinedClickable(
                                onClick = { onOpenNovel(novel.id) },
                                onLongClick = { dispatch(LibraryIntent.RequestDelete(novel)) }
                            ),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(novel.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Tap to read · Long-press to delete",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Default.Delete, contentDescription = "Delete book", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                NovelFilePicker(
                    importing = state.isImporting,
                    onSelected = ::importUri
                )
            }
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }

    state.deleteTarget?.let { novel ->
        AlertDialog(
            onDismissRequest = { dispatch(LibraryIntent.DismissDelete) },
            title = { Text("Delete book?") },
            text = { Text("“${novel.title}” and its reading progress will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = { dispatch(LibraryIntent.Delete(novel.id)) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { dispatch(LibraryIntent.DismissDelete) }) { Text("Cancel") }
            }
        )
    }
}

/** The picker only returns a Uri. Importing and error handling belong to the Model. */
@Composable
fun NovelFilePicker(
    importing: Boolean,
    onSelected: (Uri) -> Unit
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        onSelected(uri)
    }
    if (importing) {
        Text("Importing… keep the app open")
    } else {
        Button(onClick = { picker.launch(arrayOf("text/*", "text/markdown")) }) {
            Text("Import novel")
        }
    }
}
