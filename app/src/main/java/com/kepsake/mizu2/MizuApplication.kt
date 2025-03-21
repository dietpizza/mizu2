package com.kepsake.mizu2

import android.app.Application
import com.kepsake.mizu2.data.models.MyObjectBox
import io.objectbox.BoxStore

class MizuApplication : Application() {
    lateinit var boxStore: BoxStore
        private set

    override fun onCreate() {
        super.onCreate()
        boxStore = MyObjectBox.builder()
            .androidContext(this)
            .build()
    }
}