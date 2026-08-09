package net.spin.ao3.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses real AO3 HTML snapshots (app/src/test/resources/ao3) captured in
 * Aug 2026 from the current site template.
 */
class Ao3ParserTest {

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource("ao3/$name")) { "missing $name" }
            .readText()

    @Test
    fun `blurb parses summary and typed tags`() {
        val works = Ao3Parser.parseSearchResults(resource("blurb.html"))
        assertTrue("expected results, got ${works.size}", works.size > 10)

        val first = works.first()
        // Summary lives in <blockquote class="userstuff summary">.
        assertTrue("summary should be non-blank: '${first.summary}'", first.summary.isNotBlank())
        assertTrue(
            "summary should mention Naruto: '${first.summary}'",
            first.summary.contains("Naruto") || first.summary.contains("Sasuke"),
        )
        // Characters/relationships come from <ul class="tags commas">.
        assertTrue("characters should be parsed: ${first.characters}", first.characters.isNotEmpty())
        assertTrue(
            "characters should include a character: ${first.characters}",
            first.characters.any { it.contains("Naruto") || it.contains("Sasuke") },
        )
        assertTrue("relationships should be parsed: ${first.relationships}", first.relationships.isNotEmpty())
        assertTrue("otherTags should aggregate: ${first.otherTags}", first.otherTags.isNotEmpty())

        // Chapter stats in blurbs are strings like "1/1".
        assertTrue(first.chapterCount >= 1)
        assertNotNull(first.rating)
    }

    @Test
    fun `work detail parses every chapter from the select index`() {
        val detail = Ao3Parser.parseWorkDetail(resource("work.html"), 8211566)
        assertNotNull(detail)
        val d = detail!!

        // The modern template lists chapters in <select id="selected_id">.
        assertTrue("expected >=30 chapters, got ${d.chapters.size}", d.chapters.size >= 30)
        assertEquals("Echoed hours", d.chapters[0].title)
        assertTrue(d.chapters[0].url!!.contains("/chapters/"))
        assertTrue(d.chapters.last().url!!.contains("/chapters/"))

        // Stats from dl.work.meta.group.
        assertTrue(d.summary.chapterCount >= 30)
        assertTrue(d.summary.words > 0)
        assertTrue(d.summary.kudos > 0)

        // Tag sections.
        assertTrue("characters parsed: ${d.characters.size}", d.characters.isNotEmpty())
        assertTrue("relationships parsed: ${d.relationships.size}", d.relationships.isNotEmpty())
        assertTrue("additional tags parsed: ${d.additionalTags.size}", d.additionalTags.isNotEmpty())

        // Summary from <div class="summary module"><blockquote class="userstuff">.
        assertTrue("work summary should be non-blank", d.summary.summary.isNotBlank())
    }

    @Test
    fun `chapter page extracts content and skips notes`() {
        val (content, title) = Ao3Parser.parseChapter(resource("chap.html"))
        assertNotNull("content should be extracted", content)
        val html = content!!
        assertTrue("content should contain real paragraphs", html.contains("<p>"))
        // The notes blockquote must not be mistaken for the chapter body.
        assertTrue("content should be substantial", html.length > 500)
        assertEquals("Promise of Heart", title)
    }

    @Test
    fun `sanitize absolutizes links and strips scripts`() {
        val out = Ao3Parser.sanitize(
            """<p>Hola <a href="/works/1">link</a><script>alert(1)</script><img src="/images/foo.jpg"></p>""",
        )
        assertNotNull(out)
        assertTrue(out!!.contains("https://archiveofourown.org/works/1"))
        assertTrue(!out.contains("<script"))
    }
}
