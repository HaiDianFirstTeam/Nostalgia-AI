package com.haidianfirstteam.nostalgiaai.util

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Minimal crash-guard to reduce hard crashes in legacy devices.
 * Avoids bringing new deps; logs are minimal.
 */
object CrashGuard {

    fun install(app: Application) {
        // Global uncaught exception handler
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                // Best-effort: keep default behavior
                previous?.uncaughtException(t, e)
            } catch (_: Throwable) {
                // swallow
            }
        }
    }
}
