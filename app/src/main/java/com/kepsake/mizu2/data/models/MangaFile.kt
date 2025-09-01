package com.kepsake.mizu2.data.models

import android.os.Parcelable
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.parcelize.Parcelize


@Parcelize
@Entity(tableName = "manga_files", indices = [Index(value = ["path"], unique = true)])
data class MangaFile(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var path: String,
    var name: String,
    var cover_path: String,
    var current_progress: Int,
    var total_pages: Int,
    var last_modified: Long
) : Parcelable

@Dao
interface MangaFileDao {
    @Query("SELECT * FROM manga_files")
    suspend fun getAll(): List<MangaFile>

    @Query("SELECT * FROM manga_files WHERE id = :id")
    suspend fun getById(id: Long): MangaFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(mangaFile: MangaFile): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(mangaFiles: List<MangaFile>)

    @Delete
    suspend fun delete(mangaFile: MangaFile)

    @Query("DELETE FROM manga_files WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query(
        """
        DELETE FROM manga_files 
        WHERE id NOT IN (
            SELECT MIN(id) 
            FROM manga_files 
            GROUP BY path
        )
        AND path IN (
            SELECT path 
            FROM manga_files 
            GROUP BY path 
            HAVING COUNT(*) > 1
        )
    """
    )
    suspend fun deleteDuplicatePaths()

}
