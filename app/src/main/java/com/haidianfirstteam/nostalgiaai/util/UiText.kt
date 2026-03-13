package com.haidianfirstteam.nostalgiaai.util

import android.content.Context
import com.haidianfirstteam.nostalgiaai.R

object UiText {
    fun prettyError(context: Context, msg: String?): String {
        val t = msg?.trim().orEmpty()
        if (t.isEmpty()) return context.getString(R.string.err_unknown)
        return t
    }
}
