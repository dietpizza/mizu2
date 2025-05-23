package com.kepsake.mizu2.helpers

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioGroup
import androidx.core.view.updatePadding
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kepsake.mizu2.R
import com.kepsake.mizu2.activities.HomeActivity
import com.kepsake.mizu2.adapters.MangaCardAdapter
import com.kepsake.mizu2.constants.LibraryPath
import com.kepsake.mizu2.data.viewmodels.MangaFileViewModel
import com.kepsake.mizu2.data.viewmodels.SortOption
import com.kepsake.mizu2.data.viewmodels.SortOrder
import com.kepsake.mizu2.databinding.ActivityHomeBinding
import com.kepsake.mizu2.ui.GridSpacingItemDecoration
import com.kepsake.mizu2.utils.dpToPx
import com.kepsake.mizu2.utils.getSystemBarsHeight
import com.kepsake.mizu2.utils.processMangaFiles
import kotlinx.coroutines.launch

class MainActivityUIHelper(
    private val activity: HomeActivity,
    private val binding: ActivityHomeBinding,
    private val vMangaFile: MangaFileViewModel,
    private val lifecycleScope: LifecycleCoroutineScope
) {
    private lateinit var mangaAdapter: MangaCardAdapter

    fun initTopBar() {
        val heights = getSystemBarsHeight(activity)

        binding.topBarLayout.setStatusBarForegroundColor(Color.TRANSPARENT)
        binding.topBarLayout.background?.apply { setTint(Color.TRANSPARENT) }

        binding.toolBar.updatePadding(top = binding.toolBar.paddingTop + heights.statusBarHeight)
        binding.toolBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.topMenuSort -> {
                    openSortDialog()
                    true
                }

                else -> false
            }
        }
    }

    fun initTopLoader() {
        binding.pullToRefresh.isEnabled = false
        binding.pullToRefresh.isRefreshing = false

        binding.pullToRefresh.setColorSchemeColors(
            MaterialColors.getColor(
                activity,
                com.google.android.material.R.attr.colorPrimary,
                Color.WHITE
            ),
        )

        binding.pullToRefresh.setProgressBackgroundColorSchemeColor(
            MaterialColors.getColor(
                activity,
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
                Color.WHITE
            )
        )
    }

    fun initGridView() {
        val mangaLayoutManager = GridLayoutManager(activity, 2)
        val heights = getSystemBarsHeight(activity)

        // Layout and data
        mangaAdapter = MangaCardAdapter(emptyList()) { manga ->
            activity.openMangaReader(manga)
        }

        binding.recyclerView.apply {
            layoutManager = mangaLayoutManager
            adapter = mangaAdapter
            clipToPadding = false
            isVerticalScrollBarEnabled = true

            setHasFixedSize(true)
            addItemDecoration(
                GridSpacingItemDecoration(2, 16.dpToPx(), false)
            )
            updatePadding(
                bottom = heights.navigationBarHeight
            )
        }
    }

    fun initGetDataView() {
        binding.selectFolderButton.setOnClickListener {
            activity.openFolderPicker()
        }
    }

    fun initEmptyFolderView() {
        binding.changeFolderButton.setOnClickListener {
            activity.openFolderPicker()
        }
    }

    fun observeMangaData() {
        vMangaFile.mangaFiles.observe(activity) {
            mangaAdapter.updateData(it)
            syncViewVisibility()
        }
    }

    fun syncViewVisibility() {
        val mangaList = vMangaFile.mangaFiles.value ?: emptyList()
        val sharedPrefs = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val libraryPath = sharedPrefs.getString(LibraryPath, null)
        val isLibraryPathAvailable = libraryPath != null

        if (binding.pullToRefresh.isRefreshing) {
            binding.selectFolderButton.isEnabled = false
            binding.changeFolderButton.isEnabled = false
        } else {
            binding.selectFolderButton.isEnabled = true
            binding.changeFolderButton.isEnabled = true
        }

        if (binding.pullToRefresh.isRefreshing) {
            binding.selectFolderLayout.visibility = View.GONE
            binding.changeFolderLayout.visibility = View.GONE
            binding.recyclerView.visibility = View.GONE
            return
        }

        if (mangaList.isEmpty()) {
            if (isLibraryPathAvailable) {
                binding.changeFolderLayout.visibility = View.VISIBLE
                binding.selectFolderLayout.visibility = View.GONE
                binding.recyclerView.visibility = View.GONE
            }
            if (!isLibraryPathAvailable) {
                binding.changeFolderLayout.visibility = View.GONE
                binding.selectFolderLayout.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
            }
        } else {
            binding.changeFolderLayout.visibility = View.GONE
            binding.selectFolderLayout.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    private fun openSortDialog() {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.widget_sort_dialog, null)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .create()

        val sortCriteriaGroup = dialogView.findViewById<RadioGroup>(R.id.sort_criteria)
        val sortDirectionGroup = dialogView.findViewById<RadioGroup>(R.id.sort_direction)

        val okButton = dialogView.findViewById<MaterialButton>(R.id.ok_button)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.cancel_button)

        val sortCriteria = when (vMangaFile.currentSortOption) {
            SortOption.NAME -> R.id.sort_by_name
            SortOption.SIZE -> R.id.sort_by_size
            SortOption.LAST_MODIFIED -> R.id.sort_by_last_modified
        }

        val sortDirection = when (vMangaFile.currentSortOrder) {
            SortOrder.ASC -> R.id.sort_ascending
            SortOrder.DESC -> R.id.sort_descending
        }

        sortCriteriaGroup.check(sortCriteria)
        sortDirectionGroup.check(sortDirection)

        okButton.setOnClickListener {
            val selectedCriteria = sortCriteriaGroup.checkedRadioButtonId
            val selectedDirection = sortDirectionGroup.checkedRadioButtonId
            val isAscending = selectedDirection == R.id.sort_ascending

            when (selectedCriteria) {
                R.id.sort_by_name -> vMangaFile.setSortInfo(SortOption.NAME, isAscending)
                R.id.sort_by_size -> vMangaFile.setSortInfo(SortOption.SIZE, isAscending)
                R.id.sort_by_last_modified -> vMangaFile.setSortInfo(
                    SortOption.LAST_MODIFIED,
                    isAscending
                )
            }

            dialog.dismiss()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    fun syncLibrary() {
        lifecycleScope.launch {
            val sharedPrefs = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val libraryPath = sharedPrefs.getString(LibraryPath, null)

            if (libraryPath != null) {
                binding.pullToRefresh.isRefreshing = true
                syncViewVisibility()

                val mangas = processMangaFiles(activity, libraryPath)
                vMangaFile.syncWithDisk(mangas)

                binding.pullToRefresh.isRefreshing = false
            }
        }
    }

}