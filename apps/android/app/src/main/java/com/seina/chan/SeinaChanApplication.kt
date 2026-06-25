package com.seina.chan

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.seina.chan.util.FileLogger
import com.seina.chan.util.NetworkMonitor
import com.seina.chan.util.UncaughtExceptionHandler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SeinaChanApplication : Application(), ImageLoaderFactory {
    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    override fun onCreate() {
        super.onCreate()
        FileLogger.init(this)
        Thread.setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler())
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                networkMonitor.destroy()
                FileLogger.i("SeinaChanApplication", "NetworkMonitor destroyed")
            }
        })
    }


    override fun newImageLoader(): ImageLoader = imageLoader
}
