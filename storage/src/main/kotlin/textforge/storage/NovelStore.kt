package textforge.storage

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import textforge.parser.ChapterIndexer
import textforge.parser.ChapterIndex
import textforge.parser.NovelParser
import java.io.File
import java.util.UUID

object NovelStore {
    private const val SESSION_PREFS = "reading_session"
    private const val LAST_NOVEL_ID = "last_novel_id"

    fun saveLastNovelId(context: Context, id: String) {
    context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(LAST_NOVEL_ID, id)
        .apply()
}

    fun getRestorableNovelId(context: Context): String? {
        return try {
            val id = context.getSharedPreferences(
                SESSION_PREFS, Context.MODE_PRIVATE
            ).getString(LAST_NOVEL_ID, null) ?: return null
            val dir = File(context.filesDir, "novels/$id")
            if (!dir.isDirectory) return null
            val metadata = File(dir, "metadata.json")
            val index = File(dir, "index.json")
            val progress = File(dir, "progress.json")
            if (!metadata.isFile || !index.isFile || !progress.isFile) return null
            if (JSONObject(metadata.readText()).optString("id") != id) return null
            id
        } catch (_: Exception) {
            null
        }
    }

    private fun root(context: Context) =
        File(context.filesDir, "novels").apply { mkdirs() }

    private fun folder(context: Context, id: String) =
        File(root(context), id).apply { mkdirs() }

    fun importNovel(context: Context, uri: Uri): String {
        val id = UUID.randomUUID().toString()
        val dir = folder(context, id)

        val source = File(dir, "source.md")

        context.contentResolver.openInputStream(uri)!!.use { input ->
            source.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val title = context.contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else "Untitled"
        } ?: "Untitled"

        JSONObject().apply {
            put("id", id)
            put("title", title)
            put("sourceUri", uri.toString())
        }.write(File(dir, "metadata.json"))

        try {
            buildIndex(context, id)
        } catch (e: Throwable) {
            dir.deleteRecursively()
            throw e
        }

        return id
    }

    fun buildIndex(context: Context, id: String) {
        val dir = folder(context, id)
        val bytes = File(dir, "source.md").readBytes()
        val chapters = ChapterIndexer.index(bytes)

        val indexJson = JSONArray()
        chapters.forEach { chapter ->
            indexJson.put(
                JSONObject().apply {
                    put("number", chapter.number)
                    put("title", chapter.title)
                    put("startByte", chapter.startByte)
                    put("endByte", chapter.endByte)
                }
            )
        }

        JSONObject().put("chapters", indexJson)
            .write(File(dir, "index.json"))

        saveProgress(
            context,
            id,
            ReadingProgress(0, 0, chapters.firstOrNull()?.startByte ?: 0L, 0)
        )
    }

    fun getLibrary(context: Context): List<NovelMetadata> {
        return root(context).listFiles()?.mapNotNull { dir ->
            val file = File(dir, "metadata.json")
            if (!file.exists()) return@mapNotNull null
            if (!File(dir, "index.json").exists()) return@mapNotNull null

            val json = JSONObject(file.readText())
            NovelMetadata(
                id = json.getString("id"),
                title = json.getString("title"),
                sourceUri = json.getString("sourceUri")
            )
        } ?: emptyList()
    }

    /** Remove the source, byte index, metadata, and reading progress for a book. */
    fun deleteNovel(context: Context, id: String): Boolean {
        val removed = File(root(context), id).deleteRecursively()
        val prefs = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(LAST_NOVEL_ID, null) == id) {
            prefs.edit().remove(LAST_NOVEL_ID).apply()
        }
        return removed
    }

    fun getChapters(context: Context, id: String): List<ChapterIndex> {
        val json = JSONObject(
            File(folder(context, id), "index.json").readText()
        )

        val array = json.getJSONArray("chapters")

        return List(array.length()) { i ->
            val item = array.getJSONObject(i)
            ChapterIndex(
                item.getInt("number"),
                item.getString("title"),
                item.getLong("startByte"),
                item.getLong("endByte")
            )
        }
    }

    /**
     * Parse exactly one chapter from the original source file.
     *
     * Parsed blocks are intentionally not written to disk or retained in a
     * cross-chapter cache. The byte index is the only chapter-level storage.
     */
    fun getChapterBlocks(
        context: Context,
        id: String,
        chapter: Int
    ): JSONArray {
        val meta = getChapters(context, id).firstOrNull { it.number == chapter }
            ?: return JSONArray()
        val source = File(folder(context, id), "source.md")
        val length = (meta.endByte - meta.startByte).coerceAtLeast(0L)
        require(length <= Int.MAX_VALUE) { "Chapter is too large to parse" }

        val bytes = ByteArray(length.toInt())
        java.io.RandomAccessFile(source, "r").use { file ->
            file.seek(meta.startByte)
            file.readFully(bytes)
        }

        val document = NovelParser.parse(bytes.toString(Charsets.UTF_8))
        return JSONArray().apply {
            document.blocks.forEach { block ->
                put(
                    when (block) {
                        is textforge.parser.NovelBlock.Heading -> JSONObject().apply {
                            put("type", "heading")
                            put("level", block.level)
                            put("title", block.title)
                        }
                        is textforge.parser.NovelBlock.Paragraph -> JSONObject().apply {
                            put("type", "paragraph")
                            put("segments", JSONArray().apply {
                                block.segments.forEach { segment ->
                                    put(JSONObject().apply {
                                        put("text", segment.text)
                                        put("highlighted", segment.highlighted)
                                        put("italic", segment.italic)
                                    })
                                }
                            })
                            put("fullHighlight", block.fullHighlight)
                        }
                    }
                )
            }
        }
    }

    fun saveProgress(
        context: Context,
        id: String,
        progress: ReadingProgress
    ) {
        JSONObject().apply {
            put("chapterNumber", progress.chapterNumber)
            put("blockIndex", progress.blockIndex)
            put("byteOffset", progress.byteOffset)
            put("pageIndex", progress.pageIndex)
        }.write(File(folder(context, id), "progress.json"))
    }

    fun getProgress(context: Context, id: String): ReadingProgress {
        val file = File(folder(context, id), "progress.json")
        if (!file.exists()) return ReadingProgress(0, 0, 0L)

        val json = JSONObject(file.readText())
        return ReadingProgress(
            json.getInt("chapterNumber"),
            json.getInt("blockIndex"),
            json.getLong("byteOffset"),
            json.optInt("pageIndex", 0)
        )
    }

    private fun JSONObject.write(file: File) {
        file.writeText(toString(2))
    }
}
