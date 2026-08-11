package net.spin.ao3.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Google Translate through the unofficial endpoint that translate.google.com
 * itself uses (`translate.googleapis.com/translate_a/single?client=gtx`): no
 * API key, no billing, real Google quality. Verified limits (measured):
 *  - ~10k characters per request (25k+ returns HTTP 400), so chunks of 8k
 *  - bursts of ~15 rapid requests all return 200; a personal reader doing a
 *    few chapters a day is far below what triggers throttling
 *
 * This is technically against Google's ToS (it is an internal endpoint); for
 * a personal-use app this is the standard pragmatic route. Translations are
 * cached forever in [cache], so each chapter is translated at most once.
 */
class Translator(
    private val cache: DiskCache,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Comfortably under the ~10k measured ceiling, big enough to keep context. */
    private val chunkChars = 8_000
    /** Small pause between chunks: respectful, avoids bursty behavior. */
    private val paceMs = 150L

    private val endpoint = "https://translate.googleapis.com/translate_a/single"

    /**
     * Translates the block text of [html] (a chapter fragment: `<p>` blocks
     * and similar) preserving paragraph structure, caches the result under
     * [key] and returns the rebuilt HTML fragment. [onProgress] reports
     * (doneBatches, totalBatches) for the loading overlay.
     */
    suspend fun translateChapterHtml(
        key: String,
        html: String,
        lang: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): String {
        cache.get(key)?.let { return it }
        val blocks = extractBlocks(html)
        if (blocks.isEmpty()) {
            onProgress(1, 1)
            return html
        }
        // Batch consecutive blocks into chunks of <= chunkChars.
        val batches = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        var length = 0
        for (block in blocks) {
            if (length + block.length > chunkChars && current.isNotEmpty()) {
                batches += current
                current = mutableListOf()
                length = 0
            }
            current += block
            length += block.length
        }
        if (current.isNotEmpty()) batches += current

        val translated = mutableListOf<String>()
        batches.forEachIndexed { i, batch ->
            val joined = batch.joinToString("\n\n")
            val out = translateChunk(joined, lang)
            // Google usually preserves the blank line between paragraphs; when
            // the split matches the batch size, map 1:1, else keep it as one.
            val parts = out.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
            translated += if (parts.size == batch.size) parts else listOf(out.trim())
            onProgress(i + 1, batches.size)
            if (i < batches.lastIndex) delay(paceMs)
        }
        val result = rebuildHtml(translated)
        if (result.isNotBlank()) cache.put(key, result)
        return result
    }

    private suspend fun translateChunk(text: String, lang: String): String = withContext(Dispatchers.IO) {
        val url = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", "auto")
            .addQueryParameter("tl", lang)
            .addQueryParameter("dt", "t")
            .addQueryParameter("q", text)
            .build()
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "AO3-Lector/0.7 (personal reader app; okhttp)")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code != 200) throw IOException("Traducción: HTTP ${response.code}")
                    val body = response.body?.string() ?: ""
                    return@withContext parseGtx(body)
                }
            } catch (e: Exception) {
                lastError = e
                delay(600L * (attempt + 1))
            }
        }
        throw lastError ?: IOException("No se pudo traducir")
    }

    /**
     * Extracts the translated text from the gtx JSON. The shape is
     * `[[["t1","o1",null,...],["t2","o2",...]],null,"en",null,...]`: element
     * [0] is the list of sentence segments, each segment's [0] is the
     * translation.
     */
    internal fun parseGtx(json: String): String {
        val root = JSONArray(json)
        val segments = root.getJSONArray(0)
        val out = StringBuilder()
        for (i in 0 until segments.length()) {
            val segment = segments.getJSONArray(i)
            if (segment.length() > 0 && !segment.isNull(0)) out.append(segment.getString(0))
        }
        return out.toString()
    }

    companion object {
        /**
         * Block-level text of a chapter fragment. Direct children of the body
         * (the sanitized chapter HTML has `<p>` blocks directly, occasionally
         * wrapped in one div, which gets unwrapped one level).
         */
        internal fun extractBlocks(html: String): List<String> {
            val doc = Jsoup.parseBodyFragment(html)
            var roots = doc.body().children()
            if (roots.size == 1 && roots[0].isBlock && roots[0].children().isNotEmpty()) {
                roots = roots[0].children()
            }
            return roots.mapNotNull { el ->
                val text = el.text().trim()
                text.takeIf { it.isNotEmpty() }
            }
        }

        /** Rebuilds the translated blocks as `<p>` elements. */
        internal fun rebuildHtml(blocks: List<String>): String =
            blocks.joinToString("\n") { "<p>${escapeHtml(it)}</p>" }

        internal fun escapeHtml(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
