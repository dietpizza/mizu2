package com.kepsake.mizu2.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.kepsake.mizu2.data.viewmodels.MangaFileViewModel
import com.kepsake.mizu2.data.viewmodels.MangaPanelViewModel
import com.kepsake.mizu2.databinding.ActivityMangaReaderBinding
import com.kepsake.mizu2.helpers.MangaReaderUIHelper
import kotlinx.coroutines.launch

class MangaReaderActivity : ComponentActivity() {
    val TAG = "MangaReaderActivity"

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
                        uiSetup.initSlider(panels.size - 1)
                    }
                }
                uiSetup.syncViewVisibility()
            }
        }
    }
}