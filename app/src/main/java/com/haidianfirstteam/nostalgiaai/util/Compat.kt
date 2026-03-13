package com.haidianfirstteam.nostalgiaai.util

import android.os.Build

object Compat {
    val isKitkatOrBelow: Boolean
        get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT
}
