package net.spin.ao3

import android.app.Application
import net.spin.ao3.data.AppContainer
import net.spin.ao3.data.DownloadQueueService
import net.spin.ao3.util.AvatarImages

class Ao3App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Disk cache for author avatars (survives cold starts).
        AvatarImages.init(this)
        // Resume an interrupted download queue (persisted jobs) on app launch.
        DownloadQueueService.startIfPending(this)
    }
}
