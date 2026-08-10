package net.spin.ao3.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSearchTest {

    private fun plain(html: String): String = pagePlainText(htmlToLines(html))

    private fun page(text: String): List<Line> = htmlToLines("<p>$text</p>")

    @Test
    fun `pagePlainText keeps words and spacing`() {
        val html = "<p>Hello <b>world</b>, this is a test.</p>"
        assertEquals("Hello world, this is a test.", plain(html))
    }

    @Test
    fun `pagePlainText adds paragraph breaks`() {
        val html = "<p>First line</p><p>Second line</p>"
        val out = plain(html)
        assertTrue("was: $out", out.contains("First line"))
        assertTrue(out.contains("Second line"))
        assertTrue(out.contains("\n\n"))
    }

    @Test
    fun `pagePlainText wraps quotes in guillemets`() {
        val html = "<blockquote>To be or not to be</blockquote>"
        val out = plain(html)
        assertTrue("was: $out", out.contains("“To be or not to be”"))
    }

    @Test
    fun `findInPages finds matches across pages`() {
        val pages = listOf(
            page("Evitative is a dark magical story."),
            page("The story continues with more magic."),
        )
        val matches = findInPages(pages, "magic")
        assertEquals(2, matches.size)
        assertEquals(0, matches[0].page)
        assertEquals(1, matches[1].page)
    }

    @Test
    fun `findInPages is case insensitive`() {
        val pages = listOf(page("Magic and magic"))
        val matches = findInPages(pages, "MAGIC")
        assertEquals(2, matches.size)
    }

    @Test
    fun `findInPages returns empty when not found`() {
        val pages = listOf(page("Nothing here"))
        assertTrue(findInPages(pages, "zzz").isEmpty())
    }

    @Test
    fun `SearchMatch offsets point at the term`() {
        val text = "The quick brown fox"
        val match = SearchMatch(page = 0, start = 4, end = 9) // "quick"
        assertEquals("quick", text.substring(match.start, match.end))
    }
}
