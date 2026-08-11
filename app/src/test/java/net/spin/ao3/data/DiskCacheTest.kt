package net.spin.ao3.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DiskCacheTest {

    private fun tempDir(): File = File.createTempFile("diskcache", "").apply {
        delete()
        mkdirs()
    }

    @Test
    fun `roundtrip stores and returns the body`() {
        runBlocking {
            val dir = tempDir()
            val cache = DiskCache(dir, ttlMs = 60_000)
            cache.put("https://a.example/x", "<html>hola</html>")
            assertEquals("<html>hola</html>", cache.get("https://a.example/x"))
            dir.deleteRecursively()
        }
    }

    @Test
    fun `expired entries are a miss`() {
        runBlocking {
            val dir = tempDir()
            val cache = DiskCache(dir, ttlMs = -1) // already expired on write
            cache.put("https://a.example/x", "body")
            assertNull(cache.get("https://a.example/x"))
            dir.deleteRecursively()
        }
    }

    @Test
    fun `unknown keys are a miss`() {
        runBlocking {
            val dir = tempDir()
            val cache = DiskCache(dir, ttlMs = 60_000)
            assertNull(cache.get("https://a.example/nope"))
            dir.deleteRecursively()
        }
    }

    @Test
    fun `eviction drops the oldest file over the cap`() {
        runBlocking {
            val dir = tempDir()
            val cache = DiskCache(dir, ttlMs = 60_000, maxFiles = 2)
            cache.put("k1", "1")
            Thread.sleep(10)
            cache.put("k2", "2")
            Thread.sleep(10)
            cache.put("k3", "3")
            // k1 was evicted; k2/k3 survive.
            assertNull(cache.get("k1"))
            assertEquals("2", cache.get("k2"))
            assertEquals("3", cache.get("k3"))
            dir.deleteRecursively()
        }
    }

    @Test
    fun `author works page url appends page param only for later pages`() {
        val client = Ao3Client()
        val page1 = client.authorPageUrl("Sable_Scribe", "works")
        assertEquals("https://archiveofourown.org/users/Sable_Scribe/works", page1)
        assertTrue(page1.contains("?page=").not())
    }
}
