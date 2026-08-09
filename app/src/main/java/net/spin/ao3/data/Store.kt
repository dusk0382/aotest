package net.spin.ao3.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.spin.ao3.data.model.ChapterInfo
import net.spin.ao3.data.model.WorkSummary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local persistence for favorites, reading history, downloads and reader
 * preferences, stored as a single JSON file (personal-use scale).
 */
class Store(context: Context) {

    enum class ReaderTheme(val label: String) {
        LIGHT("Claro"),
        SEPIA("Sepia"),
        DARK("Oscuro"),
        /** Pure black background — OLED/AMOLED screens turn pixels off. */
        BLACK("AMOLED"),
    }

    class Prefs {
        var fontSizeSp: Int = 18
        var theme: ReaderTheme = ReaderTheme.DARK
        var serif: Boolean = true
        var lineHeight: Float = 1.75f
        /** 0 = estrecho, 1 = normal, 2 = amplio. */
        var margins: Int = 1
        var appThemeMode: String = "SYSTEM"
        var commentName: String = ""
        var commentEmail: String = ""
    }

    data class SavedWork(
        val id: Long,
        val title: String,
        val author: String,
        val fandoms: List<String>,
        val rating: String?,
        val words: Long,
        val chapters: Int,
        val savedAt: Long,
    )

    data class HistoryEntry(
        val id: Long,
        val title: String,
        val author: String,
        val chapterIndex: Int,
        val scrollRatio: Float,
        /** Chapter index -> scroll ratio (0..1) for the whole work. */
        val chapterProgress: Map<Int, Float> = emptyMap(),
        val at: Long,
    )

    data class Download(
        val id: Long,
        val title: String,
        val chapters: List<ChapterInfo>,
        val downloadedAt: Long,
    )

    private val file = File(context.filesDir, "ao3_library.json")
    private val mutex = Mutex()

    val prefs = Prefs()

    private var root: JSONObject = load()

    // ---- Saved (favorites) --------------------------------------------------

    fun savedWorks(): List<SavedWork> = root.optJSONArray("saved")?.let { arr ->
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.toSavedWork() }
    }.orEmpty()

    fun isSaved(id: Long): Boolean = root.optJSONArray("saved")?.let { arr ->
        (0 until arr.length()).any { arr.optJSONObject(it)?.optLong("id") == id }
    } ?: false

    fun saveWork(work: WorkSummary) {
        if (isSaved(work.id)) return
        val arr = root.optJSONArray("saved") ?: JSONArray()
        arr.put(JSONObject().apply {
            put("id", work.id)
            put("title", work.title)
            put("author", work.author)
            put("fandoms", JSONArray(work.fandoms))
            put("rating", work.rating ?: "")
            put("words", work.words)
            put("chapters", work.chapterCount)
            put("savedAt", System.currentTimeMillis())
        })
        root.put("saved", arr)
        persist()
    }

    fun removeSaved(id: Long) {
        val arr = root.optJSONArray("saved") ?: return
        val filtered = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
            if (obj?.optLong("id") != id) filtered.put(obj)
        }
        root.put("saved", filtered)
        persist()
    }

    // ---- History ------------------------------------------------------------

    fun history(): List<HistoryEntry> = root.optJSONArray("history")?.let { arr ->
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.toHistory() }
            .sortedByDescending { it.at }
    }.orEmpty()

    fun updateHistory(entry: HistoryEntry) {
        val arr = root.optJSONArray("history") ?: JSONArray()
        val filtered = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
            if (obj?.optLong("id") != entry.id) filtered.put(obj)
        }
        filtered.put(JSONObject().apply {
            put("id", entry.id)
            put("title", entry.title)
            put("author", entry.author)
            put("chapter", entry.chapterIndex)
            put("scroll", entry.scrollRatio)
            put("at", entry.at)
            put("progress", JSONObject().apply {
                entry.chapterProgress.forEach { (k, v) -> put(k.toString(), v.toDouble()) }
            })
        })
        root.put("history", filtered)
        persist()
    }

    fun removeHistory(id: Long) {
        val arr = root.optJSONArray("history") ?: return
        val filtered = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
            if (obj?.optLong("id") != id) filtered.put(obj)
        }
        root.put("history", filtered)
        persist()
    }

    fun clearHistory() {
        root.put("history", JSONArray())
        persist()
    }

    // ---- Downloads ----------------------------------------------------------

    fun downloads(): List<Download> = root.optJSONArray("downloads")?.let { arr ->
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.toDownload() }
    }.orEmpty()

    fun download(id: Long): Download? = root.optJSONArray("downloads")?.let { arr ->
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
            if (obj?.optLong("id") == id) return obj.toDownload()
        }
        null
    }

    fun isDownloaded(id: Long): Boolean = download(id) != null

    fun downloadedChapterIds(id: Long): Set<Int> =
        download(id)?.chapters?.map { it.index }?.toSet() ?: emptySet()

    fun saveDownload(id: Long, title: String, chapters: List<ChapterInfo>) {
        removeDownload(id)
        val arr = root.optJSONArray("downloads") ?: JSONArray()
        arr.put(JSONObject().apply {
            put("id", id)
            put("title", title)
            put("downloadedAt", System.currentTimeMillis())
            put("chapters", JSONArray().apply {
                chapters.forEach { ch -> put(ch.toJson()) }
            })
        })
        root.put("downloads", arr)
        persist()
    }

    /** Adds (or replaces) one chapter inside an existing download record. */
    fun addDownloadedChapter(workId: Long, title: String, chapter: ChapterInfo) {
        val existing = download(workId)
        val merged = (existing?.chapters.orEmpty().filter { it.index != chapter.index } + chapter)
            .sortedBy { it.index }
        saveDownload(workId, existing?.title ?: title, merged)
    }

    /** Removes a single downloaded chapter; drops the record when it was the last one. */
    fun removeDownloadedChapter(workId: Long, index: Int) {
        val dl = download(workId) ?: return
        val left = dl.chapters.filter { it.index != index }
        if (left.isEmpty()) removeDownload(workId) else saveDownload(workId, dl.title, left)
    }

    fun removeDownload(id: Long) {
        val arr = root.optJSONArray("downloads") ?: return
        val filtered = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
            if (obj?.optLong("id") != id) filtered.put(obj)
        }
        root.put("downloads", filtered)
        persist()
    }

    // ---- Persistence --------------------------------------------------------

    private fun load(): JSONObject {
        prefs.apply {
            fontSizeSp = 18
            theme = ReaderTheme.DARK
            serif = true
            lineHeight = 1.75f
            margins = 1
            appThemeMode = "SYSTEM"
            commentName = ""
            commentEmail = ""
        }
        if (!file.exists()) return JSONObject()
        return try {
            val obj = JSONObject(file.readText())
            val p = obj.optJSONObject("prefs")
            prefs.fontSizeSp = p?.optInt("fontSize", 18) ?: 18
            prefs.theme = try {
                ReaderTheme.valueOf(p?.optString("theme", "DARK") ?: "DARK")
            } catch (_: Exception) {
                ReaderTheme.DARK
            }
            prefs.serif = p?.optBoolean("serif", true) ?: true
            prefs.lineHeight = p?.optDouble("lineHeight", 1.75)?.toFloat() ?: 1.75f
            prefs.margins = p?.optInt("margins", 1) ?: 1
            prefs.appThemeMode = p?.optString("appThemeMode", "SYSTEM") ?: "SYSTEM"
            prefs.commentName = p?.optString("commentName", "") ?: ""
            prefs.commentEmail = p?.optString("commentEmail", "") ?: ""
            obj
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun persist() {
        root.put("prefs", JSONObject().apply {
            put("fontSize", prefs.fontSizeSp)
            put("theme", prefs.theme.name)
            put("serif", prefs.serif)
            put("lineHeight", prefs.lineHeight.toDouble())
            put("margins", prefs.margins)
            put("appThemeMode", prefs.appThemeMode)
            put("commentName", prefs.commentName)
            put("commentEmail", prefs.commentEmail)
        })
        // Write asynchronously to keep the UI snappy
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    try {
                        file.parentFile?.mkdirs()
                        file.writeText(root.toString())
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    fun savePrefs() = persist()

    // ---- JSON mappers -------------------------------------------------------

    private fun JSONObject.toSavedWork(): SavedWork = SavedWork(
        id = optLong("id"),
        title = optString("title", ""),
        author = optString("author", ""),
        fandoms = optJSONArray("fandoms")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }
        }.orEmpty(),
        rating = optString("rating", "").ifEmpty { null },
        words = optLong("words"),
        chapters = optInt("chapters", 1),
        savedAt = optLong("savedAt"),
    )

    private fun JSONObject.toHistory(): HistoryEntry = HistoryEntry(
        id = optLong("id"),
        title = optString("title", ""),
        author = optString("author", ""),
        chapterIndex = optInt("chapter", 0),
        scrollRatio = optDouble("scroll", 0.0).toFloat(),
        chapterProgress = optJSONObject("progress")?.let { p ->
            val m = mutableMapOf<Int, Float>()
            val it = p.keys()
            while (it.hasNext()) {
                val k = it.next()
                val idx = k.toIntOrNull()
                val v = p.optDouble(k, 0.0)
                if (idx != null && v > 0.0) m[idx] = v.toFloat()
            }
            m
        } ?: emptyMap(),
        at = optLong("at"),
    )

    private fun JSONObject.toDownload(): Download = Download(
        id = optLong("id"),
        title = optString("title", ""),
        downloadedAt = optLong("downloadedAt"),
        chapters = optJSONArray("chapters")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.toChapter() }
        }.orEmpty(),
    )

    private fun ChapterInfo.toJson(): JSONObject = JSONObject().apply {
        put("index", index)
        put("title", title)
        put("url", url ?: "")
        chapterId?.let { put("chapterId", it) }
        put("content", content?.takeIf { it.isNotBlank() } ?: "")
    }

    private fun JSONObject.toChapter(): ChapterInfo = ChapterInfo(
        index = optInt("index", 0),
        title = optString("title", ""),
        url = optString("url", "").ifEmpty { null },
        chapterId = if (has("chapterId")) optLong("chapterId") else null,
        content = optString("content", "").ifEmpty { null },
    )
}
