package com.kepsake.mizu2.activities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioGroup
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kepsake.mizu2.MizuApplication
import com.kepsake.mizu2.R
import com.kepsake.mizu2.adapters.MangaCardImageAdapter
import com.kepsake.mizu2.constants.LibraryPath
import com.kepsake.mizu2.data.models.MangaFile
import com.kepsake.mizu2.data.viewmodels.MangaFileViewModel
import com.kepsake.mizu2.data.viewmodels.SortOption
import com.kepsake.mizu2.data.viewmodels.SortOrder
import com.kepsake.mizu2.databinding.ActivityMainBinding
import com.kepsake.mizu2.ui.GridSpacingItemDecoration
import com.kepsake.mizu2.utils.dpToPx
import com.kepsake.mizu2.utils.getFilePathFromUri
import com.kepsake.mizu2.utils.getSystemBarsHeight
import com.kepsake.mizu2.utils.processMangaFiles
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.launch

val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private lateinit var mangaBox: Box<MangaFile>
    private lateinit var mangaAdapter: MangaCardImageAdapter
    private lateinit var mainBinding: ActivityMainBinding

    private val mangaFileViewModel: MangaFileViewModel by viewModels()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val libraryPath = getFilePathFromUri(this, uri)

                    sharedPrefs.edit().putString(LibraryPath, libraryPath).apply()
                    syncLibrary()
                }
            }
        }

    fun init() {
        initTopBar()
        initTopLoader()
        initGridView()
        initGetDataView()
        observeMangaData()
        syncLibrary()
    }

    fun initTopBar() {
        val heights = getSystemBarsHeight(this)

        mainBinding.topBarLayout.setStatusBarForegroundColor(Color.TRANSPARENT)
        mainBinding.topBarLayout.background?.apply { setTint(Color.TRANSPARENT) }

        mainBinding.toolBar.updatePadding(top = mainBinding.toolBar.paddingTop + heights.statusBarHeight)
        mainBinding.toolBar.setOnMenuItemClickListener { menuItem ->
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
        mainBinding.pullToRefresh.isEnabled = false
        mainBinding.pullToRefresh.isRefreshing = false

        mainBinding.pullToRefresh.setColorSchemeColors(
            MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOnSurface,
                Color.WHITE
            ),
        )

        mainBinding.pullToRefresh.setProgressBackgroundColorSchemeColor(
            MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorSurfaceVariant,
                Color.WHITE
            )
        )
    }

    fun initGridView() {
        val layoutManager = GridLayoutManager(this, 2)
        val heights = getSystemBarsHeight(this)

        // Layout and data
        mangaAdapter = MangaCardImageAdapter(emptyList(), {})
        mainBinding.mangaList.layoutManager = layoutManager
        mainBinding.mangaList.adapter = mangaAdapter

        // styling
        mainBinding.mangaList.setPadding(
            mainBinding.mangaList.paddingLeft,
            mainBinding.mangaList.paddingTop,
            mainBinding.mangaList.paddingRight,
            mainBinding.mangaList.paddingBottom + heights.navigationBarHeight
        )
        mainBinding.mangaList.clipToPadding = false
        mainBinding.mangaList.addItemDecoration(GridSpacingItemDecoration(2, dpToPx(16f), false))
    }

    fun initGetDataView() {
        mainBinding.selectFolderButton.setOnClickListener({
            openFolderPicker()
        })
    }

    fun observeMangaData() {
        mangaFileViewModel.mangaFiles.observe(this, {
            Log.e(TAG, "observeMangaData: $it")
            mangaAdapter.updateData(it)
            syncViewVisibility()
        })
    }

    fun syncViewVisibility() {
        val mangaList = mangaFileViewModel.mangaFiles.value ?: emptyList()
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val libraryPath = sharedPrefs.getString(LibraryPath, null)
        val isLibraryPathAvailable = libraryPath != null


        if (!isLibraryPathAvailable && !mainBinding.pullToRefresh.isRefreshing) {
            mainBinding.getDataLayout.visibility = View.VISIBLE
            mainBinding.mangaList.visibility = View.GONE
        }

        if (isLibraryPathAvailable && mangaList.isEmpty()) {
            Log.e(TAG, "syncViewVisibility: 2")
            // TODO show change folder ui
        }

        if (isLibraryPathAvailable) {
            mainBinding.getDataLayout.visibility = View.GONE
            mainBinding.mangaList.visibility = View.VISIBLE
        }
    }

    fun syncLibrary() {
        lifecycleScope.launch {
            val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val libraryPath = sharedPrefs.getString(LibraryPath, null)
            Log.e(TAG, "syncLibrary: $libraryPath")

            if (libraryPath != null) {
                mainBinding.pullToRefresh.isRefreshing = true
                syncViewVisibility()
                val mangas = processMangaFiles(this@MainActivity, libraryPath)
                mangaFileViewModel.batchAddMangaFiles(mangas)

                // This is to avoid weird animation jumps
                mainBinding.pullToRefresh.isRefreshing = false
                syncViewVisibility()
            }
        }
    }

    private fun requestManageExternalStoragePermission() {
        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            requestPermissionLauncher.launch(intent)
        }
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        folderPickerLauncher.launch(intent)
    }

    private fun openSortDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.widget_sort_dialog, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        val sortCriteriaGroup = dialogView.findViewById<RadioGroup>(R.id.sort_criteria)
        val sortDirectionGroup = dialogView.findViewById<RadioGroup>(R.id.sort_direction)

        val okButton = dialogView.findViewById<MaterialButton>(R.id.ok_button)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.cancel_button)

        val sortCriteria = when (mangaFileViewModel.currentSortOption) {
            SortOption.NAME -> R.id.sort_by_name
            SortOption.SIZE -> R.id.sort_by_size
            SortOption.LAST_MODIFIED -> R.id.sort_by_last_modified
        }

        val sortDirection = when (mangaFileViewModel.currentSortOrder) {
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
                R.id.sort_by_name -> mangaFileViewModel.sortByName(isAscending)

                R.id.sort_by_size -> mangaFileViewModel.sortBySize(isAscending)

                R.id.sort_by_last_modified -> mangaFileViewModel.sortByLastModified(isAscending)
            }

            dialog.dismiss()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        mainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mainBinding.root)
        // Ask for permission the first
        requestManageExternalStoragePermission()

        val boxStore = (application as MizuApplication).boxStore
        mangaBox = boxStore.boxFor()
        mangaFileViewModel.init(mangaBox)
        mangaFileViewModel.loadMangaFiles()

        init()
    }

}
