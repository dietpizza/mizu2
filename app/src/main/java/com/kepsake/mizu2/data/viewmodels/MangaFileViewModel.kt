package com.kepsake.mizu2.data.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.kepsake.mizu2.data.models.MangaFile
import com.kepsake.mizu2.logic.NaturalOrderComparator
import io.objectbox.Box

enum class SortOption {
    NAME_ASC,
    NAME_DESC,
    LAST_MODIFIED_ASC,
    LAST_MODIFIED_DESC,
    SIZE_ASC,
    SIZE_DESC
}

class MangaFileViewModel : ViewModel() {

    private lateinit var mangaFileBox: Box<MangaFile>
    private val naturalOrderComparator = NaturalOrderComparator()

    private var currentSortOption = SortOption.NAME_ASC

    private val _mangaFiles = MutableLiveData<List<MangaFile>>()
    val mangaFiles: LiveData<List<MangaFile>> get() = _mangaFiles

    private val _mangaFile = MutableLiveData<MangaFile>()
    val mangaFile: LiveData<MangaFile> get() = _mangaFile

    // Initialize the Box
    fun init(box: Box<MangaFile>) {
        mangaFileBox = box
        loadMangaFiles()
    }

    // Function to add a new MangaFile
    fun addMangaFile(mangaFile: MangaFile) {
        mangaFileBox.put(mangaFile)
        loadMangaFiles()
    }

    fun batchAddMangaFiles(mangaFiles: List<MangaFile>) {
        // Start a transaction for better performance with batch operations
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

        // Reload the list after the batch operation
        loadMangaFiles()
    }

    // Function to update an existing MangaFile
    fun updateMangaFile(mangaFile: MangaFile) {
        mangaFileBox.put(mangaFile)
        loadMangaFiles()
    }

    // Function to delete a MangaFile
    fun deleteMangaFile(mangaFile: MangaFile) {
        mangaFileBox.remove(mangaFile)
        loadMangaFiles()
    }

    // Function to change the sort option
    fun setSortOption(sortOption: SortOption) {
        if (currentSortOption != sortOption) {
            currentSortOption = sortOption
            loadMangaFiles()
        }
    }

    // Function to load all MangaFiles with current sort option applied
    fun loadMangaFiles() {
        val allFiles = mangaFileBox.all

        // Apply sorting based on currentSortOption
        val sortedFiles = when (currentSortOption) {
            SortOption.NAME_ASC -> allFiles.sortedWith(compareBy(naturalOrderComparator) { it.name })
            SortOption.NAME_DESC -> allFiles.sortedWith(compareByDescending(naturalOrderComparator) { it.name })
            SortOption.LAST_MODIFIED_ASC -> allFiles.sortedBy { it.last_modified }
            SortOption.LAST_MODIFIED_DESC -> allFiles.sortedByDescending { it.last_modified }
            SortOption.SIZE_ASC -> allFiles.sortedBy { it.total_pages }
            SortOption.SIZE_DESC -> allFiles.sortedByDescending { it.total_pages }
        }

        _mangaFiles.value = sortedFiles
    }

    // Function to load a specific MangaFile by ID
    fun loadMangaFileById(id: Long) {
        _mangaFile.value = mangaFileBox.get(id)
    }

    // Convenience functions for each sort option
    fun sortByNameAscending() = setSortOption(SortOption.NAME_ASC)
    fun sortByNameDescending() = setSortOption(SortOption.NAME_DESC)
    fun sortByLastModifiedAscending() = setSortOption(SortOption.LAST_MODIFIED_ASC)
    fun sortByLastModifiedDescending() = setSortOption(SortOption.LAST_MODIFIED_DESC)
    fun sortBySizeAscending() = setSortOption(SortOption.SIZE_ASC)
    fun sortBySizeDescending() = setSortOption(SortOption.SIZE_DESC)
}
