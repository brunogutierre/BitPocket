package io.github.brunogutierre.bitpocket

import android.app.Application
import io.github.brunogutierre.bitpocket.di.AppContainer

class BitPocketApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
