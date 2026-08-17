package net.spin.ao3.data

import kotlinx.coroutines.runBlocking
import net.spin.ao3.data.model.ChapterInfo
import net.spin.ao3.data.model.WorkSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests the JSON persistence layer (the riskiest untested part of the app):
 * favorites, history, downloads, prefs and pending jobs, including the
 * round-trip through a real file on disk (a second Store instance must read
 * exactly what the first one wrote).
 */
class StoreTest {

    private fun tempFile(): File {
        val f = File.createTempFile("ao3store", ".json")
        f.delete()
        return f
    }

    /** The downloads file is derived from the root file name (see Store). */
    private fun downloadsFile(f: File): File = File(f.parentFile, f.nameWithoutExtension + "_downloads.json")

    private fun work(id: Long, title: String = "Obra $id") = WorkSummary(
        id = id,
        title = title,
        author = "Autor",
        authorUrl = null,
        fandoms = listOf("Fandom"),
        rating = "Mature",
        ratingKey = "mature",
        warnings = emptyList(),
        categories = emptyList(),
        otherTags = emptyList(),
        summary = "Resumen",
        words = 1000,
        chapterCount = 3,
        chapterTotal = 5,
        hits = 10,
        kudos = 2,
        comments = 1,
        bookmarks = 0,
        published = "2024-01-01",
        updated = "2024-02-01",
        url = "https://archiveofourown.org/works/$id",
    )

    private fun chapter(index: Int, title: String = "Cap $index") =
        ChapterInfo(
            index = index,
            title = title,
            url = "https://archiveofourown.org/works/1/chapters/$index",
            chapterId = index.toLong(),
            content = "<p>$title</p>",
        )

    @Test
    fun `empty store has no data`() {
        val store = Store(tempFile())
        assertTrue(store.savedWorks().isEmpty())
        assertTrue(store.history().isEmpty())
        assertTrue(store.downloads().isEmpty())
        assertTrue(store.pendingJobs().isEmpty())
        assertNull(store.historyEntry(1))
    }

    @Test
    fun `saved works round trip and removal`() {
        val f = tempFile()
        val store = Store(f)
        store.saveWork(work(1))
        store.saveWork(work(2))
        assertTrue(store.isSaved(1))
        assertTrue(store.isSaved(2))
        assertEquals(2, store.savedWorks().size)
        assertEquals("Obra 1", store.savedWorks().first().title)

        store.removeSaved(1)
        assertFalse(store.isSaved(1))
        assertTrue(store.isSaved(2))
        assertEquals(1, store.savedWorks().size)
        f.delete()
    }

    @Test
    fun `history keeps latest entry and progress map`() {
        val store = Store(tempFile())
        store.updateHistory(
            Store.HistoryEntry(
                id = 1, title = "Obra", author = "Autor", chapterIndex = 0,
                scrollRatio = 0.5f, chapterProgress = mapOf(0 to 0.5f, 1 to 0.9f), at = 100,
            ),
        )
        store.updateHistory(
            Store.HistoryEntry(id = 2, title = "Otra", author = "Autor", chapterIndex = 2, scrollRatio = 0f, at = 200),
        )
        val entry = store.historyEntry(1)
        assertEquals(0.5f, entry!!.scrollRatio, 0.001f)
        assertEquals(0.9f, entry.chapterProgress[1]!!, 0.001f)
        // history() is sorted by recency (at desc)
        assertEquals(2L, store.history().first().id)
        store.removeHistory(1)
        assertNull(store.historyEntry(1))
        store.clearHistory()
        assertTrue(store.history().isEmpty())
    }

    @Test
    fun `downloads merge chapters and drop the record when empty`() {
        val store = Store(tempFile())
        store.saveDownload(1, "Obra", listOf(chapter(0), chapter(1)))
        assertTrue(store.isDownloaded(1))
        assertEquals(setOf(0, 1), store.downloadedChapterIds(1))
        assertEquals(2, store.download(1)!!.chapters.size)

        // add/merge a new chapter
        store.addDownloadedChapter(1, "Obra", chapter(2))
        assertEquals(setOf(0, 1, 2), store.downloadedChapterIds(1))

        // remove one chapter
        store.removeDownloadedChapter(1, 1)
        assertEquals(setOf(0, 2), store.downloadedChapterIds(1))

        // removing the last chapter drops the whole record
        store.removeDownloadedChapter(1, 0)
        store.removeDownloadedChapter(1, 2)
        assertFalse(store.isDownloaded(1))
        assertTrue(store.downloads().isEmpty())
    }

    @Test
    fun `pending jobs replace per work`() {
        val store = Store(tempFile())
        store.addPendingJob(Store.PendingJob(1, "Obra", listOf(chapter(0))))
        store.addPendingJob(Store.PendingJob(1, "Obra", listOf(chapter(0), chapter(1))))
        assertEquals(1, store.pendingJobs().size)
        assertEquals(2, store.pendingJobs().first().chapters.size)
        store.removePendingJob(1)
        assertTrue(store.pendingJobs().isEmpty())
    }

    @Test
    fun `prefs persist across store instances`() {
        val f = tempFile()
        val s1 = Store(f)
        s1.prefs.fontSizeSp = 22
        s1.prefs.theme = Store.ReaderTheme.SEPIA
        s1.prefs.paged = true
        s1.savePrefs()
        runBlocking { s1.flush() }

        val s2 = Store(f)
        assertEquals(22, s2.prefs.fontSizeSp)
        assertEquals(Store.ReaderTheme.SEPIA, s2.prefs.theme)
        assertTrue(s2.prefs.paged)
        f.delete()
    }

    @Test
    fun `full library persists across store instances`() {
        val f = tempFile()
        val s1 = Store(f)
        s1.saveWork(work(7))
        s1.updateHistory(
            Store.HistoryEntry(
                id = 7, title = "Obra 7", author = "Autor", chapterIndex = 1,
                scrollRatio = 0.25f, chapterProgress = mapOf(1 to 0.25f), at = 42,
            ),
        )
        s1.saveDownload(7, "Obra 7", listOf(chapter(0), chapter(1)))
        runBlocking { s1.flush() }

        val s2 = Store(f)
        assertTrue(s2.isSaved(7))
        assertEquals("Obra 7", s2.savedWorks().first().title)
        val entry = s2.historyEntry(7)
        assertEquals(1, entry!!.chapterIndex)
        assertEquals(0.25f, entry.scrollRatio, 0.001f)
        assertEquals(2, s2.download(7)!!.chapters.size)
        f.delete()
    }

    @Test
    fun `corrupt file degrades to empty store`() {
        val f = tempFile()
        f.writeText("{ not valid json !!!")
        val store = Store(f)
        assertTrue(store.savedWorks().isEmpty())
        assertTrue(store.history().isEmpty())
        f.delete()
    }

    @Test
    fun `downloads live in their own file, not the library`() {
        val f = tempFile()
        val store = Store(f)
        store.saveWork(work(1))
        store.saveDownload(1, "Obra", listOf(chapter(0), chapter(1)))
        runBlocking { store.flush() }

        // The small library file must NOT contain the big downloads array…
        assertFalse(f.readText().contains("\"downloads\""))
        // …and the downloads file carries them.
        val dlFile = downloadsFile(f)
        assertTrue(dlFile.exists())
        assertTrue(dlFile.readText().contains("\"downloads\""))
        f.delete(); dlFile.delete()
    }

    @Test
    fun `legacy single-file downloads migrate to the downloads file`() {
        val f = tempFile()
        // Legacy layout: downloads inside the main library JSON.
        val legacy = org.json.JSONObject().apply {
            put("downloads", org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("id", 5L)
                    put("title", "Legacy")
                    put("chapters", org.json.JSONArray().apply {
                        put(chapter(0).toJson())
                    })
                })
            })
        }
        f.writeText(legacy.toString())

        val store = Store(f)
        runBlocking { store.flush() }
        assertTrue(store.isDownloaded(5))
        assertEquals(1, store.download(5)!!.chapters.size)
        // After migration the big array leaves the small file.
        assertFalse(f.readText().contains("\"downloads\""))
        val dlFile = downloadsFile(f)
        assertTrue(dlFile.exists())
        assertTrue(dlFile.readText().contains("\"downloads\""))
        f.delete(); dlFile.delete()
    }

    @Test
    fun `backup round trips and restores a full library`() {
        val f = tempFile()
        val s1 = Store(f)
        s1.saveWork(work(9))
        s1.updateHistory(
            Store.HistoryEntry(id = 9, title = "Obra 9", author = "A", chapterIndex = 2, scrollRatio = 0.3f, at = 5),
        )
        s1.saveDownload(9, "Obra 9", listOf(chapter(0), chapter(1)))
        s1.prefs.theme = Store.ReaderTheme.SEPIA
        s1.prefs.ttsRate = 1.5f
        s1.savePrefs()
        runBlocking { s1.flush() }
        val backup = s1.exportBackup()

        // A fresh store (same file) is wiped, then restored from the backup.
        val s2 = Store(f)
        s2.clearHistory()
        s2.removeSaved(9)
        s2.removeDownload(9)
        runBlocking { s2.flush() }
        assertTrue(s2.savedWorks().isEmpty())
        assertFalse(s2.isDownloaded(9))

        assertTrue(s2.importBackup(backup))
        runBlocking { s2.flush() }
        assertTrue(s2.isSaved(9))
        assertTrue(s2.isDownloaded(9))
        assertEquals(2, s2.download(9)!!.chapters.size)
        assertEquals(2, s2.historyEntry(9)!!.chapterIndex)
        assertEquals(Store.ReaderTheme.SEPIA, s2.prefs.theme)
        assertEquals(1.5f, s2.prefs.ttsRate, 0.001f)
        f.delete(); downloadsFile(f).delete()
    }

    @Test
    fun `import rejects garbage without touching the library`() {
        val f = tempFile()
        val store = Store(f)
        store.saveWork(work(3))
        runBlocking { store.flush() }
        assertFalse(store.importBackup("not json at all"))
        assertFalse(store.importBackup("{\"version\": 1}"))
        assertTrue(store.isSaved(3))
        f.delete(); downloadsFile(f).delete()
    }
}

/** Minimal JSON round-trip for a chapter (mirrors Store's private mapper). */
private fun net.spin.ao3.data.model.ChapterInfo.toJson(): org.json.JSONObject =
    org.json.JSONObject().apply {
        put("index", index)
        put("title", title)
        put("url", url ?: "")
        chapterId?.let { put("chapterId", it) }
        put("content", content?.takeIf { it.isNotBlank() } ?: "")
    }
