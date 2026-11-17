package textforge.storage

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import textforge.parser.ChapterIndex
import textforge.parser.ChapterIndexer
import textforge.parser.NovelBlock
import textforge.parser.NovelParser
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

/**
 * The single persistence boundary for novels.
 *
 * Only the original text, chapter byte index, metadata and reading progress are
 * persisted. Parsed chapter data is deliberately NOT written to disk: the
 * reader asks for one chapter, which is read and parsed on an IO dispatcher.
 */
object NovelStore {
    private const val SESSION_PREFS = "reading_session"
    private const val LAST_NOVEL_ID = "last_novel_id"

    private fun root(context: Context) =
        File(context.filesDir, "novels").apply { mkdirs() }

    private fun folder(context: Context, id: String) = File(root(context), id)

    fun saveLastNovelId(context: Context, id: String) {
        context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .edit().putString(LAST_NOVEL_ID, id).apply()
    }

    fun getRestorableNovelId(context: Context): String? = runCatching {
        val id = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .getString(LAST_NOVEL_ID, null) ?: return null
        val dir = folder(context, id)
        val metadata = File(dir, "metadata.json")
        val index = File(dir, "index.json")
        if (!metadata.isFile || !index.isFile) return null
        if (JSONObject(metadata.readText()).optString("id") != id) return null
        id
    }.getOrNull()

    /** Copies the selected document once and creates only a lightweight byte index. */
    fun importNovel(context: Context, uri: Uri): String {
        val id = UUID.randomUUID().toString()
        val dir = folder(context, id)
        check(dir.mkdirs() || dir.isDirectory) { "Unable to create novel storage folder" }
        try {
            val source = File(dir, "source.md")
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Unable to open selected file")
            input.use { stream -> source.outputStream().use { output -> stream.copyTo(output) } }

            val title = context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }?.takeIf { it.isNotBlank() } ?: "Untitled"

            JSONObject().apply {
                put("id", id)
                put("title", title)
                put("sourceUri", uri.toString())
            }.write(File(dir, "metadata.json"))

            createChapterIndex(context, id)
            saveProgress(context, id, ReadingProgress(0, 0, 0L, 0))
            return id
        } catch (error: Throwable) {
            dir.deleteRecursively()
            throw error
        }
    }

    private fun createChapterIndex(context: Context, id: String) {
        val chapters = ChapterIndexer.index(File(folder(context, id), "source.md").readBytes())
        val json = JSONArray().apply {
            chapters.forEach { chapter ->
                put(JSONObject().apply {
                    put("number", chapter.number)
                    put("title", chapter.title)
                    put("startByte", chapter.startByte)
                    put("endByte", chapter.endByte)
                })
            }
        }
        JSONObject().put("chapters", json).write(File(folder(context, id), "index.json"))
    }

    fun getLibrary(context: Context): List<NovelMetadata> =
        root(context).listFiles()?.mapNotNull { dir ->
            val metadata = File(dir, "metadata.json")
            val index = File(dir, "index.json")
            if (!metadata.isFile || !index.isFile) return@mapNotNull null
            runCatching {
                val json = JSONObject(metadata.readText())
                NovelMetadata(json.getString("id"), json.getString("title"), json.optString("sourceUri"))
            }.getOrNull()
        }?.sortedBy { it.title.lowercase() } ?: emptyList()

    fun deleteNovel(context: Context, id: String): Boolean {
        val removed = File(root(context), id).deleteRecursively()
        val prefs = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(LAST_NOVEL_ID, null) == id) {
            prefs.edit().remove(LAST_NOVEL_ID).apply()
        }
        return removed
    }

    fun getChapters(context: Context, id: String): List<ChapterIndex> {
        val array = JSONObject(File(folder(context, id), "index.json").readText())
            .getJSONArray("chapters")
        return List(array.length()) { i ->
            val item = array.getJSONObject(i)
            ChapterIndex(
                number = item.getInt("number"),
                title = item.getString("title"),
                startByte = item.getLong("startByte"),
                endByte = item.getLong("endByte")
            )
        }
    }

    /** Read and parse exactly one chapter from its byte range. No parsed cache. */
    fun getChapterBlocks(context: Context, id: String, chapter: Int): List<NovelBlock> {
        val metadata = getChapters(context, id).getOrNull(chapter)
            ?: error("Chapter $chapter does not exist")
        val length = (metadata.endByte - metadata.startByte).coerceAtLeast(0L)
        require(length <= Int.MAX_VALUE) { "Chapter is too large to read" }
        val bytes = ByteArray(length.toInt())
        RandomAccessFile(File(folder(context, id), "source.md"), "r").use { source ->
            source.seek(metadata.startByte)
            source.readFully(bytes)
        }
        return NovelParser.parse(bytes.toString(Charsets.UTF_8)).blocks
    }

    fun saveProgress(context: Context, id: String, progress: ReadingProgress) {
        JSONObject().apply {
            put("chapterNumber", progress.chapterNumber)
            put("blockIndex", progress.blockIndex)
            put("byteOffset", progress.byteOffset)
            put("pageIndex", progress.pageIndex)
        }.write(File(folder(context, id), "progress.json"))
    }

    fun getProgress(context: Context, id: String): ReadingProgress {
        val file = File(folder(context, id), "progress.json")
        if (!file.isFile) return ReadingProgress(0, 0, 0L)
        val json = JSONObject(file.readText())
        return ReadingProgress(
            json.optInt("chapterNumber", 0),
            json.optInt("blockIndex", 0),
            json.optLong("byteOffset", 0L),
            json.optInt("pageIndex", 0)
        )
    }

    private fun JSONObject.write(file: File) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(toString(2))
        if (!temporary.renameTo(file)) {
            file.writeText(temporary.readText())
            temporary.delete()
        }
    }
}

data class NovelMetadata(
    val id: String,
    val title: String,
    val sourceUri: String
)

data class ReadingProgress(
    val chapterNumber: Int,
    val blockIndex: Int,
    val byteOffset: Long,
    val pageIndex: Int = 0
)
