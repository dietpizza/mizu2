package com.kepsake.mizu2.views

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.widget.AppCompatImageView
import coil.load
import com.kepsake.mizu2.utils.extractImageFromZip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MangaPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var zipFilePath: String? = null
    private var entryName: String? = null

    init {
        // Default settings
        scaleType = ScaleType.FIT_XY
        adjustViewBounds = true
    }

    /**
     * Constructor that accepts zip file path and entry name directly
     */
    constructor(context: Context, zipFilePath: String, entryName: String) : this(context) {
        this.zipFilePath = zipFilePath
        this.entryName = entryName
        loadImageFromZip()
    }

    private fun loadImageFromZip() {
        val zipPath = zipFilePath ?: return
        val entry = entryName ?: return

        // Use a coroutine to perform the extraction off the main thread
        CoroutineScope(Dispatchers.Main).launch {
            val bitmap = withContext(Dispatchers.IO) {
                Log.e("TAG", "loadImageFromZip: Bitmap get")
                extractImageFromZip(zipPath, entry)
            }
            // Load the extracted bitmap using Coil
            if (bitmap != null) {
                load(bitmap)
            }
        }
    }

}