/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.example.musicplayer.data.MusicRepository
import com.example.musicplayer.data.PlaybackPrefs
import com.example.musicplayer.data.db.MusicDatabase

class MusicApp : Application() {
    companion object {
        lateinit var instance: MusicApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    val database: MusicDatabase by lazy {
        Room.databaseBuilder(this, MusicDatabase::class.java, MusicDatabase.NAME).build()
    }

    val repository: MusicRepository by lazy {
        MusicRepository(database.blacklistDao(), database.playlistDao())
    }

    val playbackPrefs: PlaybackPrefs by lazy {
        PlaybackPrefs(this)
    }

    fun getAppContext(): Context = this
}
