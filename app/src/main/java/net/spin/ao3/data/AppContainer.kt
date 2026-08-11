package net.spin.ao3.data

import android.content.Context
import java.io.File

/** Application-scoped dependencies. */
class AppContainer(context: Context) {
    val store = Store(context)
    val client = Ao3Client(cacheDir = File(context.cacheDir, "ao3_http"))
}
