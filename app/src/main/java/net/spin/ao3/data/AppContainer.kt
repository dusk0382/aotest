package net.spin.ao3.data

import android.content.Context
import java.io.File

/** Application-scoped dependencies. */
class AppContainer(context: Context) {
    val store = Store(context)
    val client = Ao3Client(
        cacheDir = File(context.cacheDir, "ao3_http"),
        context = context.applicationContext,
    )

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
