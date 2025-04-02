package com.kepsake.mizu2.activities

import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.kepsake.mizu2.data.models.MangaPanel
import com.kepsake.mizu2.data.viewmodels.MangaFileViewModel
import com.kepsake.mizu2.data.viewmodels.MangaPanelViewModel
import com.kepsake.mizu2.databinding.ActivityMangaReaderBinding
import com.kepsake.mizu2.helpers.MangaReaderUIHelper
import com.kepsake.mizu2.utils.dpToPx
import com.kepsake.mizu2.utils.extractImageFromZip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MangaReaderActivity : ComponentActivity() {
    val TAG = "MangaReaderActivity"

    private lateinit var binding: ActivityMangaReaderBinding
    private var screenWidth = Resources.getSystem().displayMetrics.widthPixels

    private val mangaId by lazy { intent.getLongExtra("id", -1) }

    private val vMangaPanel: MangaPanelViewModel by viewModels()
    private val vMangaFile: MangaFileViewModel by viewModels()

//    private val imageContainer = binding.imageList

    private val uiSetup by lazy {
        MangaReaderUIHelper(this, binding, vMangaFile, vMangaPanel, lifecycleScope)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMangaReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        vMangaFile.loadMangaFileById(mangaId)

        binding.zoomLayout.apply {
            setMaxZoom(3f)
            setMinZoom(1f)
            setOverPinchable(false)
            setOverScrollVertical(false)
            setOverScrollHorizontal(false)
        }

        initializeApp()
    }

    private fun initializeApp() {
        setupObservers()
    }

    private fun setupObservers() {
        vMangaFile.mangaFile.observe(this) {
            vMangaPanel.loadPagesForManga(mangaId)
            setupPanelObserver()
        }
    }

    private suspend fun loadImages(images: List<MangaPanel>) {

        var containerHeight = 0
        val itemOffsetList = mutableListOf<Map<String, Int>>()

        images.forEachIndexed { index, image ->
            val h = (screenWidth / image.aspect_ratio)
            itemOffsetList.add(
                mapOf(
                    "offset" to containerHeight,
                    "height" to h.toInt()
                )
            )
            containerHeight += h.toInt() + 8.dpToPx()
        }

        Log.e(TAG, "loadImages: Height: $containerHeight")

        Log.e(TAG, "loadImages: List: $itemOffsetList")

        val params = binding.imageList.layoutParams
        params.height = containerHeight
        binding.imageList.layoutParams = params

        vMangaFile.mangaFile.value?.let { file ->
//            binding.zoomLayout.panTo(0f, 0f, false)
            images.forEachIndexed { idx, p ->
                addImage(file.path, p, itemOffsetList.get(idx), idx)
            }
        }
    }

    suspend private fun addImage(
        zipPath: String,
        image: MangaPanel,
        offsetMap: Map<String, Int>,
        index: Int
    ) {
        val imageView = ImageView(this)

        val params = LinearLayout.LayoutParams(screenWidth, offsetMap.get("height")!!)

        imageView.apply {
            layoutParams = params
            scaleType = ImageView.ScaleType.FIT_XY
            translationY = offsetMap.get("offset")!!.toFloat()
        }
        binding.imageList.addView(imageView, index)

        val bitmap = withContext(Dispatchers.IO) {
            extractImageFromZip(zipPath, image.page_name)
        }
        imageView.setImageBitmap(bitmap)

    }

    private fun setupPanelObserver() {
        vMangaPanel.mangaPanels.observe(this) { panels ->
            lifecycleScope.launch {
                vMangaFile.mangaFile.value?.let { manga ->
                    if (panels.isEmpty()) {
                        uiSetup.loadMangaPanels(manga)
                    } else {
                        binding.progressbar.visibility = View.GONE
                        loadImages(vMangaPanel.mangaPanels.value!!)
                    }
                }
            }
        }
    }

}