package com.kepsake.mizu2.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.kepsake.mizu2.databinding.WidgetMangaCardBinding


class MangaCardImageAdapter(private val imageUrls: List<String>) :
    RecyclerView.Adapter<MangaCardImageAdapter.MangaCardImageViewHolder>() {

    class MangaCardImageViewHolder(val binding: WidgetMangaCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaCardImageViewHolder {
        val binding =
            WidgetMangaCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MangaCardImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MangaCardImageViewHolder, position: Int) {
        val imageUrl = imageUrls[position]

        holder.binding.gridImageView.load(imageUrl) {
            crossfade(true)
        }
    }

    override fun getItemCount(): Int = imageUrls.size
}
