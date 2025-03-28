package com.kepsake.mizu2.data.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.kepsake.mizu2.data.MangaDatabase
import com.kepsake.mizu2.data.models.MangaFile
import com.kepsake.mizu2.data.models.MangaFileDao
import com.kepsake.mizu2.data.models.MangaPanelDao
import com.kepsake.mizu2.logic.NaturalOrderComparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class SortOption {
    NAME,
    LAST_MODIFIED,
    SIZE
}

enum class SortOrder {
    ASC,
    DESC
}

class MangaFileViewModel(application: Application) : AndroidViewModel(application) {
    private val mangaFileDao: MangaFileDao = MangaDatabase.getDatabase(application).mangaFileDao()
    private val mangaPanelDao: MangaPanelDao =
        MangaDatabase.getDatabase(application).mangaPanelDao()

    private val sharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val naturalOrderComparator = NaturalOrderComparator()

    companion object {
        private const val PREFS_NAME = "manga_file_preferences"
        private const val KEY_SORT_OPTION = "sort_option"
        private const val KEY_SORT_ORDER = "sort_order"
    }

    private var _currentSortOption = SortOption.NAME
    val currentSortOption: SortOption get() = _currentSortOption

    private var _currentSortOrder = SortOrder.ASC
    val currentSortOrder: SortOrder get() = _currentSortOrder

    private val _mangaFiles = MutableLiveData<List<MangaFile>>()
    val mangaFiles: LiveData<List<MangaFile>> get() = _mangaFiles

    private val _mangaFile = MutableLiveData<MangaFile?>()
    val mangaFile: MutableLiveData<MangaFile?> get() = _mangaFile

    init {
        loadSortPreferences()
        loadMangaFiles()
    }

    private fun loadSortPreferences() {
        val sortOptionOrdinal = sharedPreferences.getInt(KEY_SORT_OPTION, SortOption.NAME.ordinal)
        val sortOrderOrdinal = sharedPreferences.getInt(KEY_SORT_ORDER, SortOrder.ASC.ordinal)

        _currentSortOption = SortOption.values()[sortOptionOrdinal]
        _currentSortOrder = SortOrder.values()[sortOrderOrdinal]
    }

    private fun saveSortPreferences() {
        sharedPreferences.edit()
            .putInt(KEY_SORT_OPTION, _currentSortOption.ordinal)
            .putInt(KEY_SORT_ORDER, _currentSortOrder.ordinal)
            .apply()
    }

    fun loadMangaFileById(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _mangaFile.postValue(mangaFileDao.getById(id))
        }
    }

    fun syncWithDisk(filesOnDisk: List<MangaFile>) {
        viewModelScope.launch(Dispatchers.IO) {
            val filesInDb = mangaFileDao.getAll()
            val filesToBeAddedOrUpdated = filesOnDisk.map { fileOnDisk ->
                val existing = filesInDb.find { it.path == fileOnDisk.path }
                if (existing != null) {
                    fileOnDisk.id = existing.id
                    fileOnDisk.current_page = existing.current_page
                }
                fileOnDisk
            }
            val idsToBeDeleted = filesInDb
                .filter { file -> filesOnDisk.find { file.path == it.path } == null }
                .map { it.id }

            if (idsToBeDeleted.size > 0) {
                mangaFileDao.deleteByIds(idsToBeDeleted)
                mangaPanelDao.deletePagesFor(idsToBeDeleted)
            }

            mangaFileDao.insertOrUpdateAll(filesToBeAddedOrUpdated)
            mangaFileDao.deleteDuplicatePaths()
            loadMangaFiles()
        }
    }

    fun loadMangaFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val allFiles = mangaFileDao.getAll()

            val sortedFiles = when (_currentSortOption) {
                SortOption.NAME -> if (_currentSortOrder == SortOrder.ASC) {
                    allFiles.sortedWith(compareBy(naturalOrderComparator) { it.name })
                } else {
                    allFiles.sortedWith(compareByDescending(naturalOrderComparator) { it.name })
                }

                SortOption.LAST_MODIFIED -> if (_currentSortOrder == SortOrder.ASC) {
                    allFiles.sortedBy { it.last_modified }
                } else {
                    allFiles.sortedByDescending { it.last_modified }
                }

                SortOption.SIZE -> if (_currentSortOrder == SortOrder.ASC) {
                    allFiles.sortedBy { it.total_pages }
                } else {
                    allFiles.sortedByDescending { it.total_pages }
                }
            }
            _mangaFiles.postValue(sortedFiles)
        }
    }

    fun silentUpdateCurrentPage(id: Long, page: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val manga = mangaFileDao.getById(id)
            if (manga != null) {
                manga.current_page = page
                mangaFileDao.insertOrUpdate(manga)
            }
        }
    }

    fun setSortInfo(option: SortOption, isAscending: Boolean) {
        if (_currentSortOption != option) {
            _currentSortOption = option
        }
        _currentSortOrder = if (isAscending) SortOrder.ASC else SortOrder.DESC

        loadMangaFiles()
        saveSortPreferences()
    }
}