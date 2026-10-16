/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BlacklistEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun blacklistDao(): BlacklistDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val NAME = "music.db"
    }
}
