package com.kepsake.mizu2.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.anggrayudi.storage.SimpleStorageHelper
import com.kepsake.mizu2.MizuApplication
import com.kepsake.mizu2.adapters.MangaCardImageAdapter
import com.kepsake.mizu2.constants.LibraryPath
import com.kepsake.mizu2.data.models.MangaFile
import com.kepsake.mizu2.data.viewmodels.MangaFileViewModel
import com.kepsake.mizu2.databinding.ActivityMainBinding
import com.kepsake.mizu2.ui.GridSpacingItemDecoration
import com.kepsake.mizu2.utils.dpToPx
import com.kepsake.mizu2.utils.getFilePathFromUri
import com.kepsake.mizu2.utils.processMangaFiles
import com.kepsake.mizu2.utils.setStatusBarColor
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.launch

val TAG = "MainActivity"
const val REQUEST_CODE_PICK_DIRECTORY = 1001

class MainActivity : ComponentActivity() {
    private val storageHelper = SimpleStorageHelper(this)

    private lateinit var mangaBox: Box<MangaFile>
    private lateinit var mangaAdapter: MangaCardImageAdapter
    private val viewModel: MangaFileViewModel by viewModels()

    private lateinit var binding: ActivityMainBinding

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    fun init() {
        requestManageExternalStoragePermission()
        initTopLoader()
        initGridView()
        initGetDataView()
        observeMangaData()
        syncLibrary()
    }

    fun initTopLoader() {
        binding.pullToRefresh.setProgressViewOffset(true, 0, 200)
        binding.pullToRefresh.isEnabled = false
        binding.pullToRefresh.isRefreshing = false
    }

    fun initGridView() {
        val layoutManager = GridLayoutManager(this, 2)
        val insets: WindowInsets =
            this.getWindowManager().getCurrentWindowMetrics().getWindowInsets()
        val statusBarHeight: Int =
            insets.getInsets(WindowInsetsCompat.Type.statusBars()).top //in pixels
        val navigationBarHeight: Int =
            insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom //in pixels


        // Layout and data
        mangaAdapter = MangaCardImageAdapter(emptyList(), {})
        binding.mangaList.layoutManager = layoutManager
        binding.mangaList.adapter = mangaAdapter

        // styling
        binding.mangaList.setPadding(
            binding.mangaList.paddingLeft,
            statusBarHeight,
            binding.mangaList.paddingRight,
            navigationBarHeight
        )
        binding.mangaList.clipToPadding = false
        binding.mangaList.addItemDecoration(GridSpacingItemDecoration(2, dpToPx(8f), true))
    }

    fun initGetDataView() {
        binding.selectFolderButton.setOnClickListener({
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_DIRECTORY)
        })
    }

    private fun observeMangaData() {
        viewModel.mangaFiles.observe(this, {
            mangaAdapter.updateData(it)
        })
    }

    fun syncViewVisibility(mangaList: List<MangaFile>) {
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val libraryPath = sharedPrefs.getString(LibraryPath, null)
        val isLibraryPathAvailable = libraryPath != null

        Log.e(TAG, "syncViewVisibility: 0 $isLibraryPathAvailable ")

        if (!isLibraryPathAvailable) {
            Log.e(TAG, "syncViewVisibility: 1")
            binding.getDataLayout.visibility = View.VISIBLE
            binding.pullToRefresh.visibility = View.GONE
        }

        if (isLibraryPathAvailable && mangaList.isEmpty()) {
            Log.e(TAG, "syncViewVisibility: 2")
            // TODO show change folder ui
        }

        if (isLibraryPathAvailable && mangaList.isNotEmpty()) {
            Log.e(TAG, "syncViewVisibility: 3")
            binding.getDataLayout.visibility = View.GONE
            binding.pullToRefresh.visibility = View.VISIBLE
        }
    }

    fun syncLibrary() {
        lifecycleScope.launch {
            val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val libraryPath = sharedPrefs.getString(LibraryPath, null)
            Log.e(TAG, "syncLibrary: $libraryPath")

            if (libraryPath != null) {
                val mangas = processMangaFiles(this@MainActivity, libraryPath)
                mangas.forEach {
                    Log.e(TAG, "syncLibrary: $it")
                }
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

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setStatusBarColor()

        // Initialize MangaBox
        val boxStore = (application as MizuApplication).boxStore
        mangaBox = boxStore.boxFor()
        viewModel.init(mangaBox)
        viewModel.loadMangaFiles()
        syncViewVisibility(viewModel.mangaFiles.value ?: emptyList())

        storageHelper.onFolderSelected = { _, folder ->
            val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val libraryPath = getFilePathFromUri(this, folder.uri)

            sharedPrefs.edit().putString(LibraryPath, libraryPath).apply()
            syncLibrary()
        }
    }

    override fun onStart() {
        super.onStart()

        binding.root.post { init() }
    }

}
