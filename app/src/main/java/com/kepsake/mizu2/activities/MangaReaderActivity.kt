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

data class PanelLayoutMeta(
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
    private var mangaPanelsLayoutMeta = mutableListOf<PanelLayoutMeta>()
    private var delY = 0f

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
                            currentIndex = computeVisibleIndex(currentY, mangaPanelsLayoutMeta)
                            scrollDirection =
                                if (currentY > prevY) ScrollDirection.DOWN else ScrollDirection.UP

                            if (currentIndex != prevIndex) {
                                lifecycleScope.launch { manageViews() }
                            }

                            val _delY = prevY - currentY
                            delY = delY + Math.abs(_delY)

                            if (delY > 25) {
                                Log.e(TAG, "Delta Y ${delY}")
                                vMangaFile.mangaFile.value?.let { mf ->
                                    Log.e(TAG, "Updating Current Progress ${engine.panY}")
                                    vMangaFile.silentUpdateCurrentProgress(
                                        mf.id, engine.panY.toInt()
                                    )
                                }

                                delY = 0f
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
        binding.pageSlider.post {
            uiSetup.setupPageSlider(mangaPanelsLayoutMeta)
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
        mangaPanelsLayoutMeta.clear()

        images.forEachIndexed { index, image ->
            val h = (screenWidth / image.aspect_ratio)
            mangaPanelsLayoutMeta.add(PanelLayoutMeta(containerHeight, h.toInt(), image, null))
            containerHeight += h.toInt() + 8.dpToPx()
        }
        Log.e(TAG, "Number of images ${images.size}")
        binding.pageSlider.valueTo = images.size.toFloat() - 1f

        val params = binding.imageList.layoutParams
        params.height = containerHeight
        binding.imageList.layoutParams = params

        manageViews()

        vMangaFile.mangaFile.value?.let { mf ->
            Log.e(TAG, "Current Progress ${mf.current_progress}")
            if (Math.abs(mf.current_progress) > 0) {
                Log.e(TAG, "Panning to ${mf.current_progress.toFloat()}")
                binding.zoomLayout.panTo(0f, mf.current_progress.toFloat(), false)
            }
        }

    }

    suspend fun manageViews() {
        if (mangaPanelsLayoutMeta.isEmpty() || vMangaPanel.mangaPanels.value.isNullOrEmpty() || currentIndex < 0)
            return


        val startIdx = max(0, currentIndex - PREFETCH_DISTANCE)
        val endIdx =
            min(mangaPanelsLayoutMeta.size - 1, currentIndex + MAX_VIEWS - PREFETCH_DISTANCE)
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
        val panelMeta = mangaPanelsLayoutMeta.getOrNull(index) ?: return

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