package com.haidianfirstteam.nostalgiaai.util

import android.app.Application

/**
 * Minimal crash-guard to reduce hard crashes in legacy devices.
 * Avoids bringing new deps; logs are minimal.
 */
object CrashGuard {

    fun install(app: Application) {
        // Enable vector resource compatibility on pre-Lollipop.
        // Some OEM/legacy devices are flaky if not explicitly enabled.
        try {
            androidx.appcompat.app.AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        } catch (_: Throwable) {
            // ignore
        }

        // Global uncaught exception handler
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                // Best-effort: print to logcat so legacy-device crashes are diagnosable.
                android.util.Log.e("NostalgiaAI", "Uncaught exception on thread=${t.name}", e)
            } catch (_: Throwable) {
                // ignore logging errors
            }
            try {
                // Best-effort: keep default behavior
                previous?.uncaughtException(t, e)
            } catch (_: Throwable) {
                // swallow
            }
        }
    }
}
