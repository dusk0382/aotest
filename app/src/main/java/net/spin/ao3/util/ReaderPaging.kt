package net.spin.ao3.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Pagination helpers for the paged reader mode. Pure JVM functions so they are
 * unit-testable: [htmlToLines] converts chapter HTML into block-level [Line]s
 * and [packPages] packs them into pages without ever splitting a paragraph.
 * The Compose layer turns [Line]s into AnnotatedStrings with the reader theme.
 */

/** One styled run of text inside a [Line]. */
data class Segment(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val link: Boolean = false,
)

enum class LineKind { PARAGRAPH, HEADING, QUOTE, LIST, CODE, SEPARATOR }

/** A block-level unit (paragraph, heading, quote, …). Pages never split a line. */
data class Line(
    val segments: List<Segment>,
    val kind: LineKind = LineKind.PARAGRAPH,
)

/**
 * Converts chapter HTML into a flat list of [Line]s. Inline emphasis
 * (b/strong/i/em/u/a) becomes Segment flags; block elements become Lines.
 *
 * Container elements (div/section/article/main/center) are recursed into so a
 * chapter wrapped in extra markup still paginates paragraph-by-paragraph;
 * otherwise the whole chapter collapses into a single oversized line.
 */
fun htmlToLines(html: String): List<Line> {
    val body = Jsoup.parseBodyFragment(html).body()
    val lines = mutableListOf<Line>()

    fun appendInline(el: Element, kind: LineKind) {
        val segs = inlineChildren(el)
        if (segs.isNotEmpty()) lines += Line(segs, kind)
    }

    fun walk(el: Element) {
        // Loose text directly inside a container becomes its own paragraph.
        val directText = el.childNodes()
            .filterIsInstance<TextNode>()
            .joinToString("") { it.text() }
            .trim()
        if (directText.isNotEmpty()) lines += Line(listOf(Segment(directText)), LineKind.PARAGRAPH)
        el.children().forEach { child ->
            when (child.tagName().lowercase()) {
                "div", "section", "article", "main", "center" -> walk(child)
                "h1", "h2", "h3", "h4", "h5", "h6" -> appendInline(child, LineKind.HEADING)
                "blockquote" -> appendInline(child, LineKind.QUOTE)
                "li" -> appendInline(child, LineKind.LIST)
                "pre" -> lines += Line(listOf(Segment(child.text())), LineKind.CODE)
                "hr" -> lines += Line(emptyList(), LineKind.SEPARATOR)
                else -> appendInline(child, LineKind.PARAGRAPH)
            }
        }
    }

    walk(body)
    return lines
}

/**
 * Packs [lines] into pages of at most [targetChars] characters. Lines are never
 * split across pages, so paragraphs stay whole.
 */
fun packPages(lines: List<Line>, targetChars: Int): List<List<Line>> {
    if (lines.isEmpty()) return emptyList()
    val pages = mutableListOf<List<Line>>()
    var current = mutableListOf<Line>()
    var budget = targetChars
    for (line in lines) {
        val len = line.segments.sumOf { it.text.length }
        if (current.isNotEmpty() && budget - len < 0) {
            pages += current
            current = mutableListOf()
            budget = targetChars
        }
        current += line
        budget -= len
    }
    if (current.isNotEmpty()) pages += current
    return pages
}

/** A text match inside a paginated chapter: [page] index + char offsets. */
data class SearchMatch(
    val page: Int,
    val start: Int,
    val end: Int,
)

/**
 * Renders a page's [Line]s exactly like the reader does (same separators,
 * bullets, quote marks and spacing) so offsets line up with the rendered text.
 */
fun pagePlainText(lines: List<Line>): String = buildString {
    lines.forEachIndexed { i, line ->
        if (i > 0) append("\n\n")
        when (line.kind) {
            LineKind.SEPARATOR -> append("  ✦   ✦   ✦  ")
            LineKind.HEADING -> line.segments.forEach { append(it.text) }
            LineKind.QUOTE -> {
                append("“")
                line.segments.forEach { append(it.text) }
                append("”")
            }
            LineKind.LIST -> {
                append("•  ")
                line.segments.forEach { append(it.text) }
            }
            LineKind.CODE -> append(line.segments.firstOrNull()?.text ?: "")
            else -> line.segments.forEach { append(it.text) }
        }
    }
}

/** Finds all case-insensitive occurrences of [query] across the [pages]. */
fun findInPages(pages: List<List<Line>>, query: String): List<SearchMatch> {
    val q = query.lowercase()
    if (q.isEmpty()) return emptyList()
    val matches = mutableListOf<SearchMatch>()
    pages.forEachIndexed { pageIndex, lines ->
        val text = pagePlainText(lines).lowercase()
        var from = 0
        while (true) {
            val idx = text.indexOf(q, from)
            if (idx < 0) break
            matches += SearchMatch(pageIndex, idx, idx + q.length)
            from = idx + q.length
        }
    }
    return matches
}

private fun inlineChildren(el: Element): List<Segment> =
    el.childNodes().flatMap { inlineSegments(it) }

private fun inlineSegments(node: Node): List<Segment> = when (node) {
    is TextNode -> {
        val text = node.text()
        if (text.isBlank()) emptyList() else listOf(Segment(text))
    }
    is Element -> when (node.tagName().lowercase()) {
        "b", "strong" -> inlineChildren(node).map { it.copy(bold = true) }
        "i", "em" -> inlineChildren(node).map { it.copy(italic = true) }
        "u" -> inlineChildren(node)
        "a" -> inlineChildren(node).map { it.copy(link = true) }
        "br" -> listOf(Segment("\n"))
        else -> inlineChildren(node)
    }
    else -> emptyList()
}
