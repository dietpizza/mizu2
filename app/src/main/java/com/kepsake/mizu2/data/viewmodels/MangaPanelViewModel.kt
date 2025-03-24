package com.kepsake.mizu2.data.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.kepsake.mizu2.data.MangaDatabase
import com.kepsake.mizu2.data.models.MangaPanel
import com.kepsake.mizu2.data.models.MangaPanelDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MangaPanelViewModel(application: Application) : AndroidViewModel(application) {
    private val mangaPanelDao: MangaPanelDao =
        MangaDatabase.getDatabase(application).mangaPanelDao()
    private val _mangaPanels = MutableLiveData<List<MangaPanel>>()
    val mangaPanels: LiveData<List<MangaPanel>> get() = _mangaPanels

    private var currentMangaId: Long? = null

    fun loadPagesForManga(id: Long) {
        currentMangaId = id
        loadMangaPanels()
    }

    fun loadMangaPanels() {
        currentMangaId?.let { mangaId ->
            viewModelScope.launch(Dispatchers.IO) {
                val pages = mangaPanelDao.getPagesForManga(mangaId)
                _mangaPanels.postValue(pages)
            }
        }
    }

    fun addMangaPanels(pages: List<MangaPanel>) {
        viewModelScope.launch(Dispatchers.IO) {
            mangaPanelDao.insertOrUpdateAll(pages)
            if (pages.any { it.manga_id == currentMangaId }) {
                loadMangaPanels()
            }
        }
    }
}

