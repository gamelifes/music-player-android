/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlacklistDao {

    @Query("SELECT songId FROM blacklist ORDER BY addedAt DESC")
    fun observeAllIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BlacklistEntity)

    @Query("DELETE FROM blacklist WHERE songId = :songId")
    suspend fun delete(songId: Long)
}
