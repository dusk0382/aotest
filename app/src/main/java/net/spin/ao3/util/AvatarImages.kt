package net.spin.ao3.util

import android.content.Context
import android.graphics.Bitmap
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

    // Avatars are only ever drawn at <= 84dp, so decoding anything larger is
    // wasted memory (a 4K avatar would otherwise eat ~33 MB). Downscaling to
    // 256px keeps them crisp on any density while capping memory use.
    private const val MAX_DIM = 256

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
                // Disk hit: decode the cached bytes (downscaled).
                runCatching { decodeDownscaled(f.absolutePath)?.asImageBitmap() }.getOrNull()
            } else {
                downloadBytes(url)?.let { bytes ->
                    // Best-effort write; a failure just means we re-download next time.
                    runCatching {
                        f.parentFile?.mkdirs()
                        f.writeBytes(bytes)
                    }
                    decodeDownscaled(bytes)?.asImageBitmap()
                }
            }
        } else {
            downloadBytes(url)?.let { bytes ->
                decodeDownscaled(bytes)?.asImageBitmap()
            }
        }
        if (bmp != null) cache.put(url, bmp)
        bmp
    }

    /** Decodes [bytes] capping the largest edge at [MAX_DIM] (inSampleSize). */
    private fun decodeDownscaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    /** Decodes the file at [path] capping the largest edge at [MAX_DIM]. */
    private fun decodeDownscaled(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        return BitmapFactory.decodeFile(path, opts)
    }

    /** Largest power-of-2 sample keeping the biggest edge within [MAX_DIM]..2x. */
    private fun sampleSize(w: Int, h: Int): Int {
        var sample = 1
        var max = maxOf(w, h)
        while (max / (sample * 2) >= MAX_DIM) sample *= 2
        return sample
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
