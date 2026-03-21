package com.haidianfirstteam.nostalgiaai.ui.tutorial

import android.view.View

data class TutorialStep(
    val targetId: Int = View.NO_ID,
    val text: String,
    val paddingDp: Int = 10,
    val finder: ((root: View) -> View?)? = null,
    val prepare: ((activity: android.app.Activity) -> Unit)? = null,
) {
    fun findTarget(root: View): View? {
        val f = finder
        if (f != null) return f.invoke(root)
        if (targetId == View.NO_ID) return null
        return root.findViewById(targetId)
    }
}
