package com.kepsake.mizu2.helpers

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import androidx.lifecycle.LifecycleCoroutineScope
import com.kepsake.mizu2.activities.MangaReaderActivity
import com.kepsake.mizu2.data.models.MangaFile
import com.kepsake.mizu2.data.models.MangaPanel
import com.kepsake.mizu2.data.viewmodels.MangaFileViewModel
import com.kepsake.mizu2.data.viewmodels.MangaPanelViewModel
import com.kepsake.mizu2.databinding.ActivityMangaReaderBinding
import com.kepsake.mizu2.logic.NaturalOrderComparator
import com.kepsake.mizu2.utils.getMangaPagesAspectRatios
import com.kepsake.mizu2.utils.getZipFileEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MangaReaderUIHelper(
    private val activity: MangaReaderActivity,
    private val binding: ActivityMangaReaderBinding,
    private val vMangaFile: MangaFileViewModel,
    private val vMangaPanel: MangaPanelViewModel,
    private val lifecycleScope: LifecycleCoroutineScope
) {

    val TAG = "MangaReaderUIHelper"

    @SuppressLint("ClickableViewAccessibility")
    fun setupGestureDetector() {
        val gestureDetector =
            GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    super.onLongPress(e)
                    Log.e(TAG, "onLongPressConfirmed: ")
                    binding.bottomAppBar.apply {
                        if (isScrolledUp) {
                            performHide(true)
                        } else {
                            performShow(true)
                        }
                    }
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    Log.e(TAG, "onDoubleTap: ")
                    binding.zoomLayout.engine.apply {
                        if (zoom > 1f) {
                            zoomTo(1f, true)
                        } else {
                            zoomTo(3f, true)
                        }
                    }
                    return true
                }
            })

        binding.zoomLayout.setOnTouchListener { v, ev ->
            gestureDetector.onTouchEvent(ev)
            false
        }

    }

    suspend fun loadMangaPanels(mangaFile: MangaFile) {
        val entries = getZipFileEntries(mangaFile.path)
            .sortedWith(compareBy(NaturalOrderComparator()) { it.name })

        val progressFlow = MutableStateFlow(0)

        val progressJob = lifecycleScope.launch(Dispatchers.Main) {
            val prevPercent = binding.progressbar.progress

            progressFlow.collect { percent ->
                if (prevPercent != percent && percent % 10 == 0) {
                    Log.e("TAG", "loadMangaPanels: Update Percent")
                    ObjectAnimator.ofInt(
                        binding.progressbar,
                        "progress",
                        binding.progressbar.progress,
                        percent
                    ).apply {
                        duration = 200
                        interpolator = DecelerateInterpolator()
                        start()
                    }
                }
            }
        }

        val pageAspectRatioMap = withContext(Dispatchers.IO) {
            getMangaPagesAspectRatios(mangaFile.path) { progress ->
                progressFlow.value = (progress * 100f).toInt()
            }
        }

        delay(250) // Let the loading animation finish
        progressJob.cancel()

        pageAspectRatioMap?.let { ratioMap ->
            val allPages = entries.mapNotNull { entry ->
                ratioMap[entry.name]?.let { aspectRatio ->
                    MangaPanel(
                        0, mangaFile.id, entry.name, aspectRatio,
                    )
                }
            }
            vMangaPanel.addMangaPanels(allPages)
        }
    }
}