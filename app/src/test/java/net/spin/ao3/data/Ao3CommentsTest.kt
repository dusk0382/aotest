package net.spin.ao3.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Ao3CommentsTest {

    @Test
    fun `parses comments from real show_comments js fragment`() {
        val js = javaClass.getResourceAsStream("/ao3/comments.js")?.readBytes()?.toString(Charsets.UTF_8)
            ?: throw AssertionError("missing snapshot comments.js")
        val comments = Ao3Parser.parseComments(js)

        assertTrue("expected at least one comment", comments.isNotEmpty())
        val first = comments.first()
        assertTrue("comment has an id", first.id > 0)
        assertTrue("author present", first.author.isNotBlank())
        assertTrue("has some content", first.text.isNotBlank() || first.html.isNotBlank())
        // All comments have sane depth bounds
        comments.forEach { c ->
            assertTrue("depth >= 0", c.depth >= 0)
            assertTrue("depth < 8", c.depth < 8)
        }
    }

    @Test
    fun `empty or unrelated js yields no comments`() {
        assertEquals(0, Ao3Parser.parseComments("alert('nothing');").size)
        assertEquals(0, Ao3Parser.parseComments("").size)
    }

    @Test
    fun `html to plain text keeps paragraph breaks`() {
        val html = "<p>Primer párrafo.</p><p>Segundo <em>párrafo</em>.</p>"
        val text = Ao3Parser.htmlToPlainText(html)
        assertTrue(text.contains("Primer párrafo."))
        assertTrue(text.contains("Segundo párrafo."))
        assertTrue("keeps a line break between paragraphs", text.split("\n").size >= 2)
    }
}
