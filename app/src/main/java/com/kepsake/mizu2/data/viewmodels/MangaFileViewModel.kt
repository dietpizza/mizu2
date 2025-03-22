package com.kepsake.mizu2.data.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.kepsake.mizu2.data.models.MangaFile
import io.objectbox.Box

class MangaFileViewModel : ViewModel() {

    // ObjectBox Box for MangaFile entity
    private lateinit var mangaFileBox: Box<MangaFile>

    // LiveData to observe the list of MangaFiles
    private val _mangaFiles = MutableLiveData<List<MangaFile>>()
    val mangaFiles: LiveData<List<MangaFile>> get() = _mangaFiles

    // LiveData to observe a single MangaFile
    private val _mangaFile = MutableLiveData<MangaFile>()
    val mangaFile: LiveData<MangaFile> get() = _mangaFile

    // Initialize the Box
    fun init(box: Box<MangaFile>) {
        mangaFileBox = box
    }

    // Function to add a new MangaFile
    fun addMangaFile(mangaFile: MangaFile) {
        mangaFileBox.put(mangaFile)
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

    // Function to load all MangaFiles
    fun loadMangaFiles() {
        _mangaFiles.value = mangaFileBox.all
    }

    // Function to load a specific MangaFile by ID
    fun loadMangaFileById(id: Long) {
        _mangaFile.value = mangaFileBox.get(id)
    }
}