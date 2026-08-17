package net.spin.ao3.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.spin.ao3.data.model.Ao3Comment
import net.spin.ao3.data.model.AuthorProfile
import net.spin.ao3.data.model.AuthorWorks
import net.spin.ao3.data.model.ChapterInfo
import net.spin.ao3.data.model.FilterFacets
import net.spin.ao3.data.model.SearchFilters
import net.spin.ao3.data.model.SortOption
import net.spin.ao3.data.model.WorkDetail
import net.spin.ao3.data.model.WorkFeedInfo
import net.spin.ao3.data.model.WorkSummary
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
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
 *    `view_adult` cookie) is followed like a browser would.
 *
 * Parsed results are cached in memory (LRU, small) so navigating back to a
 * screen already visited (search results, work detail, comments, author
 * profiles, chapters) is instant and does not hit AO3 again.
 *
 * When [cacheDir] is provided, raw HTML is also cached on disk (search pages
 * with a short TTL, everything else with a long one) so cold starts are fast:
 * pages visited in a previous session load from disk instead of the network.
 */
class Ao3Client(private val cacheDir: File? = null) {

    /** Hard cap on any single logical request (all retries + backoff included). */
    private companion object {
        const val GLOBAL_DEADLINE_MS = 45_000L
        const val POST_DEADLINE_MS = 30_000L

        // A REAL browser User-Agent. Cloudflare gives "AO3-Lector/0.1 (personal
        // reader app; okhttp)" a low bot-trust score and slow-stalls it
        // (measured: 14-16s vs ~1s for Chrome), which looks like an infinite
        // spinner. A normal Chrome UA (plus Accept-Language) passes like a
        // phone browser.
        const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        const val ACCEPT_LANGUAGE = "es-ES,es;q=0.9,en;q=0.8"
    }

    private val cookieJar = InMemoryCookieJar()

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        // HTTP/1.1: Cloudflare tarpit/525 a OkHttp en HTTP/2 (medido: 1 de 5
        // requests cuelga ~58s con HTTP 525; con HTTP/1.1 5/5 completan).
        .protocols(listOf(Protocol.HTTP_1_1))
        .cookieJar(cookieJar)
        .build()

    /** First-attempt client: 8s read timeout so a Cloudflare tarpit (which
     *  stalls the response indefinitely) fails fast instead of eating 60s per
     *  retry. The WebView fallback then takes over; later retries use the full
     *  client for legitimately slow pages. */
    private val fastClient = client.newBuilder().readTimeout(8, TimeUnit.SECONDS).build()

    private val gate = Mutex()

    /** Cloudflare explicitly blocked the request (its bot-detection codes). */
    private class CfBlockedException(val code: Int) : IOException("Cloudflare bloquea la petición (HTTP $code)")

    /** A Cloudflare JS challenge page served with HTTP 200. */
    private fun isCfChallenge(body: String): Boolean =
        body.contains("_cf_chl_opt") ||
            body.contains("challenge-platform") ||
            body.contains("cdn-cgi/challenge-platform")

    /** True when the error looks like Cloudflare bot-detection (tarpit/timeout
     *  or its block codes) and is worth retrying through the WebView. */
    private fun shouldFallbackToWebView(e: Exception): Boolean =
        e is CfBlockedException ||
            e is SocketTimeoutException ||
            e is InterruptedIOException ||
            e.message?.contains("timeout", ignoreCase = true) == true

    /** One blocking OkHttp GET against [client]. Throws on HTTP errors. */
    private fun okhttpGet(url: String, headers: Map<String, String>, client: OkHttpClient): String {
        val b = Request.Builder().url(url).header(
            "User-Agent",
            BROWSER_UA,
        ).header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", ACCEPT_LANGUAGE)
        headers.forEach { (k, v) -> b.header(k, v) }
        client.newCall(b.build()).execute().use { response ->
            if (response.code == 429) {
                // Rate-limited: honor Retry-After (default 5s) so the retry
                // loop backs off properly instead of hammering the site.
                val retryAfter = response.header("Retry-After")?.toLongOrNull()
                throw RateLimitedException((retryAfter ?: 5L).coerceAtLeast(1L) * 1000L)
            }
            if (response.code in WebViewFetcher.CF_CODES) {
                throw CfBlockedException(response.code)
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} al cargar $url")
            }
            val body = response.body?.string()
                ?: throw IOException("Respuesta vacía de AO3")
            if (isCloudflareErrorPage(body)) {
                throw IOException("AO3 respondió con un error temporal (Cloudflare)")
            }
            return body
        }
    }

    /** Fetches one URL, returning the response body or throwing.
     *
     *  Strategy (copied from the CO3 AO3 client, which works where OkHttp
     *  alone doesn't): try OkHttp first with a short 8s read timeout so a
     *  Cloudflare tarpit fails fast; on the first attempt, if that fails with
     *  a Cloudflare block/timeout or a JS challenge page, retry through the
     *  system WebView — real Chromium, so AO3 sees a browser TLS fingerprint
     *  and JS challenges resolve themselves. Later retries use plain OkHttp
     *  with the full timeout (for legitimately slow pages). */
    private suspend fun fetch(url: String, headers: Map<String, String> = emptyMap(), attempt: Int): String {
        val quick = try {
            withContext(Dispatchers.IO) { okhttpGet(url, headers, fastClient) }
        } catch (e: Exception) {
            if (attempt == 0 && shouldFallbackToWebView(e)) {
                val viaWeb = WebViewFetcher.fetch(url)
                if (viaWeb != null && !isCfChallenge(viaWeb)) return viaWeb
            }
            throw e
        }
        if (!isCfChallenge(quick)) return quick
        // OkHttp got a CF challenge page (HTTP 200): let the WebView solve it.
        if (attempt == 0) {
            val viaWeb = WebViewFetcher.fetch(url)
            if (viaWeb != null && !isCfChallenge(viaWeb)) return viaWeb
        }
        throw IOException("AO3 mostró un challenge de Cloudflare al cargar $url")
    }

    /** Serialized, polite GET: the whole app funnels through this gate so AO3
     *  only ever sees one in-flight request at a time (site-respect). */
    private suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        retries: Int = 7,
        disk: DiskCache? = contentDisk,
        allowStaleOnError: Boolean = false,
    ): String {
        disk?.get(url)?.let { return it }
        val body = try {
            gate.withLock { getInternal(url, headers, retries) }
        } catch (e: Exception) {
            // Offline or AO3 down: fall back to the stale disk copy instead of
            // failing, so recently read works/chapters stay openable.
            if (allowStaleOnError) disk?.getStale(url)?.let { return it }
            throw e
        }
        if (!isAdultGate(body)) disk?.put(url, body)
        return body
    }

    /** Same retry/backoff logic as [get] but WITHOUT the global gate. Reserved
     *  for tiny bursts to the SAME host (author profile + works), where a
     *  browser would also fire two requests at once. Before this, the two
     *  author pages serialized behind the mutex, so the user waited the SUM of
     *  both requests instead of the slower one. */
    private suspend fun getConcurrent(
        url: String,
        headers: Map<String, String> = emptyMap(),
        retries: Int = 4,
        disk: DiskCache? = contentDisk,
        allowStaleOnError: Boolean = false,
    ): String {
        disk?.get(url)?.let { return it }
        val body = try {
            getInternal(url, headers, retries)
        } catch (e: Exception) {
            // See [get]: stale disk copy beats a dead screen when offline.
            if (allowStaleOnError) disk?.getStale(url)?.let { return it }
            throw e
        }
        if (!isAdultGate(body)) disk?.put(url, body)
        return body
    }

    /**
     * Hard deadline over the WHOLE retry loop (including backoff/Retry-After
     * waits), so a flaky or down AO3 can never keep the user staring at a
     * spinner for minutes — the request gives up and reports a clear error.
     */
    private suspend fun getInternal(url: String, headers: Map<String, String>, retries: Int): String {
        var lastError: Exception? = null
        val body = withTimeoutOrNull(GLOBAL_DEADLINE_MS) {
            repeat(retries) { attempt ->
                // First attempt goes out right away; back off between retries.
                if (attempt > 0) delay(700L * attempt)
                try {
                    val fetched = fetch(url, headers, attempt)
                    // AO3 shows an age gate to visitors without the view_adult cookie.
                    if (isAdultGate(fetched)) {
                        val adultUrl = if (url.contains('?')) "$url&view_adult=true" else "$url?view_adult=true"
                        // The gated body already carries the full page (the notice is a
                        // banner, not a replacement). Only swap it for the refetch when
                        // that refetch succeeds: never throw away a good response just
                        // because a redundant refetch hit a Cloudflare 525/timeout.
                        runCatching { fetch(adultUrl, headers, attempt) }.getOrNull()?.let { return@withTimeoutOrNull it }
                    }
                    return@withTimeoutOrNull fetched
                } catch (e: CancellationException) {
                    // Never swallow cancellation (incl. the deadline's timeout)
                    // as a retryable error: it would be rethrown as a "real"
                    // error and could cancel the caller's coroutine.
                    throw e
                } catch (e: Exception) {
                    if (e is RateLimitedException) {
                        // Honor Retry-After (capped so a misbehaving server can't
                        // stall the UI for minutes).
                        delay(e.retryAfterMillis.coerceAtMost(30_000L))
                    }
                    lastError = e
                }
            }
            // All retries failed: fall through to throw the real error below.
            null
        }
        // body == null means the deadline hit; prefer the real last error, else
        // a clear timeout message.
        return body ?: throw (lastError ?: IOException("AO3 tarda demasiado en responder. Inténtalo de nuevo."))
    }

    /** POSTs a form and returns the final response body (after redirects). */
    private suspend fun post(url: String, fields: Map<String, String>): String = gate.withLock {
        var lastError: Exception? = null
        val body = withTimeoutOrNull(POST_DEADLINE_MS) {
            repeat(5) { attempt ->
                delay(if (attempt == 0) 400 else 1200L * attempt)
                try {
                    val form = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
                    val posted = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", BROWSER_UA)
                        .header("Accept-Language", ACCEPT_LANGUAGE)
                        .header("Referer", url)
                        .post(form as RequestBody)
                        .build()
                        client.newCall(request).execute().use { response ->
                            if (response.code == 429) {
                                val retryAfter = response.header("Retry-After")?.toLongOrNull()
                                throw RateLimitedException((retryAfter ?: 5L).coerceAtLeast(1L) * 1000L)
                            }
                            val b = response.body?.string() ?: ""
                            if (!response.isSuccessful && response.code != 302 && response.code != 303) {
                                throw IOException("HTTP ${response.code} al enviar el formulario")
                            }
                            b
                        }
                    }
                    if (isCloudflareErrorPage(posted)) {
                        throw IOException("AO3 respondió con un error temporal (Cloudflare)")
                    }
                    return@withTimeoutOrNull posted
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e is RateLimitedException) {
                        delay(e.retryAfterMillis.coerceAtMost(30_000L))
                    }
                    lastError = e
                }
            }
            null
        }
        body ?: throw (lastError ?: IOException("AO3 tarda demasiado en responder. Inténtalo de nuevo."))
    }

    /** Thrown when AO3/Cloudflare rate-limits the app (HTTP 429) so retries honor Retry-After. */
    private class RateLimitedException(val retryAfterMillis: Long) :
        IOException("HTTP 429 (rate limit)")

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

    /**
     * Detects AO3's real adult-content interstitial (a full-page notice that
     * replaces the content for visitors without the view_adult cookie).
     *
     * IMPORTANT: do NOT key on "view_adult=true". Once the view_adult cookie is
     * set, AO3 echoes that parameter URL-encoded into the login form's
     * return_to link on EVERY page, which made every request do a redundant
     * refetch (and routinely die on Cloudflare 525s) - the root cause of
     * author works lists coming back empty.
     */
    internal fun isAdultGate(body: String): Boolean =
        body.contains("This work could have adult content") ||
            body.contains("name=\"view_adult\"")

    /**
     * Keeps Cloudflare cookies (cf_clearance, view_adult, etc.) across requests.
     * Thread-safe: [getConcurrent] fires two requests in parallel (author
     * profile + works), so the jar must survive concurrent read/write.
     */
    private class InMemoryCookieJar : CookieJar {
        private val cookies = java.util.concurrent.ConcurrentHashMap<String, List<Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookieList: List<Cookie>) {
            cookies[url.host] = cookieList
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookies[url.host].orEmpty()
    }

    /** Bounded map (access-order LRU): evicts the oldest entry past [maxSize].
     *  Exposes [get]/[set] operators so it reads like a regular map. */
    private class BoundedMap<K, V>(private val maxSize: Int) {
        private val map = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
        }
        @Synchronized operator fun get(key: K): V? = map[key]
        @Synchronized operator fun set(key: K, value: V) {
            map[key] = value
        }
    }

    // Cache of tag name -> canonical tag_id (avoids a page fetch per pagination).
    private val canonicalTagCache = BoundedMap<String, String?>(200)
    private val facetsCache = BoundedMap<String, FilterFacets>(50)

    // ---- In-memory result caches (LRU) -------------------------------------
    // Bounded so memory stays small; keyed by the exact request so a back
    // navigation hits the cache instead of AO3.
    private val workCache = LruCache<Long, WorkDetail>(40)
    private val searchCache = LruCache<String, SearchResult>(30)
    private val chapterCache = LruCache<String, ChapterInfo>(120)
    private val commentsCache = LruCache<Long, List<Ao3Comment>>(30)
    private val authorCache = LruCache<String, AuthorProfile>(30)
    private val authorWorksCache = LruCache<String, AuthorWorks>(30)

    // ---- On-disk HTML cache (survives process death) -----------------------
    private val searchDisk: DiskCache? = cacheDir?.let {
        DiskCache(File(it, "search"), ttlMs = 30 * 60 * 1000L, maxFiles = 120)
    }
    private val contentDisk: DiskCache? = cacheDir?.let {
        DiskCache(File(it, "content"), ttlMs = 7L * 24 * 60 * 60 * 1000, maxFiles = 300)
    }
    /** Work detail pages change whenever the author posts a new chapter, so
     *  they get a much shorter TTL than stable content (chapters/comments). */
    private val workDisk: DiskCache? = cacheDir?.let {
        DiskCache(File(it, "work"), ttlMs = 6L * 60 * 60 * 1000, maxFiles = 150)
    }

    /** Simple thread-safe LRU map (insertion order -> evicts oldest). */
    private class LruCache<K, V>(private val maxSize: Int) {
        private val map = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
                size > maxSize
        }

        @Synchronized fun get(key: K): V? = map[key]
        @Synchronized fun put(key: K, value: V) {
            map[key] = value
        }
        @Synchronized fun invalidate(key: K) {
            map.remove(key)
        }
        @Synchronized fun invalidateAll() = map.clear()
    }

    /**
     * Searches works.
     *
     * AO3 has two search surfaces with different behaviours (verified live):
     *  - /works/search honours work_search[query]/sort/complete/date/words but
     *    IGNORES include_work_search/exclude_work_search tag filters;
     *  - /works honours the include/exclude filters ONLY when a canonical
     *    tag_id is present (the tag page's hidden #tag_id field), and ignores
     *    free-text query.
     */
    suspend fun search(filters: SearchFilters, page: Int = 1, sort: SortOption = SortOption.BEST_MATCH): SearchResult {
        val cacheKey = "${filters.serialize()}|$page|${sort.name}"
        searchCache.get(cacheKey)?.let { return it }
        val result = searchFresh(filters, page, sort)
        searchCache.put(cacheKey, result)
        return result
    }

    /** Returns the raw works-listing HTML for a search (shared by results + facets). */
    private suspend fun fetchSearchPage(filters: SearchFilters, page: Int, sort: SortOption): String {
        val tag = filters.tag ?: filters.query.takeIf { filters.hasFilters && it.isNotBlank() }
        if (tag != null && tag.isNotBlank()) {
            val canonical = resolveCanonicalTag(tag)
            if (canonical != null) return get(buildTagFilterUrl(canonical, filters, page, sort), disk = searchDisk)
            return get(buildSearchUrl(filters, page, sort), disk = searchDisk)
        }
        return get(buildSearchUrl(filters, page, sort), disk = searchDisk)
    }

    private suspend fun searchFresh(filters: SearchFilters, page: Int, sort: SortOption): SearchResult {
        val html = fetchSearchPage(filters, page, sort)
        // The listing is parsed ONCE (blurbs + facets + total) and always off
        // the main thread: JSoup parsing of a full results page is the heaviest
        // CPU work in the app and used to run on the caller's (main) dispatcher.
        val parsed = withContext(Dispatchers.IO) { Ao3Parser.parseSearchPage(html) }
        val tag = filters.tag ?: filters.query.takeIf { filters.hasFilters && it.isNotBlank() }
        val applied = if (tag != null && tag.isNotBlank()) {
            runCatching { resolveCanonicalTag(tag) }.getOrNull() != null
        } else {
            true
        }
        return SearchResult(parsed.works, applied, parsed.facets, parsed.total)
    }

    /**
     * Resolves a tag name to its canonical form used by /works?tag_id=...
     *
     * IMPORTANT: the name goes in the URL *path*, so it must be percent-encoded
     * (spaces -> %20). URLEncoder alone is NOT enough: it encodes spaces as '+'
     * which is only valid in query strings — /tags/Harry+Potter/works 404s.
     */
    /** Fetches (and caches) /tags/{name}/works — shared by tag resolution + facets. */
    private val tagPages = BoundedMap<String, String>(40)

    private suspend fun tagWorksPage(name: String): String? {
        tagPages[name]?.let { return it }
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("archiveofourown.org")
            .addPathSegment("tags")
            .addPathSegment(name)
            .addPathSegment("works")
            .build()
            .toString()
        return runCatching { get(url, disk = searchDisk) }.getOrNull()?.also { tagPages[name] = it }
    }

    private suspend fun resolveCanonicalTag(name: String): String? {
        canonicalTagCache[name]?.let { return it }
        val resolved = tagWorksPage(name)?.let { html ->
            Regex("""<input[^>]*name="tag_id"[^>]*value="([^"]*)"""").find(html)
                ?.groupValues?.get(1)
                ?: Regex("""<input[^>]*value="([^"]*)"[^>]*name="tag_id"""").find(html)?.groupValues?.get(1)
        }
        canonicalTagCache[name] = resolved
        return resolved
    }

    /**
     * Suggested tags for a free-text query. AO3's /works search page has NO
     * filter sidebar, so we resolve the query to a canonical tag and read THAT
     * tag page's sidebar (characters, relationships, freeforms with counts).
     */
    suspend fun searchFacets(query: String): FilterFacets {
        if (query.isBlank()) return FilterFacets()
        facetsCache[query]?.let { return it }
        val facets = runCatching {
            val canonical = resolveCanonicalTag(query) ?: return@runCatching FilterFacets()
            val page = tagWorksPage(canonical) ?: return@runCatching FilterFacets()
            withContext(Dispatchers.IO) { Ao3Parser.parseFacets(page) }
        }.getOrDefault(FilterFacets())
        facetsCache[query] = facets
        return facets
    }

    /** Adds the shared work_search[...] params used by both endpoints. */
    private fun HttpUrl.Builder.addCommon(filters: SearchFilters, sort: SortOption, page: Int): HttpUrl.Builder {
        fun add(key: String, value: String) {
            if (value.isNotBlank()) addQueryParameter(key, value)
        }
        add("work_search[other_tag_names]", filters.includeTags)
        add("work_search[excluded_tag_names]", filters.excludeTags)
        add("work_search[complete]", if (filters.completeOnly) "T" else "")
        add("work_search[crossover]", when {
            filters.excludeCrossover -> "F"
            filters.crossoverOnly -> "T"
            else -> ""
        })
        add("work_search[series_count]", if (filters.partOfSeries) "1" else "")
        add("work_search[language_id]", filters.language ?: "")
        add("work_search[words_from]", filters.wordsFrom)
        add("work_search[words_to]", filters.wordsTo)
        add("work_search[date_from]", filters.dateFrom)
        add("work_search[date_to]", filters.dateTo)
        sort.column?.let { addQueryParameter("work_search[sort_column]", it) }
        sort.column?.let { addQueryParameter("work_search[sort_order]", "desc") }
        if (page > 1) addQueryParameter("page", page.toString())
        return this
    }

    /** /works?tag_id=<canonical>&filters&sort — the only URL where AO3 applies tag filters. */
    private fun buildTagFilterUrl(tag: String, filters: SearchFilters, page: Int, sort: SortOption): String {
        val b = HttpUrl.Builder()
            .scheme("https")
            .host("archiveofourown.org")
            .addPathSegment("works")
        fun addIds(key: String, ids: Collection<Number>) {
            ids.forEach { b.addQueryParameter(key, it.toString()) }
        }
        b.addQueryParameter("tag_id", tag)
        addIds("include_work_search[rating_ids][]", filters.rating?.let { setOf(it) }.orEmpty())
        addIds("include_work_search[archive_warning_ids][]", filters.warnings)
        addIds("include_work_search[category_ids][]", filters.categories)
        addIds("exclude_work_search[rating_ids][]", filters.excludeRating?.let { setOf(it) }.orEmpty())
        addIds("exclude_work_search[archive_warning_ids][]", filters.excludeWarnings)
        addIds("exclude_work_search[category_ids][]", filters.excludeCategories)
        addIds("include_work_search[fandom_ids][]", filters.fandomIds)
        addIds("include_work_search[character_ids][]", filters.characterIds)
        addIds("include_work_search[relationship_ids][]", filters.relationshipIds)
        addIds("include_work_search[freeform_ids][]", filters.freeformIds)
        addIds("exclude_work_search[fandom_ids][]", filters.excludeFandomIds)
        addIds("exclude_work_search[character_ids][]", filters.excludeCharacterIds)
        addIds("exclude_work_search[relationship_ids][]", filters.excludeRelationshipIds)
        addIds("exclude_work_search[freeform_ids][]", filters.excludeFreeformIds)
        b.addCommon(filters, sort, page)
        return b.build().toString()
    }

    /** /works/search?work_search[query]&... — free-text search. */
    private fun buildSearchUrl(filters: SearchFilters, page: Int, sort: SortOption): String {
        val b = HttpUrl.Builder()
            .scheme("https")
            .host("archiveofourown.org")
            .addPathSegment("works")
            .addPathSegment("search")
        if (filters.query.isNotBlank()) b.addQueryParameter("work_search[query]", filters.query)
        fun addIds(key: String, ids: Collection<Number>) {
            ids.forEach { b.addQueryParameter(key, it.toString()) }
        }
        addIds("include_work_search[fandom_ids][]", filters.fandomIds)
        addIds("include_work_search[character_ids][]", filters.characterIds)
        addIds("include_work_search[relationship_ids][]", filters.relationshipIds)
        addIds("include_work_search[freeform_ids][]", filters.freeformIds)
        addIds("exclude_work_search[fandom_ids][]", filters.excludeFandomIds)
        addIds("exclude_work_search[character_ids][]", filters.excludeCharacterIds)
        addIds("exclude_work_search[relationship_ids][]", filters.excludeRelationshipIds)
        addIds("exclude_work_search[freeform_ids][]", filters.excludeFreeformIds)
        b.addCommon(filters, sort, page)
        return b.build().toString()
    }

    suspend fun getWork(id: Long): WorkDetail {
        workCache.get(id)?.let { return it }
        val html = get("https://archiveofourown.org/works/$id", disk = workDisk, allowStaleOnError = true)
        val detail = withContext(Dispatchers.IO) { Ao3Parser.parseWorkDetail(html, id) }
            ?: throw IOException("No se pudo interpretar la obra $id")
        workCache.put(id, detail)
        return detail
    }

    /**
     * Re-fetches a work detail from the network, bypassing BOTH the in-memory
     * and on-disk caches (used by pull-to-refresh). The fresh result replaces
     * the cached copies — memory AND disk — so the screen stays fast on the
     * next visit while the user gets current data (and fresh offline fallback)
     * on demand.
     */
    suspend fun refreshWork(id: Long): WorkDetail {
        workCache.invalidate(id)
        val url = "https://archiveofourown.org/works/$id"
        val html = get(url, disk = null, allowStaleOnError = true)
        val detail = withContext(Dispatchers.IO) { Ao3Parser.parseWorkDetail(html, id) }
            ?: throw IOException("No se pudo interpretar la obra $id")
        workCache.put(id, detail)
        // Refresh the offline copy too (guard: never cache an adult-gate page).
        if (!isAdultGate(html)) workDisk?.put(url, html)
        return detail
    }

    /**
     * Fetches and parses a work's Atom feed (published chapter count + last
     * update date) WITHOUT touching the HTML — used by "check for updates".
     * Returns null when the feed is unavailable. Bypasses the disk cache so a
     * tap always reflects the live site.
     */
    suspend fun getWorkFeed(id: Long): WorkFeedInfo? {
        val url = "https://archiveofourown.org/works/$id.atom"
        val xml = runCatching { get(url, disk = null, retries = 3) }.getOrNull() ?: return null
        return parseWorkFeed(xml)
    }

    /** Parses a work Atom feed; null on malformed/empty XML. (Pure: unit-testable.) */
    internal fun parseWorkFeed(xml: String): WorkFeedInfo? {
        return runCatching {
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            val entries = doc.select("entry")
            if (entries.isEmpty()) return null
            val updated = doc.select("feed > updated").firstOrNull()?.text()?.ifBlank { null }
                ?: entries.firstOrNull()?.select("updated")?.firstOrNull()?.text()
            WorkFeedInfo(chapterCount = entries.size, updated = updated)
        }.getOrNull()
    }

    /**
     * Loads a user's profile (bio, join date, pseuds) plus their works list.
     *
     * Both pages are fetched; Cloudflare occasionally fails one of the two
     * with a transient 5xx, so a partial profile (works without bio, or bio
     * without works) is still returned — only a double failure throws.
     */
    /**
     * Builds a user page URL (profile | works | ...) FRESH on every call.
     *
     * Never reuse one HttpUrl.Builder for two URLs: addPathSegment mutates the
     * builder in place, so the second build() would inherit the first page's
     * segment (e.g. /users/X/profile/works -> HTTP 404). This bug kept author
     * works lists empty for a long time.
     */
    internal fun authorPageUrl(username: String, page: String): String =
        HttpUrl.Builder()
            .scheme("https")
            .host("archiveofourown.org")
            .addPathSegment("users")
            .addPathSegment(username)
            .addPathSegment(page)
            .build()
            .toString()

    /**
     * Loads ONLY the author header (profile page, ~20 KB) — fast, cached.
     * The works list is loaded separately via [getAuthorWorks] so the header
     * renders as soon as it arrives instead of waiting for the heavier works
     * page (108 KB+).
     */
    suspend fun getAuthorProfile(username: String): AuthorProfile {
        authorCache.get(username)?.let { return it }
        val url = authorPageUrl(username, "profile")
        // Runs outside the gate: profile + works may be in flight at the same
        // time (like a browser opening both tabs), so the user waits for the
        // slower of the two, not the sum.
        val html = getConcurrent(url, retries = 4)
        val profile = runCatching {
            withContext(Dispatchers.IO) { Ao3Parser.parseAuthorProfile(html, username) }
        }.getOrNull() ?: AuthorProfile(username = username, displayName = username)
        android.util.Log.i(
            "AO3Lector",
            "AuthorProfile username=$username display=${profile.displayName} joined=${profile.joined} pseuds=${profile.pseuds} bio=${profile.bio.length}ch",
        )
        authorCache.put(username, profile)
        return profile
    }

    /** Loads the author's works list page (/users/{name}/works), cached per session. */
    /** Works of an author, paged (AO3 shows 20 per page). Page 1 is the URL
     *  without the query param; later pages use ?page=N. */
    suspend fun getAuthorWorks(username: String, page: Int = 1): AuthorWorks {
        val cacheKey = "$username|$page"
        authorWorksCache.get(cacheKey)?.let { return it }
        val base = authorPageUrl(username, "works")
        val url = if (page > 1) "$base?page=$page" else base
        val html = getConcurrent(url, retries = 4)
        val (count, works) = withContext(Dispatchers.IO) { Ao3Parser.parseAuthorWorks(html) }
        val result = AuthorWorks(count = count, works = works)
        android.util.Log.i("AO3Lector", "AuthorWorks username=$username page=$page count=$count parsed=${works.size}")
        authorWorksCache.put(cacheKey, result)
        return result
    }

    /** Fetches a chapter's content; falls back to the work page when no chapter URL is known. */
    suspend fun getChapter(workId: Long, chapter: ChapterInfo): ChapterInfo {
        val url = chapter.url ?: "https://archiveofourown.org/works/$workId"
        val cacheKey = "$workId#${chapter.index}"
        chapterCache.get(cacheKey)?.let { return it }
        val html = get(url, allowStaleOnError = true)
        val (content, title) = withContext(Dispatchers.IO) { Ao3Parser.parseChapter(html) }
        val ready = chapter.copy(
            title = title ?: chapter.title,
            content = content ?: chapter.content ?: "",
        )
        chapterCache.put(cacheKey, ready)
        return ready
    }

    // ---- Comments -----------------------------------------------------------

    /** Loads a chapter's comment thread via AO3's AJAX endpoint. */
    suspend fun getComments(chapterId: Long): List<Ao3Comment> {
        commentsCache.get(chapterId)?.let { return it }
        val url = "https://archiveofourown.org/comments/show_comments?chapter_id=$chapterId"
        val js = get(url, mapOf("X-Requested-With" to "XMLHttpRequest", "Accept" to "text/javascript, */*"))
        val comments = withContext(Dispatchers.IO) { Ao3Parser.parseComments(js) }
        android.util.Log.i(
            "AO3Lector",
            "Comments chapter=$chapterId count=${comments.size} maxDepth=${comments.maxOfOrNull { it.depth } ?: -1} authors=${comments.map { it.author }.distinct().take(5)}",
        )
        commentsCache.put(chapterId, comments)
        return comments
    }

    /**
     * Posts a comment as a guest (name + email). Returns the text of any AO3
     * error message, or null on success.
     */
    suspend fun postComment(
        workId: Long,
        chapterId: Long?,
        name: String,
        email: String,
        content: String,
        replyTo: Long? = null,
    ): String? {
        // Grab a fresh authenticity token + form action from the page.
        val pageUrl = if (chapterId != null) {
            "https://archiveofourown.org/works/$workId/chapters/$chapterId"
        } else {
            "https://archiveofourown.org/works/$workId"
        }
        val page = get(pageUrl)
        val token = extractAuthToken(page)
        val action = Regex("""<form[^>]*action="(/chapters/\d+/comments|/works/\d+/comments)"""").find(page)
            ?.groupValues?.get(1)
            ?: "/works/$workId/comments"

        val fields = linkedMapOf<String, String>()
        fields["authenticity_token"] = token
        fields["comment[name]"] = name
        fields["comment[email]"] = email
        fields["comment[comment_content]"] = content
        fields["comment[anonymous]"] = "0"
        if (replyTo != null) fields["comment[reply_to]"] = replyTo.toString()
        fields["commit"] = "Comment"

        val body = post("https://archiveofourown.org$action", fields)
        // A new comment invalidates the cached thread for that chapter.
        val error = Regex("""(?:class="error"|error explanation|We couldn't submit|can't be blank|is invalid|are required)""")
            .find(body)
        if (error == null && chapterId != null) commentsCache.invalidate(chapterId)
        return error?.let {
            Regex("<div[^>]*class=\"error[^\"]*\"[^>]*>(.*?)</div>", RegexOption.DOT_MATCHES_ALL)
                .find(body)?.groupValues?.get(1)?.let { m -> JsoupText(m) }
                ?: "AO3 rechazó el comentario. Revisa que nombre y email sean válidos."
        }
    }

    private fun JsoupText(html: String): String = org.jsoup.Jsoup.parse(html).text().trim()

    // ---- Kudos --------------------------------------------------------------

    /**
     * Posts an anonymous guest kudo to a work (AO3 has no name/email for kudos).
     * Returns null on success, or a human-readable AO3 message otherwise
     * (e.g. "already left kudos" which is not really an error).
     */
    suspend fun postKudos(workId: Long): String? {
        val page = get("https://archiveofourown.org/works/$workId")
        // Guest kudos are tracked with a cookie; if the form is already gone the
        // visitor has left kudos before.
        if (page.contains("You have already left kudos")) {
            return "Ya habías dejado kudos en esta obra ❤"
        }
        val token = extractAuthToken(page)
        val fields = linkedMapOf(
            "authenticity_token" to token,
            "kudo[commentable_id]" to workId.toString(),
            "kudo[commentable_type]" to "Work",
            "commit" to "Kudos ♥",
        )
        val body = post("https://archiveofourown.org/kudos", fields)
        return when {
            body.contains("You have already left kudos") -> "Ya habías dejado kudos en esta obra ❤"
            body.contains("doesn't exist") -> "La obra ya no existe"
            Regex("""<div[^>]*class="error[^"]*"""").containsMatchIn(body) ->
                Regex("""<div[^>]*class="error[^"]*"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
                    .find(body)?.groupValues?.get(1)?.let { JsoupText(it) }
                    ?: "AO3 no pudo registrar el kudo"
            else -> null
        }
    }

    private fun extractAuthToken(page: String): String =
        Regex("""name="authenticity_token"\s+value="([^"]+)"""").find(page)?.groupValues?.get(1)
            ?: Regex("""value="([^"]+)"\s+name="authenticity_token"""").find(page)?.groupValues?.get(1)
            ?: throw IOException("No se pudo obtener el token de sesión de AO3")
}

/** Search outcome: results + whether tag filters were actually applied by AO3. */
data class SearchResult(
    val works: List<WorkSummary>,
    val filtersApplied: Boolean,
    /** The "Filters" sidebar (suggested tags) of the listing, when present. */
    val facets: FilterFacets? = null,
    /** Total number of works matching ("N Works found"), when AO3 shows it. */
    val total: Int? = null,
)
