package net.spin.ao3.util

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Tiny shared loader for author avatars (profile header + comment icons).
 * One in-memory cache across every screen, so an avatar fetched in the
 * profile is instant when the same author appears in a comment thread.
 */
object AvatarImages {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val cache = LruCache<String, ImageBitmap>(64)

    suspend fun load(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext it }
        val img = try {
            val resp = client.newCall(Request.Builder().url(url).build()).execute()
            resp.use {
                if (!it.isSuccessful) {
                    null
                } else {
                    BitmapFactory.decodeStream(it.body?.byteStream())?.asImageBitmap()
                }
            }
        } catch (_: Exception) {
            null
        }
        if (img != null) cache.put(url, img)
        img
    }
}
