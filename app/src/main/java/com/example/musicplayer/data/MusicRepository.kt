/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.data

import com.example.musicplayer.data.db.BlacklistDao
import com.example.musicplayer.data.db.BlacklistEntity
import com.example.musicplayer.data.db.PlaylistDao
import com.example.musicplayer.data.db.PlaylistEntity
import com.example.musicplayer.data.db.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 数据仓库：封装黑名单与歌单的 Room 访问。
 * 歌曲本体不落库——启动时扫描 MediaStore，把 songId 解析回 Song。
 */
class MusicRepository(
    private val blacklistDao: BlacklistDao,
    private val playlistDao: PlaylistDao
) {

    // ------------------------------------------------------------ 黑名单

    fun observeBlacklistIds(): Flow<List<Long>> = blacklistDao.observeAllIds()

    suspend fun blacklist(songId: Long) = blacklistDao.insert(BlacklistEntity(songId))

    /**
     * 拉黑 + 同步从所有歌单移除该曲。
     * 合并在一个事务里，避免「黑名单有了但歌单还在放」的中间态。
     */
    suspend fun blacklistAndRemoveFromPlaylists(songId: Long) {
        blacklistDao.insert(BlacklistEntity(songId))
        playlistDao.removeSongFromAllPlaylists(songId)
    }

    suspend fun unblacklist(songId: Long) = blacklistDao.delete(songId)

    // ------------------------------------------------------------ 歌单

    fun observePlaylists(): Flow<List<PlaylistEntity>> = playlistDao.observePlaylists()

    fun observeSongCounts(): Flow<Map<Long, Int>> =
        playlistDao.observeSongCounts().map { list -> list.associate { it.playlistId to it.songCount } }

    fun observePlaylistSongIds(playlistId: Long): Flow<List<Long>> =
        playlistDao.observeSongIds(playlistId)

    /** 一次性读取歌单曲目 id，用于启动恢复队列（不建 Flow） */
    suspend fun playlistSongIdsSnapshot(playlistId: Long): List<Long> =
        playlistDao.songIdsSnapshot(playlistId)

    suspend fun createPlaylist(name: String): Long =
        playlistDao.insert(PlaylistEntity(name = name.trim()))

    suspend fun deletePlaylist(id: Long) = playlistDao.delete(id)

    suspend fun renamePlaylist(id: Long, name: String) = playlistDao.rename(id, name.trim())

    suspend fun addToPlaylist(playlistId: Long, songId: Long) {
        playlistDao.addSong(
            PlaylistSongEntity(playlistId = playlistId, songId = songId, sortOrder = nextOrder(playlistId))
        )
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: Long) =
        playlistDao.removeSong(playlistId, songId)

    private suspend fun nextOrder(playlistId: Long): Int = playlistDao.nextSortOrder(playlistId)
}
