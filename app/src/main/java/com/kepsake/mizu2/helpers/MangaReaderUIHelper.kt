package com.kepsake.mizu2.helpers

import android.animation.ObjectAnimator
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
import com.kepsake.mizu2.utils.dpToPx
import com.kepsake.mizu2.utils.getMangaPagesAspectRatios
import com.kepsake.mizu2.utils.getSystemBarsHeight
import com.kepsake.mizu2.utils.getZipFileEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MangaReaderUIHelper(
    private val activity: MangaReaderActivity,
    private val binding: ActivityMangaReaderBinding,
    private val vMangaFile: MangaFileViewModel,
    private val vMangaPanel: MangaPanelViewModel,
    private val lifecycleScope: LifecycleCoroutineScope
) {
    private lateinit var mangaPanelAdapter: MangaPanelAdapter

    fun initSlider(max: Int) {
        val heights = getSystemBarsHeight(activity)

//        binding.bottomToolBar.apply {
//            val params = layoutParams as ViewGroup.MarginLayoutParams
//            params.updateMargins(bottom = heights.navigationBarHeight)
//            layoutParams = params
//        }
//
        binding.pageSlider.apply {
            value = 0f
            valueFrom = 0f
            valueTo = max.toFloat()
            stepSize = 1f

        }
        binding.pageSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            // No-op here, have to override both
            override fun onStartTrackingTouch(slider: Slider) {}

            override fun onStopTrackingTouch(slider: Slider) {
                binding.mangaReader.scrollToPosition(slider.value.toInt())
            }
        })
    }

    fun initReader() {
        vMangaFile.mangaFile.value?.let { manga ->
            val heights = getSystemBarsHeight(activity)
            mangaPanelAdapter = MangaPanelAdapter(manga, emptyList())

            binding.mangaReader.updatePadding()
            binding.mangaReader.apply {
                layoutManager = LinearLayoutManager(activity)
                adapter = mangaPanelAdapter
                clipToPadding = false

                updatePadding(
                    top = heights.statusBarHeight,
                    bottom = heights.navigationBarHeight
                )
                setHasFixedSize(true)
                addOnScrollListener(createScrollListener())
                addItemDecoration(SpaceItemDecoration(8.dpToPx()))
            }
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
            val firstVisibleItemPosition = layoutManager.findFirstCompletelyVisibleItemPosition()

            if (firstVisibleItemPosition >= 0 && firstVisibleItemPosition != currentPage) {
                currentPage = firstVisibleItemPosition

                vMangaFile.mangaFile.value?.let {
                    vMangaFile.silentUpdateCurrentPage(it.id, currentPage)
                }
            }
        }
    }

    @OptIn(FlowPreview::class)
    suspend fun loadMangaPanels(mangaFile: MangaFile) {
        val entries = getZipFileEntries(mangaFile.path)
            .sortedWith(compareBy(NaturalOrderComparator()) { it.name })

        val progressFlow = MutableStateFlow(0f)

        val progressJob = lifecycleScope.launch(Dispatchers.Main) {
            progressFlow.sample(300).collect { progress ->
                val progressValue = (progress * 100f)
                ObjectAnimator.ofInt(
                    binding.progressbar,
                    "progress",
                    binding.progressbar.progress,
                    progressValue.toInt()
                ).apply {
                    duration = 200 // Animation duration in milliseconds
                    interpolator = DecelerateInterpolator() // For a smooth deceleration effect
                    start()
                }
            }
        }

        val pageAspectRatioMap = withContext(Dispatchers.IO) {
            getMangaPagesAspectRatios(activity, mangaFile.path) { progress ->
                progressFlow.value = progress
            }
        }

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