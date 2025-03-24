package com.kepsake.mizu2.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kepsake.mizu2.data.models.MangaFile
import com.kepsake.mizu2.data.models.MangaFileDao
import com.kepsake.mizu2.data.models.MangaPanel
import com.kepsake.mizu2.data.models.MangaPanelDao

@Database(entities = [MangaFile::class, MangaPanel::class], version = 1, exportSchema = false)
abstract class MangaDatabase : RoomDatabase() {
    abstract fun mangaFileDao(): MangaFileDao
    abstract fun mangaPanelDao(): MangaPanelDao

    companion object {
        @Volatile
        private var INSTANCE: MangaDatabase? = null

        fun getDatabase(context: Context): MangaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MangaDatabase::class.java,
                    "manga_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}