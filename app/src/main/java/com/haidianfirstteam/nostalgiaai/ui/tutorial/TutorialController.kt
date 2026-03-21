package com.haidianfirstteam.nostalgiaai.ui.tutorial

import android.app.Activity
import android.graphics.RectF
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object TutorialController {

    fun maybeShow(activity: Activity, screenKey: String, steps: List<TutorialStep>) {
        if (steps.isEmpty()) return
        val prefs = TutorialPrefs(activity)
        if (!prefs.shouldShow(screenKey)) return

        val root = activity.window?.decorView as? View ?: return
        // Avoid stacking multiple tutorials at the same time.
        val decor = activity.window?.decorView as? android.view.ViewGroup
        if (decor != null) {
            for (i in 0 until decor.childCount) {
                if (decor.getChildAt(i) is SpotlightOverlay) return
            }
        }
        // wait layout
        root.post {
            if (!prefs.shouldShow(screenKey)) return@post
            show(activity, screenKey, steps)
        }
    }

    fun maybeShowDialog(dialog: android.app.Dialog, screenKey: String, steps: List<TutorialStep>) {
        if (steps.isEmpty()) return
        val prefs = TutorialPrefs(dialog.context)
        if (!prefs.shouldShow(screenKey)) return
        val decor = dialog.window?.decorView as? android.view.ViewGroup ?: return
        for (i in 0 until decor.childCount) {
            if (decor.getChildAt(i) is SpotlightOverlay) return
        }
        decor.post {
            if (!prefs.shouldShow(screenKey)) return@post
            showOnHost(
                hostDecor = decor,
                confirmContext = dialog.context,
                prefs = prefs,
                screenKey = screenKey,
                steps = steps
            )
        }
    }

    private fun show(activity: Activity, screenKey: String, steps: List<TutorialStep>) {
        val prefs = TutorialPrefs(activity)
        val decor = activity.window?.decorView as? android.view.ViewGroup ?: return
        showOnHost(
            hostDecor = decor,
            confirmContext = activity,
            prefs = prefs,
            screenKey = screenKey,
            steps = steps
        )
    }

    private fun showOnHost(
        hostDecor: android.view.ViewGroup,
        confirmContext: android.content.Context,
        prefs: TutorialPrefs,
        screenKey: String,
        steps: List<TutorialStep>
    ) {
        // Mark as shown at start.
        // Rationale: some steps trigger navigation (e.g. opening Settings) or require gestures
        // (e.g. pull-to-show history). If user leaves mid-tutorial, we still treat the page
        // tutorial as completed to prevent it from reappearing every time.
        prefs.markShown(screenKey)

        val overlay = SpotlightOverlay(hostDecor.context)
        val activity = (confirmContext as? Activity) ?: (hostDecor.context as? Activity)

        var idx = 0

        fun computeHole(step: TutorialStep): RectF? {
            val target = step.findTarget(hostDecor) ?: return null
            if (target.width <= 0 || target.height <= 0) return null
            val loc = IntArray(2)
            target.getLocationInWindow(loc)
            val pad = dp(hostDecor, step.paddingDp)
            return RectF(
                (loc[0] - pad).toFloat(),
                (loc[1] - pad).toFloat(),
                (loc[0] + target.width + pad).toFloat(),
                (loc[1] + target.height + pad).toFloat(),
            )
        }

        fun render() {
            // Auto-skip targeted steps that can't locate a view.
            var guard = 0
            while (idx < steps.size && guard < steps.size) {
                val step = steps[idx]
                try {
                    val a = activity
                    if (a != null) step.prepare?.invoke(a)
                } catch (_: Throwable) {
                }
                val targeted = (step.finder != null) || (step.targetId != View.NO_ID)
                val hole = computeHole(step)
                if (targeted && hole == null) {
                    idx += 1
                    guard += 1
                    continue
                }
                overlay.setStep(step.text, hole, stepIndex1 = (idx + 1), stepTotal = steps.size, isLast = (idx == steps.size - 1))
                return
            }
            prefs.markShown(screenKey)
            remove(overlay)
        }

        overlay.onNext = {
            idx += 1
            render()
        }
        overlay.onSkip = {
            MaterialAlertDialogBuilder(confirmContext)
                .setTitle("跳过教程")
                .setMessage("确认跳过吗？可在 设置-重新体验教程 开启教程。")
                .setPositiveButton("确认") { _, _ ->
                    prefs.markShown(screenKey)
                    remove(overlay)
                }
                .setNegativeButton("取消", null)
                .show()
        }
        overlay.onSkipAll = {
            MaterialAlertDialogBuilder(confirmContext)
                .setTitle("跳过全部教程")
                .setMessage("确认跳过全部教程吗？可在 设置-重新体验教程 开启教程。")
                .setPositiveButton("确认") { _, _ ->
                    prefs.disableAll()
                    remove(overlay)
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // Attach overlay
        hostDecor.addView(
            overlay,
            android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        render()
    }

    private fun remove(overlay: SpotlightOverlay) {
        try {
            val p = overlay.parent as? android.view.ViewGroup
            p?.removeView(overlay)
        } catch (_: Throwable) {
        }
    }

    private fun dp(hostDecor: android.view.ViewGroup, v: Int): Int = (v * hostDecor.resources.displayMetrics.density).toInt()
}
