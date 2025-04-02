package com.kepsake.mizu2.activities

import android.content.res.Resources
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
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
import com.otaliastudios.zoom.ZoomEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class ImageViewMeta(
    val offsetMap: OffsetMap,
    var view: ImageView,
)

data class OffsetMap(
    var offset: Int,
    var height: Int
)

class MangaReaderActivity : ComponentActivity() {
    val TAG = "MangaReaderActivity"

    private lateinit var binding: ActivityMangaReaderBinding
    private var screenWidth = Resources.getSystem().displayMetrics.widthPixels

    private val mangaId by lazy { intent.getLongExtra("id", -1) }

    private val vMangaPanel: MangaPanelViewModel by viewModels()
    private val vMangaFile: MangaFileViewModel by viewModels()

    private var currentY = 0
    private val MAX_VIEWS = 10
    private val PREFETCH_DISTANCE = MAX_VIEWS / 2
    private var viewList = mutableListOf<ImageView>()
    private var itemOffsetList = mutableListOf<OffsetMap>()

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
            engine.addListener(
                object : ZoomEngine.Listener {
                    override fun onIdle(engine: ZoomEngine) {}
                    override fun onUpdate(engine: ZoomEngine, matrix: Matrix) {
                        currentY = -engine.panY.toInt()
                        Log.e(TAG, "onUpdate: Manage Images!")
                        lifecycleScope.launch {
                            manageImages()
                        }
                    }
                }
            )
        }

        binding.zoomLayout.post {
            uiSetup.setupGestureDetector()
        }

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

        images.forEachIndexed { index, image ->
            val h = (screenWidth / image.aspect_ratio)
            itemOffsetList.add(OffsetMap(containerHeight, h.toInt()))
            containerHeight += h.toInt() + 8.dpToPx()
            Log.e(TAG, "loadImages: $containerHeight")
        }

        val params = binding.imageList.layoutParams
        params.height = containerHeight
        binding.imageList.layoutParams = params

        manageImages()

//        vMangaFile.mangaFile.value?.let { file ->
//            images.forEachIndexed { idx, p ->
//                addImage(file.path, p, itemOffsetList.get(idx), idx)
//            }
//        }
    }

    suspend fun manageImages() {
        val currentIndex = computeVisibleIndex()
        val range = (currentIndex..(currentIndex + MAX_VIEWS))
        val map = range.map { idx -> viewList.any { it.id == idx } }
        Log.e(TAG, "manageImages: $map")

        map.forEachIndexed { idx, isVisible ->
            if (!isVisible) {
                vMangaPanel.mangaPanels.value?.get(currentIndex + idx)?.let { panel ->
                    drawPanel(panel, idx)
                }
            }
        }

    }

    private fun computeVisibleIndex(): Int {
        if (itemOffsetList.isEmpty()) return -1

        // Handle single item case
        if (itemOffsetList.size == 1) return 0

        // Edge cases - if currentY is beyond list bounds
        if (currentY <= itemOffsetList[0].offset) return 0
        if (currentY >= itemOffsetList[itemOffsetList.lastIndex].offset) return itemOffsetList.lastIndex

        var left = 0
        var right = itemOffsetList.lastIndex

        // Binary search to find the two closest items to currentY
        while (left < right) {
            // When we're down to two adjacent elements, compare and return
            if (right - left == 1) {
                val distLeft = abs(itemOffsetList[left].offset - currentY)
                val distRight = abs(itemOffsetList[right].offset - currentY)
                return if (distLeft <= distRight) left else right
            }

            val mid = left + (right - left) / 2

            if (itemOffsetList[mid].offset == currentY) {
                // Exact match found
                return mid
            } else if (itemOffsetList[mid].offset < currentY) {
                left = mid
            } else {
                right = mid
            }
        }

        return left // Will return something even if loop exits unexpectedly
    }

    private fun createImageView(): ImageView {
        val params = FrameLayout.LayoutParams(
            screenWidth,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        val imageView = ImageView(this)

        imageView.apply {
            layoutParams = params
            scaleType = ImageView.ScaleType.FIT_XY
        }

        return imageView
    }

    private fun createOrRecycleView(offset: Int): ImageView {
        if (viewList.size < MAX_VIEWS) {
            val imageView = createImageView()
            viewList.add(imageView)
            binding.imageList.addView(imageView)

//            Log.e(TAG, "createOrRecycleView: 1")
            return viewList.last()
        } else {
            val v = viewList.first()
//            Log.e(TAG, "createOrRecycleView: List ${v.translationY} $offset")
            return v
        }
    }

    private suspend fun drawPanel(panel: MangaPanel, index: Int) {
        val zipFilePath = vMangaFile.mangaFile.value?.path
        zipFilePath?.let {
            val offsetMap = itemOffsetList.get(index)
            val view = createOrRecycleView(currentY)
            Log.e(TAG, "drawPanel ${index} ${view.id}")
            view.id = index

            val params = FrameLayout.LayoutParams(
                screenWidth,
                offsetMap.height
            )
            view.layoutParams = params
            view.translationY = offsetMap.offset.toFloat()

            val bitmap = withContext(Dispatchers.IO) {
                extractImageFromZip(zipFilePath, panel.page_name)
            }
            view.setImageBitmap(bitmap)
        }
    }

    suspend private fun addImage(
        zipPath: String,
        data: MangaPanel,
        offsetMap: OffsetMap,
        index: Int
    ) {
        val imageView = ImageView(this)

        val params = LinearLayout.LayoutParams(screenWidth, offsetMap.height)

        imageView.apply {
            layoutParams = params
            scaleType = ImageView.ScaleType.FIT_XY
            translationY = offsetMap.offset.toFloat()
        }
        binding.imageList.addView(imageView, index)

        val bitmap = withContext(Dispatchers.IO) {
            extractImageFromZip(zipPath, data.page_name)
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