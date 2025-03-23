package com.kepsake.mizu2.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.kepsake.mizu2.data.models.MangaFile
import com.kepsake.mizu2.databinding.WidgetMangaCardBinding


class MangaCardAdapter(
    private var mangaList: List<MangaFile>,
    private val onItemClick: (MangaFile) -> Unit
) : RecyclerView.Adapter<MangaCardAdapter.MangaCardImageViewHolder>() {
    val TAG = "MangaCardImageAdapter"

    private var recyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView // Store reference to the RecyclerView
    }

    fun updateData(newData: List<MangaFile>) {
        val diffCallback = MangaDiffCallback(mangaList, newData)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        mangaList = newData
        diffResult.dispatchUpdatesTo(this)

        // Scroll to top after data update
        recyclerView?.scrollToPosition(0)
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


class MangaDiffCallback(
    private val oldList: List<MangaFile>,
    private val newList: List<MangaFile>
) : DiffUtil.Callback() {

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].id == newList[newItemPosition].id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]

        return oldItem.name == newItem.name
                && oldItem.cover_path == newItem.cover_path
                && oldItem.current_page == newItem.current_page
                && oldItem.total_pages == newItem.total_pages
    }
}
