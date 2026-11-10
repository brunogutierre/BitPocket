package io.github.brunogutierre.bitpocket

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.brunogutierre.bitpocket.di.AppContainer

class BitPocketApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Auto-lock: the whole app (not one Activity) going to the background and coming back.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) = container.autoLocker.onBackground(SystemClock.elapsedRealtime())

                override fun onStart(owner: LifecycleOwner) = container.autoLocker.onForeground(SystemClock.elapsedRealtime())
            },
        )
    }
}
