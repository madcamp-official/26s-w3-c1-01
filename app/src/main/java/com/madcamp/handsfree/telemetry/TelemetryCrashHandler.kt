package com.madcamp.handsfree.telemetry

import android.content.Context

object TelemetryCrashHandler {
    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return

        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Telemetry.logger(appContext).logAppError(
                type = "APP_CRASH",
                message = throwable.stackTraceToString(),
            )
            previousHandler?.uncaughtException(thread, throwable)
        }
        installed = true
    }
}
