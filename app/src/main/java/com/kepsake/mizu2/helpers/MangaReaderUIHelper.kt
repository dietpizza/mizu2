package com.kepsake.mizu2.helpers

import android.animation.ObjectAnimator
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.view.updatePadding
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.slider.Slider
import com.kepsake.mizu2.activities.MangaReaderActivity
import com.kepsake.mizu2.adapters.MangaPanelAdapter
import com.kepsake.mizu2.data.models.MangaFile
import com.kepsake.mizu2.data.models.MangaPanel
import com.kepsake.mizu2.data.viewmodels.MangaFileViewModel
import com.kepsake.mizu2.data.viewmodels.MangaPanelViewModel
import com.kepsake.mizu2.databinding.ActivityMangaReaderBinding
import com.kepsake.mizu2.logic.NaturalOrderComparator
import com.kepsake.mizu2.ui.SpaceItemDecoration
import com.kepsake.mizu2.utils.RecyclerViewPageTracker
import com.kepsake.mizu2.utils.dpToPx
import com.kepsake.mizu2.utils.getMangaPagesAspectRatios
import com.kepsake.mizu2.utils.getSystemBarsHeight
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
    private val TAG = "MangaReaderUIHelper"
    private lateinit var mangaPanelAdapter: MangaPanelAdapter

    fun initSliderToolbar(max: Int) {
        val currentPage = vMangaFile.mangaFile.value?.current_page ?: 0
        binding.mangaReader.apply {
            binding.buttonFirstPage.setOnClickListener {
                scrollToPosition(0)
            }
            binding.buttonLastPage.setOnClickListener {
                scrollToPosition(max)
            }
        }

        binding.pageSlider.apply {
            value = currentPage.toFloat()
            valueFrom = 0f
            valueTo = max.toFloat()
            stepSize = 1f

            addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                // No-op here, have to override both
                override fun onStartTrackingTouch(slider: Slider) {}

                override fun onStopTrackingTouch(slider: Slider) {
                    binding.mangaReader.scrollToPosition(slider.value.toInt())
                }
            })
        }
    }

    fun initReader() {
        vMangaFile.mangaFile.value?.let { manga ->
            val heights = getSystemBarsHeight(activity)
            mangaPanelAdapter = MangaPanelAdapter(manga, emptyList(), {})

            binding.mangaReader.updatePadding()
            binding.mangaReader.apply {
                layoutManager = LinearLayoutManager(activity)
                adapter = mangaPanelAdapter
                clipToPadding = false

                updatePadding(
                    top = heights.statusBarHeight,
                    bottom = heights.navigationBarHeight
                )
                onPressListener = {
                    binding.bottomAppBar.apply {
                        if (isScrolledUp) {
                            performHide(true)
                        } else {
                            performShow(true)
                        }
                    }
                }
                setHasFixedSize(true)
                addOnScrollListener(createScrollListener())
                addItemDecoration(SpaceItemDecoration(8.dpToPx()))
            }
            RecyclerViewPageTracker(binding.mangaReader, onNewVisiblePosition = {
                Log.e(TAG, "initReader Page: $it")
            })
        }
    }

    fun updatePanels(panels: List<MangaPanel>) {
        mangaPanelAdapter.updateData(panels)
    }

    fun syncViewVisibility() {
        if (mangaPanelAdapter.itemCount > 0) {
            binding.progressbar.visibility = View.GONE
        }
    }

    private fun createScrollListener() = object : RecyclerView.OnScrollListener() {
        private var currentPage = 0

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            var visiblePageIndex = layoutManager.findFirstCompletelyVisibleItemPosition()

            if (visiblePageIndex < 0)
                visiblePageIndex = layoutManager.findLastVisibleItemPosition()

            if (visiblePageIndex >= 0 && visiblePageIndex != currentPage) {
                binding.buttonFirstPage.isEnabled = visiblePageIndex != 0
                binding.buttonLastPage.isEnabled =
                    visiblePageIndex != mangaPanelAdapter.itemCount - 1

                vMangaFile.mangaFile.value?.let {
                    vMangaFile.silentUpdateCurrentPage(it.id, visiblePageIndex)
                    binding.pageSlider.value = visiblePageIndex.toFloat()
                }
                currentPage = visiblePageIndex
            }
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