package net.spin.ao3.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Tiny on-disk cache for raw AO3 HTML, keyed by the URL. Makes cold starts
 * fast: pages the user already visited (search results, work details,
 * chapters, author profiles) load from disk instead of hitting AO3 again.
 *
 * Best-effort by design: every read/write is wrapped in try/catch, so a
 * corrupt or evicted file is treated as a cache miss, never a crash.
 */
class DiskCache(
    private val dir: File,
    private val ttlMs: Long,
    private val maxFiles: Int = 250,
) {
    private val mutex = Mutex()

    suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        val f = file(key)
        if (!f.exists()) return@withContext null
        if (System.currentTimeMillis() - f.lastModified() > ttlMs) return@withContext null
        try {
            f.readText()
        } catch (_: Exception) {
            null
        }
    }

    /** Reads the entry even when it is past its TTL. Used as an offline
     *  fallback: stale content is better than a dead screen when there is no
     *  network (works/chapters visited recently stay openable). */
    suspend fun getStale(key: String): String? = withContext(Dispatchers.IO) {
        val f = file(key)
        if (!f.exists()) return@withContext null
        try {
            f.readText()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun put(key: String, value: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                dir.mkdirs()
                file(key).writeText(value)
                evictIfNeeded()
            } catch (_: Exception) {
                // Cache is best-effort; a write failure is never fatal.
            }
        }
    }

    private fun file(key: String): File = File(dir, keyHash(key))

    /** URL -> truncated SHA-256 hex: safe filename, no path characters. */
    private fun keyHash(key: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)

    private fun evictIfNeeded() {
        val files = dir.listFiles() ?: return
        if (files.size <= maxFiles) return
        files.sortedBy { it.lastModified() }
            .take(files.size - maxFiles)
            .forEach { it.delete() }
    }
}
