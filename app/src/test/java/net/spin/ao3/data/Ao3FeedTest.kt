package net.spin.ao3.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests [Ao3Client.parseWorkFeed] against realistic AO3 Atom feed samples. */
class Ao3FeedTest {

    private val client = Ao3Client(cacheDir = null)

    @Test
    fun `parses chapter count and last updated from a multi-chapter feed`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Title</title>
              <updated>2026-08-15T18:30:00Z</updated>
              <entry><title>Chapter 1</title><updated>2026-08-10T10:00:00Z</updated></entry>
              <entry><title>Chapter 2</title><updated>2026-08-12T11:00:00Z</updated></entry>
              <entry><title>Chapter 3</title><updated>2026-08-15T18:30:00Z</updated></entry>
            </feed>
        """.trimIndent()
        val info = client.parseWorkFeed(xml)
        assertEquals(3, info!!.chapterCount)
        assertEquals("2026-08-15T18:30:00Z", info.updated)
    }

    @Test
    fun `falls back to the first entry when the feed has no top-level updated`() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry><title>C1</title><updated>2026-01-01T00:00:00Z</updated></entry>
            </feed>
        """.trimIndent()
        val info = client.parseWorkFeed(xml)
        assertEquals(1, info!!.chapterCount)
        assertEquals("2026-01-01T00:00:00Z", info.updated)
    }

    @Test
    fun `empty feed returns null`() {
        assertNull(client.parseWorkFeed("<feed xmlns=\"http://www.w3.org/2005/Atom\"></feed>"))
        assertNull(client.parseWorkFeed("not xml at all"))
        assertNull(client.parseWorkFeed(""))
    }

    @Test
    fun `single chapter work reports one entry`() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry><title>Only chapter</title><updated>2026-05-01T00:00:00Z</updated></entry>
            </feed>
        """.trimIndent()
        assertEquals(1, client.parseWorkFeed(xml)!!.chapterCount)
    }
}
