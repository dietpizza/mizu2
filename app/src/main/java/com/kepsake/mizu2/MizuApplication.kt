package com.kepsake.mizu2

import android.app.Application
import com.google.android.material.color.DynamicColors

class MizuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}