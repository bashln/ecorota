package app.ecorota

import android.app.Application
import app.ecorota.di.AppContainer

class EcoRotaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
