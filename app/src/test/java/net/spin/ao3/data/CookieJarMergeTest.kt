package net.spin.ao3.data

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks down the InMemoryCookieJar merge semantics. The old jar replaced the
 * whole per-host list on every response, so any response carrying a single
 * Set-Cookie (Cloudflare rotates __cf_bm constantly, redirects deliver one hop
 * at a time) wiped view_adult and friends — re-triggering the age gate and
 * CF challenges. Also pins the %20 encoding of /tags/{name}/works URLs: a '+'
 * there 404s.
 */
class CookieJarMergeTest {

    /** OkHttp's Builder defaults to a far-future expiry (session-like). */
    private fun cookie(name: String, value: String, path: String = "/", expiredAt: Long? = null): Cookie =
        Cookie.Builder()
            .name(name)
            .value(value)
            .domain("archiveofourown.org")
            .path(path)
            .apply { expiredAt?.let { expiresAt(it) } }
            .build()

    @Test
    fun `a response with one cookie keeps the rest of the jar`() {
        val existing = listOf(cookie("view_adult", "1"), cookie("__cf_bm", "abc"))
        val merged = Ao3Client.InMemoryCookieJar.merge(
            existing,
            listOf(cookie("__cf_bm", "def")),
            now = 1_000L,
        )
        assertEquals(2, merged.size)
        assertEquals("1", merged.first { it.name == "view_adult" }.value)
        assertEquals("def", merged.first { it.name == "__cf_bm" }.value)
    }

    @Test
    fun `expired set-cookie deletes its slot instead of storing a dead cookie`() {
        val merged = Ao3Client.InMemoryCookieJar.merge(
            listOf(cookie("session", "old")),
            listOf(cookie("session", "gone", expiredAt = 500L)), // e.g. Max-Age=0
            now = 1_000L,
        )
        assertTrue(merged.isEmpty())
    }

    @Test
    fun `previously expired cookies are dropped on merge`() {
        val merged = Ao3Client.InMemoryCookieJar.merge(
            listOf(cookie("stale", "x", expiredAt = 500L)),
            listOf(cookie("fresh", "y")),
            now = 1_000L,
        )
        assertTrue(merged.none { it.name == "stale" })
        assertEquals("y", merged.single { it.name == "fresh" }.value)
    }

    @Test
    fun `same name on different paths are distinct slots`() {
        val merged = Ao3Client.InMemoryCookieJar.merge(
            listOf(cookie("sid", "works-value", path = "/works")),
            listOf(cookie("sid", "root-value", path = "/")),
            now = 1_000L,
        )
        assertEquals(2, merged.size)
        assertEquals("works-value", merged.first { it.path == "/works" }.value)
        assertEquals("root-value", merged.first { it.path == "/" }.value)
    }

    @Test
    fun `last duplicate in one response wins for the same name and path`() {
        val merged = Ao3Client.InMemoryCookieJar.merge(
            emptyList(),
            listOf(cookie("k", "first"), cookie("k", "second")),
            now = 1_000L,
        )
        assertEquals(listOf("second"), merged.map { it.value })
    }

    @Test
    fun `tag urls encode spaces as percent-20 never plus`() {
        val url = Ao3Client().tagWorksUrl("Harry Potter")
        assertTrue("la URL debe llevar el tag como segmento del path: $url", url.contains("/tags/Harry%20Potter/works"))
        assertFalse("'+' en el path da 404 en AO3: $url", url.contains("+"))
    }

    // ---- Comportamiento a nivel de jar (no cubierto por merge()) -----------

    @Test
    fun `a response without set-cookie headers must not clear the jar`() {
        val jar = Ao3Client.InMemoryCookieJar()
        val url = "https://archiveofourown.org/works/1".toHttpUrl()
        jar.saveFromResponse(url, listOf(cookie("view_adult", "1")))
        // OkHttp llama saveFromResponse por CADA hop de redirección; los que no
        // traen Set-Cookie no deben borrar lo acumulado.
        jar.saveFromResponse(url, emptyList())
        assertEquals(listOf("view_adult"), jar.loadForRequest(url).map { it.name })
    }

    @Test
    fun `loadForRequest purges expired cookies on read`() {
        val jar = Ao3Client.InMemoryCookieJar()
        val url = "https://archiveofourown.org/works/1".toHttpUrl()
        jar.saveFromResponse(url, listOf(cookie("__cf_bm", "old", expiredAt = 500L)))
        // Purgar también al LEER: enviar una __cf_bm/view_adult caducada
        // invita a Cloudflare a pedir challenges extra.
        assertTrue(jar.loadForRequest(url).isEmpty())
    }

    @Test
    fun `loadForRequest only returns cookies matching path and domain`() {
        val jar = Ao3Client.InMemoryCookieJar()
        val worksUrl = "https://archiveofourown.org/works/1".toHttpUrl()
        val tagUrl = "https://archiveofourown.org/tags/Harry%20Potter/works".toHttpUrl()
        jar.saveFromResponse(worksUrl, listOf(cookie("sid", "works-value", path = "/works")))
        assertTrue(jar.loadForRequest(worksUrl).any { it.name == "sid" })
        assertTrue(jar.loadForRequest(tagUrl).none { it.name == "sid" })
    }
}
