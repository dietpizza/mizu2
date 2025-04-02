package com.kepsake.mizu2.activities

import android.content.res.Resources
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
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
import kotlin.math.max
import kotlin.math.min

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

        // Clear existing offset list when loading new images
        itemOffsetList.clear()

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
    }

    suspend fun manageImages() {
        if (itemOffsetList.isEmpty() || vMangaPanel.mangaPanels.value.isNullOrEmpty()) {
            return
        }

        val visibleStart = computeVisibleIndex()
        if (visibleStart < 0) return

        // Calculate visible range with prefetch
        val startIdx = max(0, visibleStart - PREFETCH_DISTANCE)
        val endIdx = min(itemOffsetList.size - 1, visibleStart + MAX_VIEWS - PREFETCH_DISTANCE)
        val visibleRange = startIdx..endIdx

        // Identify views to recycle (those outside visible range)
        val viewsToRecycle = viewList.filter { it.id !in visibleRange }

        // Identify indices that need views but don't have them
        val neededIndices = visibleRange.filter { idx ->
            viewList.none { it.id == idx }
        }

        // Recycle views
        viewsToRecycle.forEach { view ->
            viewList.remove(view)
            binding.imageList.removeView(view)
        }

        // Create new views for needed indices
        neededIndices.forEach { idx ->
            vMangaPanel.mangaPanels.value?.getOrNull(idx)?.let { panel ->
                drawPanel(panel, idx)
            }
        }
    }

    private fun computeVisibleIndex(): Int {
        if (itemOffsetList.isEmpty()) return -1

        // Handle single item case
        if (itemOffsetList.size == 1) return 0

        // Edge cases - if currentY is beyond list bounds
        if (currentY <= itemOffsetList[0].offset) return 0
        if (currentY >= itemOffsetList.last().offset) return itemOffsetList.lastIndex

        // Binary search to find the closest item to currentY
        var left = 0
        var right = itemOffsetList.lastIndex

        while (left <= right) {
            val mid = left + (right - left) / 2
            val midOffset = itemOffsetList[mid].offset

            when {
                midOffset == currentY -> return mid
                midOffset < currentY -> {
                    // Check if this is the closest one or we need to go right
                    if (mid < itemOffsetList.lastIndex && itemOffsetList[mid + 1].offset > currentY) {
                        // Found the closest item
                        return if (currentY - midOffset < itemOffsetList[mid + 1].offset - currentY) mid else mid + 1
                    }
                    left = mid + 1
                }

                else -> {
                    // Check if this is the closest one or we need to go left
                    if (mid > 0 && itemOffsetList[mid - 1].offset < currentY) {
                        // Found the closest item
                        return if (midOffset - currentY < currentY - itemOffsetList[mid - 1].offset) mid else mid - 1
                    }
                    right = mid - 1
                }
            }
        }

        // If we somehow exit the loop without returning, return the left bound
        return left.coerceIn(0, itemOffsetList.lastIndex)
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

    private suspend fun drawPanel(panel: MangaPanel, index: Int) {
        val zipFilePath = vMangaFile.mangaFile.value?.path ?: return

        val offsetMap = itemOffsetList.getOrNull(index) ?: return
        val view = createImageView()

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

        binding.imageList.addView(view)
        viewList.add(view)
    }

    private fun setupPanelObserver() {
        vMangaPanel.mangaPanels.observe(this) { panels ->
            lifecycleScope.launch {
                vMangaFile.mangaFile.value?.let { manga ->
                    if (panels.isEmpty()) {
                        uiSetup.loadMangaPanels(manga)
                    } else {
                        binding.progressbar.visibility = View.GONE
                        loadImages(panels)
                    }
                }
            }
        }
    }

}