package com.seina.chan.util

class UncaughtExceptionHandler : Thread.UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        FileLogger.e(
            "UncaughtException",
            "FATAL: ${throwable.javaClass.name}: ${throwable.message}",
            throwable
        )
        FileLogger.flush()
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
