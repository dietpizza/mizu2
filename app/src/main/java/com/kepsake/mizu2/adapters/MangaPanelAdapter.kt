package com.kepsake.mizu2.adapters

import android.content.res.Resources
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.kepsake.mizu2.data.models.MangaFile
import com.kepsake.mizu2.data.models.MangaPanel
import com.kepsake.mizu2.databinding.WidgetMangaPanelBinding
import com.kepsake.mizu2.utils.extractImageFromZip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MangaPanelAdapter(
    private val manga: MangaFile,
    private var mangaPanels: List<MangaPanel>,
    private val onClickListener: (MangaPanel) -> Unit
) : RecyclerView.Adapter<MangaPanelAdapter.MangaViewHolder>() {

    private val screenWidth = Resources.getSystem().displayMetrics.widthPixels
    private val pageCache = mutableMapOf<String, Bitmap?>()

    class MangaViewHolder(val binding: WidgetMangaPanelBinding) :
        RecyclerView.ViewHolder(binding.root)

    fun updateData(newData: List<MangaPanel>) {
        mangaPanels = newData
        this.notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaViewHolder {
        val binding =
            WidgetMangaPanelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MangaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MangaViewHolder, position: Int) {
        val page = mangaPanels[position]
        val imageHeight = (screenWidth / page.aspect_ratio).toInt()
        val pageKey = mangaPanels[position].page_name

        holder.binding.mangaPanelLayout.layoutParams.height = imageHeight
        holder.binding.mangaPanelLayout.requestLayout()
//        holder.binding.mangaPanel.setOnClickListener {
//            onClickListener(page)
//        }

        CoroutineScope(Dispatchers.Main).launch {
            val bitmap = pageCache[pageKey] ?: withContext(Dispatchers.IO) {
                extractImageFromZip(manga.path, page.page_name).let {
                    pageCache[pageKey] = it
                    return@withContext it
                }
            }
            bitmap?.let {
                holder.binding.mangaPanel.load(it) {
                    crossfade(true)
//                    placeholder(R.drawable.image_thin)
//                    error(R.drawable.image_broken_thin)
                }
                holder.binding.pageNumber.text = "${position + 1}"
            }
        }

    }

    override fun getItemCount() = mangaPanels.size
}