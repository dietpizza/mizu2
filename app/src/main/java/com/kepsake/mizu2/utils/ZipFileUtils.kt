package com.kepsake.mizu2.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.kepsake.mizu2.logic.NaturalOrderComparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

val TAG = "ZipUtil"

fun extractEntryToFile(
    zipFilePath: String,
    outFilePath: String,
    entryName: String
): File? {
    return try {
        ZipFile(File(zipFilePath)).use { zipFile ->
            val entry = zipFile.getEntry(entryName)
            if (entry != null && !entry.isDirectory) {
                val outFile = File(outFilePath)
                zipFile.getInputStream(entry).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Verify the file was created successfully
                if (outFile.exists() && outFile.length() > 0) {
                    return outFile
                } else {
                    null
                }
            } else {
                null
            }
        }
    } catch (e: Exception) {
        null
    }
}

fun getZipPageCount(zipFilePath: String): Int {
    val zipFile = ZipFile(File(zipFilePath))
    return zipFile.entries().toList().filter { isImageFile(it.name) }.size
}

fun getZipFileEntries(zipFilePath: String): List<ZipEntry> {
    try {
        ZipFile(zipFilePath).use { zipFile ->
            val entries =
                zipFile.entries().asSequence().filter { !it.isDirectory && isImageFile(it.name) }
                    .toList().sortedWith(compareBy(NaturalOrderComparator()) { it.name })

            return entries
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error reading zip file", e)
    }

    // Sort entries by name for correct order
    return emptyList()
}

fun sanitizeFileName(name: String): String {
    return name.replace('/', '_').replace('\\', '_')
}


fun extractCoverImage(coversDir: File, mangaPath: String): String? {
    val entry = getZipFileEntries(mangaPath).firstOrNull()

    if (entry != null) {
        val entryName = File(mangaPath).name
        val outFile = File(coversDir, sanitizeFileName(entryName))

        if (!outFile.exists()) {
            val extractedFile = extractEntryToFile(mangaPath, outFile.path, entry.name)
            return extractedFile?.path
        } else {
            return outFile.path
        }
    }

    return null
}


fun getMangaPagesAspectRatios(
    context: Context,
    zipFilePath: String,
    onAspectRatioCalculated: (progress: Float) -> Unit
): MutableMap<String, Float>? {
    val tmpFile = File(context.cacheDir, "temp_aspect")
    val pageAspectRatioMap = emptyMap<String, Float>().toMutableMap()

    try {
        ZipFile(File(zipFilePath)).use { zipFile ->
            val entries = zipFile.entries().toList()
            val totalEntries = entries.size

            entries.forEachIndexed { index, entry ->
                zipFile.getInputStream(entry).use { input ->
                    tmpFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val aspectRatio = getImageAspectRatio(tmpFile.path)
                pageAspectRatioMap[entry.name] = aspectRatio

                // Calculate progress as a percentage (0.0f to 1.0f)
                val progress = (index + 1).toFloat() / totalEntries

                // Call the callback with the file name, aspect ratio, and progress
                onAspectRatioCalculated(progress)
            }
        }
        return pageAspectRatioMap
    } catch (e: Exception) {
        // Handle exception or log error
        e.printStackTrace()
    } finally {
        tmpFile.delete()
    }
    return null
}

suspend fun extractImageFromZip(zipFilePath: String, entryName: String): Bitmap? {
    return try {
        val file = File(zipFilePath)
        if (!file.exists()) {
            throw IllegalArgumentException("ZIP file does not exist at path: $zipFilePath")
        }

        val zipFile = withContext(Dispatchers.IO) {
            ZipFile(file)
        }
        val entry: ZipEntry? = zipFile.getEntry(entryName)

        if (entry == null) {
            throw IllegalArgumentException("Entry '$entryName' not found in the ZIP file.")
        }

        // Read the entry's input stream and decode it into a Bitmap
        withContext(Dispatchers.IO) {
            zipFile.getInputStream(entry)
        }.use { inputStream ->
            BufferedInputStream(inputStream).use { bufferedInputStream ->
                BitmapFactory.decodeStream(bufferedInputStream)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
