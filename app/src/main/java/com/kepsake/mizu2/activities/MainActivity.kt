package com.kepsake.mizu2.activities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.color.MaterialColors
import com.kepsake.mizu2.MizuApplication
import com.kepsake.mizu2.adapters.MangaCardImageAdapter
import com.kepsake.mizu2.constants.LibraryPath
import com.kepsake.mizu2.data.models.MangaFile
import com.kepsake.mizu2.data.viewmodels.MangaFileViewModel
import com.kepsake.mizu2.databinding.ActivityMainBinding
import com.kepsake.mizu2.ui.GridSpacingItemDecoration
import com.kepsake.mizu2.utils.dpToPx
import com.kepsake.mizu2.utils.getFilePathFromUri
import com.kepsake.mizu2.utils.getSystemBarsHeight
import com.kepsake.mizu2.utils.processMangaFiles
import com.kepsake.mizu2.utils.setStatusBarColor
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val TAG = "MainActivity"
const val REQUEST_CODE_PICK_DIRECTORY = 1001

class MainActivity : ComponentActivity() {

    private lateinit var mangaBox: Box<MangaFile>
    private lateinit var mangaAdapter: MangaCardImageAdapter
    private lateinit var mainBinding: ActivityMainBinding

    private val mangaFileViewModel: MangaFileViewModel by viewModels()
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    fun init() {
        initTopLoader()
        initGridView()
        initGetDataView()
        observeMangaData()
        syncLibrary()
    }

    fun initTopLoader() {
        val heights = getSystemBarsHeight(this)

        mainBinding.pullToRefresh.setProgressViewOffset(
            true,
            0,
            heights.statusBarHeight + dpToPx(24f)
        )
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
            mainBinding.mangaList.paddingTop + heights.statusBarHeight,
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
                mangas.forEach {
                    Log.e(TAG, "syncLibrary: $it")
                }
                mangaFileViewModel.batchAddMangaFiles(mangas)

                // This is to avoid weird animation jumps
                delay(200)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestManageExternalStoragePermission()
        mainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mainBinding.root)
        setStatusBarColor()

        // Initialize MangaBox
        val boxStore = (application as MizuApplication).boxStore
        mangaBox = boxStore.boxFor()
        mangaFileViewModel.init(mangaBox)
        mangaFileViewModel.loadMangaFiles()

        mainBinding.root.post { init() }
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val libraryPath = getFilePathFromUri(this, uri)

                sharedPrefs.edit().putString(LibraryPath, libraryPath).apply()
                syncLibrary()
            }
        }
    }

    // Replace your openFolderPicker() with this
    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        folderPickerLauncher.launch(intent)
    }

}
