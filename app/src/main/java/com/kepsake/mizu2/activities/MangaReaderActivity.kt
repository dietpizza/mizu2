package com.kepsake.mizu2.activities

import android.graphics.Rect
import android.os.Bundle
import android.view.View
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
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.launch

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
        MangaReaderUIHelper(this, binding, vMangaFile, vMangaPanel, lifecycleScope)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMangaReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val boxStore = (application as MizuApplication).boxStore

        mangaFileBox = boxStore.boxFor()
        mangaPanelBox = boxStore.boxFor()

        vMangaFile.loadMangaFileById(mangaId)

        initializeApp()
    }

    private fun initializeApp() {
        uiSetup.initReader()
        setupObservers()
    }

    private fun setupObservers() {
        vMangaFile.mangaFile.observe(this) {
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
                        uiSetup.loadMangaPanels(manga)
                    } else {
                        uiSetup.updatePanels(panels)
                        binding.mangaReader.scrollToPosition(manga.current_page)
                    }
                }
                uiSetup.syncViewVisibility()
            }
        }
    }
}