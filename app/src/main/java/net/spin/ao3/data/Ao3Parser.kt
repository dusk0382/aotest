package net.spin.ao3.data

import net.spin.ao3.data.model.Ao3Comment
import net.spin.ao3.data.model.AuthorProfile
import net.spin.ao3.data.model.ChapterInfo
import net.spin.ao3.data.model.FacetGroup
import net.spin.ao3.data.model.FacetItem
import net.spin.ao3.data.model.FacetKind
import net.spin.ao3.data.model.FilterFacets
import net.spin.ao3.data.model.WorkDetail
import net.spin.ao3.data.model.WorkSummary
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder

/**
 * Parses AO3 HTML pages into models.
 *
 * AO3 currently serves two page templates: the classic one (div.chapter[id]
 * wrappers, div.tags.commas.group) and a newer one (tags and stats inside
 * dl.work.meta.group, chapter content directly under div#chapters). All
 * extraction here tries to be resilient to both.
 */
object Ao3Parser {

    private const val BASE = "https://archiveofourown.org"

    // ---- Search results -----------------------------------------------------

    fun parseSearchResults(html: String): List<WorkSummary> {
        val doc = Jsoup.parse(html)
        return doc.select("ol.work.index.group > li.work.blurb").mapNotNull { parseBlurb(it) }
    }

    private fun parseBlurb(li: Element): WorkSummary? {
        val id = li.attr("id").removePrefix("work_").toLongOrNull() ?: return null
        val titleEl = li.selectFirst("h4.heading a[href*='/works/']") ?: return null
        val title = titleEl.text().trim()
        val authorEl = li.selectFirst("h4.heading a[rel=author]")
        val fandoms = li.select("h5.fandoms a.tag").mapNotNull { it.text().trim().ifEmpty { null } }
        val ratingEl = li.selectFirst("ul.required-tags span.rating")
        val warnings = li.select("ul.required-tags span.warning").mapNotNull { it.attr("title").trim().ifEmpty { null } }
        val categories = li.select("ul.required-tags span.category").mapNotNull { it.attr("title").trim().ifEmpty { null } }

        // Blurb tags live in a <ul class="tags commas"> grouped per type.
        val relationships = li.select("ul.tags.commas li.relationships a.tag").mapNotNull { it.text().trim().ifEmpty { null } }
        val characters = li.select("ul.tags.commas li.characters a.tag").mapNotNull { it.text().trim().ifEmpty { null } }
        val freeforms = li.select("ul.tags.commas li.freeforms a.tag").mapNotNull { it.text().trim().ifEmpty { null } }
        val otherTags = (relationships + characters + freeforms).distinct()

        // Summary is a <blockquote class="userstuff summary"> in blurbs.
        val summary = (li.selectFirst("blockquote.userstuff.summary") ?: li.selectFirst("div.userstuff.summary"))
            ?.text()?.trim() ?: ""
        val words = li.selectFirst("dd.words")?.text()?.replace(",", "")?.toLongOrNull() ?: 0L
        val (chapters, total) = parseChapters(li.selectFirst("dd.chapters")?.text()?.trim())
        val hits = li.selectFirst("dd.hits")?.text()?.replace(",", "")?.toLongOrNull() ?: 0L
        val kudos = li.selectFirst("dd.kudos")?.text()?.replace(",", "")?.toLongOrNull() ?: 0L
        val comments = li.selectFirst("dd.comments")?.text()?.replace(",", "")?.toLongOrNull() ?: 0L
        val bookmarks = li.selectFirst("dd.bookmarks")?.text()?.replace(",", "")?.toLongOrNull() ?: 0L
        val updated = li.selectFirst("p.datetime")?.text()?.trim()?.substringAfter(": ")?.trim()
        return WorkSummary(
            id = id,
            title = title,
            author = authorEl?.text()?.trim() ?: "Autor desconocido",
            authorUrl = authorEl?.attr("href"),
            fandoms = fandoms,
            rating = ratingEl?.attr("title")?.trim(),
            ratingKey = ratingEl?.className()?.let(::ratingKeyFromClass),
            warnings = warnings,
            categories = categories,
            otherTags = otherTags,
            relationships = relationships,
            characters = characters,
            summary = summary,
            words = words,
            chapterCount = chapters,
            chapterTotal = total,
            hits = hits,
            kudos = kudos,
            comments = comments,
            bookmarks = bookmarks,
            published = null,
            updated = updated,
            url = "$BASE/works/$id",
        )
    }

    /** Maps a rating span class like "rating-teen rating" to a color key. */
    private fun ratingKeyFromClass(cls: String): String? = when {
        "rating-explicit" in cls -> "explicit"
        "rating-mature" in cls -> "mature"
        "rating-teen" in cls -> "teen-and-up-audiences"
        "rating-general" in cls -> "general-audiences"
        "rating-notrated" in cls -> "not-rated"
        else -> null
    }

    // ---- Work detail --------------------------------------------------------

    fun parseWorkDetail(html: String, id: Long): WorkDetail? {
        val doc = Jsoup.parse(html, BASE)
        val title = doc.selectFirst("h2.title.heading")?.text()?.trim() ?: return null
        val authorEl = doc.selectFirst("a[rel=author]")

        // Gather every dt -> dd pair inside the meta dl (any depth) plus legacy tag dls
        val pairs = mutableListOf<Pair<String, Element>>()
        doc.select("dl.work.meta.group, div.tags.commas.group dl, dl.tags").forEach { dl ->
            dl.select("dt").forEach { dt ->
                val dd = dt.nextElementSibling()
                if (dd != null) pairs.add(dt.text().removeSuffix(":").trim() to dd)
            }
        }

        val stats = mutableMapOf<String, String>()
        val tagSections = mutableMapOf<String, List<String>>()
        var ratingKey: String? = null
        pairs.forEach { (label, dd) ->
            if (dd.selectFirst("dl") != null) return@forEach // skip 'Stats:' container dd
            val tags = dd.select("a.tag, span.rating, span.warning, span.category")
                .mapNotNull { it.attr("title").ifEmpty { it.text() }.trim().ifEmpty { null } }
            if (tags.isNotEmpty()) {
                tagSections[label] = tags
                if (label.equals("rating", ignoreCase = true)) {
                    ratingKey = URLDecoder.decode(
                        dd.selectFirst("a.tag")?.attr("href")?.substringAfter("/tags/")?.substringBefore("/works") ?: "",
                        "UTF-8",
                    ).lowercase().replace(' ', '-')
                }
            } else {
                stats[label] = dd.text().trim()
            }
        }

        val fandoms = tagSections.entries.firstOrNull { it.key.contains("fandom", ignoreCase = true) }?.value.orEmpty()
        val warnings = tagSections.entries.firstOrNull { it.key.contains("warning", ignoreCase = true) }?.value.orEmpty()
        val categories = tagSections.entries.firstOrNull { it.key.contains("categor", ignoreCase = true) }?.value.orEmpty()
        val relationships = tagSections.entries.firstOrNull { it.key.contains("relationship", ignoreCase = true) }?.value.orEmpty()
        val characters = tagSections.entries.firstOrNull { it.key.contains("character", ignoreCase = true) }?.value.orEmpty()
        val additionalTags = tagSections.entries.firstOrNull { it.key.contains("additional", ignoreCase = true) }?.value.orEmpty()

        val chaptersCount = stats["Chapters"] ?: ""
        val (chCount, chTotal) = parseChapters(chaptersCount)
        val words = stats["Words"]?.replace(",", "")?.toLongOrNull() ?: 0L
        val hits = stats["Hits"]?.replace(",", "")?.toLongOrNull() ?: 0L
        val kudos = stats["Kudos"]?.replace(",", "")?.toLongOrNull() ?: 0L
        val comments = stats["Comments"]?.replace(",", "")?.toLongOrNull() ?: 0L
        val bookmarks = stats["Bookmarks"]?.replace(",", "")?.toLongOrNull() ?: 0L
        val rating = tagSections.entries.firstOrNull { it.key.equals("rating", ignoreCase = true) }?.value?.firstOrNull()

        val summary = WorkSummary(
            id = id,
            title = title,
            author = authorEl?.text()?.trim() ?: "Autor desconocido",
            authorUrl = authorEl?.attr("href"),
            fandoms = fandoms,
            rating = rating,
            ratingKey = ratingKey,
            warnings = warnings,
            categories = categories,
            otherTags = (relationships + characters + additionalTags).distinct(),
            relationships = relationships,
            characters = characters,
            summary = doc.selectFirst("div.summary.module .userstuff")?.text()?.trim() ?: "",
            words = words,
            chapterCount = chCount,
            chapterTotal = chTotal,
            hits = hits,
            kudos = kudos,
            comments = comments,
            bookmarks = bookmarks,
            published = stats["Published"],
            updated = stats["Updated"],
            url = "$BASE/works/$id",
        )

        val descriptionHtml = sanitize(doc.selectFirst("div.summary.module .userstuff")?.html(), BASE)
        val notesHtml = sanitize(doc.selectFirst("div.notes.module .userstuff")?.html(), BASE)
        val chapters = parseChapters(doc, id)

        return WorkDetail(
            summary = summary,
            descriptionHtml = descriptionHtml,
            notesHtml = notesHtml,
            relationships = relationships,
            characters = characters,
            additionalTags = additionalTags,
            chapters = chapters,
        )
    }

    private fun parseChapters(doc: Document, workId: Long): List<ChapterInfo> {
        // Newer template: chapter navigation is a <select id="selected_id"> whose
        // options carry the chapter id and the title ("N. Title").
        val select = doc.selectFirst("select#selected_id")
        if (select != null) {
            val options = select.select("option")
            if (options.isNotEmpty()) {
                return options.mapIndexed { i, opt ->
                    val text = opt.text().trim()
                    val title = text.substringAfter('.').trim().ifEmpty { text }
                    val chapterId = opt.attr("value").toLongOrNull()
                    ChapterInfo(
                        index = i,
                        title = title,
                        url = if (chapterId != null) "$BASE/works/$workId/chapters/$chapterId" else "$BASE/works/$workId",
                        chapterId = chapterId,
                    )
                }
            }
        }
        // Legacy template: chapter index as a list of links.
        val indexLinks = doc.select("ol.chapter li a[href*='/chapters/']")
        if (indexLinks.isNotEmpty()) {
            return indexLinks.mapIndexed { i, a ->
                val href = a.absUrl("href")
                ChapterInfo(
                    index = i,
                    title = a.text().trim(),
                    url = href,
                    chapterId = Regex("/chapters/(\\d+)").find(href)?.groupValues?.get(1)?.toLongOrNull(),
                )
            }
        }
        // Classic template: all chapters inline on the work page.
        val inline = doc.select("div#chapters div.chapter[id]")
        if (inline.isNotEmpty()) {
            return inline.mapIndexed { i, c ->
                ChapterInfo(
                    index = i,
                    title = c.selectFirst("h3.title")?.text()?.trim() ?: "",
                    url = "$BASE/works/$workId",
                    content = sanitize(c.selectFirst("div.userstuff")?.html(), BASE),
                )
            }
        }
        // Single chapter page without an index.
        val content = extractChapterContent(doc)
        if (content != null) {
            return listOf(
                ChapterInfo(index = 0, title = "", url = "$BASE/works/$workId", content = sanitize(content, BASE)),
            )
        }
        return emptyList()
    }

    // ---- Chapter page -------------------------------------------------------

    /** Returns (sanitized content html, optional chapter title). */
    fun parseChapter(html: String): Pair<String?, String?> {
        val doc = Jsoup.parse(html, BASE)
        return sanitize(extractChapterContent(doc), BASE) to extractChapterTitle(doc)
    }

    /**
     * The content userstuff is a direct child of the chapter div; preface
     * (notes) and afterword blocks nest their own userstuff inside them.
     */
    private fun extractChapterContent(doc: Document): String? {
        doc.selectFirst("div#chapters div.chapter[id] > div.userstuff")?.let { return it.html() }
        return doc.select("div#chapters div.userstuff").firstOrNull { el ->
            el.parents().none { p -> p.hasClass("preface") || p.hasClass("afterword") }
        }?.html()
    }

    private fun extractChapterTitle(doc: Document): String? =
        doc.selectFirst("div#chapters h3.title")?.text()?.trim()?.substringAfter(":")?.trim()?.ifEmpty { null }

    // ---- Author profile -----------------------------------------------------

    /**
     * Parses the profile page (/users/{name}/profile): display name, join date,
     * pseuds and bio. The bio block is optional (many accounts have none).
     */
    fun parseAuthorProfile(html: String, username: String): AuthorProfile {
        val doc = Jsoup.parse(html, BASE)
        val displayName = doc.selectFirst("h2.heading")?.text()?.trim() ?: username
        val metaDl = doc.selectFirst("dl.meta")
        var joined: String? = null
        val pseuds = mutableListOf<String>()
        metaDl?.select("dt")?.forEach { dt ->
            val label = dt.text().lowercase()
            val dd = dt.nextElementSibling()
            when {
                label.contains("joined") -> joined = dd?.text()?.trim()?.ifEmpty { null }
                label.contains("pseud") -> dd?.select("a")?.forEach { a ->
                    a.text().trim().takeIf { it.isNotBlank() }?.let { pseuds += it }
                }
            }
        }
        if (pseuds.isEmpty()) {
            // Older layout: plain dd text.
            metaDl?.select("dt")?.forEach { dt ->
                if (dt.text().lowercase().contains("pseud")) {
                    dt.nextElementSibling()?.text()?.split(Regex("\\s+(?:and|&)\\s+"))
                        ?.map { it.trim() }?.filter { it.isNotBlank() }?.let { pseuds += it }
                }
            }
        }
        val bioEl = doc.selectFirst("div#bio, dd.bio, div.bio, .userstuff#bio, #bio")
        val bio = bioEl?.text()?.trim() ?: ""
        // The profile icon is an <img class="icon"> in the header; the default
        // iconsets/default/icon_user.png is served when the user has none, in
        // which case we keep the avatar null and show an initial-letter avatar.
        val avatarUrl = realIconUrl(doc.selectFirst("img.icon"))
        return AuthorProfile(
            username = username,
            displayName = displayName,
            joined = joined,
            pseuds = pseuds,
            bio = bio,
            avatarUrl = avatarUrl,
        )
    }

    /** Parses /users/{name}/works: works count (from the h2) + blurb list. */
    fun parseAuthorWorks(html: String): Pair<Int?, List<WorkSummary>> {
        val doc = Jsoup.parse(html, BASE)
        val works = doc.select("ol.work.index.group > li.work.blurb").mapNotNull { parseBlurb(it) }
        val count = Regex("""(\d+)\s+Works?""")
            .find(doc.selectFirst("h2.heading")?.text() ?: "")
            ?.groupValues?.get(1)?.toIntOrNull()
        return count to works
    }

    // ---- Filter sidebar facets ----------------------------------------------

    private fun facetDdSuffix(kind: FacetKind): String = when (kind) {
        FacetKind.RATING -> "rating_tags"
        FacetKind.WARNING -> "archive_warning_tags"
        FacetKind.CATEGORY -> "category_tags"
        FacetKind.FANDOM -> "fandom_tags"
        FacetKind.CHARACTER -> "character_tags"
        FacetKind.RELATIONSHIP -> "relationship_tags"
        FacetKind.FREEFORM -> "freeform_tags"
    }

    private fun facetKindName(kind: FacetKind): String = when (kind) {
        FacetKind.RATING -> "Ratings"
        FacetKind.WARNING -> "Warnings"
        FacetKind.CATEGORY -> "Categorías"
        FacetKind.FANDOM -> "Fandoms"
        FacetKind.CHARACTER -> "Personajes"
        FacetKind.RELATIONSHIP -> "Relaciones"
        FacetKind.FREEFORM -> "Etiquetas"
    }

    /**
     * Parses the "Filters" sidebar of a tag/works listing (fieldset > dl with
     * include_/exclude_ dd blocks; each li has a hidden input with the tag id
     * and a <span>Name (count)</span> label).
     */
    fun parseFacets(html: String): FilterFacets {
        val doc = Jsoup.parse(html, BASE)
        val groups = mutableListOf<FacetGroup>()
        FacetKind.entries.forEach { kind ->
            val suffix = facetDdSuffix(kind)
            fun items(prefix: String): List<FacetItem> {
                val dd = doc.selectFirst("dd#$prefix$suffix") ?: return emptyList()
                return dd.select("li").mapNotNull { li ->
                    val input = li.selectFirst("input[value]") ?: return@mapNotNull null
                    val id = input.attr("value").toLongOrNull() ?: return@mapNotNull null
                    val span = li.select("span").lastOrNull()?.text() ?: return@mapNotNull null
                    val count = Regex("""\((\d[\d,]*)\)\s*$""").find(span)
                        ?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
                    val label = span.replace(Regex("""\s*\(\d[\d,]*\)\s*$"""), "").trim()
                    FacetItem(id, label, count)
                }
            }
            val include = items("include_")
            val exclude = items("exclude_")
            if (include.isNotEmpty() || exclude.isNotEmpty()) {
                groups += FacetGroup(kind, facetKindName(kind), include, exclude)
            }
        }
        return FilterFacets(groups)
    }

    // ---- Comments -----------------------------------------------------------

    /**
     * Parses a chapter's comment thread. AO3 currently answers
     * /comments/show_comments?chapter_id=... with either a small JS snippet
     * (old template: $j("#comments_placeholder").append("...")) or with the
     * full thread HTML directly (new template). Both are handled here.
     *
     * IMPORTANT: replies nest inside the parent comment as
     * `<li>… <ol class="thread"> <li class="comment group">…</ol> </li>` — a
     * plain `ol.thread > li.comment.group` selector would match BOTH the root
     * comments and every reply (jsoup evaluates every ol.thread in the doc),
     * flattening the thread. Root comments must come from the FIRST ol.thread
     * only; replies are collected recursively.
     */
    fun parseComments(fragment: String): List<Ao3Comment> {
        val html = extractThreadHtml(fragment) ?: run {
            // New template: the response already is the thread HTML (or a full page).
            if (fragment.contains("class=\"thread\"") || fragment.contains("<ol class=\"thread\">")) fragment else return emptyList()
        }
        val doc = Jsoup.parseBodyFragment(html, BASE)
        val out = mutableListOf<Ao3Comment>()
        val root = doc.selectFirst("ol.thread") ?: return out
        root.children().filter { it is Element && it.tagName() == "li" && it.hasClass("comment") && it.hasClass("group") }
            .forEach { li -> parseComment(li as Element, 0, out) }
        return out
    }

    /** Grabs the HTML inside $j("#comments_placeholder").append("..."); and unescapes it. */
    private fun extractThreadHtml(js: String): String? {
        val start = js.indexOf("#comments_placeholder").takeIf { it >= 0 } ?: return null
        val idx = js.indexOf(".append(\"", start).takeIf { it >= 0 } ?: return null
        val begin = idx + ".append(\"".length
        val end = js.indexOf("\");", begin).takeIf { it >= 0 } ?: return null
        val raw = js.substring(begin, end)
        if (raw.isBlank()) return null
        return unescapeJs(raw)
    }

    /**
     * Properly unescapes a JS double-quoted string (the comment HTML is served
     * inside $j(...).append("...")). Chained String.replace calls get the
     * escape order wrong (e.g. \\' and \\\"); this walks the string once.
     */
    private fun unescapeJs(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    '"' -> sb.append('"')
                    '\'' -> sb.append('\'')
                    '/' -> sb.append('/')
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    '\\' -> sb.append('\\')
                    'u' -> {
                        val hex = s.substring(i + 2, minOf(i + 6, s.length))
                        hex.toIntOrNull(16)?.let { sb.append(it.toChar()) } ?: sb.append("\\u$hex")
                        i += 4
                    }
                    else -> {
                        sb.append(c)
                        sb.append(n)
                    }
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun parseComment(li: Element, depth: Int, out: MutableList<Ao3Comment>) {
        val id = li.attr("id").removePrefix("comment_").toLongOrNull() ?: 0L
        val byline = li.selectFirst("h4.heading.byline")
        // The commenter link is the first <a> of the byline NOT inside the
        // "on Chapter N" parent span. Newer JS template omits rel=author.
        val authorEl = byline?.selectFirst("a[rel=author]")
            ?: byline?.select("a")?.firstOrNull { link ->
                link.parents().none { p -> p.hasClass("parent") }
            }
        val author = authorEl?.text()?.trim()
            // Guest comments have no link: take the first plain span (skipping
            // "(Guest)" role and "on Chapter N" parent annotations).
            ?: byline?.select("span")?.firstOrNull { !it.hasClass("role") && !it.hasClass("parent") }?.text()?.trim()
            ?: byline?.ownText()?.trim()?.ifEmpty { byline.text().trim() }
            ?: "Anónimo"
        val date = byline?.selectFirst("span.posted.datetime")?.text()?.trim() ?: ""
        val body = li.selectFirst("blockquote.userstuff")
        val html = body?.html()?.trim().orEmpty()
        val text = body?.text()?.trim() ?: ""
        if (html.isNotBlank() || author.isNotBlank()) {
            out.add(
                Ao3Comment(
                    id = id,
                    author = author,
                    authorUrl = authorEl?.attr("href")?.takeIf { it.startsWith("http") || it.startsWith("/") },
                    date = date,
                    html = sanitize(html, BASE) ?: "",
                    text = text,
                    depth = depth,
                    avatarUrl = realIconUrl(li.selectFirst("img.icon")),
                ),
            )
        }
        // Replies are NOT nested inside the comment <li>: AO3 emits them in the
        // FOLLOWING sibling <li> (a bare wrapper) that contains its own
        // <ol class="thread">. IMPORTANT: only the FIRST ol.thread of the wrapper
        // belongs to this comment — deeper ones are replies of replies and are
        // reached through recursion (selectAll would duplicate them).
        // Legacy selectors kept as fallbacks.
        val wrapper = li.nextElementSibling()
        if (wrapper != null && wrapper.tagName() == "li" && !wrapper.hasClass("comment")) {
            wrapper.selectFirst("ol.thread")?.let { thread ->
                thread.children()
                    .filter { it is Element && it.tagName() == "li" && it.hasClass("comment") && it.hasClass("group") }
                    .forEach { child -> parseComment(child as Element, depth + 1, out) }
            }
        }
        // Legacy: replies nested directly inside the comment <li>.
        li.select(
            "> ol.thread > li.comment.group, " +
                "> ol.comment.children > li.comment.group, " +
                "> ol.comment > li.comment.group, " +
                "> ol.children > li.comment.group",
        ).forEach { child -> parseComment(child, depth + 1, out) }
    }

    /**
     * Absolutizes an icon URL, returning null for the anonymous default icon
     * (iconsets/default/icon_user.png) so the UI shows a nicer initial avatar
     * instead of the generic gray person.
     */
    private fun realIconUrl(el: Element?): String? {
        val url = el?.absUrl("src")?.takeIf { it.isNotBlank() } ?: return null
        return if (url.contains("icon_user") || url.contains("/default/")) null else url
    }

    // ---- Helpers ------------------------------------------------------------

    /** "4/?", "12/12" -> (4, 12), "1/1" -> (1, 1). */
    private fun parseChapters(text: String?): Pair<Int, Int?> {
        if (text.isNullOrBlank()) return 1 to 1
        val m = Regex("""(\d+)\s*/\s*(\d+|\?)""").find(text)
        if (m == null) {
            val n = text.toIntOrNull()
            return (n ?: 1) to (n ?: 1)
        }
        val current = m.groupValues[1].toIntOrNull() ?: 1
        val total = m.groupValues[2].toIntOrNull()
        return current to total
    }

    /** Strips interactive elements and absolutizes URLs so content is safe to render. */
    fun sanitize(html: String?, baseUrl: String = BASE): String? {
        if (html.isNullOrBlank()) return null
        val doc = Jsoup.parseBodyFragment(html, baseUrl)
        doc.select("script, style, iframe, form, nav, button, input, select, textarea").remove()
        // AO3 puts a screen-reader-only "Chapter Text" landmark inside the
        // chapter userstuff; it must not show up as a paragraph in the reader.
        doc.select("h1.landmark, h2.landmark, h3.landmark, h4.landmark, h5.landmark, h6.landmark").remove()
        doc.select("img").forEach { img ->
            img.attr("src", img.absUrl("src")).attr("referrerpolicy", "no-referrer")
            img.attr("loading", "lazy")
        }
        doc.select("a").forEach { a -> a.attr("href", a.absUrl("href")) }
        return doc.body().html()
    }

    /** Converts sanitized HTML to a readable plain-text rendition. */
    fun htmlToPlainText(html: String?): String {
        if (html.isNullOrBlank()) return ""
        val doc = Jsoup.parseBodyFragment(html)
        val sb = StringBuilder()
        walkText(doc.body(), sb)
        return sb.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private val blockTags = setOf(
        "p", "div", "li", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6",
        "center", "hr", "pre", "table", "ul", "ol", "tr", "td", "section", "header", "footer",
    )

    private fun walkText(el: Element, sb: StringBuilder) {
        for (node in el.childNodes()) {
            when (node) {
                is org.jsoup.nodes.TextNode -> appendText(sb, node.text().trim())
                is Element -> {
                    val tag = node.tagName().lowercase()
                    if (tag in blockTags) {
                        trimTrailingSpace(sb)
                        sb.append('\n')
                        walkText(node, sb)
                        trimTrailingSpace(sb)
                        sb.append('\n')
                    } else if (tag == "br") {
                        trimTrailingSpace(sb)
                        sb.append('\n')
                    } else {
                        walkText(node, sb)
                    }
                }
                else -> Unit
            }
        }
    }

    private fun trimTrailingSpace(sb: StringBuilder) {
        if (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1)
    }

    /** Appends a word, avoiding a space before punctuation or another space. */
    private fun appendText(sb: StringBuilder, t: String) {
        if (t.isEmpty()) return
        if (sb.isNotEmpty() && sb.last() == ' ') {
            if (t.first() in ".,;:!?…") sb.setLength(sb.length - 1)
        }
        sb.append(t).append(' ')
    }
}
