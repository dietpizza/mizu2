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
import coil.load
import com.kepsake.mizu2.data.models.MangaPanel
import com.kepsake.mizu2.data.viewmodels.MangaFileViewModel
import com.kepsake.mizu2.data.viewmodels.MangaPanelViewModel
import com.kepsake.mizu2.databinding.ActivityMangaReaderBinding
import com.kepsake.mizu2.helpers.MangaReaderUIHelper
import com.kepsake.mizu2.logic.computeVisibleIndex
import com.kepsake.mizu2.utils.dpToPx
import com.kepsake.mizu2.utils.extractImageFromZip
import com.otaliastudios.zoom.ZoomEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

enum class ScrollDirection {
    UP,
    DOWN
}

data class ViewOffsetMap(
    var offset: Int,
    var height: Int,
    var panel: MangaPanel,
    var view: ImageView?
)

class MangaReaderActivity : ComponentActivity() {
    val TAG = "MangaReaderActivity"

    private lateinit var binding: ActivityMangaReaderBinding
    private var screenWidth = Resources.getSystem().displayMetrics.widthPixels

    private val mangaId by lazy { intent.getLongExtra("id", -1) }

    private val vMangaPanel: MangaPanelViewModel by viewModels()
    private val vMangaFile: MangaFileViewModel by viewModels()

    private var currentY = 0
    private var currentIndex = 0

    private var prevY = 0
    private var prevIndex = 0

    private var scrollDirection = ScrollDirection.DOWN

    private val MAX_VIEWS = 5
    private val PREFETCH_DISTANCE = MAX_VIEWS / 2

    private var viewPool = mutableListOf<ImageView>()
    private var itemOffsetList = mutableListOf<ViewOffsetMap>()

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
                        currentY = -engine.panY.toInt() + screenWidth / 2

                        if (currentY != prevY) {
                            currentIndex = computeVisibleIndex(currentY, itemOffsetList)
                            scrollDirection =
                                if (currentY > prevY) ScrollDirection.DOWN else ScrollDirection.UP

                            if (currentIndex != prevIndex) {
                                lifecycleScope.launch { manageViews() }
                            }

                            prevY = currentY
                            prevIndex = currentIndex
                        }
                    }
                }
            )
        }

        binding.zoomLayout.post {
            uiSetup.setupGestureDetector()
        }

        binding.bottomAppBar.post {
            binding.bottomAppBar.performHide(false)
        }

        setupObservers()
    }

    private fun setupObservers() {
        vMangaFile.mangaFile.observe(this) {
            vMangaPanel.loadPagesForManga(mangaId)
            setupPanelObserver()
        }
    }

    private suspend fun prepareOffsetsAndContainer(images: List<MangaPanel>) {
        var containerHeight = 0

        // Clear existing offset list when loading new images
        itemOffsetList.clear()

        images.forEachIndexed { index, image ->
            val h = (screenWidth / image.aspect_ratio)
            itemOffsetList.add(ViewOffsetMap(containerHeight, h.toInt(), image, null))
            containerHeight += h.toInt() + 8.dpToPx()
        }

        val params = binding.imageList.layoutParams
        params.height = containerHeight
        binding.imageList.layoutParams = params

        manageViews()
    }

    suspend fun manageViews() {
        if (itemOffsetList.isEmpty() || vMangaPanel.mangaPanels.value.isNullOrEmpty() || currentIndex < 0)
            return


        val startIdx = max(0, currentIndex - PREFETCH_DISTANCE)
        val endIdx = min(itemOffsetList.size - 1, currentIndex + MAX_VIEWS - PREFETCH_DISTANCE)
        val indexesToDraw = (startIdx..endIdx).toList()

        val presentIndexes = viewPool.map { it.id }.toList()
        val remainingIndexes = indexesToDraw - presentIndexes

        remainingIndexes.forEach {
            drawPanel(it)
        }
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

    private suspend fun drawPanel(index: Int) {
        val imageView =
            if (viewPool.size == MAX_VIEWS) {
                var newImageView: ImageView
                newImageView =
                    if (scrollDirection == ScrollDirection.DOWN)
                        viewPool.minBy { it.translationY }
                    else
                        viewPool.maxBy { it.translationY }

                newImageView.setImageBitmap(null)

                newImageView
            } else {
                val newImageView = createImageView()
                newImageView.id = index

                viewPool.add(newImageView)
                binding.imageList.addView(newImageView)

                newImageView
            }

        drawImage(imageView, index)
    }

    suspend fun drawImage(view: ImageView, index: Int) {
        Log.e(TAG, "draw: index: $index")

        val zipFilePath = vMangaFile.mangaFile.value?.path ?: return
        val panelMeta = itemOffsetList.getOrNull(index) ?: return

        val vY = panelMeta.offset
        val vH = panelMeta.height
        val vP = panelMeta.panel

        val params = FrameLayout.LayoutParams(screenWidth, vH)
        view.layoutParams = params
        view.translationY = vY.toFloat()
        view.id = index

        withContext(Dispatchers.IO) {
            val bitmap = extractImageFromZip(zipFilePath, vP.page_name)
            view.load(bitmap) {
                crossfade(true)
                crossfade(300)
            }
        }
    }

    private fun setupPanelObserver() {
        vMangaPanel.mangaPanels.observe(this) { panels ->
            lifecycleScope.launch {
                vMangaFile.mangaFile.value?.let { manga ->
                    if (panels.isEmpty()) {
                        uiSetup.loadMangaPanels(manga)
                    } else {
                        binding.progressbar.visibility = View.GONE
                        prepareOffsetsAndContainer(panels)
                    }
                }
            }
        }
    }

}