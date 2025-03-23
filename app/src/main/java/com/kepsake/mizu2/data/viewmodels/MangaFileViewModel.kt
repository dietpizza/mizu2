package com.kepsake.mizu2.data.viewmodels

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.kepsake.mizu2.data.models.MangaFile
import com.kepsake.mizu2.logic.NaturalOrderComparator
import io.objectbox.Box

enum class SortOption {
    NAME,
    LAST_MODIFIED,
    SIZE
}

enum class SortOrder {
    ASC,
    DESC
}

class MangaFileViewModel : ViewModel() {

    private lateinit var mangaFileBox: Box<MangaFile>
    private lateinit var sharedPreferences: SharedPreferences
    private val naturalOrderComparator = NaturalOrderComparator()

    // Constants for SharedPreferences keys
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

    private val _mangaFile = MutableLiveData<MangaFile>()
    val mangaFile: LiveData<MangaFile> get() = _mangaFile

    fun init(box: Box<MangaFile>, context: Context) {
        mangaFileBox = box
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Load sort preferences from SharedPreferences
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
        _mangaFile.value = mangaFileBox.get(id)
    }

    fun syncWithDisk(filesOnDisk: List<MangaFile>) {
        mangaFileBox.store.runInTx {
            val filesInDb = mangaFileBox.all

            val filesToBeAddedOrUpdated = filesOnDisk.map { fileOnDisk ->
                val existing = filesInDb.find { it.path == fileOnDisk.path }
                if (existing != null) fileOnDisk.id = existing.id

                return@map fileOnDisk
            }

            val filesToBeDeleted = filesInDb.mapNotNull { manga ->
                val fileOnDisk = filesOnDisk.find { it.path == manga.path }

                if (fileOnDisk == null) {
                    return@mapNotNull manga
                }
                null
            }

            mangaFileBox.putBatched(filesToBeAddedOrUpdated, 20)
            mangaFileBox.remove(filesToBeDeleted)
        }

        loadMangaFiles()
    }

    // Function to load all MangaFiles with current sort option applied
    fun loadMangaFiles() {
        val allFiles = mangaFileBox.all

        // Apply sorting based on currentSortOption and currentSortOrder
        val sortedFiles = when (_currentSortOption) {
            SortOption.NAME -> {
                if (_currentSortOrder == SortOrder.ASC) {
                    allFiles.sortedWith(compareBy(naturalOrderComparator) { it.name })
                } else {
                    allFiles.sortedWith(compareByDescending(naturalOrderComparator) { it.name })
                }
            }

            SortOption.LAST_MODIFIED -> {
                if (_currentSortOrder == SortOrder.ASC) {
                    allFiles.sortedBy { it.last_modified }
                } else {
                    allFiles.sortedByDescending { it.last_modified }
                }
            }

            SortOption.SIZE -> {
                if (_currentSortOrder == SortOrder.ASC) {
                    allFiles.sortedBy { it.total_pages }
                } else {
                    allFiles.sortedByDescending { it.total_pages }
                }
            }
        }

        _mangaFiles.value = sortedFiles
    }

    // Sorting
    fun sortByName(ascending: Boolean = true) {
        _currentSortOption = SortOption.NAME
        _currentSortOrder = if (ascending) SortOrder.ASC else SortOrder.DESC
        saveSortPreferences()
        loadMangaFiles()
    }

    fun sortByLastModified(ascending: Boolean = true) {
        _currentSortOption = SortOption.LAST_MODIFIED
        _currentSortOrder = if (ascending) SortOrder.ASC else SortOrder.DESC
        saveSortPreferences()
        loadMangaFiles()
    }

    fun sortBySize(ascending: Boolean = true) {
        _currentSortOption = SortOption.SIZE
        _currentSortOrder = if (ascending) SortOrder.ASC else SortOrder.DESC
        saveSortPreferences()
        loadMangaFiles()
    }

    fun updateCurrentPage(id: Long, page: Int) {
        silentUpdateCurrentPage(id, page)
        loadMangaFileById(id)

    }

    fun silentUpdateCurrentPage(id: Long, page: Int) {
        val _newEntry = mangaFile.value

        if (_newEntry != null) {
            _newEntry.current_page = page
            mangaFileBox.put(_newEntry)
        }

    }

    fun setSortOption(option: SortOption) {
        if (_currentSortOption != option) {
            _currentSortOption = option
            saveSortPreferences()
        }
    }

    fun setSortOrder(order: SortOrder) {
        if (_currentSortOrder != order) {
            _currentSortOrder = order
            saveSortPreferences()
        }
    }
}