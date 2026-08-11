package net.spin.ao3.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.spin.ao3.data.model.Ao3Comment
import net.spin.ao3.data.model.AuthorProfile
import net.spin.ao3.data.model.ChapterInfo
import net.spin.ao3.data.model.FilterFacets
import net.spin.ao3.data.model.SearchFilters
import net.spin.ao3.data.model.SortOption
import net.spin.ao3.data.model.WorkDetail
import net.spin.ao3.data.model.WorkSummary
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
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
 *    `view_adult` cookie) is followed like a browser would.
 *
 * Parsed results are cached in memory (LRU, small) so navigating back to a
 * screen already visited (search results, work detail, comments, author
 * profiles, chapters) is instant and does not hit AO3 again.
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
    private suspend fun fetch(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val b = Request.Builder().url(url).header(
                "User-Agent",
                "AO3-Lector/0.1 (personal reader app; okhttp)",
            ).header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            headers.forEach { (k, v) -> b.header(k, v) }
            client.newCall(b.build()).execute().use { response ->
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

    private suspend fun get(url: String, headers: Map<String, String> = emptyMap(), retries: Int = 7): String = gate.withLock {
        var lastError: Exception? = null
        repeat(retries) { attempt ->
            // First attempt goes out right away; back off between retries.
            if (attempt > 0) delay(700L * attempt)
            try {
                val body = fetch(url, headers)
                // AO3 shows an age gate to visitors without the view_adult cookie.
                if (isAdultGate(body)) {
                    val adultUrl = if (url.contains('?')) "$url&view_adult=true" else "$url?view_adult=true"
                    // The gated body already carries the full page (the notice is a
                    // banner, not a replacement). Only swap it for the refetch when
                    // that refetch succeeds: never throw away a good response just
                    // because a redundant refetch hit a Cloudflare 525/timeout.
                    runCatching { fetch(adultUrl, headers) }.getOrNull()?.let { return it }
                }
                return body
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Error de red")
    }

    /** POSTs a form and returns the final response body (after redirects). */
    private suspend fun post(url: String, fields: Map<String, String>): String = gate.withLock {
        var lastError: Exception? = null
        repeat(5) { attempt ->
            delay(if (attempt == 0) 400 else 1200L * attempt)
            try {
                val form = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
                val body = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "AO3-Lector/0.1 (personal reader app; okhttp)")
                        .header("Referer", url)
                        .post(form as RequestBody)
                        .build()
                    client.newCall(request).execute().use { response ->
                        val b = response.body?.string() ?: ""
                        if (!response.isSuccessful && response.code != 302 && response.code != 303) {
                            throw IOException("HTTP ${response.code} al enviar el formulario")
                        }
                        b
                    }
                }
                if (isCloudflareErrorPage(body)) {
                    throw IOException("AO3 respondió con un error temporal (Cloudflare)")
                }
                return body
            } catch (e: Exception) {
                lastError = e
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
    private val canonicalTagCache = java.util.concurrent.ConcurrentHashMap<String, String?>()
    private val facetsCache = java.util.concurrent.ConcurrentHashMap<String, FilterFacets>()

    // ---- In-memory result caches (LRU) -------------------------------------
    // Bounded so memory stays small; keyed by the exact request so a back
    // navigation hits the cache instead of AO3.
    private val workCache = LruCache<Long, WorkDetail>(40)
    private val searchCache = LruCache<String, SearchResult>(30)
    private val chapterCache = LruCache<String, ChapterInfo>(120)
    private val commentsCache = LruCache<Long, List<Ao3Comment>>(30)
    private val authorCache = LruCache<String, AuthorProfile>(30)

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
            if (canonical != null) return get(buildTagFilterUrl(canonical, filters, page, sort))
            return get(buildSearchUrl(filters, page, sort))
        }
        return get(buildSearchUrl(filters, page, sort))
    }

    private suspend fun searchFresh(filters: SearchFilters, page: Int, sort: SortOption): SearchResult {
        val html = fetchSearchPage(filters, page, sort)
        val works = Ao3Parser.parseSearchResults(html)
        val tag = filters.tag ?: filters.query.takeIf { filters.hasFilters && it.isNotBlank() }
        val applied = if (tag != null && tag.isNotBlank()) {
            runCatching { resolveCanonicalTag(tag) }.getOrNull() != null
        } else {
            true
        }
        return SearchResult(works, applied, Ao3Parser.parseFacets(html), Ao3Parser.parseResultCount(html))
    }

    /**
     * Resolves a tag name to its canonical form used by /works?tag_id=...
     *
     * IMPORTANT: the name goes in the URL *path*, so it must be percent-encoded
     * (spaces -> %20). URLEncoder alone is NOT enough: it encodes spaces as '+'
     * which is only valid in query strings — /tags/Harry+Potter/works 404s.
     */
    /** Fetches (and caches) /tags/{name}/works — shared by tag resolution + facets. */
    private val tagPages = java.util.concurrent.ConcurrentHashMap<String, String>()

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
        return runCatching { get(url) }.getOrNull()?.also { tagPages[name] = it }
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
            Ao3Parser.parseFacets(tagWorksPage(canonical) ?: return@runCatching FilterFacets())
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
        val html = get("https://archiveofourown.org/works/$id")
        val detail = Ao3Parser.parseWorkDetail(html, id)
            ?: throw IOException("No se pudo interpretar la obra $id")
        workCache.put(id, detail)
        return detail
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

    suspend fun getAuthorProfile(username: String): AuthorProfile {
        authorCache.get(username)?.let { return it }
        val profileUrl = authorPageUrl(username, "profile")
        val worksUrl = authorPageUrl(username, "works")

        // Author pages occasionally flake behind Cloudflare; keep the retries
        // moderate so a partial profile (name + avatar) still renders quickly.
        // Note: requests are serialized by the shared gate mutex (site-respect),
        // so worst-case wait is the sum of both retry ladders, not the max.
        val (profileHtml, worksHtml) = coroutineScope {
            val p = async { runCatching { get(profileUrl, retries = 4) } }
            val w = async { runCatching { get(worksUrl, retries = 4) } }
            p.await() to w.await()
        }

        val profile = profileHtml.getOrNull()?.let { runCatching { Ao3Parser.parseAuthorProfile(it, username) }.getOrNull() }
        val worksParse = worksHtml.getOrNull()?.let { runCatching { Ao3Parser.parseAuthorWorks(it) } }
        val works = worksParse?.getOrNull()
        // Surface WHY the works list is missing (network vs parse) so the UI can
        // tell a transient Cloudflare failure apart from a genuinely empty list.
        val worksError = worksHtml.exceptionOrNull()?.message
            ?: worksParse?.exceptionOrNull()?.message

        val result = (profile ?: AuthorProfile(username = username, displayName = username))
            .copy(
                worksCount = works?.first,
                works = works?.second.orEmpty(),
                worksError = worksError,
            )
        if (profileHtml.isFailure && worksHtml.isFailure) {
            throw profileHtml.exceptionOrNull() ?: worksHtml.exceptionOrNull()!!
        }
        android.util.Log.i(
            "AO3Lector",
            "AuthorProfile username=$username display=${result.displayName} worksCount=${result.worksCount} worksParsed=${result.works.size} joined=${result.joined} pseuds=${result.pseuds} bio=${result.bio.length}ch" +
                (if (profileHtml.isFailure) " profileError=${profileHtml.exceptionOrNull()?.message}" else "") +
                (if (worksError != null) " worksError=$worksError" else ""),
        )
        // Only cache COMPLETE profiles: caching a transient works failure would
        // keep the works list empty forever on every later visit.
        if (profileHtml.isSuccess && worksHtml.isSuccess) {
            authorCache.put(username, result)
        }
        return result
    }

    /** Fetches a chapter's content; falls back to the work page when no chapter URL is known. */
    suspend fun getChapter(workId: Long, chapter: ChapterInfo): ChapterInfo {
        val url = chapter.url ?: "https://archiveofourown.org/works/$workId"
        val cacheKey = "$workId#${chapter.index}"
        chapterCache.get(cacheKey)?.let { return it }
        val html = get(url)
        val (content, title) = Ao3Parser.parseChapter(html)
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
        val comments = Ao3Parser.parseComments(js)
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
