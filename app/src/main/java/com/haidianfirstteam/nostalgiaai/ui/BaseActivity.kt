package com.haidianfirstteam.nostalgiaai.ui

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.haidianfirstteam.nostalgiaai.NostalgiaApp
import com.haidianfirstteam.nostalgiaai.ui.tutorial.TutorialController
import com.haidianfirstteam.nostalgiaai.ui.tutorial.TutorialRegistry
import com.haidianfirstteam.nostalgiaai.util.FontScale

/**
 * Applies per-app font scaling (API 19+).
 */
open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val wrapped = try {
            val app = newBase.applicationContext as? NostalgiaApp
            val scale = app?.settingsRepository?.getFontScaleBlocking() ?: 1.0f
            FontScale.wrap(newBase, scale)
        } catch (_: Throwable) {
            newBase
        }
        super.attachBaseContext(wrapped)
    }

    override fun onResume() {
        super.onResume()
        // If font scale changed in Settings while this Activity was alive, recreate to apply.
        try {
            val app = application as? NostalgiaApp
            val desired = app?.settingsRepository?.getFontScaleBlocking() ?: 1.0f
            val current = resources.configuration.fontScale
            if (kotlin.math.abs(current - desired) >= 0.0001f) {
                recreate()
            }
        } catch (_: Throwable) {
            // no-op
        }

        // Page-level spotlight tutorial (first time only)
        try {
            val spec = TutorialRegistry.stepsFor(this)
            if (spec != null) {
                val (key, steps) = spec
                TutorialController.maybeShow(this, key, steps)
            }
        } catch (_: Throwable) {
        }
    }
}
