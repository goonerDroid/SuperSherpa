package com.sublime.supersherpa.core.history.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TranscriptHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SuperSherpaDatabase : RoomDatabase() {
    abstract fun transcriptHistoryDao(): TranscriptHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: SuperSherpaDatabase? = null

        fun getInstance(context: Context): SuperSherpaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SuperSherpaDatabase::class.java,
                    "supersherpa.db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}
