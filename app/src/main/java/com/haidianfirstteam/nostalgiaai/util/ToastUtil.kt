package com.haidianfirstteam.nostalgiaai.util

import android.content.Context
import android.widget.Toast

object ToastUtil {
    fun show(context: Context, msg: String) {
        Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).show()
    }
}
