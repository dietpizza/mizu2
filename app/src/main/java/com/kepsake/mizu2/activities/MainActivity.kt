package com.kepsake.mizu2.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.kepsake.mizu2.adapters.MangaCardImageAdapter
import com.kepsake.mizu2.constants.LibraryPath
import com.kepsake.mizu2.data.models.MangaFile
import com.kepsake.mizu2.databinding.ActivityMainBinding
import com.kepsake.mizu2.ui.GridSpacingItemDecoration
import com.kepsake.mizu2.utils.dpToPx
import com.kepsake.mizu2.utils.setStatusBarColor
import io.objectbox.Box
import io.objectbox.android.AndroidScheduler
import io.objectbox.reactive.DataSubscription

val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private lateinit var mangaBox: Box<MangaFile>
    private lateinit var mangaAdapter: MangaCardImageAdapter
    private var subscription: DataSubscription? = null

    private lateinit var binding: ActivityMainBinding

    fun init() {
        requestManageExternalStoragePermission()
        initTopLoader()
        initGridView()
        loadContent()
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


        mangaAdapter = MangaCardImageAdapter(emptyList(), {
            Log.e(TAG, "Manga Clicked: ${it.name}")
        })

        // Layout and data
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

    private fun observeMangaData() {
        val query = mangaBox.query().build()

        subscription = query.subscribe()
            .on(AndroidScheduler.mainThread())
            .observer { data ->
                mangaAdapter.updateData(data)
            }
    }

    fun onLibraryPathSelected() {

    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Environment.isExternalStorageManager()) {
            // TODO
        } else {
            // TODO
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

    fun loadContent() {
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val libraryPath = sharedPrefs.getString(LibraryPath, null)

        if (libraryPath == null) {
            sharedPrefs.edit().putString(LibraryPath, "").apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setStatusBarColor()
    }

    override fun onStart() {
        super.onStart()

        binding.root.post {
            init()
        }
    }

    override fun onDestroy() {
        subscription?.cancel()
        super.onDestroy()
    }

}
