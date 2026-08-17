package net.spin.ao3.data

import android.content.Context
import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import net.spin.ao3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
 * preferences.
 *
 * Data is split across TWO files so the hot write path stays small:
 *  - [file] (`ao3_library.json`): favorites, kudos, history, prefs and the
 *    pending download queue — tiny, rewritten constantly while reading.
 *  - [downloadsFile] (`ao3_downloads.json`): downloaded chapters (full HTML,
 *    tens of KB each) — large, only rewritten when a download changes.
 * This way the reader saving progress every few seconds never rewrites the
 * megabytes of downloaded content.
 *
 * Thread-safety: the JSON roots are mutated from the main thread (reader,
 * screens) AND from the download queue service (IO), so every public method
 * synchronizes on [lock]. Persistence captures a consistent snapshot on the
 * caller thread and writes it atomically (temp file + rename) through a
 * single-writer loop, so the last requested state always ends up on disk and a
 * crash mid-write never corrupts the files.
 */
class Store(rootFile: File) {
    /** Convenience constructor used by the app (files live in filesDir). */
    constructor(context: Context) : this(File(context.filesDir, "ao3_library.json"))

    enum class ReaderTheme(@StringRes val labelRes: Int) {
        LIGHT(R.string.theme_light),
        SEPIA(R.string.theme_sepia),
        DARK(R.string.theme_dark),
        /** Pure black background — OLED/AMOLED screens turn pixels off. */
        BLACK(R.string.theme_black),
    }

    class Prefs {
        var fontSizeSp: Int = 18
        var theme: ReaderTheme = ReaderTheme.DARK
        var serif: Boolean = true
        var lineHeight: Float = 1.75f
        /** 0 = estrecho, 1 = normal, 2 = amplio. */
        var margins: Int = 1
        /** Modo de lectura: false = scroll continuo (WebView), true = paginado. */
        var paged: Boolean = false
        var appThemeMode: String = "SYSTEM"
        /** Wallpaper-derived Material You colors on Android 12+ (opt-in). */
        var dynamicColor: Boolean = false
        var commentName: String = ""
        var commentEmail: String = ""
        /** BCP-47 target language for the reader translator ("" = off). */
        var translationLang: String = "es"
        /** Reader text-to-speech speech rate (0.5..2.0). */
        var ttsRate: Float = 1.0f
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

    private val file = rootFile
    // Derived from the root file name so every Store instance (and test) owns a
    // distinct downloads file instead of colliding on a shared "ao3_downloads".
    private val downloadsFile = File(rootFile.parentFile, rootFile.nameWithoutExtension + "_downloads.json")
    /** Guards [root], [downloadsRoot] and the snapshots against concurrent main/IO access. */
    private val lock = Any()
    private val writeMutex = Mutex()
    // Declared before the init block below: migrateDownloads() can persist the
    // split downloads file during construction, so io must already exist.
    private val io = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val prefs = Prefs()

    private var root: JSONObject = load()
    private var downloadsRoot: JSONObject = loadDownloads()

    init {
        migrateDownloads()
    }

    /** Splits a legacy single-file library (downloads inside the main JSON) into the new layout. */
    private fun migrateDownloads() {
        synchronized(lock) {
            val legacy = root.optJSONArray("downloads")
            if (legacy != null && legacy.length() > 0 && !downloadsFile.exists()) {
                downloadsRoot.put("downloads", legacy)
                persistDownloadsLocked()
            }
            // The small file never needs to keep (or re-write) the big array.
            root.remove("downloads")
            persist(immediate = true)
        }
    }

    // ---- Saved (favorites) --------------------------------------------------

    fun savedWorks(): List<SavedWork> = synchronized(lock) {
        root.optJSONArray("saved")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.toSavedWork() }
        }.orEmpty()
    }

    fun isSaved(id: Long): Boolean = synchronized(lock) {
        root.optJSONArray("saved")?.let { arr ->
            (0 until arr.length()).any { arr.optJSONObject(it)?.optLong("id") == id }
        } ?: false
    }

    fun saveWork(work: WorkSummary) = synchronized(lock) {
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

    fun removeSaved(id: Long) = synchronized(lock) {
        val arr = root.optJSONArray("saved") ?: return
        val filtered = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
            if (obj?.optLong("id") != id) filtered.put(obj)
        }
        root.put("saved", filtered)
        persist()
    }

    // ---- Kudos given (guest kudos are one-per-cookie; remember per work) -----

    fun kudoedIds(): Set<Long> = synchronized(lock) {
        root.optJSONArray("kudoed")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optLong(i) }.toSet()
        } ?: emptySet()
    }

    fun isKudoed(id: Long): Boolean = synchronized(lock) { id in kudoedIds() }

    fun markKudoed(id: Long) = synchronized(lock) {
        if (isKudoed(id)) return
        val arr = root.optJSONArray("kudoed") ?: JSONArray()
        arr.put(id)
        root.put("kudoed", arr)
        persist()
    }

    // ---- History ------------------------------------------------------------

    fun history(): List<HistoryEntry> = synchronized(lock) {
        root.optJSONArray("history")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.toHistory() }
                .sortedByDescending { it.at }
        }.orEmpty()
    }

    // The reader saves progress every few seconds; scanning + sorting the whole
    // history for a single entry each time is wasted work, so single lookups go
    // through a cache invalidated on every history mutation.
    private var historyIndex: Map<Long, HistoryEntry>? = null

    /** O(1) lookup of one history entry (the reader's hot path). */
    fun historyEntry(id: Long): HistoryEntry? = synchronized(lock) {
        (historyIndex ?: buildHistoryIndex())[id]
    }

    private fun buildHistoryIndex(): Map<Long, HistoryEntry> {
        val m = root.optJSONArray("history")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.toHistory() }
                .associateBy { it.id }
        }.orEmpty()
        historyIndex = m
        return m
    }

    fun updateHistory(entry: HistoryEntry) = synchronized(lock) {
        historyIndex = null
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

    fun removeHistory(id: Long) = synchronized(lock) {
        historyIndex = null
        val arr = root.optJSONArray("history") ?: return
        val filtered = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
            if (obj?.optLong("id") != id) filtered.put(obj)
        }
        root.put("history", filtered)
        persist()
    }

    fun clearHistory() = synchronized(lock) {
        historyIndex = null
        root.put("history", JSONArray())
        persist()
    }

    // ---- Downloads (separate file, only rewritten when they change) ---------

    fun downloads(): List<Download> = synchronized(lock) {
        downloadsRoot.optJSONArray("downloads")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.toDownload() }
        }.orEmpty()
    }

    fun download(id: Long): Download? = synchronized(lock) {
        downloadsRoot.optJSONArray("downloads")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj?.optLong("id") == id) return obj.toDownload()
            }
            null
        }
    }

    fun isDownloaded(id: Long): Boolean = synchronized(lock) { download(id) != null }

    fun downloadedChapterIds(id: Long): Set<Int> = synchronized(lock) {
        download(id)?.chapters?.map { it.index }?.toSet() ?: emptySet()
    }

    fun saveDownload(id: Long, title: String, chapters: List<ChapterInfo>) = synchronized(lock) {
        removeDownload(id)
        val arr = downloadsRoot.optJSONArray("downloads") ?: JSONArray()
        arr.put(JSONObject().apply {
            put("id", id)
            put("title", title)
            put("downloadedAt", System.currentTimeMillis())
            put("chapters", JSONArray().apply {
                chapters.forEach { ch -> put(ch.toJson()) }
            })
        })
        downloadsRoot.put("downloads", arr)
        persistDownloads()
    }

    /** Adds (or replaces) one chapter inside an existing download record. */
    fun addDownloadedChapter(workId: Long, title: String, chapter: ChapterInfo) = synchronized(lock) {
        val existing = download(workId)
        val merged = (existing?.chapters.orEmpty().filter { it.index != chapter.index } + chapter)
            .sortedBy { it.index }
        saveDownload(workId, existing?.title ?: title, merged)
    }

    /** Removes a single downloaded chapter; drops the record when it was the last one. */
    fun removeDownloadedChapter(workId: Long, index: Int) = synchronized(lock) {
        val dl = download(workId) ?: return
        val left = dl.chapters.filter { it.index != index }
        if (left.isEmpty()) removeDownload(workId) else saveDownload(workId, dl.title, left)
    }

    fun removeDownload(id: Long) = synchronized(lock) {
        val arr = downloadsRoot.optJSONArray("downloads") ?: return
        val filtered = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
            if (obj?.optLong("id") != id) filtered.put(obj)
        }
        downloadsRoot.put("downloads", filtered)
        persistDownloads()
    }

    // ---- Pending download queue (persists across app restarts) --------------

    /** A queued download waiting to run (survives process death). */
    data class PendingJob(
        val workId: Long,
        val title: String,
        val chapters: List<ChapterInfo>,
    )

    fun pendingJobs(): List<PendingJob> = synchronized(lock) {
        root.optJSONArray("pending")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                PendingJob(
                    workId = o.optLong("workId"),
                    title = o.optString("title", ""),
                    chapters = o.optJSONArray("chapters")?.let { ca ->
                        (0 until ca.length()).mapNotNull { j -> ca.optJSONObject(j)?.toChapter() }
                    }.orEmpty(),
                )
            }
        }.orEmpty()
    }

    /** Adds a job, replacing any pending job for the same work. */
    fun addPendingJob(job: PendingJob) = synchronized(lock) {
        val arr = root.optJSONArray("pending") ?: JSONArray()
        val filtered = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o?.optLong("workId") != job.workId) filtered.put(o)
        }
        filtered.put(JSONObject().apply {
            put("workId", job.workId)
            put("title", job.title)
            put("chapters", JSONArray().apply {
                job.chapters.forEach { ch -> put(ch.toJson()) }
            })
        })
        root.put("pending", filtered)
        persist()
    }

    fun removePendingJob(workId: Long) = synchronized(lock) {
        val arr = root.optJSONArray("pending") ?: return
        val filtered = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o?.optLong("workId") != workId) filtered.put(o)
        }
        root.put("pending", filtered)
        persist()
    }

    // ---- Backup / restore ---------------------------------------------------

    /** Serializes the WHOLE library (favorites, history, prefs, downloads) for export. */
    fun exportBackup(): String = synchronized(lock) {
        JSONObject().apply {
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("library", root)
            put("downloads", downloadsRoot)
        }.toString()
    }

    /**
     * Replaces the whole library from a backup produced by [exportBackup].
     * Returns false (and leaves the store untouched) on malformed input.
     */
    fun importBackup(json: String): Boolean = synchronized(lock) {
        try {
            val bundle = JSONObject(json)
            val lib = bundle.optJSONObject("library")
            val dl = bundle.optJSONObject("downloads")
            if (lib == null && dl == null) return false
            if (lib != null) {
                root = lib
                applyPrefsFrom(root)
            }
            if (dl != null) downloadsRoot = dl
            historyIndex = null
            persist(immediate = true)
            persistDownloads(immediate = true)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ---- Persistence --------------------------------------------------------

    private fun load(): JSONObject {
        // Defaults first, then whatever the file overrides.
        prefs.apply {
            fontSizeSp = 18
            theme = ReaderTheme.DARK
            serif = true
            lineHeight = 1.75f
            margins = 1
            appThemeMode = "SYSTEM"
            commentName = ""
            commentEmail = ""
            translationLang = "es"
            ttsRate = 1.0f
        }
        if (!file.exists()) return JSONObject()
        return try {
            val obj = JSONObject(file.readText())
            applyPrefsFrom(obj)
            obj
        } catch (_: Exception) {
            JSONObject()
        }
    }

    /** Reads the reader/app prefs out of a library JSON object (load + import). */
    private fun applyPrefsFrom(obj: JSONObject) {
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
        prefs.paged = p?.optBoolean("paged", false) ?: false
        prefs.appThemeMode = p?.optString("appThemeMode", "SYSTEM") ?: "SYSTEM"
        prefs.dynamicColor = p?.optBoolean("dynamicColor", false) ?: false
        prefs.commentName = p?.optString("commentName", "") ?: ""
        prefs.commentEmail = p?.optString("commentEmail", "") ?: ""
        prefs.translationLang = p?.optString("translationLang", "es") ?: "es"
        prefs.ttsRate = p?.optDouble("ttsRate", 1.0)?.toFloat() ?: 1.0f
    }

    private fun loadDownloads(): JSONObject {
        if (!downloadsFile.exists()) return JSONObject()
        return try {
            JSONObject(downloadsFile.readText())
        } catch (_: Exception) {
            JSONObject()
        }
    }

    /** Latest requested snapshot; written by the single-writer [writeLatest]. */
    private var latestSnapshot: String? = null
    private var writeJob: Job? = null
    private var latestDownloadsSnapshot: String? = null
    private var downloadsWriteJob: Job? = null

    /**
     * Persists the in-memory [root] (small file) to disk, debounced with a
     * 300ms coalescing window: the reader calls this every 3.5s while reading —
     * bursts collapse into one write of the latest state. [immediate] bypasses
     * the window (settings apply, so a force-kill right after still keeps the
     * choice).
     *
     * The snapshot is captured synchronously on the caller thread (inside
     * [lock]), so the written bytes are always a coherent view of [root] — never
     * a torn read from a background thread. The single-writer loop guarantees
     * the LAST requested snapshot is the one that ends up on disk, so two rapid
     * persists can never land out of order.
     */
    private fun persist(immediate: Boolean = false) {
        val snapshot: String
        synchronized(lock) {
            root.put("prefs", JSONObject().apply {
                put("fontSize", prefs.fontSizeSp)
                put("theme", prefs.theme.name)
                put("serif", prefs.serif)
                put("lineHeight", prefs.lineHeight.toDouble())
                put("margins", prefs.margins)
                put("paged", prefs.paged)
                put("appThemeMode", prefs.appThemeMode)
                put("dynamicColor", prefs.dynamicColor)
                put("commentName", prefs.commentName)
                put("commentEmail", prefs.commentEmail)
                put("translationLang", prefs.translationLang)
                put("ttsRate", prefs.ttsRate.toDouble())
            })
            latestSnapshot = root.toString()
            snapshot = latestSnapshot!!
        }
        writeJob?.cancel()
        writeJob = io.launch {
            if (!immediate) delay(300)
            writeLatest()
        }
    }

    /** Same debounced single-writer pattern, but for the large downloads file. */
    private fun persistDownloads(immediate: Boolean = false) {
        val snapshot: String
        synchronized(lock) {
            latestDownloadsSnapshot = downloadsRoot.toString()
            snapshot = latestDownloadsSnapshot!!
        }
        downloadsWriteJob?.cancel()
        downloadsWriteJob = io.launch {
            if (!immediate) delay(300)
            writeDownloadsLatest()
        }
    }

    /** Must be called with [lock] held (migration path). */
    private fun persistDownloadsLocked() {
        val snapshot = downloadsRoot.toString()
        latestDownloadsSnapshot = snapshot
        downloadsWriteJob?.cancel()
        downloadsWriteJob = io.launch {
            delay(300)
            writeDownloadsLatest()
        }
    }

    /**
     * Writes the newest [latestSnapshot] atomically: temp file + rename, so a
     * crash or a cancelled write mid-flight never corrupts the library file
     * (a partial temp file is simply replaced next time). Writes are serialized
     * by [writeMutex]; each call reads the snapshot AT WRITE TIME (it never
     * "consumes" it) and only clears it when it is still the newest — so a
     * job cancelled by a newer persist can never leave the snapshot empty and
     * the most recent persist always wins.
     */
    private suspend fun writeLatest() {
        val snapshot = synchronized(lock) { latestSnapshot } ?: return
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                atomicWrite(file, snapshot)
            }
        }
        synchronized(lock) {
            if (latestSnapshot == snapshot) latestSnapshot = null
        }
    }

    private suspend fun writeDownloadsLatest() {
        val snapshot = synchronized(lock) { latestDownloadsSnapshot } ?: return
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                atomicWrite(downloadsFile, snapshot)
            }
        }
        synchronized(lock) {
            if (latestDownloadsSnapshot == snapshot) latestDownloadsSnapshot = null
        }
    }

    private fun atomicWrite(target: File, snapshot: String) {
        try {
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(snapshot)
            if (!tmp.renameTo(target)) {
                // rename can fail on some filesystems; fall back to a
                // direct write (best-effort).
                target.writeText(snapshot)
            }
        } catch (_: Exception) {
        }
    }

    fun savePrefs() = persist(immediate = true)

    /** Test hook: waits for any in-flight write to finish (used by JVM tests). */
    internal suspend fun flush() {
        writeJob?.join()
        downloadsWriteJob?.join()
    }

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
