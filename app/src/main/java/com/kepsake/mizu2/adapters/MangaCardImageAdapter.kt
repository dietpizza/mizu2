package com.kepsake.mizu2.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.kepsake.mizu2.adapters.diff.MangaDiffCallback
import com.kepsake.mizu2.data.models.MangaFile
import com.kepsake.mizu2.databinding.WidgetMangaCardBinding

val TAG = "MangaCardImageAdapter"

class MangaCardImageAdapter(
    private var mangaList: List<MangaFile>,
    private val onItemClick: (MangaFile) -> Unit
) :
    RecyclerView.Adapter<MangaCardImageAdapter.MangaCardImageViewHolder>() {

    fun updateData(newData: List<MangaFile>) {
        val diffCallback = MangaDiffCallback(mangaList, newData)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        Log.e(TAG, "updateData: $newData")
        mangaList = newData
        diffResult.dispatchUpdatesTo(this)
    }

    class MangaCardImageViewHolder(val binding: WidgetMangaCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaCardImageViewHolder {
        val binding =
            WidgetMangaCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MangaCardImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MangaCardImageViewHolder, position: Int) {
        val manga = mangaList[position]

        holder.binding.mangaCover.load(manga.cover_path) {
            crossfade(true)
        }
        holder.binding.mangaName.setText(manga.name)
        holder.itemView.setOnClickListener { onItemClick(manga) }
    }

    override fun getItemCount(): Int = mangaList.size
}
