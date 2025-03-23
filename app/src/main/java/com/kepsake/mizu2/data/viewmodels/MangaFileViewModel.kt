package com.kepsake.mizu2.data.viewmodels

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
    private val naturalOrderComparator = NaturalOrderComparator()

    private var _currentSortOption = SortOption.NAME
    val currentSortOption: SortOption get() = _currentSortOption

    private var _currentSortOrder = SortOrder.ASC
    val currentSortOrder: SortOrder get() = _currentSortOrder

    private val _mangaFiles = MutableLiveData<List<MangaFile>>()
    val mangaFiles: LiveData<List<MangaFile>> get() = _mangaFiles

    private val _mangaFile = MutableLiveData<MangaFile>()
    val mangaFile: LiveData<MangaFile> get() = _mangaFile

    fun init(box: Box<MangaFile>) {
        mangaFileBox = box
        loadMangaFiles()
    }

    fun addMangaFile(mangaFile: MangaFile) {
        mangaFileBox.put(mangaFile)
        loadMangaFiles()
    }

    fun batchAddMangaFiles(mangaFiles: List<MangaFile>) {
        mangaFileBox.store.runInTx {
            val allManga = mangaFileBox.all
            for (newFile in mangaFiles) {
                val existing = allManga.find { it.path == newFile.path }
                if (existing != null) {
                    newFile.id = existing.id
                }
                mangaFileBox.put(newFile)
            }
        }

        loadMangaFiles()
    }

    fun updateMangaFile(mangaFile: MangaFile) {
        mangaFileBox.put(mangaFile)
        loadMangaFiles()
    }

    fun deleteMangaFile(mangaFile: MangaFile) {
        mangaFileBox.remove(mangaFile)
        loadMangaFiles()
    }

    fun getSortOption(): SortOption {
        return _currentSortOption
    }

    fun getSortOrder(): SortOrder {
        return _currentSortOrder
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

    // Function to load a specific MangaFile by ID
    fun loadMangaFileById(id: Long) {
        _mangaFile.value = mangaFileBox.get(id)
    }

    // Convenience functions for sorting
    fun sortByName(ascending: Boolean = true) {
        _currentSortOption = SortOption.NAME
        _currentSortOrder = if (ascending) SortOrder.ASC else SortOrder.DESC
        loadMangaFiles()
    }

    fun sortByLastModified(ascending: Boolean = true) {
        _currentSortOption = SortOption.LAST_MODIFIED
        _currentSortOrder = if (ascending) SortOrder.ASC else SortOrder.DESC
        loadMangaFiles()
    }

    fun sortBySize(ascending: Boolean = true) {
        _currentSortOption = SortOption.SIZE
        _currentSortOrder = if (ascending) SortOrder.ASC else SortOrder.DESC
        loadMangaFiles()
    }
}