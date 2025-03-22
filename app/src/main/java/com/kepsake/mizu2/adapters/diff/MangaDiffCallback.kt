package com.kepsake.mizu2.adapters.diff

import androidx.recyclerview.widget.DiffUtil
import com.kepsake.mizu2.data.models.MangaFile

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