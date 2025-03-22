package com.kepsake.mizu2.utils

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.MaterialColors

fun Activity.setStatusBarColor() {
    val isDarkTheme =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    val backgroundColor = MaterialColors.getColor(this, android.R.attr.colorBackground, Color.BLACK)
    val statusBarColor = Color.argb(
        128,
        Color.red(backgroundColor),
        Color.green(backgroundColor),
        Color.blue(backgroundColor)
    )

    window.apply {
        this.statusBarColor = statusBarColor

        WindowCompat.setDecorFitsSystemWindows(this, false)

        insetsController?.setSystemBarsAppearance(
            if (isDarkTheme) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        )
    }
}

data class SystemBarHeights(
    val statusBarHeight: Int,
    val navigationBarHeight: Int
)

fun getSystemBarsHeight(context: Activity): SystemBarHeights {
    val insets: WindowInsets =
        context.getWindowManager().getCurrentWindowMetrics().getWindowInsets()
    val statusBarHeight: Int =
        insets.getInsets(WindowInsetsCompat.Type.statusBars()).top //in pixels
    val navigationBarHeight: Int =
        insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom //in pixels

    return SystemBarHeights(statusBarHeight, navigationBarHeight)

}