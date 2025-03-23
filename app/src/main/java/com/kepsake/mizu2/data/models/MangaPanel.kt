package com.kepsake.mizu2.data.models

import android.os.Parcelable
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity
data class MangaPanel(
    @Id var id: Long = 0,
    @Index val manga_id: Long,
    val page_name: String,
    val aspect_ratio: Float
) : Parcelable