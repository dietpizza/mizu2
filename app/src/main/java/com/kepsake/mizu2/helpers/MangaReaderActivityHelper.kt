package com.kepsake.mizu2.helpers

import android.view.View
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kepsake.mizu2.activities.MangaReaderActivity
import com.kepsake.mizu2.activities.SpaceItemDecoration
import com.kepsake.mizu2.adapters.MangaPanelAdapter
import com.kepsake.mizu2.data.models.MangaPanel
import com.kepsake.mizu2.data.viewmodels.MangaFileViewModel
import com.kepsake.mizu2.databinding.ActivityMangaReaderBinding
import com.kepsake.mizu2.utils.dpToPx
import com.kepsake.mizu2.utils.getSystemBarsHeight

class MangaReaderUIHelper(
    private val activity: MangaReaderActivity,
    private val binding: ActivityMangaReaderBinding,
    private val mangaFileViewModel: MangaFileViewModel,
) {
    private lateinit var mangaPanelAdapter: MangaPanelAdapter

    fun initReader() {
        mangaFileViewModel.mangaFile.value?.let { manga ->
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

                mangaFileViewModel.mangaFile.value?.let {
                    mangaFileViewModel.silentUpdateCurrentPage(it.id, currentPage)
                }
            }
        }
    }
}