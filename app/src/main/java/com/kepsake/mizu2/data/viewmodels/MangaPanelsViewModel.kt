package com.kepsake.mizu2.data.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.kepsake.mizu2.data.models.MangaPanel
import com.kepsake.mizu2.data.models.MangaPanel_
import io.objectbox.Box

class MangaPanelViewModel : ViewModel() {

    private lateinit var MangaPanelBox: Box<MangaPanel>

    private val _MangaPanels = MutableLiveData<List<MangaPanel>>()
    val mangaPanels: LiveData<List<MangaPanel>> get() = _MangaPanels

    // Current manga id to filter pages
    private var currentMangaId: Long? = null

    fun init(box: Box<MangaPanel>) {
        MangaPanelBox = box
    }

    // Set manga ID and load all pages for that manga
    fun loadPagesForManga(id: Long) {
        currentMangaId = id
        loadMangaPanels()
    }

    // Function to load MangaPanels filtered by manga_id
    fun loadMangaPanels() {
        val query = MangaPanelBox.query()

        // Apply manga_id filter if set
        currentMangaId?.let { id ->
            query.equal(MangaPanel_.manga_id, id)
        }

        // Execute query and update LiveData
        val pages = query.build().find()
        _MangaPanels.value = pages
    }

    // Add multiple manga pages
    fun addMangaPanels(pages: List<MangaPanel>) {
        MangaPanelBox.put(pages)
        if (pages.any { it.manga_id == currentMangaId }) {
            loadMangaPanels()
        }
    }
}