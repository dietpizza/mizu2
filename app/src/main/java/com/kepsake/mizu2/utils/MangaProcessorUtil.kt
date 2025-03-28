package com.kepsake.mizu2.utils

import android.content.Context
import com.kepsake.mizu2.data.models.MangaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

suspend fun processMangaFiles(
    context: Context,
    libraryPath: String,
    existingFiles: List<String> = emptyList()
): List<MangaFile> =
    withContext(Dispatchers.IO) {
        val mangaFilePaths = getMangaFiles(libraryPath)
        val zipFilesToProcess = mangaFilePaths - existingFiles

        val libraryEntries = mutableListOf<MangaFile>()
        val coversDir = File(context.filesDir, "covers").apply {
            if (!exists()) mkdirs()
        }

        zipFilesToProcess.forEach { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    val zipFile = ZipFile(file)
                    val coverPath = extractCoverImage(coversDir, path)
                    val lastModified: Long = file.lastModified()
                    val pageCount = getZipPageCount(path)

                    coverPath?.let {
                        val manga = MangaFile(
                            id = 0,
                            path = path,
                            name = file.nameWithoutExtension,
                            cover_path = coverPath,
                            current_page = 0,
                            total_pages = pageCount,
                            last_modified = lastModified
                        )

                        libraryEntries.add(manga)
                    }
                }
            } catch (e: Exception) {
                // TODO nothing to do
            }
        }
        return@withContext libraryEntries.toList()
    }