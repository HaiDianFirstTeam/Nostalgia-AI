package com.haidianfirstteam.nostalgiaai.util

import android.app.Application
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Minimal crash-guard to reduce hard crashes in legacy devices.
 * Avoids bringing new deps; logs are minimal.
 */
object CrashGuard {

    private const val LAST_CRASH_FILE = "last_crash.txt"

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
                // Persist to internal storage so users can copy after restart.
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                pw.println("time=" + System.currentTimeMillis())
                pw.println("thread=" + t.name)
                pw.println("sdk=" + android.os.Build.VERSION.SDK_INT)
                pw.println("device=" + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL)
                pw.println()
                e.printStackTrace(pw)
                pw.flush()
                val text = sw.toString()
                app.openFileOutput(LAST_CRASH_FILE, android.content.Context.MODE_PRIVATE).use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                }
            } catch (_: Throwable) {
                // ignore
            }
            try {
                // Best-effort: keep default behavior
                previous?.uncaughtException(t, e)
            } catch (_: Throwable) {
                // swallow
            }
        }
    }

    fun consumeLastCrash(app: Application): String? {
        return try {
            val f = app.getFileStreamPath(LAST_CRASH_FILE)
            if (f == null || !f.exists()) return null
            val text = f.readText(Charsets.UTF_8)
            // Delete after read to avoid repeated popups.
            try {
                f.delete()
            } catch (_: Throwable) {
                // ignore
            }
            text
        } catch (_: Throwable) {
            null
        }
    }
}
