package net.spin.ao3.data

import android.app.Application
import android.content.Context
import java.io.File

/** Application-scoped dependencies. */
class AppContainer(context: Context) {
    val store = Store(context)
    val client = Ao3Client(
        cacheDir = File(context.cacheDir, "ao3_http"),
    )

    init {
        // Registers the activity tracker used by WebViewFetcher (the Cloudflare
        // bypass that falls back to the system WebView = real Chromium).
        WebViewFetcher.register(context.applicationContext as Application)
    }

    /** Live connectivity state, used by the offline banner + error messages. */
    val connectivity = ConnectivityMonitor(context)

    /** Per-chapter translations, cached forever on disk (each chapter is
     *  translated at most once per language). */
    val translator = Translator(
        DiskCache(
            dir = File(context.filesDir, "ao3_translations"),
            ttlMs = Long.MAX_VALUE,
            maxFiles = 400,
        ),
    )
}
