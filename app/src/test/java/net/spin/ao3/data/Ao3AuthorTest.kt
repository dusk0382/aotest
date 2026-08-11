package net.spin.ao3.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Diagnostic + regression tests for the author profile / works parsing.
 *
 * The fixtures are REAL snapshots fetched from AO3 (author: Sable_Scribe,
 * 4 works). If these pass, the parser is fine and the reported failure
 * ("works do not load") lives in the network layer.
 */
class Ao3AuthorTest {

    private fun resource(name: String): String =
        javaClass.getResourceAsStream("/ao3/$name")?.readBytes()?.toString(Charsets.UTF_8)
            ?: throw AssertionError("missing snapshot $name")

    @Test
    fun `parseAuthorWorks parses the real works page`() {
        val html = resource("author_works_snapshot.html")
        val (count, works) = Ao3Parser.parseAuthorWorks(html)

        assertEquals("works count from h2.heading", 4, count)
        assertEquals("blurbs parsed", 4, works.size)

        val first = works.first()
        assertEquals("first work id", 84286016L, first.id)
        assertEquals("first work title", "A Monument to All We Are", first.title)
        assertEquals("author", "Sable_Scribe", first.author)
        assertTrue("fandoms not empty", first.fandoms.isNotEmpty())
        assertTrue("summary not empty", first.summary.isNotBlank())
        assertTrue("words > 0", first.words > 0)
        assertTrue("kudos > 0", first.kudos > 0)
    }

    @Test
    fun `parseAuthorWorks on a Cloudflare 525 body yields empty list without crashing`() {
        // If a 525 page ever slips through, the parser must degrade gracefully.
        val cf = """<html><body id="cf-error-details"><h1>SSL handshake failed</h1>
            <p>error code: 525</p></body></html>"""
        val (count, works) = Ao3Parser.parseAuthorWorks(cf)
        assertNull(count)
        assertEquals(0, works.size)
    }

    @Test
    fun `parseAuthorProfile parses the real profile page`() {
        val html = resource("author_profile_snapshot.html")
        val p = Ao3Parser.parseAuthorProfile(html, "Sable_Scribe")

        assertEquals("displayName", "Sable_Scribe", p.displayName)
        assertEquals("joined", "2014-11-24", p.joined)
        assertTrue("pseuds contains the user", p.pseuds.contains("Sable_Scribe"))
        // Profile has the default icon -> avatarUrl must be null (initial-letter avatar).
        assertNull("default icon maps to null avatar", p.avatarUrl)
    }

    // ---- Regression tests for the adult-gate false positive -----------------
    // The bug: once the view_adult cookie is set, AO3 echoes "view_adult=true"
    // URL-encoded into every page's login return_to, so the old body-contains
    // check treated EVERY page as gated and did a redundant (525-prone) refetch,
    // throwing away the first good response and leaving author works empty.

    @Test
    fun `isAdultGate is false for a real works page that merely mentions view_adult`() {
        val client = Ao3Client()
        val html = resource("author_works_snapshot.html")
        // The snapshot contains "view_adult=true" in the login return_to link.
        assertTrue("snapshot mentions view_adult", html.contains("view_adult"))
        // ...but it is a normal page with the full works list, NOT a gate.
        assertFalse("not an adult gate", client.isAdultGate(html))
    }

    @Test
    fun `isAdultGate is true for a real adult-content interstitial`() {
        val client = Ao3Client()
        val gate = """<div class="notice module"><h3 class="landmark heading">Adult Content</h3>
            <p>This work could have adult content. If you continue, you'll be agreeing to read
            works that are not appropriate for anyone under the age of 18.</p>
            <form><input type="submit" name="view_adult" value="true" /></form></div>"""
        assertTrue("real interstitial detected", client.isAdultGate(gate))
    }

    @Test
    fun `isAdultGate is false for a Cloudflare 525 body`() {
        val client = Ao3Client()
        val cf = """<html><body id="cf-error-details"><h1>SSL handshake failed</h1>
            <p>error code: 525</p></body></html>"""
        assertFalse("CF error page is not an adult gate", client.isAdultGate(cf))
    }
}
