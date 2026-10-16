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
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY sortOrder")
    fun observeSongIds(playlistId: Long): Flow<List<Long>>

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY sortOrder")
    suspend fun songIdsSnapshot(playlistId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSong(entity: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)

    /** 从所有歌单中移除该曲（拉黑时同步清理，避免黑名单曲仍留在歌单里） */
    @Query("DELETE FROM playlist_songs WHERE songId = :songId")
    suspend fun removeSongFromAllPlaylists(songId: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun nextSortOrder(playlistId: Long): Int

    /** 每个歌单的曲目数，用于歌单列表展示 */
    @Query("SELECT playlistId, COUNT(songId) AS songCount FROM playlist_songs GROUP BY playlistId")
    fun observeSongCounts(): Flow<List<PlaylistSongCount>>
}

data class PlaylistSongCount(
    val playlistId: Long,
    val songCount: Int
)
