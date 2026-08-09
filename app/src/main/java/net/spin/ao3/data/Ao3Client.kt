package net.spin.ao3.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.spin.ao3.data.model.ChapterInfo
import net.spin.ao3.data.model.SearchFilters
import net.spin.ao3.data.model.SortOption
import net.spin.ao3.data.model.WorkDetail
import net.spin.ao3.data.model.WorkSummary
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP client for archiveofourown.org. Requests are serialized and slightly
 * delayed to stay respectful of the site (personal-use app).
 *
 * Resilience notes (AO3 is served behind Cloudflare):
 *  - 5xx / timeouts are retried with backoff;
 *  - Cloudflare error/challenge pages served with HTTP 200 are detected and
 *    treated as retryable failures;
 *  - the "Adult Content Warning" interstitial (served to visitors without the
 *    `view_adult` cookie) is followed like a browser would, by requesting the
 *    same URL with `view_adult=true`.
 */
class Ao3Client {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .cookieJar(InMemoryCookieJar())
        .build()

    private val gate = Mutex()

    /** Fetches one URL, returning the response body or throwing. */
    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "AO3-Lector/0.1 (personal reader app; okhttp)",
            )
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9,es;q=0.8")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} al cargar $url")
            }
            val body = response.body?.string()
                ?: throw IOException("Respuesta vacía de AO3")
            if (isCloudflareErrorPage(body)) {
                throw IOException("AO3 respondió con un error temporal (Cloudflare)")
            }
            body
        }
    }

    private suspend fun get(url: String): String = gate.withLock {
        var lastError: Exception? = null
        repeat(5) { attempt ->
            delay(if (attempt == 0) 400 else 1200L * attempt)
            try {
                val body = fetch(url)
                // AO3 shows an age gate to visitors without the view_adult cookie.
                if (isAdultGate(body)) {
                    val adultUrl = if (url.contains('?')) "$url&view_adult=true" else "$url?view_adult=true"
                    return fetch(adultUrl)
                }
                return body
            } catch (e: Exception) {
                lastError = e
                // Retry transient failures (Cloudflare 5xx / timeouts)
            }
        }
        throw lastError ?: IOException("Error de red")
    }

    /**
     * Cloudflare serves 520/521/522/525 error pages and "Just a moment"
     * challenge pages, sometimes with a 200 status.
     */
    private fun isCloudflareErrorPage(body: String): Boolean =
        body.contains("cf-error-details") ||
            body.contains("SSL handshake failed") ||
            body.contains("challenge-form") ||
            body.contains("challenge-running") ||
            body.contains("cf-chl") ||
            body.contains("jschl") ||
            Regex("error code: 5\\d\\d").containsMatchIn(body)

    /** AO3's "Adult Content Warning" interstitial. */
    private fun isAdultGate(body: String): Boolean =
        body.contains("view_adult=true") || body.contains("Adult Content Warning")

    /** Keeps Cloudflare cookies (cf_clearance, view_adult, etc.) across requests. */
    private class InMemoryCookieJar : CookieJar {
        private val cookies = mutableMapOf<String, List<Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookieList: List<Cookie>) {
            cookies[url.host] = cookieList
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookies[url.host].orEmpty()
    }

    // Cache of tag name -> canonical tag_id (avoids a page fetch per pagination).
    private val canonicalTagCache = HashMap<String, String?>()

    /**
     * Searches works.
     *
     * AO3 has two search surfaces with different behaviours (verified live):
     *  - /works/search honours work_search[query]/sort/complete/date/words but
     *    IGNORES include_work_search/exclude_work_search tag filters;
     *  - /works honours the include/exclude filters ONLY when a canonical
     *    tag_id is present (the tag page's hidden #tag_id field), and ignores
     *    free-text query.
     * So: pure text search goes to /works/search; tag browsing + filters go to
     * /works with the canonical tag name.
     */
    suspend fun search(filters: SearchFilters, page: Int = 1, sort: SortOption = SortOption.BEST_MATCH): SearchResult {
        // When filters are active, treat the query as a tag to explore so the
        // filters actually take effect (matches AO3's own sidebar behaviour).
        val tag = filters.tag ?: filters.query.takeIf { filters.hasFilters && it.isNotBlank() }
        if (tag != null && tag.isNotBlank()) {
            val canonical = resolveCanonicalTag(tag)
            if (canonical != null) {
                return SearchResult(Ao3Parser.parseSearchResults(get(buildTagFilterUrl(canonical, filters, page, sort))), true)
            }
            // Tag didn't resolve: fall back to text search, but report that
            // filters could not be applied so the UI can warn the user.
            return SearchResult(Ao3Parser.parseSearchResults(get(buildSearchUrl(filters, page, sort))), false)
        }
        return SearchResult(Ao3Parser.parseSearchResults(get(buildSearchUrl(filters, page, sort))), true)
    }

    /**
     * Resolves a tag name to its canonical form used by /works?tag_id=...
     *
     * IMPORTANT: the name goes in the URL *path*, so it must be percent-encoded
     * (spaces -> %20). URLEncoder alone is NOT enough: it encodes spaces as '+'
     * which is only valid in query strings — /tags/Harry+Potter/works 404s.
     */
    private suspend fun resolveCanonicalTag(name: String): String? {
        canonicalTagCache[name]?.let { return it }
        val resolved = runCatching {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("archiveofourown.org")
                .addPathSegment("tags")
                .addPathSegment(name)
                .addPathSegment("works")
                .build()
            val html = get(url.toString())
            // Robust: find the hidden tag_id input regardless of attribute order.
            Regex("""<input[^>]*name="tag_id"[^>]*value="([^"]*)"""").find(html)
                ?.groupValues?.get(1)
                ?: Regex("""<input[^>]*value="([^"]*)"[^>]*name="tag_id"""").find(html)?.groupValues?.get(1)
        }.getOrNull()
        canonicalTagCache[name] = resolved
        return resolved
    }

    /** /works?tag_id=<canonical>&filters&sort — the only URL where AO3 applies tag filters. */
    private fun buildTagFilterUrl(tag: String, filters: SearchFilters, page: Int, sort: SortOption): String {
        val b = HttpUrl.Builder()
            .scheme("https")
            .host("archiveofourown.org")
            .addPathSegment("works")
        fun add(key: String, value: String) {
            if (value.isNotBlank()) b.addQueryParameter(key, value)
        }
        fun addIds(key: String, ids: Set<Int>) {
            ids.forEach { b.addQueryParameter(key, it.toString()) }
        }
        b.addQueryParameter("tag_id", tag)
        add("work_search[other_tag_names]", filters.includeTags)
        add("work_search[excluded_tag_names]", filters.excludeTags)
        addIds("include_work_search[rating_ids][]", filters.rating?.let { setOf(it) }.orEmpty())
        addIds("include_work_search[archive_warning_ids][]", filters.warnings)
        addIds("include_work_search[category_ids][]", filters.categories)
        addIds("exclude_work_search[rating_ids][]", filters.excludeRating?.let { setOf(it) }.orEmpty())
        addIds("exclude_work_search[archive_warning_ids][]", filters.excludeWarnings)
        addIds("exclude_work_search[category_ids][]", filters.excludeCategories)
        add("work_search[complete]", if (filters.completeOnly) "T" else "")
        add("work_search[crossover]", if (filters.crossoverOnly) "T" else "")
        add("work_search[words_from]", filters.wordsFrom)
        add("work_search[words_to]", filters.wordsTo)
        add("work_search[date_from]", filters.dateFrom)
        add("work_search[date_to]", filters.dateTo)
        sort.column?.let { b.addQueryParameter("work_search[sort_column]", it) }
        sort.column?.let { b.addQueryParameter("work_search[sort_order]", "desc") }
        if (page > 1) b.addQueryParameter("page", page.toString())
        return b.build().toString()
    }

    /** /works/search?work_search[query]&... — free-text search. */
    private fun buildSearchUrl(filters: SearchFilters, page: Int, sort: SortOption): String {
        val b = HttpUrl.Builder()
            .scheme("https")
            .host("archiveofourown.org")
            .addPathSegment("works")
            .addPathSegment("search")
        fun add(key: String, value: String) {
            if (value.isNotBlank()) b.addQueryParameter(key, value)
        }
        add("work_search[query]", filters.query)
        add("work_search[other_tag_names]", filters.includeTags)
        add("work_search[excluded_tag_names]", filters.excludeTags)
        add("work_search[complete]", if (filters.completeOnly) "T" else "")
        add("work_search[crossover]", if (filters.crossoverOnly) "T" else "")
        add("work_search[words_from]", filters.wordsFrom)
        add("work_search[words_to]", filters.wordsTo)
        add("work_search[date_from]", filters.dateFrom)
        add("work_search[date_to]", filters.dateTo)
        sort.column?.let { b.addQueryParameter("work_search[sort_column]", it) }
        sort.column?.let { b.addQueryParameter("work_search[sort_order]", "desc") }
        if (page > 1) b.addQueryParameter("page", page.toString())
        return b.build().toString()
    }

    suspend fun getWork(id: Long): WorkDetail {
        val html = get("https://archiveofourown.org/works/$id")
        return Ao3Parser.parseWorkDetail(html, id)
            ?: throw IOException("No se pudo interpretar la obra $id")
    }

    /** Fetches a chapter's content; falls back to the work page when no chapter URL is known. */
    suspend fun getChapter(workId: Long, chapter: ChapterInfo): ChapterInfo {
        val url = chapter.url ?: "https://archiveofourown.org/works/$workId"
        val html = get(url)
        val (content, title) = Ao3Parser.parseChapter(html)
        return chapter.copy(
            title = title ?: chapter.title,
            content = content ?: chapter.content ?: "",
        )
    }
}

/** Search outcome: results + whether tag filters were actually applied by AO3. */
data class SearchResult(
    val works: List<WorkSummary>,
    val filtersApplied: Boolean,
)
