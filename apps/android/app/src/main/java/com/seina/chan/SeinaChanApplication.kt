package com.seina.chan

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.seina.chan.util.FileLogger
import com.seina.chan.util.UncaughtExceptionHandler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SeinaChanApplication : Application(), ImageLoaderFactory {
    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        FileLogger.init(this)
        Thread.setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler())
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}
