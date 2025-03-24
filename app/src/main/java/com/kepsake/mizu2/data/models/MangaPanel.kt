package com.kepsake.mizu2.data.models

import android.os.Parcelable
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.parcelize.Parcelize


@Parcelize
@Entity(
    tableName = "manga_panels",
    indices = [Index(value = ["manga_id"])]
)
data class MangaPanel(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    val manga_id: Long,
    val page_name: String,
    val aspect_ratio: Float
) : Parcelable

@Dao
interface MangaPanelDao {
    @Query("SELECT * FROM manga_panels WHERE manga_id = :mangaId")
    suspend fun getPagesForManga(mangaId: Long): List<MangaPanel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(mangaPanels: List<MangaPanel>)
}
