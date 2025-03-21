package com.kepsake.mizu2.data.models

import android.os.Parcelable
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity
data class MangaFile(
    @Id var id: Long = 0,
    @Index var path: String,
    var name: String,
    var cover_path: String,
    var last_page: Int,
    var total_pages: Int,
    var last_modified: Long
) : Parcelable
