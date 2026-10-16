/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** 歌单曲目：歌单删除时级联删除曲目。songId 不加外键（MediaStore id 可能在 rescan 后变化） */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: Long,
    val sortOrder: Int
)
