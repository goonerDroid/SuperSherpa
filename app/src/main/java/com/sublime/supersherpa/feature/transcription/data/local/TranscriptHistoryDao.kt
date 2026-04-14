package com.sublime.supersherpa.feature.transcription.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptHistoryDao {
    @Query("SELECT * FROM transcript_history ORDER BY createdAtEpochMillis DESC")
    fun observeHistory(): Flow<List<TranscriptHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: TranscriptHistoryEntity)

    @Query("DELETE FROM transcript_history WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query(
        """
        DELETE FROM transcript_history
        WHERE id NOT IN (
            SELECT id FROM transcript_history
            ORDER BY createdAtEpochMillis DESC
            LIMIT :limit
        )
        """,
    )
    suspend fun trimToLimit(limit: Int)
}
