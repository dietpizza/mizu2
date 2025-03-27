package com.kepsake.mizu2.utils

import android.view.Choreographer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class RecyclerViewPageTracker(
    private val recyclerView: RecyclerView,
    private val frameInterval: Int = 10,
    private val onNewVisiblePosition: (position: Int) -> Unit
) {
    private val choreographer = Choreographer.getInstance()
    private var frameCallback: Choreographer.FrameCallback? = null
    private var frameCount = 0

    fun startTracking() {
        stopTracking()

        frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frameCount++
                if (frameCount % frameInterval == 0) {
                    val firstVisiblePosition = getFirstVisibleItemPosition()
                    onNewVisiblePosition(firstVisiblePosition)
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(frameCallback)
    }

    fun stopTracking() {
        frameCallback?.let {
            choreographer.removeFrameCallback(it)
            frameCallback = null
        }
    }

    private fun getFirstVisibleItemPosition(): Int {
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        return layoutManager.findFirstVisibleItemPosition()
    }
}