package net.spin.ao3.data

import net.spin.ao3.data.model.Ao3Comment
import net.spin.ao3.data.model.ChapterInfo
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

    // ---- Comments -----------------------------------------------------------

    /**
     * AO3's modern template loads comments via AJAX from
     * /comments/show_comments?chapter_id=... which answers a small JS snippet
     * whose appended HTML contains the <ol class="thread">. This extracts the
     * HTML, unescapes it and flattens the nested thread into a list with depth.
     */
    fun parseComments(jsFragment: String): List<Ao3Comment> {
        val html = extractThreadHtml(jsFragment) ?: return emptyList()
        val doc = Jsoup.parseBodyFragment(html, BASE)
        val out = mutableListOf<Ao3Comment>()
        doc.select("ol.thread > li.comment.group").forEach { li -> parseComment(li, 0, out) }
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
        return raw
            .replace("\\\"", "\"")
            .replace("\\/", "/")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }

    private fun parseComment(li: Element, depth: Int, out: MutableList<Ao3Comment>) {
        val id = li.attr("id").removePrefix("comment_").toLongOrNull() ?: 0L
        val byline = li.selectFirst("h4.heading.byline")
        val authorEl = byline?.selectFirst("a[rel=author]") ?: byline?.selectFirst("a")
        val author = authorEl?.text()?.trim()
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
                ),
            )
        }
        // Replies live in a nested <ol class="comment children"> (or ol.comment).
        li.select("> ol.comment.children > li.comment.group, > ol.comment > li.comment.group")
            .forEach { child -> parseComment(child, depth + 1, out) }
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
