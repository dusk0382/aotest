package net.spin.ao3.data

import android.content.Context

/** Application-scoped dependencies. */
class AppContainer(context: Context) {
    val store = Store(context)
    val client = Ao3Client()
}
