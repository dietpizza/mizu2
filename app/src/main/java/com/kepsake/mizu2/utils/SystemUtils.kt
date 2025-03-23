package com.kepsake.mizu2.utils

import android.content.res.Resources

fun Int.dpToPx(): Int {
    return (this * Resources.getSystem().displayMetrics.density).toInt()
}