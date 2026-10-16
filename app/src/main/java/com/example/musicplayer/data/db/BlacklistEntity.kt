/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 黑名单：只存 MediaStore 的 songId，加黑后歌曲从曲库和所有歌单消失（记录保留，移出即恢复） */
@Entity(tableName = "blacklist")
data class BlacklistEntity(
    @PrimaryKey val songId: Long,
    val addedAt: Long = System.currentTimeMillis()
)
