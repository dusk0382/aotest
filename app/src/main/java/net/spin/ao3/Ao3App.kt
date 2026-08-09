package net.spin.ao3

import android.app.Application
import net.spin.ao3.data.AppContainer

class Ao3App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
