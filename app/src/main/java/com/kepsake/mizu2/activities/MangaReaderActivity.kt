package com.kepsake.mizu2.activities

import android.animation.ObjectAnimator
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.kepsake.mizu2.MizuApplication
import com.kepsake.mizu2.data.models.MangaFile
import com.kepsake.mizu2.data.models.MangaPanel
import com.kepsake.mizu2.data.viewmodels.MangaFileViewModel
import com.kepsake.mizu2.data.viewmodels.MangaPanelViewModel
import com.kepsake.mizu2.databinding.ActivityMangaReaderBinding
import com.kepsake.mizu2.helpers.MangaReaderUIHelper
import com.kepsake.mizu2.logic.NaturalOrderComparator
import com.kepsake.mizu2.utils.getMangaPagesAspectRatios
import com.kepsake.mizu2.utils.getZipFileEntries
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpaceItemDecoration(private val spaceHeight: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        val itemCount = state.itemCount

        if (position < itemCount - 1) {
            outRect.bottom = spaceHeight
        } else {
            outRect.bottom = 0
        }
    }
}

class MangaReaderActivity : ComponentActivity() {
    val TAG = "MangaReaderActivity"

    private lateinit var mangaPanelBox: Box<MangaPanel>
    private lateinit var mangaFileBox: Box<MangaFile>
    private lateinit var binding: ActivityMangaReaderBinding

    private val mangaId by lazy { intent.getLongExtra("id", -1) }

    private val vMangaPanel: MangaPanelViewModel by viewModels()
    private val vMangaFile: MangaFileViewModel by viewModels()

    private val uiSetup by lazy {
        MangaReaderUIHelper(this, binding, vMangaFile)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMangaReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val boxStore = (application as MizuApplication).boxStore

        mangaFileBox = boxStore.boxFor()
        mangaPanelBox = boxStore.boxFor()

        vMangaFile.init(mangaFileBox, this)
        vMangaFile.loadMangaFileById(mangaId)

        initializeApp()
    }

    private fun initializeApp() {
        uiSetup.initReader()
        setupObservers()
    }

    private fun setupObservers() {
        vMangaFile.mangaFile.observe(this) {
            vMangaPanel.init(mangaPanelBox)
            vMangaPanel.loadPagesForManga(mangaId)

            uiSetup.initReader()
            setupPanelObserver()
        }
    }

    private fun setupPanelObserver() {
        vMangaPanel.mangaPanels.observe(this) { panels ->
            lifecycleScope.launch {
                vMangaFile.mangaFile.value?.let { manga ->
                    if (panels.isEmpty()) {
                        loadMangaPanels(manga)
                    } else {
                        uiSetup.updatePanels(panels)
                        binding.mangaReader.scrollToPosition(manga.current_page)
                    }
                }
                uiSetup.syncViewVisibility()
            }
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun loadMangaPanels(mangaFile: MangaFile) {
        val entries = getZipFileEntries(mangaFile.path)
            .sortedWith(compareBy(NaturalOrderComparator()) { it.name })

        val progressFlow = MutableStateFlow(0f)

        val progressJob = lifecycleScope.launch(Dispatchers.Main) {
            progressFlow.sample(300).collect { progress ->
                val progressValue = (progress * 100f)
                ObjectAnimator.ofInt(
                    binding.progressbar,
                    "progress",
                    binding.progressbar.progress,
                    progressValue.toInt()
                ).apply {
                    duration = 200 // Animation duration in milliseconds
                    interpolator = DecelerateInterpolator() // For a smooth deceleration effect
                    start()
                }
            }
        }

        val pageAspectRatioMap = withContext(Dispatchers.IO) {
            getMangaPagesAspectRatios(
                this@MangaReaderActivity,
                mangaFile.path
            ) { progress ->
                progressFlow.value = progress
            }
        }

        progressJob.cancel()

        pageAspectRatioMap?.let { ratioMap ->
            val allPages = entries.mapNotNull { entry ->
                ratioMap[entry.name]?.let { aspectRatio ->
                    MangaPanel(
                        0, mangaFile.id, entry.name, aspectRatio,
                    )
                }
            }
            vMangaPanel.addMangaPanels(allPages)
        }
    }
}