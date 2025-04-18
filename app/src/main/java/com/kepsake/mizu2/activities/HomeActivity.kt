package com.kepsake.mizu2.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.kepsake.mizu2.constants.LibraryPath
import com.kepsake.mizu2.data.models.MangaFile
import com.kepsake.mizu2.data.viewmodels.MangaFileViewModel
import com.kepsake.mizu2.databinding.ActivityHomeBinding
import com.kepsake.mizu2.helpers.MainActivityUIHelper
import com.kepsake.mizu2.utils.getFilePathFromUri


class HomeActivity : ComponentActivity() {
    val TAG = "HomeActivity"

    private lateinit var binding: ActivityHomeBinding

    private val mangaFileViewModel: MangaFileViewModel by viewModels()
    private val uiHelper by lazy {
        MainActivityUIHelper(
            this,
            binding,
            mangaFileViewModel,
            lifecycleScope
        )
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val libraryPath = getFilePathFromUri(this, uri)

                    sharedPrefs.edit().putString(LibraryPath, libraryPath).apply()
                    uiHelper.syncLibrary()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestManageExternalStoragePermission()

        mangaFileViewModel.loadMangaFiles()

        initializeUI()
    }

    private fun initializeUI() {
        uiHelper.initTopBar()
        uiHelper.initTopLoader()
        uiHelper.initGridView()
        uiHelper.initGetDataView()
        uiHelper.initEmptyFolderView()
        uiHelper.observeMangaData()
        uiHelper.syncLibrary()
    }

    private fun requestManageExternalStoragePermission() {
        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            requestPermissionLauncher.launch(intent)
        }
    }

    fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        folderPickerLauncher.launch(intent)
    }

    fun openMangaReader(manga: MangaFile) {
        val intent = Intent(this, MangaReaderActivity::class.java)
        intent.putExtra("id", manga.id)
        this.startActivity(intent)
    }
}