package net.spin.ao3.util

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Tiny shared loader for author avatars (profile header + comment icons).
 * One in-memory cache across every screen, so an avatar fetched in the
 * profile is instant when the same author appears in a comment thread.
 *
 * Also caches raw bytes on disk (keyed by URL) so avatars survive cold starts
 * instead of re-downloading every launch. Call [init] once from Ao3App.
 */
object AvatarImages {
    private var cacheDir: File? = null

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val cache = LruCache<String, ImageBitmap>(64)

    /** Sets up the disk cache directory (idempotent; call from Ao3App.onCreate). */
    fun init(context: Context) {
        if (cacheDir == null) {
            cacheDir = File(context.cacheDir, "avatars")
        }
    }

    suspend fun load(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext it }
        val dir = cacheDir
        val bmp = if (dir != null) {
            val f = diskFile(dir, url)
            if (f.exists()) {
                // Disk hit: decode the cached bytes.
                runCatching { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }.getOrNull()
            } else {
                downloadBytes(url)?.let { bytes ->
                    // Best-effort write; a failure just means we re-download next time.
                    runCatching {
                        f.parentFile?.mkdirs()
                        f.writeBytes(bytes)
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }
        } else {
            downloadBytes(url)?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }
        if (bmp != null) cache.put(url, bmp)
        bmp
    }

    private fun downloadBytes(url: String): ByteArray? = try {
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        resp.use {
            if (!it.isSuccessful) null else it.body?.bytes()
        }
    } catch (_: Exception) {
        null
    }

    private fun diskFile(dir: File, url: String): File = File(dir, keyHash(url))

    /** URL -> truncated SHA-256 hex + .img (safe filename, no path characters). */
    private fun keyHash(url: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32) + ".img"
}
